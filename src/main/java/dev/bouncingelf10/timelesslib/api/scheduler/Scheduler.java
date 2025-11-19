package dev.bouncingelf10.timelesslib.api.scheduler;

import dev.bouncingelf10.timelesslib.api.time.Duration;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A flexible scheduler for executing tasks after a delay or periodically.

 * <p>Example usage:</p>
 * <pre>{@code
 * Scheduler<MyContext> scheduler = new Scheduler<>(() -> myContext);
 * // if not making your own scheduler, use the Timeless one:
 * TimelessLib.getServerScheduler() or TimelessLib.getClientScheduler()
 *
 * // Run a task once after 5 seconds
 * scheduler.after(Duration.ofSeconds(5), server -> server.doSomething());
 *
 * // Run a task every 2 seconds
 * Scheduler.TaskHandle handle = scheduler.every(Duration.ofSeconds(2), client -> client.doSomethingPeriodic());
 *
 * // Pause the task
 * handle.pause();
 *
 * // Resume the task
 * handle.resume();
 *
 * // Cancel the task
 * handle.cancel();
 *
 * // Shutdown the scheduler
 * scheduler.shutdown();
 * }</pre>
 *
 * @param <T> the type of context object supplied to scheduled tasks
 */
public class Scheduler<T> {
    private final ScheduledThreadPoolExecutor executor;
    private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private final Supplier<T> contextProvider;

    /**
     * Creates a new scheduler using a thread pool sized based on available processors.
     *
     * @param contextProvider a supplier for the context passed to each task
     */
    public Scheduler(Supplier<T> contextProvider) {
        this(contextProvider, Math.max(1, Runtime.getRuntime().availableProcessors()));
    }

    /**
     * Creates a new scheduler with a custom thread pool size.
     *
     * @param contextProvider a supplier for the context passed to each task
     * @param poolSize the number of threads in the scheduler's thread pool
     */
    public Scheduler(Supplier<T> contextProvider, int poolSize) {
        this.contextProvider = contextProvider;
        this.executor = new ScheduledThreadPoolExecutor(poolSize);
        this.executor.setRemoveOnCancelPolicy(true);
    }

    /**
     * Schedules a one-shot task to run after the specified delay.
     *
     * @param delay the delay before executing the task
     * @param task the task to run
     * @return a handle for controlling the task
     */
    public TaskHandle after(Duration delay, Consumer<T> task) {
        Objects.requireNonNull(delay);
        Objects.requireNonNull(task);
        return scheduleInternal(delay, null, () -> task.accept(contextProvider.get()), false);
    }

    /**
     * Schedules a repeating task with a fixed delay between the end of one execution
     * and the start of the next.
     *
     * @param period the interval between executions
     * @param task the task to run periodically
     * @return a handle for controlling the task
     */
    public TaskHandle every(Duration period, Consumer<T> task){
        Objects.requireNonNull(period);
        Objects.requireNonNull(task);
        return scheduleInternal(period, period, () -> task.accept(contextProvider.get()), true);
    }

    /**
     * Schedules a repeating task at a fixed rate, where the interval is measured from
     * the scheduled start of the previous execution.
     *
     * @param period the interval between scheduled executions
     * @param task the task to run periodically
     * @return a handle for controlling the task
     */
    public TaskHandle everyFixedRate(Duration period, Consumer<T> task) {
        Objects.requireNonNull(period);
        Objects.requireNonNull(task);
        return scheduleInternal(period, period, () -> task.accept(contextProvider.get()), true, true);
    }

    /**
     * Cancels all tasks and immediately shuts down the scheduler.
     * Tasks that are currently executing may be interrupted.
     */
    public void shutdown() {
        tasks.values().forEach(ScheduledTask::cancelSilently);
        tasks.clear();
        executor.shutdownNow();
    }

