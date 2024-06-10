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

import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.CLASSIC_FIRST_NODE_ACCOUNT_NUM;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.configTxtForLocal;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.workingDirFor;
import static com.hedera.services.bdd.suites.TargetNetworkType.EMBEDDED_NETWORK;
import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.junit.hedera.AbstractNetwork;
import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeMetadata;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class EmbeddedNetwork extends AbstractNetwork {
    private static final String WORKING_DIR_SCOPE = "embedded";
    private static final String EMBEDDED_NETWORK_NAME = WORKING_DIR_SCOPE.toUpperCase();

    private final String configTxt;
    private final EmbeddedNode embeddedNode;

    @Nullable
    private EmbeddedHedera embeddedHedera;

    public static void main(String... args) {
        final var subject = new EmbeddedNetwork(4);
        subject.start();
        subject.terminate();
    }

    /**
     * Creates an embedded "network" of with the given size.
     *
     * @param size the number of nodes in the network
     * @return the embedded network
     */
    public static synchronized HederaNetwork newEmbeddedNetwork(final int size) {
        return new EmbeddedNetwork(size);
    }

    public EmbeddedNetwork(final int size) {
        super(
                EMBEDDED_NETWORK_NAME,
                Stream.<HederaNode>of(new EmbeddedNode(ghostMetadata()))
                        .flatMap(node ->
                                IntStream.range(0, size).mapToObj(((EmbeddedNode) node)::withClassicBookDataFor))
                        .toList());
        this.embeddedNode = (EmbeddedNode) nodes().getFirst();
        this.configTxt = configTxtForLocal(name(), nodes(), 0, 0);
    }

    @Override
    public void start() {
        // Initialize the working directory
        embeddedNode.initWorkingDir(configTxt);
        embeddedNode.start();
        // Start the embedded Hedera "network"
        embeddedHedera = new EmbeddedHedera(embeddedNode);
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

    private static NodeMetadata ghostMetadata() {
        return new NodeMetadata(
                0,
                "<GHOST>",
                com.hedera.hapi.node.base.AccountID.newBuilder()
                        .accountNum(CLASSIC_FIRST_NODE_ACCOUNT_NUM)
                        .build(),
                "0.0.0.0",
                0,
                0,
                0,
                0,
                workingDirFor(0, WORKING_DIR_SCOPE));
    }
}
