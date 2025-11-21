package dev.bouncingelf10.timelesslib.api.countdown;

import dev.bouncingelf10.timelesslib.TimelessClock;
import dev.bouncingelf10.timelesslib.api.time.Duration;
import dev.bouncingelf10.timelesslib.api.time.TimeFormatter;
import dev.bouncingelf10.timelesslib.fabric.TimelessFabricHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

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

    public CountdownManager(Supplier<T> contextProvider) {
        this(contextProvider, Math.max(1, Runtime.getRuntime().availableProcessors()));
    }

    public CountdownManager(Supplier<T> contextProvider, int poolSize) {
        this.contextProvider = Objects.requireNonNull(contextProvider);
        this.executor = new ScheduledThreadPoolExecutor(poolSize);
        this.executor.setRemoveOnCancelPolicy(true);
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
        private final Duration tickEvery;
        private final long tickNanos;

        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean paused = new AtomicBoolean(false);

        private volatile long endTimeNanos;
        private volatile long remainingOnPause = -1L;

        private volatile ScheduledFuture<?> future;

        private final List<BiConsumer<T, Duration>> onTick = Collections.synchronizedList(new ArrayList<>());
        private final List<Consumer<T>> onFinish = Collections.synchronizedList(new ArrayList<>());
        private final NavigableMap<Long, List<Consumer<T>>> thresholds = new ConcurrentSkipListMap<>(Collections.reverseOrder());
        private final Map<Long, List<Consumer<T>>> intervalHandlers = new ConcurrentHashMap<>();
        private final Map<Long, Long> lastIntervalFire = new ConcurrentHashMap<>();

        Countdown(Duration total, Duration tickEvery, TimelessClock.TimeSource timeSource) {
            this.total = total;
            this.totalNanos = total.toNanos();
            this.tickEvery = tickEvery;
            this.tickNanos = tickEvery.toNanos();
            this.timeSource = timeSource;
        }

        private void start() {
            long now = timeSource.now();
            this.endTimeNanos = now + totalNanos;
            scheduleNextTick();
        }

        private void scheduleNextTick() {
            if (cancelled.get()) return;
            long now = timeSource.now();
            long remaining = Math.max(0L, endTimeNanos - now);

            long delay = Math.min(tickNanos, Math.max(0L, remaining));
            future = executor.schedule(this::runTick, delay, TimeUnit.NANOSECONDS);
        }

        private void runTick() {
            if (cancelled.get()) return;

            if (paused.get()) {
                long now = timeSource.now();
                remainingOnPause = Math.max(0L, endTimeNanos - now);
                return;
            }

            long now = timeSource.now();
            long remaining = Math.max(0L, endTimeNanos - now);
            Duration remainingDuration = Duration.ofNanos(remaining);

            T ctx = contextProvider.get();
            try {
                synchronized (onTick) {
                    for (BiConsumer<T, Duration> c : onTick) {
                        try { c.accept(ctx, remainingDuration); } catch (Throwable t) { t.printStackTrace(); }
                    }
                }
            } catch (Throwable ignored) {}

            for (var entry : intervalHandlers.entrySet()) {
                long interval = entry.getKey();

                long last = lastIntervalFire.get(interval);
                if (remaining <= last - interval) {
                    for (Consumer<T> h : entry.getValue()) {
                        try { h.accept(ctx); } catch (Throwable t) { t.printStackTrace(); }
                    }
                    lastIntervalFire.put(interval, remaining);
                }
            }

            if (!thresholds.isEmpty()) {
                Iterator<Map.Entry<Long, List<Consumer<T>>>> it = thresholds.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Long, List<Consumer<T>>> e = it.next();
                    long thresholdNanos = e.getKey();
                    if (remaining <= thresholdNanos) {
                        List<Consumer<T>> handlers = e.getValue();
                        for (Consumer<T> h : handlers) {
                            try { h.accept(ctx); } catch (Throwable t) { t.printStackTrace(); }
                        }
                        it.remove();
                    }
                }
            }

            if (remaining == 0L) {
                try {
                    synchronized (onFinish) {
                        for (Consumer<T> f : onFinish) {
                            try { f.accept(ctx); } catch (Throwable t) { t.printStackTrace(); }
                        }
                    }
                } finally {
                    active.remove(id);
                }
                return;
            }

            scheduleNextTick();
        }

        public boolean cancel() {
            boolean prev = cancelled.getAndSet(true);
            if (prev) return false;
            if (future != null) future.cancel(false);
            active.remove(id);
            return true;
        }

        private void cancelSilently() {
            cancelled.set(true);
            if (future != null) future.cancel(false);
        }

        public boolean pause() {
            if (cancelled.get()) return false;
            boolean ok = paused.compareAndSet(false, true);
            if (!ok) return false;
            ScheduledFuture<?> f = future;
            if (f != null && !f.isDone()) f.cancel(false);
            long now = timeSource.now();
            remainingOnPause = Math.max(0L, endTimeNanos - now);
            return true;
        }

        public boolean resume() {
            if (cancelled.get()) return false;
            boolean ok = paused.compareAndSet(true, false);
            if (!ok) return false;
            long now = timeSource.now();
            if (remainingOnPause < 0) remainingOnPause = 0;
            endTimeNanos = now + remainingOnPause;
            remainingOnPause = -1;
            scheduleNextTick();
            return true;
        }

        public boolean isCancelled() { return cancelled.get(); }
        public boolean isPaused() { return paused.get(); }

        public Duration remaining() {
            if (paused.get() && remainingOnPause >= 0) return Duration.ofNanos(remainingOnPause);
            long now = timeSource.now();
            return Duration.ofNanos(Math.max(0L, endTimeNanos - now));
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
            long n = threshold.toNanos();
            thresholds.computeIfAbsent(n, k -> Collections.synchronizedList(new ArrayList<>())).add(handler);
            return this;
        }
        
        public Countdown every(Duration interval, Consumer<T> handler) {
            long nanos = interval.toNanos();
            intervalHandlers.computeIfAbsent(nanos, k -> Collections.synchronizedList(new ArrayList<>())).add(handler);
            lastIntervalFire.putIfAbsent(nanos, totalNanos);
            return this;
        }

        public Countdown displayToUser(ServerPlayer player) {
            return every(Duration.ofMillis(10), server -> TimelessFabricHelper.serverDisplayToUser(remaining().toNanos(), player));
        }

        public Countdown displayToUser(ServerPlayer player, TimeFormatter.TimeFormat timeFormat, String prefix, String suffix) {
            return every(Duration.ofMillis(10), server -> TimelessFabricHelper.serverDisplayToUser(remaining().toNanos(), player, timeFormat, prefix, suffix));
        }

        public Countdown displayAllUsers() {
            return every(Duration.ofMillis(10), server -> TimelessFabricHelper.serverDisplayAllUsers(remaining().toNanos()));
        }

        public Countdown displayAllUsers(TimeFormatter.TimeFormat timeFormat, String prefix, String suffix) {
            return every(Duration.ofMillis(10), server -> TimelessFabricHelper.serverDisplayAllUsers(remaining().toNanos(), timeFormat, prefix, suffix));
        }

        public Countdown displayNearbyUsers(Vec3 pos, float radius) {
            return every(Duration.ofMillis(10), server -> TimelessFabricHelper.serverDisplayNearbyUsers(remaining().toNanos(), pos, radius));
        }

        public Countdown displayNearbyUsers(Vec3 pos, float radius, TimeFormatter.TimeFormat timeFormat, String prefix, String suffix) {
            return every(Duration.ofMillis(10), server -> TimelessFabricHelper.serverDisplayNearbyUsers(remaining().toNanos(), pos, radius, timeFormat, prefix, suffix));
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