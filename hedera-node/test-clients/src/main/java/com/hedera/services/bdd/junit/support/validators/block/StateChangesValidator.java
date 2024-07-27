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

package com.hedera.services.bdd.junit.support.validators.block;

import static com.hedera.services.bdd.junit.hedera.ExternalPath.SAVED_STATES_DIR;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.STATE_METADATA_FILE;
import static com.hedera.services.bdd.junit.support.BlockStreamAccess.BLOCK_STREAM_ACCESS;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.node.app.blocks.BlockStreamService;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.addressbook.impl.AddressBookServiceImpl;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.NetworkServiceImpl;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.util.impl.UtilServiceImpl;
import com.hedera.node.app.services.OrderedServiceMigrator;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.services.ServicesRegistryImpl;
import com.hedera.node.app.spi.fixtures.info.FakeNetworkInfo;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.node.config.data.VersionConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.support.BlockStreamValidator;
import com.hedera.services.bdd.spec.HapiSpec;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.platform.state.MerkleStateRoot;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

/**
 * A validator that asserts the state changes in the block stream, when applied directly to a {@link MerkleStateRoot}
 * initialized with the genesis {@link com.swirlds.state.spi.Service} schemas, result in the given root hash.
 */
public class StateChangesValidator implements BlockStreamValidator {
    private static final Logger logger = LogManager.getLogger(StateChangesValidator.class);
    private static final MerkleCryptography CRYPTO = MerkleCryptoFactory.getInstance();

    private static final int HASH_SIZE = 48;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    private final Bytes expectedRootHash;
    private final MerkleStateRoot state = new MerkleStateRoot();

    public static void main(String[] args) throws IOException {
        final var testBlocksLoc = "/Users/michaeltinker/Dev/hedera-services/some-blocks";
        final var blocks = BLOCK_STREAM_ACCESS.readBlocks(Paths.get(testBlocksLoc));
        final var validator = new StateChangesValidator(Bytes.EMPTY);
        validator.validateBlocks(blocks);
    }

    /**
     * Constructs a validator that will assert the state changes in the block stream are consistent with the
     * root hash found in the latest saved state directory from a node targeted by the given spec.
     *
     * @param spec the spec
     * @return the validator
     */
    public static StateChangesValidator newValidatorFor(@NonNull final HapiSpec spec) {
        requireNonNull(spec);
        final var latestStateDir = findMaybeLatestSavedStateFor(spec);
        if (latestStateDir == null) {
            throw new AssertionError("No saved state directory found");
        }
        final var rootHash = findRootHashFrom(latestStateDir.resolve(STATE_METADATA_FILE));
        if (rootHash == null) {
            throw new AssertionError("No root hash found in state metadata file");
        }
        return new StateChangesValidator(rootHash);
    }

    public StateChangesValidator(@NonNull final Bytes expectedRootHash) {
        this.expectedRootHash = requireNonNull(expectedRootHash);

        final var bootstrapConfig = new BootstrapConfigProviderImpl().getConfiguration();
        final var servicesRegistry = new ServicesRegistryImpl(ConstructableRegistry.getInstance(), bootstrapConfig);
        registerServices(InstantSource.system(), servicesRegistry, bootstrapConfig);

        final var currentVersion =
                bootstrapConfig.getConfigData(VersionConfig.class).servicesVersion();
        final var migrator = new OrderedServiceMigrator();
        migrator.doMigrations(
                state,
                servicesRegistry,
                null,
                currentVersion,
                new ConfigProviderImpl().getConfiguration(),
                new FakeNetworkInfo(),
                new NoOpMetrics());
        logger.info("Registered all Service and migrated state definitions to version {}", currentVersion);
    }

    @Override
    public void validateBlocks(@NonNull final List<Block> blocks) {
        logger.info("Beginning validation of expected root hash {}", expectedRootHash);
        for (final var block : blocks) {
            for (final var item : block.items()) {
                if (item.hasStateChanges()) {
                    applyStateChanges(item.stateChangesOrThrow());
                }
            }
        }
        CRYPTO.digestTreeSync(state);
        final var rootHash = state.getHash().getBytes();
        assertEquals(expectedRootHash, rootHash, "Root hash mismatch");
    }

    private void applyStateChanges(@NonNull final StateChanges stateChanges) {
        for (final var stateChange : stateChanges.stateChanges()) {
            final var delimIndex = stateChange.stateName().indexOf('.');
            if (delimIndex == -1) {
                Assertions.fail("State name " + stateChange.stateName() + " is not in the correct format");
            }
            final var serviceName = stateChange.stateName().substring(0, delimIndex);
            final var writableStates = state.getWritableStates(serviceName);
            final var stateKey = stateChange.stateName().substring(delimIndex + 1);
            switch (stateChange.changeOperation().kind()) {
                case UNSET -> throw new IllegalStateException("Change operation is not set");
                case STATE_ADD, STATE_REMOVE -> {
                    // No-op
                }
                case SINGLETON_UPDATE -> {
                    final var singletonState = writableStates.getSingleton(stateKey);
                    singletonState.put(requireNonNull(stateChange.singletonUpdate())
                            .newValue()
                            .value());
                }
                case MAP_UPDATE -> {
                    final var mapState = writableStates.get(stateKey);
                    mapState.put(
                            requireNonNull(stateChange.mapUpdate())
                                    .keyOrThrow()
                                    .keyChoice()
                                    .value(),
                            requireNonNull(stateChange.mapUpdate())
                                    .valueOrThrow()
                                    .valueChoice()
                                    .value());
                }
                case MAP_DELETE -> {
                    final var mapState = writableStates.get(stateKey);
                    mapState.remove(requireNonNull(stateChange.mapDelete())
                            .keyOrThrow()
                            .keyChoice()
                            .value());
                }
                case QUEUE_PUSH -> {
                    final var queueState = writableStates.getQueue(stateKey);
                    queueState.add(
                            requireNonNull(stateChange.queuePush()).value().value());
                }
                case QUEUE_POP -> {
                    final var queueState = writableStates.getQueue(stateKey);
                    queueState.poll();
                }
            }
        }
    }

