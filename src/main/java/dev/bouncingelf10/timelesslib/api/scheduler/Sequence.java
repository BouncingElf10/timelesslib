package dev.bouncingelf10.timelesslib.api.scheduler;

import dev.bouncingelf10.timelesslib.api.time.Duration;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A builder for a chain of delayed steps that run one after another - "wait, then do this, then wait, then do that". <br>
 * Unlike nesting {@code after(...)} calls inside each other, a sequence is a single cancellable/pausable
 * {@link TaskHandle} for the whole chain. Obtained through {@link ServerScheduler#sequence()} / {@link ClientScheduler#sequence()}.
 */
public final class Sequence<T> {
    private final Scheduler<T> scheduler;
    private final List<Step<T>> steps = new ArrayList<>();

    Sequence(Scheduler<T> scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Adds a step that runs the given delay after the previous step finishes (or after the sequence starts, for the first step).
     */
    public Sequence<T> then(Duration delay, Consumer<T> action) {
        steps.add(new Step<>(delay, action));
        return this;
    }

    /**
     * Adds a step that runs the given delay after the previous step finishes, without needing the context.
     */
    public Sequence<T> then(Duration delay, Runnable action) {
        return then(delay, ctx -> action.run());
    }

    /**
     * Starts the sequence.
     * @return {@link TaskHandle} for the whole chain - cancelling it stops the sequence, pausing it pauses whichever step is currently pending.
     */
    public TaskHandle start() {
        return start(Scheduler.randomId());
    }

    /**
     * Starts the sequence under the given ID.
     * @throws IllegalArgumentException if a task with the specified ID already exists
     */
    public TaskHandle start(ResourceLocation id) {
        if (scheduler.tasks.containsKey(id)) {
            throw new IllegalArgumentException("Task ID already exists: " + id);
        }

        SequenceHandle<T> handle = new SequenceHandle<>(id, scheduler, List.copyOf(steps));
        scheduler.tasks.put(id, handle);
        handle.runStep(0);
        return handle;
    }

    static final class Step<T> {
        private final Duration delay;
        private final Consumer<T> action;

        Step(Duration delay, Consumer<T> action) {
            this.delay = delay;
            this.action = action;
        }

        Duration delay() { return delay; }
        Consumer<T> action() { return action; }
    }
}
