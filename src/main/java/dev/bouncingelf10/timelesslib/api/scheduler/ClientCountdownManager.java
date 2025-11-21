package dev.bouncingelf10.timelesslib.api.scheduler;

import dev.bouncingelf10.timelesslib.TimelessClock;
import dev.bouncingelf10.timelesslib.api.time.Duration;
import dev.bouncingelf10.timelesslib.api.time.TimeFormatter;
import dev.bouncingelf10.timelesslib.fabric.TimelessFabricHelper;

import java.util.function.Supplier;


public class ClientCountdownManager<T> extends CountdownManager<T> {
    public ClientCountdownManager(Supplier<T> contextProvider) {
        super(contextProvider);
    }

    public ClientCountdownManager(Supplier<T> contextProvider, int poolSize) {
        super(contextProvider, poolSize);
    }

    public ClientCountdown startClient(Duration total, Duration tickEvery, TimelessClock.TimeSource timeSource) {
        Countdown base = super.start(total, tickEvery, timeSource);
        return new ClientCountdown(base);
    }

    public class ClientCountdown {
        private final Countdown base;

        public ClientCountdown(Countdown base) {
            this.base = base;
        }

        public ClientCountdown onTick(java.util.function.BiConsumer<T, Duration> handler) {
            base.onTick(handler);
            return this;
        }

        public ClientCountdown onFinish(java.util.function.Consumer<T> handler) {
            base.onFinish(handler);
            return this;
        }

        public ClientCountdown onThreshold(Duration threshold, java.util.function.Consumer<T> handler) {
            base.onThreshold(threshold, handler);
            return this;
        }
        
        public ClientCountdown every(Duration interval, java.util.function.Consumer<T> handler) {
            base.every(interval, handler);
            return this;
        }

        public boolean pause() { return base.pause(); }
        public boolean resume() { return base.resume(); }
        public boolean cancel() { return base.cancel(); }
        public boolean isPaused() { return base.isPaused(); }
        public boolean isCancelled() { return base.isCancelled(); }
        public Duration remaining() { return base.remaining(); }
        public String id() { return base.id(); }

        public ClientCountdown displayToUser() {
            TimelessFabricHelper.clientDisplayToUser(remaining().toNanos());
            return every(Duration.ofMillis(10), context -> TimelessFabricHelper.clientDisplayToUser(remaining().toNanos()));
        }

        public ClientCountdown displayToUser(TimeFormatter.TimeFormat format, String prefix, String suffix) {
            TimelessFabricHelper.clientDisplayToUser(remaining().toNanos(), format, prefix, suffix);
            return every(Duration.ofMillis(10), context -> TimelessFabricHelper.clientDisplayToUser(remaining().toNanos(), format, prefix, suffix));
        }
    }
}