package dev.bouncingelf10.timelesslib.api.scheduler;

import dev.bouncingelf10.timelesslib.InternalAccess;
import dev.bouncingelf10.timelesslib.TimelessLib;
import dev.bouncingelf10.timelesslib.api.time.Duration;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shared task-scheduling engine backing {@link dev.bouncingelf10.timelesslib.api.scheduler.ServerScheduler}
 * and {@link dev.bouncingelf10.timelesslib.api.scheduler.ClientScheduler}. <br>
 * Not exposed directly - use one of those two classes instead, obtained through
 * {@code TimelessLib.scheduler()} / {@code TimelessLibClient.scheduler()}.
 */
abstract class Scheduler<T> {
    static final String AUTO_ID_NAMESPACE = "timelesslib";

    final ScheduledThreadPoolExecutor executor;
    final Map<ResourceLocation, TaskHandle> tasks = new ConcurrentHashMap<>();
    final Supplier<T> contextProvider;
    final BiConsumer<T, Runnable> mainThreadDispatcher;
    final ErrorHandler errorHandler;

    public static class Config {
        public int poolSize = Math.max(1, Runtime.getRuntime().availableProcessors());
        public ThreadFactory threadFactory = Executors.defaultThreadFactory();
        public boolean daemonThreads = false;
        public ErrorHandler errorHandler = (taskId, t) -> {};
    }

    public interface ErrorHandler {
        void onError(ResourceLocation taskId, Throwable t);
    }

    Scheduler(InternalAccess access, Supplier<T> contextProvider) {
        this(access, contextProvider, new Config());
    }

    Scheduler(InternalAccess access, Supplier<T> contextProvider, Config config) {
        Objects.requireNonNull(access, "Managers can only be constructed by TimelessLib");
        this.contextProvider = Objects.requireNonNull(contextProvider);
        this.errorHandler = Objects.requireNonNull(config.errorHandler);
        this.mainThreadDispatcher = detectDispatcher(contextProvider);

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

    static <T> BiConsumer<T, Runnable> detectDispatcher(Supplier<T> contextSupplier) {
        T context = contextSupplier.get();
        if (context == null)
            throw new IllegalArgumentException("Context provider returned null when probing for execute(Runnable).");

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
            throw new IllegalArgumentException("Context type " + context.getClass().getName() + " does not expose execute(Runnable).", e);
        }
    }

