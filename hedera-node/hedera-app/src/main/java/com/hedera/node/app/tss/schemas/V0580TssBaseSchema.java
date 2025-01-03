/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.tss.schemas;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.tss.TssEncryptionKeys;
import com.hedera.node.app.tss.stores.WritableTssStore;
import com.hedera.node.config.data.TssConfig;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Schema for the TSS service.
 */
public class V0580TssBaseSchema extends Schema implements TssBaseTransplantSchema {
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

    /**
     * The factory to use to create the writable roster store.
     */
    private final Function<WritableStates, WritableTssStore> tssStoreFactory;

    private final Supplier<ReadableRosterStore> readableRosterStoreSupplier;

    /**
     * Create a new instance.
     */
    public V0580TssBaseSchema(
            @NonNull final Function<WritableStates, WritableTssStore> tssStoreFactory,
            @NonNull final Supplier<ReadableRosterStore> readableRosterStoreSupplier) {
        super(VERSION);
        this.tssStoreFactory = requireNonNull(tssStoreFactory);
        this.readableRosterStoreSupplier = requireNonNull(readableRosterStoreSupplier);
    }

    @Override
    public @NonNull Set<StateDefinition> statesToCreate() {
        return Set.of(StateDefinition.onDisk(
                TSS_ENCRYPTION_KEYS_KEY, EntityNumber.PROTOBUF, TssEncryptionKeys.PROTOBUF, MAX_TSS_ENCRYPTION_KEYS));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        // remove TssEncryptionKeys from state if node ids are not present
        // in both active and candidate roster's entries
        if (ctx.appConfig().getConfigData(TssConfig.class).keyCandidateRoster()) {
            final var tssStore = tssStoreFactory.apply(ctx.newStates());
            tssStore.removeIfNotPresent(readableRosterStoreSupplier.get().getCombinedRosterEntriesNodeIds());
        }
    }

    @Override
    public void restart(@NonNull final MigrationContext ctx) {
        TssBaseTransplantSchema.super.restart(ctx);

        // remove TssEncryptionKeys from state if node ids are not present
        // in both active and candidate roster's entries
        if (ctx.appConfig().getConfigData(TssConfig.class).keyCandidateRoster()) {
            final var tssStore = tssStoreFactory.apply(ctx.newStates());
            tssStore.removeIfNotPresent(readableRosterStoreSupplier.get().getCombinedRosterEntriesNodeIds());
        }
    }
}
