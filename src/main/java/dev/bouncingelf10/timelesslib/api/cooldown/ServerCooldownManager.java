package dev.bouncingelf10.timelesslib.api.cooldown;

import java.util.UUID;
import java.util.function.Supplier;

public class ServerCooldownManager<T> extends AbstractCooldownManager<T> {

    public ServerCooldownManager(Supplier<T> server) {
        super(server);
    }

    public ServerCooldownManager(Supplier<T> server, int poolSize) {
        super(server, poolSize);
    }

    @Override
    protected UUID normalizeOwner(UUID owner) {
        return owner;
    }
}
