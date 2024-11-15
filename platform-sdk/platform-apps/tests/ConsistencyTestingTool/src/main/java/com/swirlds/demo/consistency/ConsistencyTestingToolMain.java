/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import static com.swirlds.platform.util.NoOpMerkleStateLifecycles.NO_OP_MERKLE_STATE_LIFECYCLES;
import static com.swirlds.state.merkle.StateUtils.registerWithSystem;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.platform.state.service.PlatformStateService;
import com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldMain;
import com.swirlds.state.lifecycle.StateDefinition;
import com.swirlds.state.merkle.StateMetadata;
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
            constructableRegistry.registerConstructable(new ClassConstructorPair(
                    ConsistencyTestingToolState.class,
                    () -> new ConsistencyTestingToolState(
                            NO_OP_MERKLE_STATE_LIFECYCLES, version -> new BasicSoftwareVersion(version.major()))));
            logger.info(STARTUP.getMarker(), "ConsistencyTestingToolState is registered with ConstructableRegistry");
            registerPlatformClasses(constructableRegistry);

        } catch (ConstructableRegistryException e) {
            logger.error(STARTUP.getMarker(), "Failed to register ConsistencyTestingToolState", e);
            throw new RuntimeException(e);
        }
    }

    private static void registerPlatformClasses(ConstructableRegistry constructableRegistry) {
        logger.info(STARTUP.getMarker(), "Registering PlatformState classes with ConstructableRegistry");
        final var schema = new V0540PlatformStateSchema();
        final StateDefinition def = schema.statesToCreate().iterator().next();
        final var md = new StateMetadata<>(PlatformStateService.NAME, schema, def);
        registerWithSystem(md, constructableRegistry);
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
    public MerkleStateRoot newMerkleStateRoot() {
        final MerkleStateRoot state = new ConsistencyTestingToolState(
                NO_OP_MERKLE_STATE_LIFECYCLES, version -> new BasicSoftwareVersion(softwareVersion.getVersion()));
        NO_OP_MERKLE_STATE_LIFECYCLES.initPlatformState(state);
        registerPlatformClasses(ConstructableRegistry.INSTANCE);

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
}
