/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono;

import static com.hedera.node.app.service.mono.ServicesState.EMPTY_HASH;
import static com.hedera.node.app.service.mono.context.AppsManager.APPS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_TOTAL_TINY_BAR_FLOAT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_REWARD_HISTORY_NUM_STORED_PERIODS;
import static com.hedera.node.app.service.mono.context.properties.SemanticVersions.SEMANTIC_VERSIONS;
import static com.hedera.node.app.service.mono.context.properties.SerializableSemVers.forHapiAndHedera;
import static com.hedera.node.app.service.mono.state.migration.MapMigrationToDisk.INSERTIONS_PER_COPY;
import static com.hedera.test.utils.AddresBookUtils.createPretendBookFrom;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.platform.system.InitTrigger.RECONNECT;
import static com.swirlds.platform.system.InitTrigger.RESTART;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.cache.EntityMapWarmer;
import com.hedera.node.app.service.mono.context.MutableStateChildren;
import com.hedera.node.app.service.mono.context.init.ServicesInitFlow;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.context.properties.PropertyNames;
import com.hedera.node.app.service.mono.ledger.accounts.staking.StakeStartupHelper;
import com.hedera.node.app.service.mono.sigs.EventExpansion;
import com.hedera.node.app.service.mono.state.PlatformStateAccessor;
import com.hedera.node.app.service.mono.state.exports.ExportingRecoveredStateListener;
import com.hedera.node.app.service.mono.state.forensics.HashLogger;
import com.hedera.node.app.service.mono.state.initialization.SystemAccountsCreator;
import com.hedera.node.app.service.mono.state.initialization.SystemFilesManager;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactions;
import com.hedera.node.app.service.mono.state.merkle.MerkleSpecialFiles;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.migration.MapMigrationToDisk;
import com.hedera.node.app.service.mono.state.migration.RecordConsolidation;
import com.hedera.node.app.service.mono.state.migration.StakingInfoMapBuilder;
import com.hedera.node.app.service.mono.state.migration.StateChildIndices;
import com.hedera.node.app.service.mono.state.migration.StateVersions;
import com.hedera.node.app.service.mono.state.migration.StorageStrategy;
import com.hedera.node.app.service.mono.state.migration.ToDiskMigrations;
import com.hedera.node.app.service.mono.state.org.StateMetadata;
import com.hedera.node.app.service.mono.state.virtual.VirtualMapFactory;
import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.service.mono.txns.ProcessLogic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.SystemExits;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.ClassLoaderHelper;
import com.hedera.test.utils.CryptoConfigUtils;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.ResponsibleVMapUser;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.swirlds.base.state.MutabilityException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.crypto.engine.CryptoEngine;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.platform.NodeId;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.state.notifications.NewRecoveredStateListener;
import com.swirlds.virtualmap.VirtualMap;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class ServicesStateTest extends ResponsibleVMapUser {

    private final String statesDir = "src/test/resources/states/";
    private final SoftwareVersion justPriorVersion = forHapiAndHedera("0.29.1", "0.29.2");
    private final SoftwareVersion currentVersion = SEMANTIC_VERSIONS.deployedSoftwareVersion();
    private final SoftwareVersion futureVersion = forHapiAndHedera("1.0.0", "1.0.0");
    private final SoftwareVersion configVersion = forHapiAndHedera("0.32.0", "0.32.0");
    private final NodeId selfId = new NodeId(1L);
    private static final String bookMemo = "0.0.4";

    @Mock
    private StakeStartupHelper stakeStartupHelper;

    @Mock
    private HashLogger hashLogger;

    @Mock
    private Platform platform;

    @Mock
    private AddressBook addressBook;

    @Mock
    private Address address;

    @Mock
    private ServicesApp app;

    @Mock
    private MerkleSpecialFiles specialFiles;

    @Mock
    private MerkleNetworkContext networkContext;

    @Mock
    private Round round;

    @Mock
    private Event event;

    @Mock
    private EventExpansion eventExpansion;

    @Mock
    private PlatformState platformState;

    @Mock
    private StateMetadata metadata;

    @Mock
    private ProcessLogic logic;

    @Mock
    private FCHashMap<ByteString, EntityNum> aliases;

    @Mock
    private MutableStateChildren workingState;

    @Mock
    private PlatformStateAccessor platformStateAccessor;

    @Mock
    private ServicesInitFlow initFlow;

    @Mock
    private ServicesApp.Builder appBuilder;

    @Mock
    private MerkleMap<EntityNum, MerkleAccount> accounts;

    @Mock
    private VirtualMapFactory virtualMapFactory;

    @Mock
    private ExportingRecoveredStateListener recoveredStateListener;

    @Mock
    private NotificationEngine notificationEngine;

    @Mock
    private ServicesState.StakingInfoBuilder stakingInfoBuilder;

    @Mock
    private ServicesState.MapToDiskMigration mapToDiskMigration;

    @Mock
    private ServicesState.RecordConsolidator recordConsolidator;

    @Mock
    private Supplier<VirtualMapFactory> vmf;

    @Mock
    private BootstrapProperties bootstrapProperties;

    @Mock
    private SystemAccountsCreator accountsCreator;

    @Mock
    private SystemFilesManager systemFilesManager;

    @LoggingTarget
    private LogCaptor logCaptor;

    @LoggingSubject
    private ServicesState subject;

    @BeforeAll
    static void setUpAll() {
        // Use a different VirtualMap factory instance for every test to avoid VM folder name conflicts
        ServicesState.setVmFactory(VirtualMapFactory::new);
    }

    @BeforeEach
    void setUp() {
        SEMANTIC_VERSIONS
                .deployedSoftwareVersion()
                .setProto(SemanticVersion.newBuilder().setMinor(32).build());
        SEMANTIC_VERSIONS
                .deployedSoftwareVersion()
                .setServices(SemanticVersion.newBuilder().setMinor(32).build());
        subject = tracked(new ServicesState());
        setAllChildren();
    }

    @AfterEach
    void cleanup() {
        if (APPS.includes(selfId)) {
            APPS.clear(selfId);
        }
    }

    @AfterAll
    static void clearMocks() {
        Mockito.framework().clearInlineMocks();
    }

    @Test
    void doesNoMigrationsForLateEnoughVersion() {
        mockMigratorsOnly();
        subject.setMetadata(metadata);
        given(metadata.app()).willReturn(app);
        given(app.workingState()).willReturn(workingState);

        assertDoesNotThrow(() -> subject.migrateFrom(futureVersion));

        unmockMigrators();
    }

    @Test
    void onlyInitializedIfMetadataIsSet() {
        assertFalse(subject.isInitialized());
        subject.setMetadata(metadata);
        assertTrue(subject.isInitialized());
    }

    @Test
    void getsAliasesFromMetadata() {
        given(metadata.aliases()).willReturn(aliases);
        subject.setMetadata(metadata);
        assertSame(aliases, subject.aliases());
    }

    @Test
    void logsSummaryAsExpectedWithAppAvailable() {
        // setup:
        final var consTime = Instant.ofEpochSecond(1_234_567L);
        subject.setMetadata(metadata);

        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);

        given(metadata.app()).willReturn(app);
        given(app.hashLogger()).willReturn(hashLogger);
        given(app.platformStateAccessor()).willReturn(platformStateAccessor);
        given(networkContext.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
        given(networkContext.consensusTimeOfLastHandledTxn()).willReturn(consTime);
        given(networkContext.summarizedWith(platformStateAccessor)).willReturn("IMAGINE");

        // when:
        subject.logSummary();

        // then:
        verify(hashLogger).logHashesFor(subject);
        assertEquals("IMAGINE", logCaptor.infoLogs().get(0));
        assertEquals(consTime, subject.getTimeOfLastHandledTxn());
        assertEquals(StateVersions.CURRENT_VERSION, subject.getStateVersion());
    }

    @Test
    void logsSummaryAsExpectedWithNoAppAvailable() {
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);

        given(networkContext.summarized()).willReturn("IMAGINE");

        // when:
        subject.logSummary();

        // then:
        assertEquals("IMAGINE", logCaptor.infoLogs().get(0));
    }

    @Test
    void getsAccountIdAsExpected() {
        // setup:
        subject.setChild(StateChildIndices.LEGACY_ADDRESS_BOOK, addressBook);
        subject.setPlatform(platform);
        given(platform.getAddressBook()).willReturn(addressBook);

        given(addressBook.getAddress(selfId)).willReturn(address);
        given(address.getMemo()).willReturn("0.0.3");

        // when:
        final var parsedAccount = subject.getAccountFromNodeId(selfId);

        // then:
        assertEquals(IdUtils.asAccount("0.0.3"), parsedAccount);
    }

    @Test
    void onReleaseForwardsToMetadataIfNonNull() {
        // setup:
        subject.setMetadata(metadata);

        // when:
        subject.destroyNode();

        // then:
        verify(metadata).release();
    }

    @Test
    void preHandleUsesEventExpansion() {
        subject.setMetadata(metadata);
        given(metadata.app()).willReturn(app);
        given(app.eventExpansion()).willReturn(eventExpansion);

        subject.preHandle(event);

        verify(eventExpansion).expandAllSigs(event, subject);
    }

    @Test
    void handleThrowsIfImmutable() {
        tracked(subject.copy());

        assertThrows(MutabilityException.class, () -> subject.handleConsensusRound(round, platformState));
    }

    @Test
    void handlesRoundAsExpected() {
        subject.setMetadata(metadata);

        given(metadata.app()).willReturn(app);
        final var mapWarmer = mock(EntityMapWarmer.class);
        given(app.mapWarmer()).willReturn(mapWarmer);
        given(app.logic()).willReturn(logic);
        given(app.platformStateAccessor()).willReturn(platformStateAccessor);

        subject.handleConsensusRound(round, platformState);
        verify(mapWarmer).warmCache(round);
        verify(platformStateAccessor).setPlatformState(platformState);
        verify(logic).incorporateConsensus(round);
    }

    @Test
    void minimumVersionIsRelease031() {
        // expect:
        assertEquals(StateVersions.RELEASE_0310_VERSION, subject.getMinimumSupportedVersion());
    }

    @Test
    void minimumChildCountsAsExpected() {
        assertEquals(StateChildIndices.NUM_025X_CHILDREN, subject.getMinimumChildCount());
    }

    @Test
    void merkleMetaAsExpected() {
        // expect:
        assertEquals(0x8e300b0dfdafbb1aL, subject.getClassId());
        assertEquals(StateVersions.CURRENT_VERSION, subject.getVersion());
    }

    @Test
    void doesntThrowWhenPlatformStateIsNull() {
        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.platformStateAccessor()).willReturn(platformStateAccessor);

        given(platform.getSelfId()).willReturn(selfId);
        given(app.stakeStartupHelper()).willReturn(stakeStartupHelper);

        APPS.save(selfId, app);

        assertDoesNotThrow(() -> subject.init(platform, null, RESTART, currentVersion));
    }

    @Test
    void genesisInitCreatesChildren() {
        // setup:
        ServicesState.setAppBuilder(() -> appBuilder);

        given(addressBook.getSize()).willReturn(3);
        given(appBuilder.bootstrapProps(any())).willReturn(appBuilder);
        given(appBuilder.crypto(any())).willReturn(appBuilder);
        given(appBuilder.staticAccountMemo(bookMemo)).willReturn(appBuilder);
        given(appBuilder.consoleCreator(any())).willReturn(appBuilder);
        given(appBuilder.initialHash(EMPTY_HASH)).willReturn(appBuilder);
        given(appBuilder.platform(platform)).willReturn(appBuilder);
        given(appBuilder.selfId(new NodeId(1L))).willReturn(appBuilder);
        given(appBuilder.initTrigger(InitTrigger.GENESIS)).willReturn(appBuilder);
        given(appBuilder.build()).willReturn(app);
        // and:
        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.platformStateAccessor()).willReturn(platformStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(platform.getAddressBook()).willReturn(addressBook);
        given(app.sysAccountsCreator()).willReturn(accountsCreator);
        given(app.workingState()).willReturn(workingState);
        given(app.sysFilesManager()).willReturn(systemFilesManager);
        given(app.stakeStartupHelper()).willReturn(stakeStartupHelper);
        doAnswer(invocation -> {
                    var id = invocation.getArgument(0, Integer.class);
                    return new NodeId(id);
                })
                .when(addressBook)
                .getNodeId(anyInt());
        given(addressBook.getAddress(selfId)).willReturn(address);
        given(address.getMemo()).willReturn(bookMemo);

        // when:
        subject = tracked(new ServicesState());
        subject.init(platform, platformState, InitTrigger.GENESIS, null);

        // then:
        assertFalse(subject.isImmutable());
        // and:
        assertSame(addressBook, subject.addressBook());
        assertNotNull(subject.accounts());
        assertNotNull(subject.storage());
        assertNotNull(subject.topics());
        assertNotNull(subject.tokens());
        assertNotNull(subject.tokenAssociations());
        assertNotNull(subject.scheduleTxs());
        assertNotNull(subject.networkCtx());
        assertNotNull(subject.runningHashLeaf());
        assertNotNull(subject.contractStorage());
        assertNotNull(subject.stakingInfo());
        assertNull(subject.networkCtx().consensusTimeOfLastHandledTxn());
        assertEquals(1001L, subject.networkCtx().seqNo().current());
        assertNotNull(subject.specialFiles());
        // and:
        verify(platformStateAccessor).setPlatformState(platformState);
        verify(initFlow).runWith(eq(subject), any());
        verify(appBuilder).bootstrapProps(any());
        verify(appBuilder).initialHash(EMPTY_HASH);
        verify(appBuilder).platform(platform);
        verify(appBuilder).selfId(selfId);
        // and:
        assertTrue(APPS.includes(selfId));

        // cleanup:
        ServicesState.setAppBuilder(DaggerServicesApp::builder);
    }

    @Test
    void genesisInitRespectsSelectedOnDiskMapsAndConsolidatedRecords() {
        // setup:
        // it should correspond to the default value in test/resources/bootstrap.properties
        subject = tracked(new ServicesState(bootstrapProperties));
        given(bootstrapProperties.getBooleanProperty(PropertyNames.TOKENS_NFTS_USE_VIRTUAL_MERKLE))
                .willReturn(true);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.TOKENS_STORE_RELS_ON_DISK))
                .willReturn(false);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.ACCOUNTS_STORE_ON_DISK))
                .willReturn(false);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.RECORDS_USE_CONSOLIDATED_FCQ))
                .willReturn(true);
        ServicesState.setAppBuilder(() -> appBuilder);

        given(addressBook.getSize()).willReturn(3);
        given(addressBook.getAddress(selfId)).willReturn(address);
        given(address.getMemo()).willReturn(bookMemo);
        given(appBuilder.bootstrapProps(any())).willReturn(appBuilder);
        given(appBuilder.crypto(any())).willReturn(appBuilder);
        given(appBuilder.staticAccountMemo(bookMemo)).willReturn(appBuilder);
        given(appBuilder.consoleCreator(any())).willReturn(appBuilder);
        given(appBuilder.initialHash(EMPTY_HASH)).willReturn(appBuilder);
        given(appBuilder.platform(platform)).willReturn(appBuilder);
        given(appBuilder.initTrigger(InitTrigger.GENESIS)).willReturn(appBuilder);
        given(appBuilder.selfId(new NodeId(1L))).willReturn(appBuilder);
        given(appBuilder.build()).willReturn(app);
        // and:
        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.platformStateAccessor()).willReturn(platformStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(platform.getAddressBook()).willReturn(addressBook);
        given(app.sysAccountsCreator()).willReturn(accountsCreator);
        given(app.workingState()).willReturn(workingState);
        given(app.sysFilesManager()).willReturn(systemFilesManager);
        given(app.stakeStartupHelper()).willReturn(stakeStartupHelper);
        doAnswer(invocation -> {
                    var id = invocation.getArgument(0, Integer.class);
                    return new NodeId(id);
                })
                .when(addressBook)
                .getNodeId(anyInt());

        // when:
        subject.init(platform, platformState, InitTrigger.GENESIS, null);

        // then:
        assertTrue(subject.uniqueTokens().isVirtual());
        assertEquals(StorageStrategy.IN_SINGLE_FCQ, subject.payerRecords().storageStrategy());

        // cleanup:
        ServicesState.setAppBuilder(DaggerServicesApp::builder);
    }

    @Test
    void nonGenesisInitReusesContextIfPresent() {
        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.platformStateAccessor()).willReturn(platformStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(platform.getAddressBook()).willReturn(addressBook);
        given(app.maybeNewRecoveredStateListener()).willReturn(Optional.of(recoveredStateListener));
        given(platform.getNotificationEngine()).willReturn(notificationEngine);
        // and:
        APPS.save(selfId, app);

        // when:
        subject.init(platform, platformState, RECONNECT, currentVersion);

        // then:
        assertSame(addressBook, subject.addressBook());
        assertSame(app, subject.getMetadata().app());
        // and:
        verify(initFlow).runWith(eq(subject), any());
        verify(hashLogger).logHashesFor(subject);
        verify(notificationEngine).register(NewRecoveredStateListener.class, recoveredStateListener);
    }

    @Test
    void nonGenesisInitExitsIfStateVersionLaterThanCurrentSoftware() {
        final var mockExit = mock(SystemExits.class);

        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);

        given(platform.getSelfId()).willReturn(selfId);
        given(app.systemExits()).willReturn(mockExit);
        given(app.platformStateAccessor()).willReturn(platformStateAccessor);
        // and:
        APPS.save(selfId, app);

        // when:
        subject.init(platform, platformState, RESTART, futureVersion);

        verify(mockExit).fail(1);
    }

    @Test
    void nonGenesisInitDoesNotClearPreparedUpgradeIfSameVersion() {
        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.platformStateAccessor()).willReturn(platformStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);

        given(app.stakeStartupHelper()).willReturn(stakeStartupHelper);
        // and:
        APPS.save(selfId, app);

        // when:
        subject.init(platform, platformState, RESTART, currentVersion);

        verify(networkContext, never()).discardPreparedUpgradeMeta();
    }

    @Test
    void nonGenesisInitWithBuildDoesntRunMigrations() {
        SEMANTIC_VERSIONS
                .deployedSoftwareVersion()
                .setProto(SemanticVersion.newBuilder().setMinor(32).build());
        SEMANTIC_VERSIONS
                .deployedSoftwareVersion()
                .setServices(
                        SemanticVersion.newBuilder().setMinor(32).setBuild("1").build());
        subject = tracked(new ServicesState());
        setAllChildren();

        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.platformStateAccessor()).willReturn(platformStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);

        given(app.stakeStartupHelper()).willReturn(stakeStartupHelper);
        // and:
        APPS.save(selfId, app);

        // when:

        subject.init(platform, platformState, RESTART, configVersion);

        verify(networkContext, never()).discardPreparedUpgradeMeta();
    }

    @Test
    void nonGenesisInitClearsPreparedUpgradeIfDeployedIsLaterVersion() {
        mockMigrators();
        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);

        final var when = Instant.ofEpochSecond(1_234_567L, 890);
        given(app.workingState()).willReturn(workingState);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.platformStateAccessor()).willReturn(platformStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);

        given(app.stakeStartupHelper()).willReturn(stakeStartupHelper);
        // and:
        APPS.save(selfId, app);

        // when:
        subject.init(platform, platformState, RESTART, justPriorVersion);

        verify(networkContext).discardPreparedUpgradeMeta();
        unmockMigrators();
    }

    @Test
    void nonGenesisInitWithOldVersionMarksMigrationRecordsNotStreamed() {
        mockMigrators();
        subject.setMetadata(metadata);
        given(app.workingState()).willReturn(workingState);

        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);
        subject.setDeserializedStateVersion(StateVersions.RELEASE_0310_VERSION);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.platformStateAccessor()).willReturn(platformStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);

        given(app.stakeStartupHelper()).willReturn(stakeStartupHelper);
        // and:
        APPS.save(selfId, app);

        // when:
        subject.init(platform, platformState, RESTART, justPriorVersion);

        verify(networkContext).discardPreparedUpgradeMeta();
        verify(networkContext).markMigrationRecordsNotYetStreamed();

        unmockMigrators();
    }

    @Test
    void nonGenesisInitThrowsWithUnsupportedStateVersionUsed() {
        subject.setDeserializedStateVersion(StateVersions.RELEASE_0310_VERSION - 1);

        assertThrows(IllegalStateException.class, () -> subject.init(platform, platformState, RESTART, null));
    }

    @Test
    void nonGenesisInitDoesntClearPreparedUpgradeIfNotUpgrade() {
        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.platformStateAccessor()).willReturn(platformStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        // and:
        APPS.save(selfId, app);

        // when:
        subject.init(platform, platformState, RECONNECT, currentVersion);

        verify(networkContext, never()).discardPreparedUpgradeMeta();
    }

    @Test
    void nonGenesisInitConsolidatesRecords() {
        subject = tracked(new ServicesState(bootstrapProperties));
        given(bootstrapProperties.getBooleanProperty(PropertyNames.TOKENS_NFTS_USE_VIRTUAL_MERKLE))
                .willReturn(false);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.ACCOUNTS_STORE_ON_DISK))
                .willReturn(false);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.TOKENS_STORE_RELS_ON_DISK))
                .willReturn(false);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.RECORDS_USE_CONSOLIDATED_FCQ))
                .willReturn(true);
        ServicesState.setRecordConsolidator(recordConsolidator);

        final var vmap = mock(VirtualMap.class);
        final var mmap = mock(MerkleMap.class);
        mockAllMaps(mmap, vmap);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.STORAGE, vmap);
        subject.setChild(StateChildIndices.CONTRACT_STORAGE, vmap);
        subject.setChild(StateChildIndices.PAYER_RECORDS_OR_CONSOLIDATED_FCQ, mmap);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.platformStateAccessor()).willReturn(platformStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(platform.getAddressBook()).willReturn(addressBook);

        given(app.stakeStartupHelper()).willReturn(stakeStartupHelper);
        // and:
        APPS.save(selfId, app);

        // when:
        subject.init(platform, platformState, RESTART, currentVersion);
        verify(recordConsolidator).consolidateRecordsToSingleFcq(subject);

        ServicesState.setRecordConsolidator(RecordConsolidation::toSingleFcq);
    }

    @Test
    void nonGenesisInitHandlesNftMigration() {
        subject = tracked(new ServicesState(bootstrapProperties));
        given(bootstrapProperties.getBooleanProperty(PropertyNames.TOKENS_NFTS_USE_VIRTUAL_MERKLE))
                .willReturn(true);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.ACCOUNTS_STORE_ON_DISK))
                .willReturn(true);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.TOKENS_STORE_RELS_ON_DISK))
                .willReturn(false);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.RECORDS_USE_CONSOLIDATED_FCQ))
                .willReturn(false);
        ServicesState.setMapToDiskMigration(mapToDiskMigration);
        ServicesState.setVmFactory(vmf);
        given(vmf.get()).willReturn(virtualMapFactory);

        final var vmap = mock(VirtualMap.class);
        final var mmap = mock(MerkleMap.class);
        mockAllMaps(mmap, vmap);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.STORAGE, vmap);
        subject.setChild(StateChildIndices.CONTRACT_STORAGE, vmap);

        final var when = Instant.ofEpochSecond(1_234_567L, 890);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.platformStateAccessor()).willReturn(platformStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(platform.getAddressBook()).willReturn(addressBook);

        given(app.stakeStartupHelper()).willReturn(stakeStartupHelper);
        // and:
        APPS.save(selfId, app);

        // when:
        subject.init(platform, platformState, RESTART, currentVersion);
        verify(mapToDiskMigration)
                .migrateToDiskAsApropos(
                        INSERTIONS_PER_COPY,
                        false,
                        subject,
                        new ToDiskMigrations(true, false),
                        virtualMapFactory,
                        ServicesState.accountMigrator,
                        ServicesState.tokenRelMigrator);
        subject.setChild(StateChildIndices.PAYER_RECORDS_OR_CONSOLIDATED_FCQ, mmap);
        assertEquals(StorageStrategy.IN_PAYER_SCOPED_FCQ, subject.payerRecords().storageStrategy());

        ServicesState.setMapToDiskMigration(MapMigrationToDisk::migrateToDiskAsApropos);
        ServicesState.setVmFactory(VirtualMapFactory::new);
    }

    @Test
    void nonGenesisInitHandlesTokenRelMigrationToDisk() {
        subject = tracked(new ServicesState(bootstrapProperties));
        given(bootstrapProperties.getBooleanProperty(PropertyNames.TOKENS_NFTS_USE_VIRTUAL_MERKLE))
                .willReturn(false);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.ACCOUNTS_STORE_ON_DISK))
                .willReturn(false);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.TOKENS_STORE_RELS_ON_DISK))
                .willReturn(true);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.RECORDS_USE_CONSOLIDATED_FCQ))
                .willReturn(false);
        ServicesState.setMapToDiskMigration(mapToDiskMigration);
        ServicesState.setVmFactory(vmf);
        given(vmf.get()).willReturn(virtualMapFactory);

        final var vmap = mock(VirtualMap.class);
        mockAllMaps(mock(MerkleMap.class), vmap);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.STORAGE, vmap);
        subject.setChild(StateChildIndices.CONTRACT_STORAGE, vmap);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.platformStateAccessor()).willReturn(platformStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(platform.getAddressBook()).willReturn(addressBook);

        given(app.stakeStartupHelper()).willReturn(stakeStartupHelper);
        // and:
        APPS.save(selfId, app);

        // when:
        subject.init(platform, platformState, RESTART, currentVersion);
        verify(mapToDiskMigration)
                .migrateToDiskAsApropos(
                        INSERTIONS_PER_COPY,
                        false,
                        subject,
                        new ToDiskMigrations(false, true),
                        virtualMapFactory,
                        ServicesState.accountMigrator,
                        ServicesState.tokenRelMigrator);

        ServicesState.setMapToDiskMigration(MapMigrationToDisk::migrateToDiskAsApropos);
        ServicesState.setVmFactory(VirtualMapFactory::new);
    }

    @Test
    void copySetsMutabilityAsExpected() {
        // when:
        final var copy = tracked(subject.copy());

        // then:
        assertTrue(subject.isImmutable());
        assertFalse(copy.isImmutable());
    }

    @Test
    void copyUpdateCtxWithNonNullMeta() {
        // setup:
        subject.setMetadata(metadata);

        given(metadata.app()).willReturn(app);
        given(app.workingState()).willReturn(workingState);

        // when:
        final var copy = tracked(subject.copy());

        // then:
        verify(workingState).updateFrom(copy);
    }

    @Test
    void copiesNonNullChildren() {
        subject.setChild(StateChildIndices.LEGACY_ADDRESS_BOOK, addressBook);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        // and:
        subject.setMetadata(metadata);
        subject.setDeserializedStateVersion(10);
        subject.setPlatform(platform);
        given(platform.getAddressBook()).willReturn(addressBook);

        given(addressBook.copy()).willReturn(addressBook);
        given(networkContext.copy()).willReturn(networkContext);
        given(specialFiles.copy()).willReturn(specialFiles);
        given(metadata.copy()).willReturn(metadata);
        given(metadata.app()).willReturn(app);
        given(app.workingState()).willReturn(workingState);

        // when:
        final var copy = tracked(subject.copy());

        // then:
        assertEquals(10, copy.getDeserializedStateVersion());
        assertSame(metadata, copy.getMetadata());
        verify(metadata).copy();
        // and:
        assertSame(addressBook, copy.addressBook());
        assertSame(networkContext, copy.networkCtx());
        assertSame(specialFiles, copy.specialFiles());
    }

    @Test
    void testGenesisState() {
        ClassLoaderHelper.loadClassPathDependencies();
        final var servicesState = tracked(new ServicesState());
        final var platform = createMockPlatformWithCrypto();
        final var addressBook = createPretendBookFrom(platform, true, true);
        given(platform.getAddressBook()).willReturn(addressBook);
        final var recordsRunningHashLeaf = new RecordsRunningHashLeaf();
        recordsRunningHashLeaf.setRunningHash(new RunningHash(EMPTY_HASH));
        servicesState.setChild(StateChildIndices.RECORD_STREAM_RUNNING_HASH, recordsRunningHashLeaf);
        final var app = createApp(platform);

        APPS.save(platform.getSelfId(), app);
        assertDoesNotThrow(() -> servicesState.init(platform, new PlatformState(), InitTrigger.GENESIS, null));
    }

    @Test
    void testUniqueTokensWhenVirtual() {
        final var vmap = new VirtualMap<>();
        subject.setChild(StateChildIndices.UNIQUE_TOKENS, vmap);
        assertTrue(subject.uniqueTokens().isVirtual());
    }

    @Test
    void testUniqueTokensWhenMerkleMap() {
        final var mmap = new MerkleMap<>();
        subject.setChild(StateChildIndices.UNIQUE_TOKENS, mmap);
        assertFalse(subject.uniqueTokens().isVirtual());
        assertSame(mmap, subject.uniqueTokens().merkleMap());
    }

    @Test
    void updatesAddressBookWithZeroWeightOnGenesisStart() {
        final var node0 = new NodeId(0);
        final var node1 = new NodeId(1);
        given(platform.getSelfId()).willReturn(node0);

        final var pretendAddressBook = createPretendBookFrom(platform, true, false);

        final MerkleMap<EntityNum, MerkleStakingInfo> stakingMap = subject.getChild(StateChildIndices.STAKING_INFO);
        assertEquals(1, stakingMap.size());
        assertEquals(0, stakingMap.get(EntityNum.fromLong(0L)).getWeight());

        assertEquals(10L, pretendAddressBook.getAddress(node0).getWeight());
        assertEquals(10L, pretendAddressBook.getAddress(node1).getWeight());

        subject.updateWeight(pretendAddressBook, platform.getContext());

        // if staking info map has node with 0 weight and a new node is added,
        // both gets weight of 0
        assertEquals(0L, pretendAddressBook.getAddress(node0).getWeight());
        assertEquals(0L, pretendAddressBook.getAddress(node1).getWeight());
    }

    @Test
    void updatesAddressBookWithZeroWeightForNewNodes() {
        final var node0 = new NodeId(0);
        final var node1 = new NodeId(1);

        given(platform.getSelfId()).willReturn(node0);

        final var pretendAddressBook = createPretendBookFrom(platform, true, false);
        final MerkleMap<EntityNum, MerkleStakingInfo> stakingMap = subject.getChild(StateChildIndices.STAKING_INFO);
        assertEquals(1, stakingMap.size());
        assertEquals(0, stakingMap.get(EntityNum.fromLong(0L)).getWeight());

        stakingMap.forEach((k, v) -> {
            v.setStake(1000L);
            v.setWeight(500);
        });
        assertEquals(1000L, stakingMap.get(EntityNum.fromLong(0L)).getStake());
        subject.setChild(StateChildIndices.STAKING_INFO, stakingMap);

        assertEquals(10L, pretendAddressBook.getAddress(node0).getWeight());
        assertEquals(10L, pretendAddressBook.getAddress(node1).getWeight());

        subject.updateWeight(pretendAddressBook, platform.getContext());

        // only one node in state and new node added in config.txt gets weight of 0
        assertEquals(500, pretendAddressBook.getAddress(node0).getWeight());
        assertEquals(0L, pretendAddressBook.getAddress(node1).getWeight());
    }

    @Test
    void updatesAddressBookWithNonZeroWeightsOnGenesisStartIfStakesExist() {
        final var node0 = new NodeId(0);
        final var node1 = new NodeId(1);
        given(platform.getSelfId()).willReturn(node0);
        final var pretendAddressBook = createPretendBookFrom(platform, true, false);

        final MerkleMap<EntityNum, MerkleStakingInfo> stakingMap = subject.getChild(StateChildIndices.STAKING_INFO);
        assertEquals(1, stakingMap.size());
        assertEquals(0, stakingMap.get(EntityNum.fromLong(0L)).getWeight());

        stakingMap.put(EntityNum.fromLong(1L), new MerkleStakingInfo());
        stakingMap.forEach((k, v) -> {
            v.setStake(1000L);
            v.setWeight(500);
        });
        assertEquals(1000L, stakingMap.get(EntityNum.fromLong(0L)).getStake());
        subject.setChild(StateChildIndices.STAKING_INFO, stakingMap);

        assertEquals(10L, pretendAddressBook.getAddress(node0).getWeight());
        assertEquals(10L, pretendAddressBook.getAddress(node1).getWeight());

        subject.updateWeight(pretendAddressBook, platform.getContext());

        // both nodes in staking info gets weight as in state
        assertEquals(500, pretendAddressBook.getAddress(node0).getWeight());
        assertEquals(500L, pretendAddressBook.getAddress(node1).getWeight());
    }

    private static ServicesApp createApp(final Platform platform) {
        return DaggerServicesApp.builder()
                .initTrigger(InitTrigger.GENESIS)
                .initialHash(new Hash())
                .platform(platform)
                .crypto(CryptographyHolder.get())
                .consoleCreator((ignore, winNum, visible) -> null)
                .selfId(platform.getSelfId())
                .staticAccountMemo("memo")
                .bootstrapProps(new BootstrapProperties())
                .build();
    }

    private Platform createMockPlatformWithCrypto() {
        final var platform = mock(Platform.class);
        final var platformContext = mock(PlatformContext.class);
        when(platform.getSelfId()).thenReturn(new NodeId(0));
        when(platformContext.getCryptography())
                .thenReturn(new CryptoEngine(getStaticThreadManager(), CryptoConfigUtils.MINIMAL_CRYPTO_CONFIG));
        assertNotNull(platformContext.getCryptography());
        return platform;
    }

    private void mockAllMaps(final MerkleMap<?, ?> mockMm, final VirtualMap<?, ?> mockVm) {
        subject.setChild(StateChildIndices.ACCOUNTS, mockMm);
        subject.setChild(StateChildIndices.TOKEN_ASSOCIATIONS, mockMm);
        subject.setChild(StateChildIndices.TOKENS, mockMm);
        subject.setChild(StateChildIndices.UNIQUE_TOKENS, mockMm);
        subject.setChild(StateChildIndices.STORAGE, mockVm);
        subject.setChild(StateChildIndices.TOPICS, mockMm);
        subject.setChild(StateChildIndices.SCHEDULE_TXS, mock(MerkleScheduledTransactions.class));
        subject.setChild(StateChildIndices.STAKING_INFO, mockMm);
    }

    private void setAllChildren() {
        given(addressBook.getSize()).willReturn(1);
        given(addressBook.getNodeId(0)).willReturn(new NodeId(0L));
        given(bootstrapProperties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT)).willReturn(3_000_000_000L);
        given(bootstrapProperties.getIntProperty(STAKING_REWARD_HISTORY_NUM_STORED_PERIODS))
                .willReturn(2);
        final File databaseFolder = new File("database");
        try {
            if (!databaseFolder.exists()) {
                databaseFolder.mkdir();
            }
            FileUtils.cleanDirectory(new File("database"));
        } catch (final IllegalArgumentException | IOException e) {
            System.err.println("Exception thrown while cleaning up directory");
            e.printStackTrace();
        }
        subject.createGenesisChildren(addressBook, 0, bootstrapProperties);
    }

    private void mockMigrators() {
        mockMigratorsOnly();
    }

    private void mockMigratorsOnly() {
        ServicesState.setMapToDiskMigration(mapToDiskMigration);
        ServicesState.setStakingInfoBuilder(stakingInfoBuilder);
        ServicesState.setMapToDiskMigration(mapToDiskMigration);
        ServicesState.setVmFactory(vmf);
    }

    private void unmockMigrators() {
        ServicesState.setMapToDiskMigration(MapMigrationToDisk::migrateToDiskAsApropos);
        ServicesState.setStakingInfoBuilder(StakingInfoMapBuilder::buildStakingInfoMap);
        ServicesState.setVmFactory(VirtualMapFactory::new);
    }

    /**
     * Recursively copies a directory from {@code sourceDir} to targetDir, transforming both path
     * segments and file names with the given function.
     *
     * @param sourceDir the source directory
     * @param targetDir the target directory
     * @param transform the function to apply to each path segment and file name
     * @throws IOException if an I/O error occurs
     */
    private void cpWithDirTransform(
            final String sourceDir, final String targetDir, final UnaryOperator<String> transform) throws IOException {
        // First ensure all the (transformed) subdirectories exist in the target location
        final var basePath = Paths.get(sourceDir);
        Files.walk(basePath).filter(Files::isDirectory).forEach(f -> {
            try {
                final var relativePath = basePath.relativize(f);
                final var tmpDir = Paths.get(targetDir, transform.apply(relativePath.toString()));
                if (!tmpDir.toFile().exists()) {
                    Files.createDirectories(tmpDir);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        // Next copy all the files, transforming the path segments and file names
        Files.walk(basePath).filter(Files::isRegularFile).forEach(f -> {
            final var relativePath = basePath.relativize(f);
            final var transformedPath = transform.apply(relativePath.toString());
            final var relocatedPath = Paths.get(targetDir, transformedPath);
            if (!new File(relocatedPath.toString()).exists()) {
                try {
                    Files.copy(f.toAbsolutePath(), relocatedPath);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }

    private static String unabbreviate(final String path) {
        return replaceAllFrom(path, ABBREV_LOOKUP);
    }

    private static String replaceAllFrom(final String path, final Map<String, String> lookup) {
        String replacedPath = path;
        for (final var entry : lookup.entrySet()) {
            replacedPath = replacedPath.replaceAll(entry.getKey(), entry.getValue());
        }
        return replacedPath;
    }

    private static final Map<String, String> ABBREV_LOOKUP = Map.of(
            "cln", ":",
            "Oct20", "2022-10-20",
            "iHs", "internalHashes",
            "fS", "fileStore",
            "sCIKVS", "smartContractIterableKvStore",
            "iHSD", "internalHashStoreDisk",
            "oK2P", "objectKeyToPath",
            "p2HKV", "pathToHashKeyValue");
}
