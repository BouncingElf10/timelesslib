package dev.bouncingelf10.timelesslib.api.clock;

import dev.bouncingelf10.timelesslib.fabric.TimelessFabricHelper;

public final class TimelessClock {
    private static long lastRealTime = System.nanoTime();
    private static long gameNanos = 0;
    private static long delta = 0;
    private static boolean paused = false;

    public static void update() {
        long now = System.nanoTime();

        if (!TimelessFabricHelper.shouldAdvanceTime()) {
            paused = true;
            lastRealTime = now;
            return;
        }

        delta = now - lastRealTime;
        lastRealTime = now;

        if (!paused) {
            gameNanos += delta;
        }

        paused = false;
    }

    /**
     * Checks if the client is paused<br>
     * Note: "paused" means that the game is frozen, not that the client is on the pause screen.
     * So the client can never be "paused" if on a dedicated server.
     */
    public static boolean isPaused() { return paused; }
    public static long deltaNanos() { return delta; }
    public static double deltaSeconds() { return delta / 1_000_000_000.0; }

    public static long gameTime() { return gameNanos; }
    public static long realTime() { return System.nanoTime(); }

}
