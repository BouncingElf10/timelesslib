package dev.bouncingelf10.timelesslib.api.scheduler;

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
        public ErrorHandler errorHandler = (taskId, t) -> {};
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
                Thread thread = config.threadFactory.newThread(r);
                thread.setDaemon(true);
                return thread;
            };
        }

        this.executor = new ScheduledThreadPoolExecutor(config.poolSize, factory);
        this.executor.setRemoveOnCancelPolicy(true);
    }

    /**
     * Schedules a task to run after the specified delay.
     * @param delay Delay before running the task
     * @param task Task to run
     * @return {@link TaskHandle}
     */
    public TaskHandle after(Duration delay, Consumer<T> task) {
        return scheduleInternal(null, delay, null, () -> task.accept(contextProvider.get()), false, false);
    }

    /**
     * Schedules a task to run after the specified delay.
     * @param id Unique ID for the task
     * @param delay Delay before running the task
     * @param task Task to run
     * @return {@link TaskHandle}
     * @throws IllegalArgumentException if a task with the specified ID already exists
     */
    public TaskHandle after(String id, Duration delay, Consumer<T> task) {
        return scheduleInternal(id, delay, null, () -> task.accept(contextProvider.get()), false, false);
    }

    /**
     * Schedules a repeating task to run at the specified interval.
     * @param period Interval between runs
     * @param task Task to run
     * @return {@link TaskHandle}
     */
    public TaskHandle every(Duration period, Consumer<T> task) {
        return scheduleInternal(null, period, period, () -> task.accept(contextProvider.get()), true, false);
    }

    /**
     * Schedules a repeating task to run at the specified interval, with a fixed delay between runs.<br>
     * E.g. {@link #every(Duration, Consumer)} will run the interval after the task has finished executing, whereas this method will run the interval immediately after the task starts executing.
     * @param period
     * @param task
     * @return {@link TaskHandle}
     */
    public TaskHandle everyFixedRate(Duration period, Consumer<T> task) {
        return scheduleInternal(null, period, period, () -> task.accept(contextProvider.get()), true, true);
    }

    /**
     * Schedules a task to run after the specified delay, returning a {@link CompletableFuture} that completes when the task has finished executing.
     * @param delay Delay before running the task
     * @param task Task to run
     * @return {@link CompletableFuture}
     */
    public CompletableFuture<Void> afterAsync(Duration delay, Consumer<T> task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        after(delay, ctx -> {
            try {
                task.accept(ctx);
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /**
     * If you're getting the scheduler through {@link TimelessLib#getServerScheduler()} or the client counterpart you should NOT call this method.
     */
    public void shutdown() {
        tasks.values().forEach(ScheduledTask::cancelSilently);
        tasks.clear();
        executor.shutdownNow();
    }

    /**
     * If you're getting the scheduler through {@link TimelessLib#getServerScheduler()} or the client counterpart you should NOT call this method.
     */
    public void shutdownGracefully(long timeout, TimeUnit unit) throws InterruptedException {
        tasks.values().forEach(ScheduledTask::cancelSilently);
        tasks.clear();
        executor.shutdown();
        executor.awaitTermination(timeout, unit);
    }

    public boolean isShutdown() { return executor.isShutdown(); }
    public boolean isTerminated() { return executor.isTerminated(); }

    private TaskHandle scheduleInternal(String idOverride, Duration initialDelay, Duration period, Runnable userTask, boolean repeating, boolean fixedRate) {
        Objects.requireNonNull(initialDelay);
        Objects.requireNonNull(userTask);

        String id = (idOverride != null ? idOverride : UUID.randomUUID().toString());
        if (tasks.containsKey(id)) {
            throw new IllegalArgumentException("Task ID already exists: " + id);
        }

        ScheduledTask scheduledTask = new ScheduledTask(id, initialDelay, period, userTask, repeating, fixedRate);

        tasks.put(id, scheduledTask);
        scheduledTask.scheduleNext();

        return scheduledTask;
    }

    private class ScheduledTask implements TaskHandle {
        private final String id;
        private final Runnable userTask;
        private final boolean repeating;
        private final boolean fixedRate;
        private final Duration period;

        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean paused = new AtomicBoolean(false);
        private final AtomicBoolean running = new AtomicBoolean(false);

        private volatile ScheduledFuture<?> future;

        private final AtomicLong nextRunNanos = new AtomicLong(-1);
        private volatile long remainingNanosOnPause = -1;

        private long now() {
            return System.nanoTime();
        }

        ScheduledTask(String id, Duration initialDelay, Duration period, Runnable userTask, boolean repeating, boolean fixedRate) {
            this.id = id;
            this.userTask = userTask;
            this.repeating = repeating;
            this.fixedRate = fixedRate;
            this.period = period;

            long start = now();
            long delayNanos = Math.max(0L, initialDelay.toNanos());

            this.nextRunNanos.set(start + delayNanos);
        }

        private void scheduleNext() {
            if (cancelled.get()) return;

            long delay = Math.max(0L, nextRunNanos.get() - now());
            future = executor.schedule(this::runTask, delay, TimeUnit.NANOSECONDS);
        }

        private void runTask() {
            if (cancelled.get()) return;
            if (paused.get()) {
                remainingNanosOnPause = Math.max(0L, nextRunNanos.get() - now());
                return;
            }

            running.set(true);
            long scheduledStart = nextRunNanos.get();

            try {
                userTask.run();
            } catch (Throwable t) {
                errorHandler.onError(id, t);
            } finally {
                running.set(false);
            }

            if (!repeating) {
                tasks.remove(id);
                return;
            }

            long next;
            if (fixedRate) {
                next = scheduledStart + period.toNanos();
            } else {
                next = now() + period.toNanos();
            }

            nextRunNanos.set(next);

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
            if (cancelled.get() || !paused.compareAndSet(false, true)) return false;

            if (future != null && !future.isDone()) {
                remainingNanosOnPause = Math.max(0L, nextRunNanos.get() - now());
                future.cancel(false);
            }
            return true;
        }

        @Override
        public boolean resume() {
            if (cancelled.get() || !paused.compareAndSet(true, false)) return false;

            long delay = remainingNanosOnPause < 0 ? 0 : remainingNanosOnPause;
            nextRunNanos.set(now() + delay);
            remainingNanosOnPause = -1;

            scheduleNext();
            return true;
        }

        @Override
        public boolean pauseOrUnpause() {
            return !paused.get() ? pause() : resume();
        }

        @Override public boolean isCancelled() { return cancelled.get(); }
        @Override public boolean isPaused() { return paused.get(); }
        @Override public boolean isRunning() { return running.get(); }

        @Override
        public boolean isScheduled() {
            return future != null && !future.isDone() && !cancelled.get();
        }

        @Override
        public Optional<Duration> getRemainingDelay() {
            if (cancelled.get()) return Optional.empty();
            if (paused.get()) return Optional.of(Duration.ofNanos(Math.max(0L, remainingNanosOnPause)));

            long remaining = Math.max(0L, nextRunNanos.get() - now());
            return Optional.of(Duration.ofNanos(remaining));
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

        @Override public String id() { return id; }
    }
}