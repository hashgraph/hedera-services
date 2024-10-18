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

package com.hedera.services.bdd.spec.utilops;

import static com.hedera.node.config.types.StreamMode.RECORDS;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountPendingAirdrop;
import com.hedera.services.bdd.junit.hedera.embedded.EmbeddedNetwork;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.utilops.embedded.MutateAccountOp;
import com.hedera.services.bdd.spec.utilops.embedded.MutateNodeOp;
import com.hedera.services.bdd.spec.utilops.embedded.ViewAccountOp;
import com.hedera.services.bdd.spec.utilops.embedded.ViewNodeOp;
import com.hedera.services.bdd.spec.utilops.embedded.ViewPendingAirdropOp;
import com.swirlds.state.spi.CommittableWritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Contains operations that are usable only with an {@link EmbeddedNetwork}.
 */
public final class EmbeddedVerbs {
    private EmbeddedVerbs() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns an operation that allows the test author to directly mutate an account.
     *
     * @param name the name of the account to mutate
     * @param mutation the mutation to apply to the account
     * @return the operation that will mutate the account
     */
    public static MutateAccountOp mutateAccount(
            @NonNull final String name, @NonNull final Consumer<Account.Builder> mutation) {
        return new MutateAccountOp(name, mutation);
    }

    /**
     * Returns an operation that allows the test author to directly mutate an account.
     *
     * @param name the name of the account to mutate
     * @param observer the mutation to apply to the account
     * @return the operation that will mutate the account
     */
    public static ViewAccountOp viewAccount(@NonNull final String name, @NonNull final Consumer<Account> observer) {
        return new ViewAccountOp(name, observer);
    }

    /**
     * Returns an operation that allows the test author to directly mutate an account.
     *
     * @param name the name of the account to mutate
     * @param mutation the mutation to apply to the account
     * @return the operation that will mutate the account
     */
    public static MutateNodeOp mutateNode(@NonNull final String name, @NonNull final Consumer<Node.Builder> mutation) {
        return new MutateNodeOp(name, mutation);
    }

    /**
     * Returns an operation that allows the test author to view a node.
     *
     * @param name the name of the node to view
     * @param observer the mutation to receive the node
     * @return the operation that will expose the node to the observer
     */
    public static ViewNodeOp viewNode(@NonNull final String name, @NonNull final Consumer<Node> observer) {
        return new ViewNodeOp(name, observer);
    }

    /***
     * Returns an operation that allows the test author to view the pending airdrop of an account.
     * @param tokenName the name of the token
     * @param senderName the name of the sender
     * @param receiverName the name of the receiver
     * @param observer the observer to apply to the account
     * @return the operation that will expose the pending airdrop of the account
     */
    public static ViewPendingAirdropOp viewAccountPendingAirdrop(
            @NonNull final String tokenName,
            @NonNull final String senderName,
            @NonNull final String receiverName,
            @NonNull final Consumer<AccountPendingAirdrop> observer) {
        return new ViewPendingAirdropOp(tokenName, senderName, receiverName, observer);
    }

    /**
     * Returns an operation that changes the state of an embedded network to appear to be handling
     * the first transaction after an upgrade.
     *
     * @return the operation that simulates the first transaction after an upgrade
     */
    public static SpecOperation simulatePostUpgradeTransaction() {
        return withOpContext((spec, opLog) -> {
            if (spec.targetNetworkOrThrow() instanceof EmbeddedNetwork embeddedNetwork) {
                // Ensure there are no in-flight transactions that will overwrite our state changes
                spec.sleepConsensusTime(Duration.ofSeconds(1));
                final var embeddedHedera = embeddedNetwork.embeddedHederaOrThrow();
                final var fakeState = embeddedHedera.state();
                final var streamMode = spec.startupProperties().getStreamMode("blockStream.streamMode");
                if (streamMode == RECORDS) {
                    // Mark the migration records as not streamed
                    final var writableStates = fakeState.getWritableStates("BlockRecordService");
                    final var blockInfo = writableStates.<BlockInfo>getSingleton("BLOCKS");
                    blockInfo.put(requireNonNull(blockInfo.get())
                            .copyBuilder()
                            .migrationRecordsStreamed(false)
                            .build());
                    ((CommittableWritableStates) writableStates).commit();
                    // Ensure the next transaction is in a new round with ConcurrentEmbeddedHedera
                    spec.sleepConsensusTime(Duration.ofMillis(10L));
                } else {
                    final var writableStates = fakeState.getWritableStates("BlockStreamService");
                    final var state = writableStates.<BlockStreamInfo>getSingleton("BLOCK_STREAM_INFO");
                    final var blockStreamInfo = requireNonNull(state.get());
                    state.put(blockStreamInfo
                            .copyBuilder()
                            .postUpgradeWorkDone(false)
                            .build());
                    ((CommittableWritableStates) writableStates).commit();
                }
                // Ensure the next transaction is in a new block period
                spec.sleepConsensusTime(Duration.ofSeconds(2L));
            } else {
                throw new IllegalStateException("Cannot simulate post-upgrade transaction on non-embedded network");
            }
        });
    }
}
