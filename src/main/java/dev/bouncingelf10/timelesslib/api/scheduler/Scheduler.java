package dev.bouncingelf10.timelesslib.api.scheduler;

import dev.bouncingelf10.timelesslib.TimelessClock;
import dev.bouncingelf10.timelesslib.TimelessLib;
import dev.bouncingelf10.timelesslib.api.time.Duration;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Scheduler<T> {
    private final ScheduledThreadPoolExecutor executor;
    private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private final Supplier<T> contextProvider;
    private final ErrorHandler errorHandler;

    public static class Config {
        public int poolSize = Math.max(1, Runtime.getRuntime().availableProcessors());
        public ThreadFactory threadFactory = Executors.defaultThreadFactory();
        public boolean daemonThreads = false;
        public ErrorHandler errorHandler = (id, t) -> {};
    }

    public interface ErrorHandler {
        void onError(String taskId, Throwable t);
    }

    public Scheduler(Supplier<T> contextProvider) {
        this(contextProvider, new Config());
    }

    public Scheduler(Supplier<T> contextProvider, Config config) {
        this.contextProvider = Objects.requireNonNull(contextProvider);
        this.errorHandler = Objects.requireNonNull(config.errorHandler);

        ThreadFactory factory = config.threadFactory;
        if (config.daemonThreads) {
            factory = r -> {
                Thread t = config.threadFactory.newThread(r);
                t.setDaemon(true);
                return t;
            };
        }

        this.executor = new ScheduledThreadPoolExecutor(config.poolSize, factory);
        this.executor.setRemoveOnCancelPolicy(true);
    }

    public TaskHandle afterRealTime(Duration delay, Consumer<T> task) {
        return scheduleInternal(null, delay, null, () -> task.accept(contextProvider.get()), false, false, TimelessClock.TimeSources.REAL_TIME);
    }

    public TaskHandle afterRealTime(String id, Duration delay, Consumer<T> task) {
        return scheduleInternal(id, delay, null, () -> task.accept(contextProvider.get()), false, false, TimelessClock.TimeSources.REAL_TIME);
    }

    public TaskHandle afterGameTime(Duration delay, Consumer<T> task) {
        return scheduleInternal(null, delay, null, () -> task.accept(contextProvider.get()), false, false, TimelessClock.TimeSources.GAME_TIME);
    }

    public TaskHandle afterGameTime(String id, Duration delay, Consumer<T> task) {
        return scheduleInternal(id, delay, null, () -> task.accept(contextProvider.get()), false, false, TimelessClock.TimeSources.GAME_TIME);
    }

    public TaskHandle everyRealTime(Duration period, Consumer<T> task) {
        return scheduleInternal(null, period, period, () -> task.accept(contextProvider.get()), true, false, TimelessClock.TimeSources.REAL_TIME);
    }

    public TaskHandle everyRealTime(String id, Duration period, Consumer<T> task) {
        return scheduleInternal(id, period, period, () -> task.accept(contextProvider.get()), true, false, TimelessClock.TimeSources.REAL_TIME);
    }

    public TaskHandle everyGameTime(Duration period, Consumer<T> task) {
        return scheduleInternal(null, period, period, () -> task.accept(contextProvider.get()), true, false, TimelessClock.TimeSources.GAME_TIME);
    }

    public TaskHandle everyGameTime(String id, Duration period, Consumer<T> task) {
        return scheduleInternal(id, period, period, () -> task.accept(contextProvider.get()), true, false, TimelessClock.TimeSources.GAME_TIME);
    }

    public TaskHandle everyRealTimeFixedRate(Duration period, Consumer<T> task) {
        return scheduleInternal(null, period, period, () -> task.accept(contextProvider.get()), true, true, TimelessClock.TimeSources.REAL_TIME);
    }

    public TaskHandle everyRealTimeFixedRate(String id, Duration period, Consumer<T> task) {
        return scheduleInternal(id, period, period, () -> task.accept(contextProvider.get()), true, true, TimelessClock.TimeSources.REAL_TIME);
    }

    public TaskHandle everyGameTimeFixedRate(Duration period, Consumer<T> task) {
        return scheduleInternal(null, period, period, () -> task.accept(contextProvider.get()), true, true, TimelessClock.TimeSources.GAME_TIME);
    }

    public TaskHandle everyGameTimeFixedRate(String id, Duration period, Consumer<T> task) {
        return scheduleInternal(id, period, period, () -> task.accept(contextProvider.get()), true, true, TimelessClock.TimeSources.GAME_TIME);
    }

    public CompletableFuture<Void> afterRealTimeAsync(Duration delay, Consumer<T> task) {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        afterRealTime(delay, x -> {
            try { task.accept(x); cf.complete(null); }
            catch (Throwable t) { cf.completeExceptionally(t); }
        });
        return cf;
    }

    public CompletableFuture<Void> afterGameTimeAsync(Duration delay, Consumer<T> task) {
        CompletableFuture<Void> cf = new CompletableFuture<>();
        afterGameTime(delay, x -> {
            try { task.accept(x); cf.complete(null); }
            catch (Throwable t) { cf.completeExceptionally(t); }
        });
        return cf;
    }

    public void shutdown() {
        tasks.values().forEach(ScheduledTask::cancelSilently);
        tasks.clear();
        executor.shutdownNow();
    }

    public void shutdownGracefully(long timeout, TimeUnit unit) throws InterruptedException {
        tasks.values().forEach(ScheduledTask::cancelSilently);
        tasks.clear();
        executor.shutdown();
        executor.awaitTermination(timeout, unit);
    }

    public boolean isShutdown() { return executor.isShutdown(); }
    public boolean isTerminated() { return executor.isTerminated(); }

    private TaskHandle scheduleInternal(String idOverride, Duration initialDelay, Duration period, Runnable runnable, boolean repeating, boolean fixedRate, TimelessClock.TimeSource timeSource) {
        Objects.requireNonNull(initialDelay);
        Objects.requireNonNull(runnable);

        String id = idOverride != null ? idOverride : UUID.randomUUID().toString();

        if (tasks.containsKey(id)) {
            throw new IllegalArgumentException("Task ID already exists: " + id);
        }

        ScheduledTask st = new ScheduledTask(id, initialDelay, period, runnable, repeating, fixedRate, timeSource);
        tasks.put(id, st);
        st.scheduleNext();
        return st;
    }

    public interface TaskHandle {
        boolean cancel();
        boolean pause();
        boolean resume();
        boolean isCancelled();
        boolean isPaused();
        boolean isRunning();
        boolean isScheduled();
        Optional<Duration> getRemainingDelay();
        Optional<Duration> getPeriod();
        boolean runNow();
        String id();
    }

    private class ScheduledTask implements TaskHandle {
        private final String id;
        private final Runnable userTask;
        private final boolean repeating;
        private final boolean fixedRate;
        private final Duration period;
        private final TimelessClock.TimeSource timeSource;

        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean paused = new AtomicBoolean(false);
        private final AtomicBoolean running = new AtomicBoolean(false);

        private volatile ScheduledFuture<?> future;
        private final AtomicLong nextRunNanos = new AtomicLong(-1);
        private volatile long remainingNanosOnPause = -1;

        ScheduledTask(String id, Duration initialDelay, Duration period, Runnable userTask, boolean repeating, boolean fixedRate, TimelessClock.TimeSource timeSource) {
            this.id = id;
            this.userTask = userTask;
            this.repeating = repeating;
            this.fixedRate = fixedRate;
            this.period = period;
            this.timeSource = timeSource;

            long now = timeSource.now();
            long delayNanos = Math.max(0L, initialDelay.toNanos());
            this.nextRunNanos.set(now + delayNanos);
        }

        private void scheduleNext() {
            if (cancelled.get()) return;
            long now = timeSource.now();
            long delay = Math.max(0L, nextRunNanos.get() - now);
            future = executor.schedule(this::runTask, delay, TimeUnit.NANOSECONDS);
        }

        private void runTask() {
            if (cancelled.get()) return;
            if (paused.get()) {
                long now = timeSource.now();
                remainingNanosOnPause = Math.max(0L, nextRunNanos.get() - now);
                return;
            }

            running.set(true);
            long scheduledStart = nextRunNanos.get();

            try {
                userTask.run();
            } catch (Throwable t) {
                errorHandler.onError(id, t);
                TimelessLib.LOGGER.error("Failed to execute scheduled task: ", t);
            } finally {
                running.set(false);
            }

            if (!repeating) {
                tasks.remove(id);
                return;
            }

            if (fixedRate) {
                nextRunNanos.set(scheduledStart + period.toNanos());
            } else {
                nextRunNanos.set(timeSource.now() + period.toNanos());
            }

            if (!cancelled.get() && !paused.get()) {
                scheduleNext();
            }
        }

        @Override
        public boolean cancel() {
            if (!cancelled.compareAndSet(false, true)) return false;
            if (future != null) future.cancel(false);
            tasks.remove(id);
            return true;
        }

        void cancelSilently() {
            cancelled.set(true);
            if (future != null) future.cancel(false);
        }

        @Override
        public boolean pause() {
            if (cancelled.get()) return false;
            if (!paused.compareAndSet(false, true)) return false;

            ScheduledFuture<?> f = future;
            if (f != null && !f.isDone()) {
                long now = timeSource.now();
                remainingNanosOnPause = Math.max(0L, nextRunNanos.get() - now);
                f.cancel(false);
            }
            return true;
        }

        @Override
        public boolean resume() {
            if (cancelled.get()) return false;
            if (!paused.compareAndSet(true, false)) return false;

            long now = timeSource.now();
            long rem = remainingNanosOnPause < 0 ? 0 : remainingNanosOnPause;
            nextRunNanos.set(now + rem);
            remainingNanosOnPause = -1;
            scheduleNext();
            return true;
        }

        @Override
        public boolean isCancelled() { return cancelled.get(); }

        @Override
        public boolean isPaused() { return paused.get(); }

        @Override
        public boolean isRunning() { return running.get(); }

        @Override
        public boolean isScheduled() {
            ScheduledFuture<?> f = future;
            return f != null && !f.isDone() && !cancelled.get();
        }

        @Override
        public Optional<Duration> getRemainingDelay() {
            if (cancelled.get()) return Optional.empty();
            if (paused.get()) return Optional.of(Duration.ofNanos(Math.max(0L, remainingNanosOnPause)));

            long now = timeSource.now();
            long rem = Math.max(0L, nextRunNanos.get() - now);
            return Optional.of(Duration.ofNanos(rem));
        }

        @Override
        public Optional<Duration> getPeriod() {
            return repeating ? Optional.of(period) : Optional.empty();
        }

        @Override
        public boolean runNow() {
            if (cancelled.get() || paused.get()) return false;
            executor.execute(() -> {
                try { userTask.run(); }
                catch (Throwable t) { errorHandler.onError(id, t); }
            });
            return true;
        }

        @Override
        public String id() { return id; }
    }
}