    private record ServiceChangesSummary(
            Map<String, Long> singletonPuts,
            Map<String, Long> mapUpdates,
            Map<String, Long> mapDeletes,
            Map<String, Long> queuePushes,
            Map<String, Long> queuePops) {
        public static ServiceChangesSummary newSummary(@NonNull final String serviceName) {
            return new ServiceChangesSummary(
                    new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        }
    }

    private record StateChangesSummary(Map<String, ServiceChangesSummary> serviceChanges) {
        public void countSingletonPut(String serviceName, String stateKey) {
            serviceChanges
                    .computeIfAbsent(serviceName, ServiceChangesSummary::newSummary)
                    .singletonPuts()
                    .merge(stateKey, 1L, Long::sum);
        }

        public void countMapUpdate(String serviceName, String stateKey) {
            serviceChanges
                    .computeIfAbsent(serviceName, ServiceChangesSummary::newSummary)
                    .mapUpdates()
                    .merge(stateKey, 1L, Long::sum);
        }

        public void countMapDelete(String serviceName, String stateKey) {
            serviceChanges
                    .computeIfAbsent(serviceName, ServiceChangesSummary::newSummary)
                    .mapDeletes()
                    .merge(stateKey, 1L, Long::sum);
        }

        public void countQueuePush(String serviceName, String stateKey) {
            serviceChanges
                    .computeIfAbsent(serviceName, ServiceChangesSummary::newSummary)
                    .queuePushes()
                    .merge(stateKey, 1L, Long::sum);
        }

        public void countQueuePop(String serviceName, String stateKey) {
            serviceChanges
                    .computeIfAbsent(serviceName, ServiceChangesSummary::newSummary)
                    .queuePops()
                    .merge(stateKey, 1L, Long::sum);
        }
    }

    private void registerServices(
            final InstantSource instantSource,
            final ServicesRegistry servicesRegistry,
            final VersionedConfiguration bootstrapConfig) {
        // Register all service schema RuntimeConstructable factories before platform init
        Set.of(
                        new EntityIdService(),
                        new ConsensusServiceImpl(),
                        new ContractServiceImpl(instantSource),
                        new FileServiceImpl(),
                        new FreezeServiceImpl(),
                        new ScheduleServiceImpl(),
                        new TokenServiceImpl(),
                        new UtilServiceImpl(),
                        new RecordCacheService(),
                        new BlockRecordService(),
                        new BlockStreamService(bootstrapConfig),
                        new FeeService(),
                        new CongestionThrottleService(),
                        new NetworkServiceImpl(),
                        new AddressBookServiceImpl())
                .forEach(servicesRegistry::register);
    }

    private static @Nullable Bytes findRootHashFrom(@NonNull final Path stateMetadataPath) {
        try (final var lines = Files.lines(stateMetadataPath)) {
            return lines.filter(line -> line.startsWith("HASH:"))
                    .map(line -> line.substring(line.length() - 2 * HASH_SIZE))
                    .map(Bytes::fromHex)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            logger.error("Failed to read state metadata file {}", stateMetadataPath, e);
            return null;
        }
    }

    private static @Nullable Path findMaybeLatestSavedStateFor(@NonNull final HapiSpec spec) {
        final var savedStateDirs = spec.getNetworkNodes().stream()
                .map(node -> node.getExternalPath(SAVED_STATES_DIR))
                .map(Path::toAbsolutePath)
                .toList();
        for (final var savedStatesDir : savedStateDirs) {
            try {
                final var latestRoundPath = findLargestNumberDirectory(savedStatesDir);
                if (latestRoundPath != null) {
                    return latestRoundPath;
                }
            } catch (IOException e) {
                logger.error("Failed to find the latest saved state directory in {}", savedStatesDir, e);
            }
        }
        return null;
    }

    private static @Nullable Path findLargestNumberDirectory(@NonNull final Path savedStatesDir) throws IOException {
        long latestRound = -1;
        Path latestRoundPath = null;
        try (final var stream = Files.newDirectoryStream(savedStatesDir, StateChangesValidator::isNumberDirectory)) {
            for (final var numberDirectory : stream) {
                final var round = Long.parseLong(numberDirectory.getFileName().toString());
                if (round > latestRound) {
                    latestRound = round;
                    latestRoundPath = numberDirectory;
                }
            }
        }
        return latestRoundPath;
    }

    private static boolean isNumberDirectory(@NonNull final Path path) {
        return path.toFile().isDirectory()
                && NUMBER_PATTERN.matcher(path.getFileName().toString()).matches();
    }
}
