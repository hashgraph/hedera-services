/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

// File: hedera-node/hedera-app/src/main/java/com/hedera/node/app/StateInitializer.java

package com.hedera.node.app;

import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY;
import static com.hedera.node.config.types.StreamMode.BLOCKS;
import static com.swirlds.platform.state.service.PlatformStateService.PLATFORM_STATE_SERVICE;
import static com.swirlds.platform.system.InitTrigger.GENESIS;
import static com.swirlds.platform.system.InitTrigger.RECONNECT;
import static com.swirlds.platform.system.address.AddressBookUtils.createRoster;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.StateChanges.Builder;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.node.app.blocks.BlockStreamService;
import com.hedera.node.app.blocks.impl.BoundaryStateChangeListener;
import com.hedera.node.app.blocks.impl.KVStateChangeListener;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.info.GenesisNetworkInfo;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.roster.RosterService;
import com.hedera.node.app.service.addressbook.impl.AddressBookServiceImpl;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.NetworkServiceImpl;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.util.impl.UtilServiceImpl;
import com.hedera.node.app.services.AppContextImpl;
import com.hedera.node.app.services.OrderedServiceMigrator;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.services.ServicesRegistry.Factory;
import com.hedera.node.app.services.ServicesRegistryImpl;
import com.hedera.node.app.signature.AppSignatureVerifier;
import com.hedera.node.app.signature.impl.SignatureExpanderImpl;
import com.hedera.node.app.signature.impl.SignatureVerifierImpl;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.hedera.node.app.tss.PlaceholderTssLibrary;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.app.tss.TssBaseServiceImpl;
import com.hedera.node.app.version.HederaSoftwareVersion;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.VersionConfig;
import com.hedera.node.config.types.StreamMode;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.platform.state.service.PlatformStateService;
import com.swirlds.platform.state.service.ReadablePlatformStateStore;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.state.State;
import com.swirlds.state.spi.WritableSingletonStateBase;
import com.swirlds.state.spi.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StateInitializer {
    private static final Logger logger = LogManager.getLogger(StateInitializer.class);

    public static List<Builder> migrateAndInitializeServices(
            @NonNull final State state,
            @Nullable final ServicesSoftwareVersion deserializedVersion,
            @NonNull final InitTrigger trigger,
            @NonNull final Metrics metrics,
            @NonNull final Hedera hedera) {
        // Implementation of migrateAndInitializeServices method
        if (trigger != GENESIS) {
            requireNonNull(deserializedVersion, "Deserialized version cannot be null for trigger " + trigger);
        }

        final InstantSource instantSource = InstantSource.system();
        ConfigProviderImpl configProvider;
        ServiceMigrator serviceMigrator = new OrderedServiceMigrator();
        final BoundaryStateChangeListener boundaryStateChangeListener = new BoundaryStateChangeListener();
        final KVStateChangeListener kvStateChangeListener = new KVStateChangeListener();
        Factory registryFactory = ServicesRegistryImpl::new;
        BootstrapConfigProviderImpl bootstrapConfigProvider = new BootstrapConfigProviderImpl();

        // Until all service schemas are migrated, MerkleStateRoot will not be able to implement
        // the States API, even if it already has all its children in the Merkle tree, as it will lack
        // state definitions for those children. (And note services may even require migrations for
        // those children to be usable with the current version of the software.)
        ServicesRegistry servicesRegistry =
                registryFactory.create(ConstructableRegistry.getInstance(), bootstrapConfigProvider.configuration());
        final var bootstrapConfig = bootstrapConfigProvider.getConfiguration();
        StreamMode streamMode =
                bootstrapConfig.getConfigData(BlockStreamConfig.class).streamMode();

        configProvider = new ConfigProviderImpl(trigger == GENESIS, metrics);
        final ServicesSoftwareVersion version = getNodeStartupVersion(bootstrapConfig);

        final var previousVersion = deserializedVersion == null ? null : deserializedVersion.getPbjSemanticVersion();
        final var isUpgrade = version.compareTo(deserializedVersion) > 0;
        logger.info(
                "{} from Services version {} @ current {} with trigger {}",
                () -> isUpgrade ? "Upgrading" : (previousVersion == null ? "Starting" : "Restarting"),
                () -> HapiUtils.toString(Optional.ofNullable(deserializedVersion)
                        .map(ServicesSoftwareVersion::getPbjSemanticVersion)
                        .orElse(null)),
                () -> HapiUtils.toString(version.getPbjSemanticVersion()),
                () -> trigger);
        // This is set only when the trigger is genesis. Because, only in those cases
        // the migration code is using the network info values.
        NetworkInfo genesisNetworkInfo = null;
        if (trigger == GENESIS) {
            final var config = configProvider.getConfiguration();
            final var ledgerConfig = config.getConfigData(LedgerConfig.class);
            final var readableStore =
                    new ReadablePlatformStateStore(state.getReadableStates(PlatformStateService.NAME));
            final var genesisRoster = createRoster(requireNonNull(readableStore.getAddressBook()));

            genesisNetworkInfo = new GenesisNetworkInfo(genesisRoster, ledgerConfig.id());
        }
        final List<Builder> migrationStateChanges = new ArrayList<>();
        if (isNotEmbedded(instantSource)) {
            if (!(state instanceof MerkleStateRoot merkleStateRoot)) {
                throw new IllegalStateException("State must be a MerkleStateRoot");
            }
            migrationStateChanges.addAll(merkleStateRoot.platformStateInitChangesOrThrow());
        }

        AppContext appContext = new AppContextImpl(
                instantSource,
                new AppSignatureVerifier(
                        bootstrapConfig.getConfigData(HederaConfig.class),
                        new SignatureExpanderImpl(),
                        new SignatureVerifierImpl(CryptographyHolder.get())),
                hedera);

        BlockStreamService blockStreamService = new BlockStreamService();
        registerServiceRuntimeConstructables(servicesRegistry, blockStreamService, appContext);

        // (FUTURE) In principle, the FileService could actually change the active configuration during a
        // migration, which implies we should be passing the config provider and not a static configuration
        // here; but this is a currently unneeded affordance
        blockStreamService.resetMigratedLastBlockHash();
        final var migrationChanges = serviceMigrator.doMigrations(
                state,
                servicesRegistry,
                deserializedVersion,
                version,
                configProvider.getConfiguration(),
                genesisNetworkInfo,
                metrics);
        migrationStateChanges.addAll(migrationChanges);
        kvStateChangeListener.reset();
        boundaryStateChangeListener.reset();
        // If still using BlockRecordManager state, then for specifically a non-genesis upgrade,
        // set in state that post-upgrade work is pending
        if (streamMode != BLOCKS && isUpgrade && trigger != RECONNECT && trigger != GENESIS) {
            unmarkMigrationRecordsStreamed(state);
            migrationStateChanges.add(
                    StateChanges.newBuilder().stateChanges(boundaryStateChangeListener.allStateChanges()));
            boundaryStateChangeListener.reset();
        }
        logger.info("Migration complete");
        return migrationStateChanges;
    }


    // Register all service schema RuntimeConstructable factories before platform init
    private static void registerServiceRuntimeConstructables(
            ServicesRegistry servicesRegistry, BlockStreamService blockStreamService, AppContext appContext) {

        Function<AppContext, TssBaseService> tssBaseServiceFactory = (appCtx -> new TssBaseServiceImpl(
                appCtx,
                ForkJoinPool.commonPool(),
                ForkJoinPool.commonPool(),
                new PlaceholderTssLibrary(),
                ForkJoinPool.commonPool()));
        TssBaseService tssBaseService = tssBaseServiceFactory.apply(appContext);
        ContractService contractServiceImpl = new ContractServiceImpl(appContext);

        Set.of(
                        new EntityIdService(),
                        new ConsensusServiceImpl(),
                        contractServiceImpl,
                        new FileServiceImpl(),
                        tssBaseService,
                        new FreezeServiceImpl(),
                        new ScheduleServiceImpl(),
                        new TokenServiceImpl(),
                        new UtilServiceImpl(),
                        new RecordCacheService(),
                        new BlockRecordService(),
                        blockStreamService,
                        new FeeService(),
                        new CongestionThrottleService(),
                        new NetworkServiceImpl(),
                        new AddressBookServiceImpl(),
                        new RosterService(),
                        PLATFORM_STATE_SERVICE)
                .forEach(servicesRegistry::register);
    }

    private static boolean isNotEmbedded(InstantSource instantSource) {
        return instantSource == InstantSource.system();
    }


    static ServicesSoftwareVersion getServicesSoftwareVersion(SoftwareVersion previousSoftwareVersion) {
        ServicesSoftwareVersion deserializedVersion = null;
        if (previousSoftwareVersion instanceof ServicesSoftwareVersion servicesSoftwareVersion) {
            deserializedVersion = servicesSoftwareVersion;
        } else if (previousSoftwareVersion instanceof HederaSoftwareVersion hederaSoftwareVersion) {
            deserializedVersion = new ServicesSoftwareVersion(
                    hederaSoftwareVersion.servicesVersion(), hederaSoftwareVersion.configVersion());
        } else {
            if (previousSoftwareVersion != null) {
                logger.fatal("Deserialized state not created with Hedera software");
                throw new IllegalStateException("Deserialized state not created with Hedera software");
            }
        }
        return deserializedVersion;
    }

    private static ServicesSoftwareVersion getNodeStartupVersion(@NonNull final Configuration config) {
        final var versionConfig = config.getConfigData(VersionConfig.class);
        return new ServicesSoftwareVersion(
                versionConfig.servicesVersion(),
                config.getConfigData(HederaConfig.class).configVersion());
    }

    private static void unmarkMigrationRecordsStreamed(@NonNull final State state) {
        final var blockServiceState = state.getWritableStates(BlockRecordService.NAME);
        final var blockInfoState = blockServiceState.<BlockInfo>getSingleton(BLOCK_INFO_STATE_KEY);
        final var currentBlockInfo = requireNonNull(blockInfoState.get());
        final var nextBlockInfo =
                currentBlockInfo.copyBuilder().migrationRecordsStreamed(false).build();
        blockInfoState.put(nextBlockInfo);
        logger.info("Unmarked post-upgrade work as done");
        ((WritableSingletonStateBase<BlockInfo>) blockInfoState).commit();
    }


}