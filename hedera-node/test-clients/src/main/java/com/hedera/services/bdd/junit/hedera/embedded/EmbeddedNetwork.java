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

package com.hedera.services.bdd.junit.hedera.embedded;

import static com.hedera.services.bdd.junit.SharedNetworkLauncherSessionListener.CLASSIC_HAPI_TEST_NETWORK_SIZE;
import static com.hedera.services.bdd.junit.SharedNetworkLauncherSessionListener.repeatableModeRequested;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.classicMetadataFor;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.configTxtForLocal;
import static com.hedera.services.bdd.suites.TargetNetworkType.EMBEDDED_NETWORK;
import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.junit.hedera.AbstractNetwork;
import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.SystemFunctionalityTarget;
import com.hedera.services.bdd.suites.TargetNetworkType;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

public class EmbeddedNetwork extends AbstractNetwork {
    private static final String WORKING_DIR_SCOPE = "embedded";
    private static final String EMBEDDED_HOST = "127.0.0.1";
    private static final String EMBEDDED_NETWORK_NAME = WORKING_DIR_SCOPE.toUpperCase();

    private final String configTxt;
    private final EmbeddedNode embeddedNode;

    @Nullable
    private EmbeddedHedera embeddedHedera;

    /**
     * Creates an embedded "network" of with the given size.
     *
     * @return the embedded network
     */
    public static synchronized HederaNetwork newEmbeddedNetwork() {
        return new EmbeddedNetwork();
    }

    public EmbeddedNetwork() {
        super(
                EMBEDDED_NETWORK_NAME,
                List.of(new EmbeddedNode(
                        classicMetadataFor(0, EMBEDDED_NETWORK_NAME, EMBEDDED_HOST, WORKING_DIR_SCOPE, 0, 0, 0, 0))));
        this.embeddedNode = (EmbeddedNode) nodes().getFirst();
        // Even though we are only embedding node0, we generate an address book
        // for a "classic" HapiTest network with 4 nodes so that tests can still
        // submit transactions with different creator accounts; c.f. EmbeddedHedera,
        // which skips ingest and directly submits transactions for other nodes
        this.configTxt = configTxtForLocal(
                name(),
                IntStream.range(0, CLASSIC_HAPI_TEST_NETWORK_SIZE)
                        .<HederaNode>mapToObj(nodeId -> new EmbeddedNode(classicMetadataFor(
                                nodeId, EMBEDDED_NETWORK_NAME, EMBEDDED_HOST, WORKING_DIR_SCOPE, 0, 0, 0, 0)))
                        .toList(),
                0,
                0);
    }

    @Override
    public void start() {
        // Initialize the working directory
        embeddedNode.initWorkingDir(configTxt);
        embeddedNode.start();
        // Start the embedded Hedera "network"
        embeddedHedera = repeatableModeRequested()
                ? new RepeatableEmbeddedHedera(embeddedNode)
                : new ConcurrentEmbeddedHedera(embeddedNode);
        embeddedHedera.start();
    }

    @Override
    public void terminate() {
        if (embeddedHedera != null) {
            embeddedHedera.stop();
        }
    }

    @Override
    public void awaitReady(@NonNull Duration timeout) {
        if (embeddedHedera == null) {
            throw new IllegalStateException("EmbeddedNetwork is meant for single-threaded startup, please start it");
        }
    }

    @NonNull
    @Override
    public Response send(
            @NonNull final Query query,
            @NonNull final HederaFunctionality functionality,
            @NonNull final AccountID nodeAccountId) {
        return requireNonNull(embeddedHedera).send(query, nodeAccountId);
    }

    @Override
    public TransactionResponse submit(
            @NonNull Transaction transaction,
            @NonNull HederaFunctionality functionality,
            @NonNull SystemFunctionalityTarget target,
            @NonNull AccountID nodeAccountId) {
        return requireNonNull(embeddedHedera).submit(transaction, nodeAccountId);
    }

    @Override
    public TargetNetworkType type() {
        return EMBEDDED_NETWORK;
    }

    public @NonNull EmbeddedHedera embeddedHederaOrThrow() {
        return requireNonNull(embeddedHedera);
    }
}
