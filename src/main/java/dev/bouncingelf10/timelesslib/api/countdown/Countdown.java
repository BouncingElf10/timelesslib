package dev.bouncingelf10.timelesslib.api.countdown;

import dev.bouncingelf10.timelesslib.TimelessLib;
import dev.bouncingelf10.timelesslib.api.clock.TimeSource;
import dev.bouncingelf10.timelesslib.api.scheduler.TaskHandle;
import dev.bouncingelf10.timelesslib.api.time.Duration;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A single countdown-to-zero task, produced by {@link ServerCountdownManager#start(Duration)} / {@link ClientCountdownManager#start(Duration)}. <br>
 * Implements the same {@link TaskHandle} lifecycle ({@link #cancel()}, {@link #pause()}, {@link #resume()}) as a plain
 * scheduled task, plus richer countdown-shaped notifications (tick, finish, threshold, custom-interval handlers).
 *
 * @param <T> Context type (e.g. the server or client instance) passed to handlers.
 * @param <SELF> Concrete subtype, so builder-style methods return the right type. See {@link ServerCountdown} / {@link ClientCountdown}.
 */
public abstract class Countdown<T, SELF extends Countdown<T, SELF>> implements TaskHandle {
    private final CountdownManager<T> manager;

    private final ResourceLocation id;
    private final TimeSource timeSource;
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

    @SuppressWarnings("unchecked")
    private SELF self() { return (SELF) this; }

    Countdown(CountdownManager<T> manager, ResourceLocation id, Duration totalDuration, Duration tickInterval, TimeSource timeSource) {
        this.manager = manager;
        this.id = id;
        this.totalDuration = totalDuration;
        this.totalNanos = totalDuration.toNanos();
        this.tickNanos = Math.max(1L, tickInterval.toNanos());
        this.timeSource = timeSource;
    }

    void start() {
        this.endTimeNanos = timeSource.now() + totalNanos;
        intervalHandlers.keySet().forEach(interval -> nextElapsedToFire.putIfAbsent(interval, interval));
        scheduleNextTick();
    }

    private void scheduleNextTick() {
        if (cancelled.get() || finished.get()) return;

        long remainingNanos = Math.max(0L, endTimeNanos - timeSource.now());
        long delayNanos = Math.min(tickNanos, remainingNanos);

        scheduledTask = manager.executor.schedule(() -> {
            T context = manager.contextProvider.get();
            if (context == null) {
                TimelessLib.LOGGER.warning("Context provider returned null during countdown tick, cancelling countdown " + id);
                cancel();
                return;
            }

            try {
                manager.mainThreadDispatcher.accept(context, this::runTickOnMainThread);
            } catch (Throwable t) {
                TimelessLib.LOGGER.severe("Error dispatching countdown tick for " + id + " \n" + t);
                try { runTickOnMainThread(); } catch (Throwable inner) {
                    TimelessLib.LOGGER.severe("Error running tick directly for " + id + " \n" + inner);
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
        long elapsedNanos = elapsedDuration.toNanos();

        T context = manager.contextProvider.get();
        if (context == null) {
            TimelessLib.LOGGER.warning("Context provider returned null during countdown tick, cancelling countdown" + id);
            cancel();
            return;
        }

        tickHandlers.forEach(handler -> {
            try { handler.accept(context, remainingDuration); }
            catch (Throwable t) { TimelessLib.LOGGER.severe("Error in tick handler for " + id + " \n" + t); }
        });

        intervalHandlers.forEach((interval, handlers) -> {
            long nextFire = nextElapsedToFire.getOrDefault(interval, interval);
            while (elapsedNanos >= nextFire) {
                for (Consumer<T> handler : handlers) {
                    try { handler.accept(context); }
                    catch (Throwable t) { TimelessLib.LOGGER.severe("Error in interval handler for " + id +" at interval " + interval + " \n" + t); }
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
                            catch (Throwable t) { TimelessLib.LOGGER.severe("Error in threshold handler for " + id + " at " + key + " \n" + t); }
                        });
                    }
                });
            }
        }

        if (remainingNanos == 0L && finished.compareAndSet(false, true)) {
            try {
                finishHandlers.forEach(handler -> {
                    try { handler.accept(context); }
                    catch (Throwable t) { TimelessLib.LOGGER.severe("Error in finish handler for " + id + " \n" + t); }
                });
            } finally {
                manager.countdowns.remove(id);
            }
            return;
        }

        scheduleNextTick();
    }

    /**
     * Cancels the countdown.
     * @return true if the countdown was cancelled, false if it was already finished or canceled
     */
    @Override
    public boolean cancel() {
        if (cancelled.getAndSet(true)) return false;
        if (scheduledTask != null) scheduledTask.cancel(false);
        manager.countdowns.remove(id);
        return true;
    }

    /**
     * Pauses the countdown.
     * Note: If the countdown is already paused, this method does nothing.
     * @return true if the countdown was paused, false if it was already finished or canceled
     */
    @Override
    public boolean pause() {
        if (cancelled.get() || finished.get()) return false;
        if (!paused.compareAndSet(false, true)) return false;
        if (scheduledTask != null && !scheduledTask.isDone()) scheduledTask.cancel(false);
        remainingOnPause = Math.max(0L, endTimeNanos - timeSource.now());
        return true;
    }

    /**
     * Resumes a paused countdown.
     * Note: If the countdown is not paused, this method does nothing.
     * @return true if the countdown was resumed, false if it was already finished or canceled
     */
    @Override
    public boolean resume() {
        if (cancelled.get() || finished.get()) return false;
        if (!paused.compareAndSet(true, false)) return false;
        if (remainingOnPause < 0) remainingOnPause = 0;
        endTimeNanos = timeSource.now() + remainingOnPause;
        remainingOnPause = -1;
        scheduleNextTick();
        return true;
    }

    /**
     * Toggles to pause or unpause the countdown.
     * @return true if the countdown was changed state, false if it was already finished or canceled
     */
    @Override
    public boolean pauseOrUnpause() {
        return !isPaused() ? pause() : resume();
    }

    @Override public boolean isCancelled() { return cancelled.get(); }
    @Override public boolean isPaused() { return paused.get(); }
    public boolean isFinished() { return finished.get(); }
    @Override public boolean isRunning() { return !isCancelled() && !isPaused() && !isFinished(); }
    @Override public boolean isScheduled() { return scheduledTask != null && !scheduledTask.isDone() && !cancelled.get(); }
    @Override public Optional<Duration> getRemainingDelay() { return cancelled.get() ? Optional.empty() : Optional.of(remaining()); }
    @Override public Optional<Duration> getPeriod() { return Optional.of(Duration.ofNanos(tickNanos)); }

    @Override
    public boolean runNow() {
        if (cancelled.get() || paused.get() || finished.get()) return false;
        T context = manager.contextProvider.get();
        if (context == null) return false;
        manager.mainThreadDispatcher.accept(context, this::runTickOnMainThread);
        return true;
    }

    public Duration remaining() {
        if (paused.get() && remainingOnPause >= 0) return Duration.ofNanos(remainingOnPause);
        return Duration.ofNanos(Math.max(0L, endTimeNanos - timeSource.now()));
    }

    /**
     * @return time elapsed since the countdown started, clamped to the total duration.
     */
    public Duration elapsed() {
        return totalDuration.minus(remaining());
    }

    /**
     * @return progress from 0.0 (just started) to 1.0 (finished).
     */
    public double progress() {
        if (totalNanos <= 0) return 1.0;
        return Math.min(1.0, Math.max(0.0, 1.0 - ((double) remaining().toNanos() / totalNanos)));
    }

    @Override public ResourceLocation id() { return id; }

    /**
     * Adds a task to execute every countdown tick specified by the tick interval.
     * @param handler Handler to execute every countdown tick.
     */
    public SELF onTick(BiConsumer<T, Duration> handler) {
        tickHandlers.add(handler);
        return self();
    }

    /**
     * Adds a task to execute every countdown tick specified by the tick interval, without needing the context.
     * @param handler Handler to execute every countdown tick.
     */
    public SELF onTick(Consumer<Duration> handler) {
        return onTick((ctx, remaining) -> handler.accept(remaining));
    }

    /**
     * Adds a task to execute when the countdown finishes.
     * @param handler Handler to execute when the countdown finishes.
     */
    public SELF onFinish(Consumer<T> handler) {
        finishHandlers.add(handler);
        return self();
    }

    /**
     * Adds a task to execute when the countdown finishes, without needing the context.
     * @param handler Handler to execute when the countdown finishes.
     */
    public SELF onFinish(Runnable handler) {
        return onFinish(ctx -> handler.run());
    }

    /**
     * Adds a task to execute when the countdown reaches the specified threshold.
     * @param threshold Duration threshold to reach.
     * @param handler Handler to execute when the threshold is reached.
     */
    public SELF onThreshold(Duration threshold, Consumer<T> handler) {
        thresholds.computeIfAbsent(threshold.toNanos(), k -> Collections.synchronizedList(new ArrayList<>())).add(handler);
        return self();
    }

    /**
     * Adds a task to execute every time the specified interval elapses. (Like {@link #onTick(BiConsumer)},
     * but with a specified interval instead of the countdown tick interval)
     * @param interval Interval at which to execute the handler.
     * @param handler Handler to execute every time the interval elapses.
     */
    public SELF every(Duration interval, Consumer<T> handler) {
        long nanos = Math.max(1L, interval.toNanos());
        intervalHandlers.computeIfAbsent(nanos, k -> Collections.synchronizedList(new ArrayList<>())).add(handler);
        nextElapsedToFire.putIfAbsent(nanos, nanos);
        return self();
    }
}
