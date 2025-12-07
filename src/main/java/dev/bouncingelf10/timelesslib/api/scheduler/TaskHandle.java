package dev.bouncingelf10.timelesslib.api.scheduler;

import dev.bouncingelf10.timelesslib.api.time.Duration;

import java.util.Optional;

public interface TaskHandle {
    boolean cancel();
    boolean pause();
    boolean resume();
    boolean pauseOrUnpause();
    boolean isCancelled();
    boolean isPaused();
    boolean isRunning();
    boolean isScheduled();
    Optional<Duration>
    getRemainingDelay();
    Optional<Duration> getPeriod();
    boolean runNow();
    String id();
}