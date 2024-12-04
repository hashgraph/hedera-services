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

import static com.hedera.hapi.node.state.tss.RosterToKey.ACTIVE_ROSTER;
import static com.hedera.hapi.node.state.tss.RosterToKey.NONE;
import static com.hedera.hapi.node.state.tss.TssKeyingStatus.KEYING_COMPLETE;
import static com.hedera.hapi.node.state.tss.TssKeyingStatus.WAITING_FOR_ENCRYPTION_KEYS;
import static com.hedera.hapi.node.state.tss.TssKeyingStatus.WAITING_FOR_THRESHOLD_TSS_MESSAGES;
import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_MESSAGE_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_VOTE_MAP_KEY;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.tss.TssEncryptionKeys;
import com.hedera.hapi.node.state.tss.TssStatus;
import com.hedera.node.config.data.TssConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.StateDefinition;
import com.swirlds.state.spi.WritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import java.util.function.Function;

/**
 * Schema for the TSS service.
 */
public class V0580TssBaseSchema extends Schema implements TssBaseTransplantSchema {
    private static final TssStatus BOOTSTRAP_TSS_STATUS =
            new TssStatus(WAITING_FOR_ENCRYPTION_KEYS, ACTIVE_ROSTER, Bytes.EMPTY);
    private static final TssStatus PRESET_ENCRYPTION_KEYS_STATUS =
            new TssStatus(WAITING_FOR_THRESHOLD_TSS_MESSAGES, ACTIVE_ROSTER, Bytes.EMPTY);
    private static final Function<Bytes, TssStatus> PRESET_LEDGER_ID_STATUS_FN =
            ledgerId -> new TssStatus(KEYING_COMPLETE, NONE, ledgerId);

    public static final String TSS_STATUS_KEY = "TSS_STATUS";
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
        return Set.of(
                StateDefinition.singleton(TSS_STATUS_KEY, TssStatus.PROTOBUF),
                StateDefinition.onDisk(
                        TSS_ENCRYPTION_KEYS_KEY,
                        EntityNumber.PROTOBUF,
                        TssEncryptionKeys.PROTOBUF,
                        MAX_TSS_ENCRYPTION_KEYS));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        final var state = ctx.newStates().getSingleton(TSS_STATUS_KEY);
        if (state.get() == null) {
            final var tssEnabled =
                    ctx.appConfig().getConfigData(TssConfig.class).keyCandidateRoster();
            if (!tssEnabled || !ctx.isGenesis()) {
                // If TSS is enabled after but there is no status yet, we are at the upgrade boundary and
                // know for sure we have to bootstrap keys
                state.put(BOOTSTRAP_TSS_STATUS);
            } else {
                // With TSS enabled at genesis the roster lifecycle must also be enabled so we can safely
                // check for the presence of encryption keys and possibly ledger ID in the startup assets
                final var network = ctx.startupNetworks().genesisNetworkOrThrow();
                final WritableKVState<EntityNumber, TssEncryptionKeys> encryptionKeys =
                        ctx.newStates().get(TSS_ENCRYPTION_KEYS_KEY);
                setEncryptionKeys(network, encryptionKeys);
                if (encryptionKeys.size() == network.nodeMetadata().size()) {
                    final var ledgerId = network.ledgerId();
                    if (ledgerId.length() > 0) {
                        state.put(PRESET_LEDGER_ID_STATUS_FN.apply(ledgerId));
                        setTssMessageOpsAndVotes(
                                network,
                                ctx.newStates().get(TSS_MESSAGE_MAP_KEY),
                                ctx.newStates().get(TSS_VOTE_MAP_KEY));
                    } else {
                        state.put(PRESET_ENCRYPTION_KEYS_STATUS);
                    }
                } else {
                    state.put(BOOTSTRAP_TSS_STATUS);
                }
            }
        }
    }

    @Override
    public void restart(@NonNull final MigrationContext ctx) {
        TssBaseTransplantSchema.super.restart(ctx);
    }
}
