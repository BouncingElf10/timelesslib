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

public class Scheduler<T> {
    private final ScheduledThreadPoolExecutor executor;
    private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private final Supplier<T> contextProvider;

    public Scheduler(Supplier<T> contextProvider) {
        this(contextProvider, Math.max(1, Runtime.getRuntime().availableProcessors()));
    }

    public Scheduler(Supplier<T> contextProvider, int poolSize) {
        this.contextProvider = contextProvider;
        this.executor = new ScheduledThreadPoolExecutor(poolSize);
        this.executor.setRemoveOnCancelPolicy(true);
    }

    /**
     * Schedule a one-shot task to run after the given duration.
     */
    public TaskHandle after(Duration delay, Consumer<T> task) {
        Objects.requireNonNull(delay);
        Objects.requireNonNull(task);
        return scheduleInternal(delay, null, () -> task.accept(contextProvider.get()), false);
    }

    /**
     * Schedule a periodic task that runs every {@code period} duration. This is implemented
     * as fixed-delay (the next run is scheduled after the run completes) to make pause/resume
     * and high-precision behavior easier to reason about.
     */
    public TaskHandle every(Duration period, Consumer<T> task){
        Objects.requireNonNull(period);
        Objects.requireNonNull(task);
        return scheduleInternal(period, period, () -> task.accept(contextProvider.get()), true);
    }

    /**
     * Schedule a task to run at a fixed rate. (Optional convenience) -- here implemented as
     * rescheduling with the requested period measured from scheduled time, not end of execution.
     */
    public TaskHandle everyFixedRate(Duration period, Consumer<T> task) {
        Objects.requireNonNull(period);
        Objects.requireNonNull(task);
        return scheduleInternal(period, period, () -> task.accept(contextProvider.get()), true, true);
    }

    /**
     * Internal scheduling factory.
     */
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

    /**
     * Stop the scheduler: cancels all tasks and shuts down the thread pool.
     */
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
