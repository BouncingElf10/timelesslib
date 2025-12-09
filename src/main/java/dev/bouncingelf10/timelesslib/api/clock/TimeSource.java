package dev.bouncingelf10.timelesslib.api.clock;

@FunctionalInterface
public interface TimeSource {
    long now();
}
