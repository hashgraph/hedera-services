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

import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_MESSAGE_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_VOTE_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0580TssBaseSchema.TSS_ENCRYPTION_KEYS_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.tss.TssEncryptionKeys;
import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.config.data.TssConfig;
import com.hedera.node.internal.network.Network;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.spi.WritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The {@link Schema#restart(MigrationContext)} implementation whereby the {@link TssBaseService} ensures that any
 * TSS keys in the startup assets are copied into the state. These assets may range from no keys at all, to just
 * encryption keys; to a full set of TSS messages with shares for a transplant roster encrypted using the encryption
 * keys in the override address book.
 * <p>
 * <b>Important:</b> The latest {@link TssBaseService} schema should always implement this interface.
 */
public interface TssBaseTransplantSchema {
    default void restart(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);
        if (!ctx.appConfig().getConfigData(TssConfig.class).keyCandidateRoster()) {
            return;
        }
        ctx.startupNetworks().overrideNetworkFor(ctx.roundNumber()).ifPresent(network -> {
            setEncryptionKeys(network, ctx.newStates().get(TSS_ENCRYPTION_KEYS_KEY));
            setTssMessageOpsAndVotes(
                    network,
                    ctx.newStates().get(TSS_MESSAGE_MAP_KEY),
                    ctx.newStates().get(TSS_VOTE_MAP_KEY));
        });
    }

    /**
     * Set the encryption keys in the state from the provided network, for whatever nodes they are available.
     * @param network the network from which to extract the encryption keys
     * @param encryptionKeys the state in which to store the encryption keys
     */
    default void setEncryptionKeys(
            @NonNull final Network network,
            @NonNull final WritableKVState<EntityNumber, TssEncryptionKeys> encryptionKeys) {
        network.nodeMetadata().forEach(metadata -> {
            if (metadata.tssEncryptionKey().length() > 0) {
                final var key = new EntityNumber(metadata.rosterEntryOrThrow().nodeId());
                final var value = new TssEncryptionKeys(metadata.tssEncryptionKey(), Bytes.EMPTY);
                encryptionKeys.put(key, value);
            }
        });
    }

    /**
     * Set the TSS messages in the state from the provided network, for whatever nodes they are available.
     * @param network the network from which to extract the TSS messages
     * @param tssMessageOps the state in which to store the TSS messages
     */
    default void setTssMessageOpsAndVotes(
            @NonNull final Network network,
            @NonNull final WritableKVState<TssMessageMapKey, TssMessageTransactionBody> tssMessageOps,
            @NonNull final WritableKVState<TssVoteMapKey, TssVoteTransactionBody> tssVotes) {
        final var ops = network.tssMessages();
        if (ops.isEmpty()) {
            return;
        }
        final AtomicLong seqNo = new AtomicLong(1);
        final var roster = RosterUtils.rosterFrom(network);
        final var rosterHash = RosterUtils.hash(roster).getBytes();
        network.tssMessages()
                .forEach(op -> tssMessageOps.put(new TssMessageMapKey(rosterHash, seqNo.getAndIncrement()), op));
        final var tssVote = new BitSet();
        for (int i = 0, n = ops.size(); i < n; i++) {
            tssVote.set(i);
        }
        network.nodeMetadata()
                .forEach(metadata -> tssVotes.put(
                        new TssVoteMapKey(
                                rosterHash, metadata.rosterEntryOrThrow().nodeId()),
                        TssVoteTransactionBody.newBuilder()
                                .ledgerId(network.ledgerId())
                                // (FUTURE) Are there any environments that would want a real signature here?
                                .nodeSignature(Bytes.EMPTY)
                                // (FUTURE) Are there any environments that would want a real source roster hash here?
                                .sourceRosterHash(Bytes.EMPTY)
                                .targetRosterHash(rosterHash)
                                .tssVote(Bytes.wrap(tssVote.toByteArray()))
                                .build()));
    }
}
