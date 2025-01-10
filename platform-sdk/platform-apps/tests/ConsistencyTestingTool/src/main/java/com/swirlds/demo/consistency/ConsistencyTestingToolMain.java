/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.swirlds.demo.consistency;

import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static com.swirlds.platform.test.fixtures.state.FakeStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;
import static com.swirlds.platform.test.fixtures.state.FakeStateLifecycles.registerMerkleStateRootClassIds;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldMain;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A testing app for guaranteeing proper handling of transactions after a restart
 */
public class ConsistencyTestingToolMain implements SwirldMain {

    private static final Logger logger = LogManager.getLogger(ConsistencyTestingToolMain.class);

    /**
     * The default software version of this application
     */
    private static final SoftwareVersion softwareVersion = new BasicSoftwareVersion(1);

    static {
        try {
            logger.info(STARTUP.getMarker(), "Registering ConsistencyTestingToolState with ConstructableRegistry");
            ConstructableRegistry constructableRegistry = ConstructableRegistry.getInstance();
            constructableRegistry.registerConstructable(
                    new ClassConstructorPair(ConsistencyTestingToolState.class, () -> {
                        ConsistencyTestingToolState consistencyTestingToolState = new ConsistencyTestingToolState(
                                FAKE_MERKLE_STATE_LIFECYCLES, version -> new BasicSoftwareVersion(version.major()));
                        // Don't call FAKE_MERKLE_STATE_LIFECYCLES.initStates(consistencyTestingToolState) here.
                        // The stub states are automatically initialized upon loading the state from disk,
                        // or after finishing a reconnect.
                        return consistencyTestingToolState;
                    }));
            registerMerkleStateRootClassIds();
            logger.info(STARTUP.getMarker(), "ConsistencyTestingToolState is registered with ConstructableRegistry");
        } catch (ConstructableRegistryException e) {
            logger.error(STARTUP.getMarker(), "Failed to register ConsistencyTestingToolState", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * The platform instance
     */
    private Platform platform;

    /**
     * The number of transactions to generate per second.
     */
    private static final int TRANSACTIONS_PER_SECOND = 100;

    /**
     * Constructor
     */
    public ConsistencyTestingToolMain() {
        logger.info(STARTUP.getMarker(), "constructor called in Main.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(@NonNull final Platform platform, @NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId);

        this.platform = Objects.requireNonNull(platform);

        logger.info(STARTUP.getMarker(), "init called in Main for node {}.", nodeId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        logger.info(STARTUP.getMarker(), "run called in Main.");
        new TransactionGenerator(new SecureRandom(), platform, TRANSACTIONS_PER_SECOND).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public PlatformMerkleStateRoot newMerkleStateRoot() {
        final PlatformMerkleStateRoot state = new ConsistencyTestingToolState(
                FAKE_MERKLE_STATE_LIFECYCLES, version -> new BasicSoftwareVersion(softwareVersion.getVersion()));
        FAKE_MERKLE_STATE_LIFECYCLES.initStates(state);

        return state;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public SoftwareVersion getSoftwareVersion() {
        logger.info(STARTUP.getMarker(), "returning software version {}", softwareVersion);
        return softwareVersion;
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public List<Class<? extends Record>> getConfigDataTypes() {
        return List.of(ConsistencyTestingToolConfig.class);
    }

    @Override
    public Bytes encodeSystemTransaction(final @NonNull StateSignatureTransaction transaction) {
        return StateSignatureTransaction.PROTOBUF.toBytes(transaction);
    }
}
