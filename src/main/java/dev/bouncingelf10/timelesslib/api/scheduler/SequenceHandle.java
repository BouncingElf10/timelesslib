package dev.bouncingelf10.timelesslib.api.scheduler;

import dev.bouncingelf10.timelesslib.api.time.Duration;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

final class SequenceHandle<T> implements TaskHandle {
    private final ResourceLocation id;
    private final Scheduler<T> scheduler;
    private final List<Sequence.Step<T>> steps;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile TaskHandle current;

    SequenceHandle(ResourceLocation id, Scheduler<T> scheduler, List<Sequence.Step<T>> steps) {
        this.id = id;
        this.scheduler = scheduler;
        this.steps = steps;
    }

    void runStep(int index) {
        if (cancelled.get()) return;
        if (index >= steps.size()) {
            scheduler.tasks.remove(id);
            return;
        }

        Sequence.Step<T> step = steps.get(index);
        current = scheduler.after(step.delay(), ctx -> {
            try {
                step.action().accept(ctx);
            } finally {
                runStep(index + 1);
            }
        });
    }

    @Override
    public boolean cancel() {
        if (!cancelled.compareAndSet(false, true)) return false;
        TaskHandle handle = current;
        if (handle != null) handle.cancel();
        scheduler.tasks.remove(id);
        return true;
    }

    @Override public boolean pause() { TaskHandle handle = current; return handle != null && handle.pause(); }
    @Override public boolean resume() { TaskHandle handle = current; return handle != null && handle.resume(); }
    @Override public boolean pauseOrUnpause() { TaskHandle handle = current; return handle != null && handle.pauseOrUnpause(); }
    @Override public boolean isCancelled() { return cancelled.get(); }
    @Override public boolean isPaused() { TaskHandle handle = current; return handle != null && handle.isPaused(); }
    @Override public boolean isRunning() { TaskHandle handle = current; return handle != null && handle.isRunning(); }
    @Override public boolean isScheduled() { TaskHandle handle = current; return handle != null && handle.isScheduled(); }
    @Override public Optional<Duration> getRemainingDelay() { TaskHandle handle = current; return handle == null ? Optional.empty() : handle.getRemainingDelay(); }
    @Override public Optional<Duration> getPeriod() { return Optional.empty(); }
    @Override public boolean runNow() { TaskHandle handle = current; return handle != null && handle.runNow(); }
    @Override public ResourceLocation id() { return id; }
}
