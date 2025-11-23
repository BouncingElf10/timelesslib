package dev.bouncingelf10.timelesslib.api.countdown;

import dev.bouncingelf10.timelesslib.TimelessClock;
import dev.bouncingelf10.timelesslib.TimelessLib;
import dev.bouncingelf10.timelesslib.api.time.Duration;
import dev.bouncingelf10.timelesslib.api.time.TimeFormatter;
import dev.bouncingelf10.timelesslib.fabric.TimelessFabricHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CountdownManager<T> {
    private final ScheduledThreadPoolExecutor executor;
    private final Map<String, Countdown> activeCountdowns = new ConcurrentHashMap<>();
    private final Supplier<T> contextProvider;
    private final BiConsumer<T, Runnable> mainThreadDispatcher;

    public CountdownManager(Supplier<T> contextProvider, BiConsumer<T, Runnable> mainThreadDispatcher) {
        this(contextProvider, mainThreadDispatcher, Math.max(1, Runtime.getRuntime().availableProcessors()));
    }

    public CountdownManager(Supplier<T> contextProvider, BiConsumer<T, Runnable> mainThreadDispatcher, int poolSize) {
        this.contextProvider = Objects.requireNonNull(contextProvider, "contextProvider");
        this.mainThreadDispatcher = Objects.requireNonNull(mainThreadDispatcher, "mainThreadDispatcher");
        this.executor = new ScheduledThreadPoolExecutor(poolSize);
        this.executor.setRemoveOnCancelPolicy(true);
    }

    public CountdownManager(Supplier<T> contextProvider) {
        this(contextProvider, detectDispatcher(contextProvider));
    }

    public CountdownManager(Supplier<T> contextProvider, int poolSize) {
        this(contextProvider, detectDispatcher(contextProvider), poolSize);
    }

    private static <T> BiConsumer<T, Runnable> detectDispatcher(Supplier<T> contextSupplier) {
        T context = contextSupplier.get();
        if (context == null) throw new IllegalArgumentException("Context provider returned null when probing for execute(Runnable). Provide an explicit dispatcher instead.");
        try {
            Method executeMethod = context.getClass().getMethod("execute", Runnable.class);
            executeMethod.setAccessible(true);
            return (ctx, runnable) -> {
                try { executeMethod.invoke(ctx, runnable); }
                catch (RuntimeException re) { throw re; }
                catch (Exception e) {
                    TimelessLib.LOGGER.error("Failed to dispatch task to main thread", e);
                    throw new RuntimeException(e);
                }
            };
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Context type " + context.getClass().getName() + " does not expose execute(Runnable). Provide explicit dispatcher.", e);
        }
    }

    public Countdown start(Duration totalDuration) {
        Objects.requireNonNull(totalDuration);
        return start(totalDuration, Duration.ofMillis(50), TimelessClock.TimeSources.GAME_TIME);
    }

    public Countdown startRealtime(Duration totalDuration) {
        Objects.requireNonNull(totalDuration);
        return start(totalDuration, Duration.ofMillis(50), TimelessClock.TimeSources.REAL_TIME);
    }

    public Countdown start(Duration totalDuration, Duration tickInterval, TimelessClock.TimeSource timeSource) {
        Objects.requireNonNull(totalDuration);
        Objects.requireNonNull(tickInterval);
        Objects.requireNonNull(timeSource);

        Countdown countdown = new Countdown(totalDuration, tickInterval, timeSource);
        activeCountdowns.put(countdown.getId(), countdown);
        countdown.start();
        return countdown;
    }

    public Optional<Countdown> get(String id) {
        return Optional.ofNullable(activeCountdowns.get(id));
    }

    public void shutdown() {
        activeCountdowns.values().forEach(Countdown::cancelSilently);
        activeCountdowns.clear();
        executor.shutdownNow();
    }

    public void shutdownGracefully(long timeout, TimeUnit unit) throws InterruptedException {
        activeCountdowns.values().forEach(Countdown::cancelSilently);
        executor.shutdown();
        if (!executor.awaitTermination(timeout, unit)) {
            TimelessLib.LOGGER.warn("CountdownManager did not shutdown gracefully within the timeout");
            executor.shutdownNow();
        }
    }

    public class Countdown {
        private final String id = UUID.randomUUID().toString();
        private final TimelessClock.TimeSource timeSource;
        private final Duration totalDuration;
        private final long totalNanos;
        private final long tickNanos;

        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean paused = new AtomicBoolean(false);
        private final AtomicBoolean finished = new AtomicBoolean(false);

        private volatile long endTimeNanos;
        private volatile long remainingOnPause = -1L;
        private volatile ScheduledFuture<?> scheduledTask;

        private final CopyOnWriteArrayList<BiConsumer<T, Duration>> tickHandlers = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<Consumer<T>> finishHandlers = new CopyOnWriteArrayList<>();
        private final NavigableMap<Long, List<Consumer<T>>> thresholds = new ConcurrentSkipListMap<>();
        private final Map<Long, List<Consumer<T>>> intervalHandlers = new ConcurrentHashMap<>();
        private final Map<Long, Long> nextElapsedToFire = new ConcurrentHashMap<>();

        Countdown(Duration totalDuration, Duration tickInterval, TimelessClock.TimeSource timeSource) {
            this.totalDuration = totalDuration;
            this.totalNanos = totalDuration.toNanos();
            this.tickNanos = Math.max(1L, tickInterval.toNanos());
            this.timeSource = timeSource;
        }

        private void start() {
            this.endTimeNanos = timeSource.now() + totalNanos;
            intervalHandlers.keySet().forEach(interval -> nextElapsedToFire.putIfAbsent(interval, interval));
            scheduleNextTick();
        }

        private void scheduleNextTick() {
            if (cancelled.get() || finished.get()) return;

            long remainingNanos = Math.max(0L, endTimeNanos - timeSource.now());
            long delayNanos = Math.min(tickNanos, remainingNanos);

            scheduledTask = executor.schedule(() -> {
                T context = contextProvider.get();
                if (context == null) {
                    TimelessLib.LOGGER.warn("Context provider returned null during countdown tick, cancelling countdown {}", id);
                    cancel();
                    return;
                }
                try {
                    mainThreadDispatcher.accept(context, this::runTickOnMainThread);
                } catch (Throwable t) {
                    TimelessLib.LOGGER.error("Error dispatching countdown tick for {}", id, t);
                    try { runTickOnMainThread(); } catch (Throwable inner) {
                        TimelessLib.LOGGER.error("Error running tick directly for {}", id, inner);
                    }
                }
            }, delayNanos, TimeUnit.NANOSECONDS);
        }

        private void runTickOnMainThread() {
            if (cancelled.get() || finished.get() || paused.get()) return;

            long now = timeSource.now();
            long remainingNanos = Math.max(0L, endTimeNanos - now);
            Duration remainingDuration = Duration.ofNanos(remainingNanos);
            Duration elapsedDuration = Duration.ofNanos(Math.max(0L, totalNanos - remainingNanos));
            long elapsedNanos = Math.max(0L, totalNanos - remainingNanos);

            T context = contextProvider.get();
            if (context == null) {
                TimelessLib.LOGGER.warn("Context provider returned null during countdown tick, cancelling countdown {}", id);
                cancel();
                return;
            }

            tickHandlers.forEach(handler -> {
                try { handler.accept(context, remainingDuration); }
                catch (Throwable t) { TimelessLib.LOGGER.error("Error in tick handler for {}", id, t); }
            });

            intervalHandlers.forEach((interval, handlers) -> {
                long nextFire = nextElapsedToFire.getOrDefault(interval, interval);
                while (elapsedNanos >= nextFire) {
                    for (Consumer<T> handler : handlers) {
                        try { handler.accept(context); }
                        catch (Throwable t) { TimelessLib.LOGGER.error("Error in interval handler for {} at interval {}", id, interval, t); }
                    }
                    nextFire += interval;
                    nextElapsedToFire.put(interval, nextFire);
                }
            });

            if (!thresholds.isEmpty()) {
                var toFire = thresholds.tailMap(remainingNanos, true);
                if (!toFire.isEmpty()) {
                    new ArrayList<>(toFire.keySet()).forEach(key -> {
                        List<Consumer<T>> handlers = thresholds.remove(key);
                        if (handlers != null) {
                            handlers.forEach(handler -> {
                                try { handler.accept(context); }
                                catch (Throwable t) { TimelessLib.LOGGER.error("Error in threshold handler for {} at {}", id, key, t); }
                            });
                        }
                    });
                }
            }

            if (remainingNanos == 0L && finished.compareAndSet(false, true)) {
                try {
                    finishHandlers.forEach(handler -> {
                        try { handler.accept(context); }
                        catch (Throwable t) { TimelessLib.LOGGER.error("Error in finish handler for {}", id, t); }
                    });
                } finally {
                    activeCountdowns.remove(id);
                }
                return;
            }

            scheduleNextTick();
        }

        public boolean cancel() {
            if (cancelled.getAndSet(true)) return false;
            if (scheduledTask != null) scheduledTask.cancel(false);
            activeCountdowns.remove(id);
            return true;
        }

        private void cancelSilently() {
            cancelled.set(true);
            if (scheduledTask != null) scheduledTask.cancel(false);
        }

        public boolean pause() {
            if (cancelled.get() || finished.get()) return false;
            if (!paused.compareAndSet(false, true)) return false;
            if (scheduledTask != null && !scheduledTask.isDone()) scheduledTask.cancel(false);
            remainingOnPause = Math.max(0L, endTimeNanos - timeSource.now());
            return true;
        }

        public boolean resume() {
            if (cancelled.get() || finished.get()) return false;
            if (!paused.compareAndSet(true, false)) return false;
            if (remainingOnPause < 0) remainingOnPause = 0;
            endTimeNanos = timeSource.now() + remainingOnPause;
            remainingOnPause = -1;
            scheduleNextTick();
            return true;
        }

        public boolean isCancelled() { return cancelled.get(); }
        public boolean isPaused() { return paused.get(); }
        public boolean isFinished() { return finished.get(); }

        public Duration remaining() {
            if (paused.get() && remainingOnPause >= 0) return Duration.ofNanos(remainingOnPause);
            return Duration.ofNanos(Math.max(0L, endTimeNanos - timeSource.now()));
        }

        public String getId() { return id; }

        public Countdown onTick(BiConsumer<T, Duration> handler) {
            Objects.requireNonNull(handler);
            tickHandlers.add(handler);
            return this;
        }

        public Countdown onFinish(Consumer<T> handler) {
            Objects.requireNonNull(handler);
            finishHandlers.add(handler);
            return this;
        }

        public Countdown onThreshold(Duration threshold, Consumer<T> handler) {
            Objects.requireNonNull(threshold);
            Objects.requireNonNull(handler);
            thresholds.computeIfAbsent(threshold.toNanos(), k -> Collections.synchronizedList(new ArrayList<>())).add(handler);
            return this;
        }

        public Countdown every(Duration interval, Consumer<T> handler) {
            Objects.requireNonNull(interval);
            Objects.requireNonNull(handler);
            long nanos = Math.max(1L, interval.toNanos());
            intervalHandlers.computeIfAbsent(nanos, k -> Collections.synchronizedList(new ArrayList<>())).add(handler);
            nextElapsedToFire.putIfAbsent(nanos, nanos);
            return this;
        }

        public Countdown displayToUser(ServerPlayer player) {
            return every(Duration.TICK, server -> TimelessFabricHelper.serverDisplayToUser(remaining().toNanos(), player));
        }

        public Countdown displayToUser(ServerPlayer player, TimeFormatter.TimeFormat timeFormat, String prefix, String suffix) {
            return every(Duration.TICK, server -> TimelessFabricHelper.serverDisplayToUser(remaining().toNanos(), player, timeFormat, prefix, suffix));
        }

        public Countdown displayAllUsers() {
            return every(Duration.TICK, server -> TimelessFabricHelper.serverDisplayAllUsers(remaining().toNanos()));
        }

        public Countdown displayAllUsers(TimeFormatter.TimeFormat timeFormat, String prefix, String suffix) {
            return every(Duration.TICK, server -> TimelessFabricHelper.serverDisplayAllUsers(remaining().toNanos(), timeFormat, prefix, suffix));
        }

        public Countdown displayNearbyUsers(Vec3 position, float radius) {
            return every(Duration.TICK, server -> TimelessFabricHelper.serverDisplayNearbyUsers(remaining().toNanos(), position, radius));
        }

        public Countdown displayNearbyUsers(Vec3 position, float radius, TimeFormatter.TimeFormat timeFormat, String prefix, String suffix) {
            return every(Duration.TICK, server -> TimelessFabricHelper.serverDisplayNearbyUsers(remaining().toNanos(), position, radius, timeFormat, prefix, suffix));
        }

        public Countdown displayNearbyUsers(ServerPlayer player, float radius) {
            return displayNearbyUsers(player.position(), radius);
        }

        public Countdown displayNearbyUsers(ServerPlayer player, float radius, TimeFormatter.TimeFormat timeFormat, String prefix, String suffix) {
            return displayNearbyUsers(player.position(), radius, timeFormat, prefix, suffix);
        }

        public Countdown displayNearbyUsers(BlockPos blockPos, float radius) {
            return displayNearbyUsers(new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5), radius);
        }

        public Countdown displayNearbyUsers(BlockPos blockPos, float radius, TimeFormatter.TimeFormat timeFormat, String prefix, String suffix) {
            return displayNearbyUsers(new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5), radius, timeFormat, prefix, suffix);
        }

        public Countdown displayNearbyUsers(double x, double y, double z, float radius) {
            return displayNearbyUsers(new Vec3(x, y, z), radius);
        }

        public Countdown displayNearbyUsers(double x, double y, double z, float radius, TimeFormatter.TimeFormat timeFormat, String prefix, String suffix) {
            return displayNearbyUsers(new Vec3(x, y, z), radius, timeFormat, prefix, suffix);
        }
    }
}