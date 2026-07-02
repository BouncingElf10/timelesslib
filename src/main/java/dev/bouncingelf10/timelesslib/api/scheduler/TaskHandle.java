package dev.bouncingelf10.timelesslib.api.scheduler;

import dev.bouncingelf10.timelesslib.api.time.Duration;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Represents a scheduled task that can be controlled at runtime. <br>
 * Implemented by plain scheduled tasks, {@link Sequence} handles, and {@code Countdown} handles, so the same
 * cancel/pause/resume vocabulary applies everywhere in the scheduler and countdown APIs.
 */
public interface TaskHandle {

    /**
     * Cancels the task.
     * @return true if the task was canceled, false otherwise
     */
    boolean cancel();

    /**
     * Pauses the task.
     * <br>Note: Returns false if the task is already paused or canceled.
     * @return true if the task was paused, false otherwise
     */
    boolean pause();

    /**
     * Resumes the task if paused.
     * <br>Note: Returns false if the task is not paused or is canceled.
     * @return true if resumed, false otherwise
     */
    boolean resume();

    /**
     * Toggles between paused and unpaused.
     * @return true if the state changed, false otherwise
     */
    boolean pauseOrUnpause();

    /**
     * @return true if the task has been canceled
     */
    boolean isCancelled();
    boolean isPaused();
    boolean isRunning();
    /**
     * @return true if the task is scheduled for execution
     */
    boolean isScheduled();

    /**
     * Returns the remaining delay before the next run.
     * @return remaining delay, or empty if canceled
     */
    Optional<Duration> getRemainingDelay();

    /**
     * Returns the period of a repeating task.
     * @return the period, or empty if not repeating
     */
    Optional<Duration> getPeriod();

    /**
     * Runs the task immediately if possible.
     * @return true if the task was executed
     */
    boolean runNow();

    /**
     * @return unique ID of this task
     */
    ResourceLocation id();
}
