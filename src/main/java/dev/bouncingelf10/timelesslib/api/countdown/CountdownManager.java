package dev.bouncingelf10.timelesslib.api.countdown;

import dev.bouncingelf10.timelesslib.TimelessClock;
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
    private final Map<String, Countdown> active = new ConcurrentHashMap<>();
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
        this(contextProvider, detectExecuteDispatcher(contextProvider));
    }

    public CountdownManager(Supplier<T> contextProvider, int poolSize) {
        this(contextProvider, detectExecuteDispatcher(contextProvider), poolSize);
    }

    private static <T> BiConsumer<T, Runnable> detectExecuteDispatcher(Supplier<T> ctxSupplier) {
        T ctx = ctxSupplier.get();
        if (ctx == null) throw new IllegalArgumentException("Context provider returned null when probing for execute(Runnable). Provide an explicit dispatcher instead.");
        try {
            Method m = ctx.getClass().getMethod("execute", Runnable.class);
            m.setAccessible(true);
            return (context, runnable) -> {
                try { m.invoke(context, runnable); }
                catch (RuntimeException re) { throw re; }
                catch (Exception e) { throw new RuntimeException(e); }
            };
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Context type " + ctx.getClass().getName() + " does not expose execute(Runnable). Provide explicit dispatcher.");
        }
    }

    public Countdown start(Duration total) {
        Objects.requireNonNull(total);
        return start(total, Duration.ofMillis(50), TimelessClock.TimeSources.GAME_TIME); // default tick 50ms (20 TPS friendly)
    }

    public Countdown startRealtime(Duration total) {
        Objects.requireNonNull(total);
        return start(total, Duration.ofMillis(50), TimelessClock.TimeSources.REAL_TIME);
    }

    public Countdown start(Duration total, Duration tickEvery, TimelessClock.TimeSource timeSource) {
        Objects.requireNonNull(total);
        Objects.requireNonNull(tickEvery);
        Objects.requireNonNull(timeSource);

        Countdown cd = new Countdown(total, tickEvery, timeSource);
        active.put(cd.id(), cd);
        cd.start();
        return cd;
    }

    public Optional<Countdown> get(String id) {
        return Optional.ofNullable(active.get(id));
    }

    public void shutdown() {
        active.values().forEach(Countdown::cancelSilently);
        active.clear();
        executor.shutdownNow();
    }

    public void shutdownGracefully(long timeout, TimeUnit unit) throws InterruptedException {
        active.values().forEach(Countdown::cancelSilently);
        executor.shutdown();
        executor.awaitTermination(timeout, unit);
    }

    public class Countdown {
        private final String id = UUID.randomUUID().toString();

        private final TimelessClock.TimeSource timeSource;
        private final Duration total;
        private final long totalNanos;
        private final long tickNanos;

        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean paused = new AtomicBoolean(false);

        private volatile long endTimeNanos;
        private volatile long remainingOnPause = -1L;

        private volatile ScheduledFuture<?> future;

        private final CopyOnWriteArrayList<BiConsumer<T, Duration>> onTick = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<Consumer<T>> onFinish = new CopyOnWriteArrayList<>();
        private final NavigableMap<Long, List<Consumer<T>>> thresholds = new ConcurrentSkipListMap<>();
        private final Map<Long, List<Consumer<T>>> intervalHandlers = new ConcurrentHashMap<>();
        private final Map<Long, Long> nextElapsedToFire = new ConcurrentHashMap<>();

        private final AtomicBoolean finished = new AtomicBoolean(false);

        Countdown(Duration total, Duration tickEvery, TimelessClock.TimeSource timeSource) {
            this.total = total;
            this.totalNanos = total.toNanos();
            this.tickNanos = Math.max(1L, tickEvery.toNanos()); // avoid zero
            this.timeSource = timeSource;
        }

        private void start() {
            long now = timeSource.now();
            this.endTimeNanos = now + totalNanos;
            for (Long interval : intervalHandlers.keySet()) {
                nextElapsedToFire.putIfAbsent(interval, interval);
            }
            scheduleNextTick();
        }

        private void scheduleNextTick() {
            if (cancelled.get() || finished.get()) return;
            long now = timeSource.now();
            long remaining = Math.max(0L, endTimeNanos - now);
            long delay = Math.min(tickNanos, Math.max(0L, remaining));
            future = executor.schedule(() -> {
                T ctx = contextProvider.get();
                if (ctx == null) return;
                try {
                    mainThreadDispatcher.accept(ctx, this::runTickOnMain);
                } catch (Throwable t) {
                    t.printStackTrace();
                    try { runTickOnMain(); } catch (Throwable ignored) {}
                }
            }, delay, TimeUnit.NANOSECONDS);
        }

        private void runTickOnMain() {
            if (cancelled.get() || finished.get()) return;

            if (paused.get()) {
                return;
            }

            long now = timeSource.now();
            long remaining = Math.max(0L, endTimeNanos - now);
            Duration remainingDuration = Duration.ofNanos(remaining);
            Duration elapsedDuration = Duration.ofNanos(Math.max(0L, totalNanos - remaining));
            long elapsed = Math.max(0L, totalNanos - remaining);

            T ctx = contextProvider.get();
            if (ctx == null) {
                cancel();
                return;
            }

            for (BiConsumer<T, Duration> handler : onTick) {
                try { handler.accept(ctx, remainingDuration); } catch (Throwable t) { t.printStackTrace(); }
            }

            if (!intervalHandlers.isEmpty()) {
                for (Map.Entry<Long, List<Consumer<T>>> e : intervalHandlers.entrySet()) {
                    long interval = e.getKey();
                    long nextToFire = nextElapsedToFire.getOrDefault(interval, interval);
                    while (elapsed >= nextToFire) {
                        for (Consumer<T> h : e.getValue()) {
                            try { h.accept(ctx); } catch (Throwable t) { t.printStackTrace(); }
                        }
                        nextToFire += interval;
                        nextElapsedToFire.put(interval, nextToFire);
                    }
                }
            }

            if (!thresholds.isEmpty()) {
                var toFire = thresholds.tailMap(remaining, true);
                if (!toFire.isEmpty()) {
                    var keys = new ArrayList<>(toFire.keySet());
                    for (Long k : keys) {
                        List<Consumer<T>> handlers = thresholds.remove(k);
                        if (handlers == null) continue;
                        for (Consumer<T> h : handlers) {
                            try { h.accept(ctx); } catch (Throwable t) { t.printStackTrace(); }
                        }
                    }
                }
            }

            if (remaining == 0L) {
                if (finished.compareAndSet(false, true)) {
                    try {
                        for (Consumer<T> f : onFinish) {
                            try { f.accept(ctx); } catch (Throwable t) { t.printStackTrace(); }
                        }
                    } finally {
                        active.remove(id);
                    }
                }
                return;
            }

            scheduleNextTick();
        }

        public boolean cancel() {
            if (cancelled.getAndSet(true)) return false;
            if (future != null) future.cancel(false);
            active.remove(id);
            return true;
        }

        private void cancelSilently() {
            cancelled.set(true);
            if (future != null) future.cancel(false);
        }

        public boolean pause() {
            if (cancelled.get() || finished.get()) return false;
            if (!paused.compareAndSet(false, true)) return false;
            if (future != null && !future.isDone()) future.cancel(false);
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

        public String id() { return id; }

        public Countdown onTick(BiConsumer<T, Duration> tickHandler) {
            Objects.requireNonNull(tickHandler);
            onTick.add(tickHandler);
            return this;
        }

        public Countdown onFinish(Consumer<T> finishHandler) {
            Objects.requireNonNull(finishHandler);
            onFinish.add(finishHandler);
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

        public Countdown displayNearbyUsers(Vec3 pos, float radius) {
            return every(Duration.TICK, server -> TimelessFabricHelper.serverDisplayNearbyUsers(remaining().toNanos(), pos, radius));
        }

        public Countdown displayNearbyUsers(Vec3 pos, float radius, TimeFormatter.TimeFormat timeFormat, String prefix, String suffix) {
            return every(Duration.TICK, server -> TimelessFabricHelper.serverDisplayNearbyUsers(remaining().toNanos(), pos, radius, timeFormat, prefix, suffix));
        }

        public Countdown displayNearbyUsers(ServerPlayer player, float radius) {
            return displayNearbyUsers(player.position(), radius);
        }

        public Countdown displayNearbyUsers(ServerPlayer player, float radius, TimeFormatter.TimeFormat timeFormat, String prefix, String suffix) {
            return displayNearbyUsers(player.position(), radius, timeFormat, prefix, suffix);
        }

        public Countdown displayNearbyUsers(BlockPos pos, float radius) {
            return displayNearbyUsers(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), radius);
        }

        public Countdown displayNearbyUsers(BlockPos pos, float radius, TimeFormatter.TimeFormat timeFormat, String prefix, String suffix) {
            return displayNearbyUsers(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), radius, timeFormat, prefix, suffix);
        }

        public Countdown displayNearbyUsers(double x, double y, double z, float radius) {
            return displayNearbyUsers(new Vec3(x, y, z), radius);
        }

        public Countdown displayNearbyUsers(double x, double y, double z, float radius, TimeFormatter.TimeFormat timeFormat, String prefix, String suffix) {
            return displayNearbyUsers(new Vec3(x, y, z), radius, timeFormat, prefix, suffix);
        }
    }
}
