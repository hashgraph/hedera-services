// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.tss.schemas;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.tss.TssEncryptionKeys;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Schema for the TSS service.
 */
@Deprecated(forRemoval = true, since = "0.59.0")
public class V0580TssBaseSchema extends Schema {
    public static final String TSS_ENCRYPTION_KEYS_KEY = "TSS_ENCRYPTION_KEYS";
    /**
     * This will at most be equal to the number of nodes in the network.
     */
    private static final long MAX_TSS_ENCRYPTION_KEYS = 65_536L;

    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(58).patch(0).build();

    public V0580TssBaseSchema() {
        super(VERSION);
    }

    @Override
    public @NonNull Set<StateDefinition> statesToCreate() {
        return Set.of(StateDefinition.onDisk(
                TSS_ENCRYPTION_KEYS_KEY, EntityNumber.PROTOBUF, TssEncryptionKeys.PROTOBUF, MAX_TSS_ENCRYPTION_KEYS));
    }
}
