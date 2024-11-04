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
import static com.hedera.services.bdd.junit.hedera.embedded.EmbeddedMode.REPEATABLE;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.classicMetadataFor;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.configTxtForLocal;
import static com.hedera.services.bdd.spec.TargetNetworkType.EMBEDDED_NETWORK;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.services.bdd.junit.hedera.AbstractNetwork;
import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.SystemFunctionalityTarget;
import com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.TargetNetworkType;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EmbeddedNetwork extends AbstractNetwork {
    private static final Logger log = LogManager.getLogger(EmbeddedNetwork.class);

    private static final String FAKE_HOST = "127.0.0.1";
    public static final String CONCURRENT_WORKING_DIR = "concurrent";
    private static final String CONCURRENT_NAME = CONCURRENT_WORKING_DIR.toUpperCase();
    public static final String REPEATABLE_WORKING_DIR = "repeatable";
    private static final String REPEATABLE_NAME = REPEATABLE_WORKING_DIR.toUpperCase();

    private final String configTxt;
    private final EmbeddedMode mode;
    private final EmbeddedNode embeddedNode;

    @Nullable
    private EmbeddedHedera embeddedHedera;

    /**
     * Creates an embedded "network" with default name and scope to be shared by many tests.
     *
     * @return the embedded network
     */
    public static HederaNetwork newSharedNetwork(@NonNull final EmbeddedMode mode) {
        requireNonNull(mode);
        return switch (mode) {
            case CONCURRENT -> new EmbeddedNetwork(CONCURRENT_NAME, CONCURRENT_WORKING_DIR, mode);
            case REPEATABLE -> new EmbeddedNetwork(REPEATABLE_NAME, REPEATABLE_WORKING_DIR, mode);
        };
    }

    public EmbeddedNetwork(
            @NonNull final String name, @NonNull final String workingDir, @NonNull final EmbeddedMode mode) {
        super(
                name,
                IntStream.range(0, CLASSIC_HAPI_TEST_NETWORK_SIZE)
                        .<HederaNode>mapToObj(nodeId -> new EmbeddedNode(
                                classicMetadataFor(nodeId, name, FAKE_HOST, workingDir, 0, 0, 0, 0, 0)))
                        .toList());
        this.mode = requireNonNull(mode);
        this.embeddedNode = (EmbeddedNode) nodes().getFirst();
        // Even though we are only embedding node0, we generate an address book
        // for a "classic" HapiTest network with 4 nodes so that tests can still
        // submit transactions with different creator accounts; c.f. EmbeddedHedera,
        // which skips ingest and directly submits transactions for other nodes
        this.configTxt = configTxtForLocal(name(), nodes(), 1, 1);
    }

    @Override
    public void start() {
        // Initialize the working directory
        embeddedNode.initWorkingDir(configTxt);
        embeddedNode.start();
        // Start the embedded Hedera "network"
        embeddedHedera = switch (mode) {
            case REPEATABLE -> new RepeatableEmbeddedHedera(embeddedNode);
            case CONCURRENT -> new ConcurrentEmbeddedHedera(embeddedNode);};
        embeddedHedera.start();
    }

    @Override
    public void terminate() {
        if (embeddedHedera != null) {
            embeddedHedera.stop();
            if (mode == REPEATABLE) {
                final var runningHashes = embeddedHedera
                        .state()
                        .getReadableStates("BlockRecordService")
                        .<RunningHashes>getSingleton("RUNNING_HASHES")
                        .get();
                if (runningHashes != null) {
                    log.info(
                            "Final record running hash - {}",
                            runningHashes.runningHash().toHex());
                }
            }
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
            @NonNull final AccountID nodeAccountId,
            final boolean asNodeOperator) {
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

    /**
     * Returns whether the embedded network is in repeatable mode.
     */
    public boolean inRepeatableMode() {
        return mode == REPEATABLE;
    }

    /**
     * Returns the embedded mode of the network.
     */
    public EmbeddedMode mode() {
        return mode;
    }

    @Override
    protected HapiPropertySource networkOverrides() {
        return WorkingDirUtils.hapiTestStartupProperties();
    }
}
