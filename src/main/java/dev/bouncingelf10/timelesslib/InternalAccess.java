package dev.bouncingelf10.timelesslib;

/**
 * Capability token proving that a manager is being constructed by TimelessLib itself. <br>
 * Manager constructors across the {@code api} package require an instance of this class.
 * Since only {@link TimelessLib} and {@link TimelessLibClient} (same package) can obtain one via {@link #issue()},
 * no other mod can construct a manager directly.
 */
public final class InternalAccess {
    private InternalAccess() {}

    static InternalAccess issue() {
        return new InternalAccess();
    }
}
