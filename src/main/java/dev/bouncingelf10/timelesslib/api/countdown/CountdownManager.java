package dev.bouncingelf10.timelesslib.api.countdown;

import dev.bouncingelf10.timelesslib.api.clock.TimeSource;
import dev.bouncingelf10.timelesslib.api.clock.TimeSources;
import dev.bouncingelf10.timelesslib.TimelessLib;
import dev.bouncingelf10.timelesslib.api.time.Duration;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class CountdownManager<T> {
    final ScheduledThreadPoolExecutor executor;
    final Map<String, Countdown> activeCountdowns = new ConcurrentHashMap<>();
    final Supplier<T> contextProvider;
    final BiConsumer<T, Runnable> mainThreadDispatcher;

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

    static <T> BiConsumer<T, Runnable> detectDispatcher(Supplier<T> contextSupplier) {
        T context = contextSupplier.get();
        if (context == null)
            throw new IllegalArgumentException("Context provider returned null when probing for execute(Runnable). Provide an explicit dispatcher instead.");

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
    /**
     * Starts a countdown with a tick interval of 50ms and the game time source.
     * @param totalDuration Duration of the countdown.
     * @return {@link Countdown}
     * @see TimeSources
     */
    public Countdown start(Duration totalDuration) {
        Objects.requireNonNull(totalDuration);
        return start(totalDuration, Duration.ofMillis(50), TimeSources.GAME_TIME);
    }

    /**
     * Starts a countdown with a tick interval of 50ms and the real time source.
     * @param totalDuration Duration of the countdown.
     * @return {@link Countdown}
     * @see TimeSources
     */
    public Countdown startRealtime(Duration totalDuration) {
        Objects.requireNonNull(totalDuration);
        return start(totalDuration, Duration.ofMillis(50), TimeSources.REAL_TIME);
    }

    /**
     * Starts a countdown with the specified tick interval and time source.
     * @param totalDuration Duration of the countdown.
     * @param tickInterval Tick interval of the countdown.
     * @param timeSource Time source to use.
     * @return {@link Countdown}
     * @see TimeSources
     * */
    public Countdown start(Duration totalDuration, Duration tickInterval, TimeSource timeSource) {
        Objects.requireNonNull(totalDuration);
        Objects.requireNonNull(tickInterval);
        Objects.requireNonNull(timeSource);

        Countdown countdown = new Countdown(this, totalDuration, tickInterval, timeSource);
        activeCountdowns.put(countdown.getId(), countdown);
        countdown.start();
        return countdown;
    }

    public Optional<Countdown> get(String id) {
        return Optional.ofNullable(activeCountdowns.get(id));
    }

    /**
     * If you're getting the countdown manager through {@link TimelessLib#getServerCountdownManager()} or the client counterpart you should NOT call this method.
     */
    public void shutdown() {
        activeCountdowns.values().forEach(Countdown::cancelSilently);
        activeCountdowns.clear();
        executor.shutdownNow();
    }

    /**
     * If you're getting the countdown manager through {@link TimelessLib#getServerCountdownManager()} or the client counterpart you should NOT call this method.
     */
    public void shutdownGracefully(long timeout, TimeUnit unit) throws InterruptedException {
        activeCountdowns.values().forEach(Countdown::cancelSilently);
        executor.shutdown();
        if (!executor.awaitTermination(timeout, unit)) {
            TimelessLib.LOGGER.warn("CountdownManager did not shutdown gracefully within the timeout");
            executor.shutdownNow();
        }
    }
}