    static ResourceLocation randomId() {
        return ResourceLocation.fromNamespaceAndPath(AUTO_ID_NAMESPACE, UUID.randomUUID().toString());
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
     * @param delay Delay before running the task
     * @param task Task to run
     * @return {@link TaskHandle}
     */
    public TaskHandle after(Duration delay, Runnable task) {
        return after(delay, ctx -> task.run());
    }

    /**
     * Schedules a task to run after the specified delay.
     * @param id Unique ID for the task
     * @param delay Delay before running the task
     * @param task Task to run
     * @return {@link TaskHandle}
     * @throws IllegalArgumentException if a task with the specified ID already exists
     */
    public TaskHandle after(ResourceLocation id, Duration delay, Consumer<T> task) {
        return scheduleInternal(id, delay, null, () -> task.accept(contextProvider.get()), false, false);
    }

    /**
     * Schedules a task to run after the specified delay.
     * @param id Unique ID for the task
     * @param delay Delay before running the task
     * @param task Task to run
     * @return {@link TaskHandle}
     * @throws IllegalArgumentException if a task with the specified ID already exists
     */
    public TaskHandle after(ResourceLocation id, Duration delay, Runnable task) {
        return after(id, delay, ctx -> task.run());
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
     * Schedules a repeating task to run at the specified interval.
     * @param period Interval between runs
     * @param task Task to run
     * @return {@link TaskHandle}
     */
    public TaskHandle every(Duration period, Runnable task) {
        return every(period, ctx -> task.run());
    }

    /**
     * Schedules a repeating task to run at the specified interval, with a fixed delay between runs.<br>
     * E.g. {@link #every(Duration, Consumer)} will run the interval after the task has finished executing, whereas this method will run the interval immediately after the task starts executing.
     * @param period Interval between runs
     * @param task Task to run
     * @return {@link TaskHandle}
     */
    public TaskHandle everyFixedRate(Duration period, Consumer<T> task) {
        return scheduleInternal(null, period, period, () -> task.accept(contextProvider.get()), true, true);
    }

    /**
     * Schedules a repeating task to run at the specified interval, with a fixed delay between runs.<br>
     * E.g. {@link #every(Duration, Runnable)} will run the interval after the task has finished executing, whereas this method will run the interval immediately after the task starts executing.
     * @param period Interval between runs
     * @param task Task to run
     * @return {@link TaskHandle}
     */
    public TaskHandle everyFixedRate(Duration period, Runnable task) {
        return everyFixedRate(period, ctx -> task.run());
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
     * Starts building a sequence of delayed steps that run one after another - "wait, then do this, then wait, then do that".
     * @return {@link Sequence} builder
     */
    public Sequence<T> sequence() {
        return new Sequence<>(this);
    }

    /**
     * Looks up an active task (or sequence) by its ID.
     * @param id Task ID
     * @return {@link TaskHandle} or empty if no active task has that ID
     */
    public Optional<TaskHandle> get(ResourceLocation id) {
        return Optional.ofNullable(tasks.get(id));
    }

    /**
     * @return the number of currently active tasks and sequences.
     */
    public int activeTaskCount() {
        return tasks.size();
    }

    /**
     * Cancels every currently active task and sequence.
     * @return true if at least one task was cancelled
     */
    public boolean cancelAll() {
        boolean any = false;
        for (TaskHandle handle : List.copyOf(tasks.values())) {
            any |= handle.cancel();
        }
        return any;
    }

    /**
     * If you're getting the scheduler through {@code TimelessLib.scheduler()} or the client counterpart you should NOT call this method.
     */
    public void shutdown() {
        List.copyOf(tasks.values()).forEach(TaskHandle::cancel);
        tasks.clear();
        executor.shutdownNow();
    }

    /**
     * If you're getting the scheduler through {@code TimelessLib.scheduler()} or the client counterpart you should NOT call this method.
     */
    public void shutdownGracefully(long timeout, TimeUnit unit) throws InterruptedException {
        List.copyOf(tasks.values()).forEach(TaskHandle::cancel);
        tasks.clear();
        executor.shutdown();
        if (!executor.awaitTermination(timeout, unit)) {
            TimelessLib.LOGGER.warn("Scheduler did not shutdown gracefully within the timeout");
            executor.shutdownNow();
        }
    }

    public boolean isShutdown() { return executor.isShutdown(); }
    public boolean isTerminated() { return executor.isTerminated(); }

    private TaskHandle scheduleInternal(ResourceLocation idOverride, Duration initialDelay, Duration period, Runnable userTask, boolean repeating, boolean fixedRate) {
        Objects.requireNonNull(initialDelay);
        Objects.requireNonNull(userTask);

        ResourceLocation id = (idOverride != null ? idOverride : randomId());
        if (tasks.containsKey(id)) {
            throw new IllegalArgumentException("Task ID already exists: " + id);
        }

        ScheduledTask scheduledTask = new ScheduledTask(id, initialDelay, period, userTask, repeating, fixedRate);

        tasks.put(id, scheduledTask);
        scheduledTask.scheduleNext();

        return scheduledTask;
    }

    private class ScheduledTask implements TaskHandle {
        private final ResourceLocation id;
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

        ScheduledTask(ResourceLocation id, Duration initialDelay, Duration period, Runnable userTask, boolean repeating, boolean fixedRate) {
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

        @Override public ResourceLocation id() { return id; }
    }
}