    /**
     * Cancels all tasks and gracefully shuts down the scheduler, waiting up to the
     * specified timeout for tasks to complete.
     *
     * @param timeout the maximum time to wait for termination
     * @param unit the time unit of the timeout
     * @throws InterruptedException if interrupted while waiting
     */
    public void shutdownGracefully(long timeout, TimeUnit unit) throws InterruptedException {
        tasks.values().forEach(ScheduledTask::cancelSilently);
        tasks.clear();
        executor.shutdown();
        executor.awaitTermination(timeout, unit);
    }

    private TaskHandle scheduleInternal(Duration initialDelay, Duration period, Runnable runnable, boolean repeating) {
        return scheduleInternal(initialDelay, period, runnable, repeating, false);
    }

    private TaskHandle scheduleInternal(Duration initialDelay, Duration period, Runnable runnable, boolean repeating, boolean fixedRate) {
        String id = UUID.randomUUID().toString();
        ScheduledTask st = new ScheduledTask(id, initialDelay, period, runnable, repeating, fixedRate);
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

        Optional<Duration> getRemainingDelay();

        String id();
    }

    private class ScheduledTask implements TaskHandle {
        private final String id;
        private final Runnable userTask;
        private final boolean repeating;
        private final boolean fixedRate;
        private final Duration period;

        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean paused = new AtomicBoolean(false);

        private final Object lock = new Object();

        private volatile ScheduledFuture<?> future;
        private final AtomicLong nextRunNanos = new AtomicLong(-1);
        private volatile long remainingNanosOnPause = -1;

        ScheduledTask(String id, Duration initialDelay, Duration period, Runnable userTask, boolean repeating, boolean fixedRate) {
            this.id = id;
            this.userTask = userTask;
            this.repeating = repeating;
            this.fixedRate = fixedRate;
            this.period = period;
            long now = System.nanoTime();
            long delayNanos = Math.max(0L, initialDelay.toNanos());
            this.nextRunNanos.set(now + delayNanos);
        }

        private void scheduleNext() {
            if (cancelled.get()) return;
            long now = System.nanoTime();
            long delay = Math.max(0L, nextRunNanos.get() - now);
            future = executor.schedule(this::runTask, delay, TimeUnit.NANOSECONDS);
        }

        private void runTask() {
            if (cancelled.get()) return;
            if (paused.get()) {
                long now = System.nanoTime();
                remainingNanosOnPause = Math.max(0L, nextRunNanos.get() - now);
                return;
            }

            long scheduledStart = nextRunNanos.get();
            try {
                userTask.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }

            if (!repeating) {
                tasks.remove(id);
                return;
            }

            if (fixedRate) {
                long next = scheduledStart + period.toNanos();
                nextRunNanos.set(next);
            } else {
                nextRunNanos.set(System.nanoTime() + period.toNanos());
            }

            if (!cancelled.get() && !paused.get()) {
                scheduleNext();
            }
        }

        @Override
        public boolean cancel() {
            boolean already = cancelled.getAndSet(true);
            if (already) return false;
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
            boolean set = paused.compareAndSet(false, true);
            if (!set) return false;

            ScheduledFuture<?> f = future;
            if (f != null && !f.isDone()) {
                long now = System.nanoTime();
                remainingNanosOnPause = Math.max(0L, nextRunNanos.get() - now);
                f.cancel(false);
            }
            return true;
        }

        @Override
        public boolean resume() {
            if (cancelled.get()) return false;
            boolean set = paused.compareAndSet(true, false);
            if (!set) return false;

            long now = System.nanoTime();
            long rem = remainingNanosOnPause;
            if (rem < 0) rem = 0L;
            nextRunNanos.set(now + rem);
            remainingNanosOnPause = -1;
            scheduleNext();
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public boolean isPaused() {
            return paused.get();
        }

        @Override
        public Optional<Duration> getRemainingDelay() {
            if (cancelled.get()) return Optional.empty();
            if (paused.get()) return Optional.of(Duration.ofNanos(Math.max(0L, remainingNanosOnPause)));
            long now = System.nanoTime();
            long rem = Math.max(0L, nextRunNanos.get() - now);
            return Optional.of(Duration.ofNanos(rem));
        }

        @Override
        public String id() {
            return id;
        }
    }
}
