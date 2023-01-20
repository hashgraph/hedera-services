package com.hedera.node.app.state.mono;


import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An implementation of {@link SchemaRegistry} used with registering services on the mono-repo merkle tree.
 */
public class MonoSchemaRegistry implements SchemaRegistry {
    @Override
    public SchemaRegistry register(@NonNull Schema schema) {
        // Presently, this is a no-op. We won't modify the mono-repo state tree using the schema system,
        // only through the migration system of the mono-repo.
        return this;
    }
}
