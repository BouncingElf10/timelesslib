package dev.bouncingelf10.timelesslib.api.countdown;

import dev.bouncingelf10.timelesslib.InternalAccess;
import dev.bouncingelf10.timelesslib.TimelessLib;
import dev.bouncingelf10.timelesslib.api.scheduler.TaskHandle;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Self-contained countdown engine backing {@link dev.bouncingelf10.timelesslib.api.countdown.ServerCountdownManager}
 * and {@link dev.bouncingelf10.timelesslib.api.countdown.ClientCountdownManager}. <br>
 * Deliberately independent from {@code Scheduler} - a countdown's tick/pause/cancel machinery is not shared with
 * plain scheduled tasks. Not exposed directly - use one of the two classes above instead.
 */
abstract class CountdownManager<T> {
    static final String AUTO_ID_NAMESPACE = "timelesslib";

    final ScheduledThreadPoolExecutor executor;
    final Map<Identifier, TaskHandle> countdowns = new ConcurrentHashMap<>();
    final Supplier<T> contextProvider;
    final BiConsumer<T, Runnable> mainThreadDispatcher;

    CountdownManager(InternalAccess access, Supplier<T> contextProvider) {
        this(access, contextProvider, Math.max(1, Runtime.getRuntime().availableProcessors()));
    }

    CountdownManager(InternalAccess access, Supplier<T> contextProvider, int poolSize) {
        Objects.requireNonNull(access, "Managers can only be constructed by TimelessLib");
        this.contextProvider = Objects.requireNonNull(contextProvider, "contextProvider");
        this.mainThreadDispatcher = detectDispatcher(contextProvider);
        this.executor = new ScheduledThreadPoolExecutor(poolSize);
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

    static Identifier randomId() {
        return Identifier.fromNamespaceAndPath(AUTO_ID_NAMESPACE, UUID.randomUUID().toString());
    }

    /**
     * Looks up an active countdown by its ID.
     * @param id Countdown ID
     * @return {@link TaskHandle} or empty if no active countdown has that ID
     */
    public Optional<TaskHandle> get(Identifier id) {
        return Optional.ofNullable(countdowns.get(id));
    }

    /**
     * @return the number of currently active countdowns.
     */
    public int activeCountdownCount() {
        return countdowns.size();
    }

    /**
     * Cancels every currently active countdown.
     * @return true if at least one countdown was cancelled
     */
    public boolean cancelAll() {
        boolean any = false;
        for (TaskHandle handle : List.copyOf(countdowns.values())) {
            any |= handle.cancel();
        }
        return any;
    }

    /**
     * If you're getting the countdown manager through {@code TimelessLib.countdowns()} or the client counterpart you should NOT call this method.
     */
    public void shutdown() {
        List.copyOf(countdowns.values()).forEach(TaskHandle::cancel);
        countdowns.clear();
        executor.shutdownNow();
    }

    /**
     * If you're getting the countdown manager through {@code TimelessLib.countdowns()} or the client counterpart you should NOT call this method.
     */
    public void shutdownGracefully(long timeout, TimeUnit unit) throws InterruptedException {
        List.copyOf(countdowns.values()).forEach(TaskHandle::cancel);
        countdowns.clear();
        executor.shutdown();
        if (!executor.awaitTermination(timeout, unit)) {
            TimelessLib.LOGGER.warn("CountdownManager did not shutdown gracefully within the timeout");
            executor.shutdownNow();
        }
    }

    public boolean isShutdown() { return executor.isShutdown(); }
    public boolean isTerminated() { return executor.isTerminated(); }
}
