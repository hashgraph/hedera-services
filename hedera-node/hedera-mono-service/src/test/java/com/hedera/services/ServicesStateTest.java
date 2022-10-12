/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services;

import static com.hedera.services.ServicesState.EMPTY_HASH;
import static com.hedera.services.context.AppsManager.APPS;
import static com.hedera.services.context.properties.PropertyNames.LEDGER_TOTAL_TINY_BAR_FLOAT;
import static com.hedera.services.context.properties.PropertyNames.STAKING_REWARD_HISTORY_NUM_STORED_PERIODS;
import static com.hedera.services.context.properties.SemanticVersions.SEMANTIC_VERSIONS;
import static com.hedera.services.context.properties.SerializableSemVers.forHapiAndHedera;
import static com.hedera.services.state.migration.MapMigrationToDisk.INSERTIONS_PER_COPY;
import static com.swirlds.common.system.InitTrigger.RECONNECT;
import static com.swirlds.common.system.InitTrigger.RESTART;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.init.ServicesInitFlow;
import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.context.properties.PropertyNames;
import com.hedera.services.sigs.EventExpansion;
import com.hedera.services.state.DualStateAccessor;
import com.hedera.services.state.forensics.HashLogger;
import com.hedera.services.state.initialization.SystemAccountsCreator;
import com.hedera.services.state.initialization.SystemFilesManager;
import com.hedera.services.state.initialization.TreasuryCloner;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleScheduledTransactions;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.migration.*;
import com.hedera.services.state.org.StateMetadata;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.state.virtual.VirtualMapFactory;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.txns.ProcessLogic;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.SystemExits;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.ClassLoaderHelper;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.crypto.SerializablePublicKey;
import com.swirlds.common.crypto.engine.CryptoEngine;
import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.Event;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.state.DualStateImpl;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileReader;
import com.swirlds.virtualmap.VirtualMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class ServicesStateTest {
    private final String signedStateDir = "src/test/resources/signedState/";
    private final SoftwareVersion some025xVersion = forHapiAndHedera("0.25.0", "0.25.2");
    private final SoftwareVersion justPriorVersion = forHapiAndHedera("0.28.1", "0.27.9");
    private final SoftwareVersion currentVersion = SEMANTIC_VERSIONS.deployedSoftwareVersion();
    private final SoftwareVersion futureVersion = forHapiAndHedera("1.0.0", "1.0.0");
    private final Instant consensusTime = Instant.ofEpochSecond(2_345_678L, 9);
    private final NodeId selfId = new NodeId(false, 1L);
    private static final String bookMemo = "0.0.4";

    @Mock private HashLogger hashLogger;
    @Mock private Platform platform;
    @Mock private AddressBook addressBook;
    @Mock private Address address;
    @Mock private ServicesApp app;
    @Mock private MerkleSpecialFiles specialFiles;
    @Mock private MerkleNetworkContext networkContext;
    @Mock private Round round;
    @Mock private Event event;
    @Mock private EventExpansion eventExpansion;
    @Mock private SwirldDualState dualState;
    @Mock private StateMetadata metadata;
    @Mock private ProcessLogic logic;
    @Mock private FCHashMap<ByteString, EntityNum> aliases;
    @Mock private MutableStateChildren workingState;
    @Mock private DualStateAccessor dualStateAccessor;
    @Mock private ServicesInitFlow initFlow;
    @Mock private TreasuryCloner treasuryCloner;
    @Mock private ServicesApp.Builder appBuilder;
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations;
    @Mock private VirtualMapFactory virtualMapFactory;
    @Mock private VirtualMap<ContractKey, IterableContractValue> iterableStorage;
    @Mock private ServicesState.NftLinksRepair nftLinksRepair;
    @Mock private ServicesState.StakingInfoBuilder stakingInfoBuilder;
    @Mock private ServicesState.IterableStorageMigrator iterableStorageMigrator;
    @Mock private ServicesState.ContractAutoRenewalMigrator autoRenewalMigrator;
    @Mock private ServicesState.MapToDiskMigration mapToDiskMigration;
    @Mock private Function<VirtualMapFactory.JasperDbBuilderFactory, VirtualMapFactory> vmf;
    @Mock private Consumer<ServicesState> scheduledTxnsMigrator;
    @Mock private BootstrapProperties bootstrapProperties;
    @Mock private SystemAccountsCreator accountsCreator;
    @Mock private SystemFilesManager systemFilesManager;
    @Mock private MigrationRecordsManager migrationRecordsManager;

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private ServicesState subject;

    @BeforeEach
    void setUp() {
        SEMANTIC_VERSIONS
                .deployedSoftwareVersion()
                .setProto(SemanticVersion.newBuilder().setMinor(32).build());
        SEMANTIC_VERSIONS
                .deployedSoftwareVersion()
                .setServices(SemanticVersion.newBuilder().setMinor(32).build());
        subject = new ServicesState();
        setAllChildren();
    }

    @AfterEach
    void cleanup() {
        if (APPS.includes(selfId.getId())) {
            APPS.clear(selfId.getId());
        }
    }

    @Test
    void doesNoMigrationsForLateEnoughVersion() {
        mockMigratorsOnly();
        subject.setMetadata(metadata);
        given(metadata.app()).willReturn(app);
        given(app.workingState()).willReturn(workingState);

        subject.migrateFrom(futureVersion);

        verifyNoInteractions(autoRenewalMigrator, iterableStorageMigrator);

        unmockMigrators();
    }

    @Test
    void doesAllMigrationsFromRelease025Version() {
        mockMigrators();
        final var inOrder =
                inOrder(
                        networkContext,
                        autoRenewalMigrator,
                        scheduledTxnsMigrator,
                        iterableStorageMigrator,
                        vmf,
                        workingState);

        subject.setChild(StateChildIndices.ACCOUNTS, accounts);
        subject.setChild(StateChildIndices.TOKEN_ASSOCIATIONS, tokenAssociations);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.SCHEDULE_TXS, mock(MerkleMap.class));
        subject.setMetadata(metadata);

        given(metadata.app()).willReturn(app);
        given(app.workingState()).willReturn(workingState);
        given(virtualMapFactory.newVirtualizedIterableStorage()).willReturn(iterableStorage);
        given(vmf.apply(any())).willReturn(virtualMapFactory);
        doAnswer(
                        inv -> {
                            subject.setChild(
                                    StateChildIndices.SCHEDULE_TXS,
                                    new MerkleScheduledTransactions(1));
                            return null;
                        })
                .when(scheduledTxnsMigrator)
                .accept(subject);

        subject.migrateFrom(some025xVersion);

        inOrder.verify(iterableStorageMigrator)
                .makeStorageIterable(eq(subject), any(), any(), eq(iterableStorage));
        inOrder.verify(scheduledTxnsMigrator).accept(subject);
        inOrder.verify(autoRenewalMigrator, never()).grantFreeAutoRenew(subject, consensusTime);
        inOrder.verify(workingState).updatePrimitiveChildrenFrom(subject);
        inOrder.verify(networkContext).markPostUpgradeScanStatus();

        unmockMigrators();
    }

    @Test
    void doesScheduledTxnMigrationRegardlessOfVersion() {
        mockMigratorsOnly();

        subject.setMetadata(metadata);
        given(metadata.app()).willReturn(app);
        given(app.workingState()).willReturn(workingState);
        subject.setChild(StateChildIndices.SCHEDULE_TXS, mock(MerkleMap.class));
        doAnswer(
                        inv -> {
                            subject.setChild(
                                    StateChildIndices.SCHEDULE_TXS,
                                    new MerkleScheduledTransactions(1));
                            return null;
                        })
                .when(scheduledTxnsMigrator)
                .accept(subject);

        subject.migrateFrom(futureVersion);

        verify(iterableStorageMigrator, never()).makeStorageIterable(any(), any(), any(), any());
        verify(autoRenewalMigrator, never()).grantFreeAutoRenew(any(), any());

        verify(scheduledTxnsMigrator).accept(subject);

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
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(networkContext.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
        given(networkContext.consensusTimeOfLastHandledTxn()).willReturn(consTime);
        given(networkContext.summarizedWith(dualStateAccessor)).willReturn("IMAGINE");

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
        subject.setChild(StateChildIndices.ADDRESS_BOOK, addressBook);

        given(addressBook.getAddress(selfId.getId())).willReturn(address);
        given(address.getMemo()).willReturn("0.0.3");

        // when:
        final var parsedAccount = subject.getAccountFromNodeId(selfId);

        // then:
        assertEquals(IdUtils.asAccount("0.0.3"), parsedAccount);
    }

    @Test
    void onReleaseAndArchiveNoopIfMetadataNull() {
        setAllMmsTo(mock(MerkleMap.class));
        Assertions.assertDoesNotThrow(subject::archive);
        Assertions.assertDoesNotThrow(subject::destroyNode);
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
    void archiveForwardsToMetadataAndMerkleMaps() {
        final MerkleMap<?, ?> mockMm = mock(MerkleMap.class);

        subject.setMetadata(metadata);
        setAllMmsTo(mockMm);

        // when:
        subject.archive();

        // then:
        verify(metadata).release();
        verify(mockMm, times(6)).archive();
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
        subject.copy();

        assertThrows(
                MutabilityException.class, () -> subject.handleConsensusRound(round, dualState));
    }

    @Test
    void handlesRoundAsExpected() {
        subject.setMetadata(metadata);

        given(metadata.app()).willReturn(app);
        given(app.logic()).willReturn(logic);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);

        subject.handleConsensusRound(round, dualState);
        verify(dualStateAccessor).setDualState(dualState);
        verify(logic).incorporateConsensus(round);
    }

    @Test
    void addressBookCopyWorks() {
        given(addressBook.copy()).willReturn(addressBook);
        // and:
        subject.setChild(StateChildIndices.ADDRESS_BOOK, addressBook);

        // when:
        final var bookCopy = subject.getAddressBookCopy();

        // then:
        assertSame(addressBook, bookCopy);
        verify(addressBook).copy();
    }

    @Test
    void minimumVersionIsRelease025() {
        // expect:
        assertEquals(StateVersions.RELEASE_025X_VERSION, subject.getMinimumSupportedVersion());
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
    void doesntThrowWhenDualStateIsNull() {
        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(app.sysFilesManager()).willReturn(systemFilesManager);
        given(platform.getSelfId()).willReturn(selfId);

        APPS.save(selfId.getId(), app);

        assertDoesNotThrow(
                () -> subject.init(platform, addressBook, null, RESTART, currentVersion));
    }

    @Test
    void genesisInitCreatesChildren() {
        // setup:
        ServicesState.setAppBuilder(() -> appBuilder);

        given(addressBook.getSize()).willReturn(3);
        given(addressBook.getAddress(anyLong())).willReturn(address);
        given(address.getMemo()).willReturn(bookMemo);
        given(appBuilder.bootstrapProps(any())).willReturn(appBuilder);
        given(appBuilder.crypto(any())).willReturn(appBuilder);
        given(appBuilder.staticAccountMemo(bookMemo)).willReturn(appBuilder);
        given(appBuilder.initialHash(EMPTY_HASH)).willReturn(appBuilder);
        given(appBuilder.platform(platform)).willReturn(appBuilder);
        given(appBuilder.selfId(1L)).willReturn(appBuilder);
        given(appBuilder.build()).willReturn(app);
        // and:
        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(app.sysAccountsCreator()).willReturn(accountsCreator);
        given(app.workingState()).willReturn(workingState);
        given(app.sysFilesManager()).willReturn(systemFilesManager);

        // when:
        subject.init(platform, addressBook, dualState, InitTrigger.GENESIS, null);

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
        verify(dualStateAccessor).setDualState(dualState);
        verify(initFlow).runWith(eq(subject), any());
        verify(appBuilder).bootstrapProps(any());
        verify(appBuilder).initialHash(EMPTY_HASH);
        verify(appBuilder).platform(platform);
        verify(appBuilder).selfId(selfId.getId());
        // and:
        assertTrue(APPS.includes(selfId.getId()));

        // cleanup:
        ServicesState.setAppBuilder(DaggerServicesApp::builder);
    }

    @Test
    void genesisWhenVirtualNftsEnabled() {
        // setup:
        subject = new ServicesState(bootstrapProperties);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.TOKENS_NFTS_USE_VIRTUAL_MERKLE))
                .willReturn(true);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.ACCOUNTS_STORE_ON_DISK))
                .willReturn(false);
        ServicesState.setAppBuilder(() -> appBuilder);

        given(addressBook.getSize()).willReturn(3);
        given(addressBook.getAddress(anyLong())).willReturn(address);
        given(address.getMemo()).willReturn(bookMemo);
        given(appBuilder.bootstrapProps(any())).willReturn(appBuilder);
        given(appBuilder.crypto(any())).willReturn(appBuilder);
        given(appBuilder.staticAccountMemo(bookMemo)).willReturn(appBuilder);
        given(appBuilder.initialHash(EMPTY_HASH)).willReturn(appBuilder);
        given(appBuilder.platform(platform)).willReturn(appBuilder);
        given(appBuilder.selfId(1L)).willReturn(appBuilder);
        given(appBuilder.build()).willReturn(app);
        // and:
        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(app.sysAccountsCreator()).willReturn(accountsCreator);
        given(app.workingState()).willReturn(workingState);
        given(app.sysFilesManager()).willReturn(systemFilesManager);

        // when:
        subject.init(platform, addressBook, dualState, InitTrigger.GENESIS, null);
        setAllChildren();

        // then:
        assertTrue(subject.uniqueTokens().isVirtual());

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
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        // and:
        APPS.save(selfId.getId(), app);

        // when:
        subject.init(platform, addressBook, dualState, RECONNECT, currentVersion);

        // then:
        assertSame(addressBook, subject.addressBook());
        assertSame(app, subject.getMetadata().app());
        // and:
        verify(initFlow).runWith(eq(subject), any());
        verify(hashLogger).logHashesFor(subject);
    }

    @Test
    void nonGenesisInitExitsIfStateVersionLaterThanCurrentSoftware() {
        final var mockExit = mock(SystemExits.class);

        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);

        given(platform.getSelfId()).willReturn(selfId);
        given(app.systemExits()).willReturn(mockExit);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        // and:
        APPS.save(selfId.getId(), app);

        // when:
        subject.init(platform, addressBook, dualState, RESTART, futureVersion);

        verify(mockExit).fail(1);
    }

    @Test
    void nonGenesisInitDoesNotClearPreparedUpgradeIfSameVersion() {
        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);

        final var when = Instant.ofEpochSecond(1_234_567L, 890);
        given(dualState.getFreezeTime()).willReturn(when);
        given(dualState.getLastFrozenTime()).willReturn(when);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(app.sysFilesManager()).willReturn(systemFilesManager);
        // and:
        APPS.save(selfId.getId(), app);

        // when:
        subject.init(platform, addressBook, dualState, RESTART, currentVersion);

        verify(networkContext, never()).discardPreparedUpgradeMeta();
        verify(dualState, never()).setFreezeTime(null);
    }

    @Test
    void nonGenesisInitClearsPreparedUpgradeIfDeployedIsLaterVersion() {
        mockMigrators();
        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);

        final var when = Instant.ofEpochSecond(1_234_567L, 890);
        given(dualState.getFreezeTime()).willReturn(when);
        given(dualState.getLastFrozenTime()).willReturn(when);
        given(app.workingState()).willReturn(workingState);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(app.sysFilesManager()).willReturn(systemFilesManager);
        given(app.treasuryCloner()).willReturn(treasuryCloner);
        // and:
        APPS.save(selfId.getId(), app);

        // when:
        subject.init(platform, addressBook, dualState, RESTART, justPriorVersion);

        verify(networkContext).discardPreparedUpgradeMeta();
        verify(dualState).setFreezeTime(null);
        unmockMigrators();
    }

    @Test
    void nonGenesisInitWithOldVersionMarksMigrationRecordsNotStreamed() {
        mockMigrators();
        givenSpecialAccountsForMigration();
        given(virtualMapFactory.newVirtualizedIterableStorage()).willReturn(iterableStorage);
        given(vmf.apply(any())).willReturn(virtualMapFactory);
        subject.setMetadata(metadata);
        given(app.workingState()).willReturn(workingState);

        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);
        subject.setDeserializedStateVersion(StateVersions.RELEASE_025X_VERSION);

        final var when = Instant.ofEpochSecond(1_234_567L, 890);
        given(dualState.getFreezeTime()).willReturn(when);
        given(dualState.getLastFrozenTime()).willReturn(when);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(app.sysFilesManager()).willReturn(systemFilesManager);
        given(app.treasuryCloner()).willReturn(treasuryCloner);
        // and:
        APPS.save(selfId.getId(), app);

        // when:
        subject.init(platform, addressBook, dualState, RESTART, null);

        verify(networkContext).discardPreparedUpgradeMeta();
        verify(networkContext).markMigrationRecordsNotYetStreamed();
        verify(dualState).setFreezeTime(null);

        unmockMigrators();
    }

    @Test
    void nonGenesisInitThrowsWithUnsupportedStateVersionUsed() {
        subject.setDeserializedStateVersion(StateVersions.RELEASE_025X_VERSION - 1);

        assertThrows(
                IllegalStateException.class,
                () -> subject.init(platform, addressBook, dualState, RESTART, null));
    }

    @Test
    void nonGenesisInitDoesntClearPreparedUpgradeIfNotUpgrade() {
        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        // and:
        APPS.save(selfId.getId(), app);

        // when:
        subject.init(platform, addressBook, dualState, RECONNECT, currentVersion);

        verify(networkContext, never()).discardPreparedUpgradeMeta();
    }

    @Test
    void initHandlesScheduledTxnMigration() {
        subject.setChild(StateChildIndices.SCHEDULE_TXS, mock(MerkleScheduledTransactions.class));
        assertInstanceOf(MerkleScheduledTransactions.class, subject.scheduleTxs());

        var mockLegacyScheduledTxns = mock(MerkleMap.class);
        given(mockLegacyScheduledTxns.size()).willReturn(2);

        subject.setChild(StateChildIndices.SCHEDULE_TXS, mockLegacyScheduledTxns);
        assertNull(subject.scheduleTxs());

        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.ACCOUNTS, accounts);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(app.sysFilesManager()).willReturn(systemFilesManager);
        // and:
        APPS.save(selfId.getId(), app);

        // when:
        subject.init(platform, addressBook, dualState, RESTART, currentVersion);

        var scheduledTxns = subject.scheduleTxs();

        assertInstanceOf(MerkleScheduledTransactions.class, scheduledTxns);
        assertEquals(2, scheduledTxns.getNumSchedules());
    }

    @Test
    void nonGenesisInitHandlesNftMigration() {
        subject = new ServicesState(bootstrapProperties);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.TOKENS_NFTS_USE_VIRTUAL_MERKLE))
                .willReturn(true);
        given(bootstrapProperties.getBooleanProperty(PropertyNames.ACCOUNTS_STORE_ON_DISK))
                .willReturn(true);
        ServicesState.setMapToDiskMigration(mapToDiskMigration);
        ServicesState.setVmFactory(vmf);
        given(vmf.apply(any())).willReturn(virtualMapFactory);

        final var vmap = mock(VirtualMap.class);
        setAllMmsTo(mock(MerkleMap.class));
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.STORAGE, vmap);
        subject.setChild(StateChildIndices.CONTRACT_STORAGE, vmap);

        final var when = Instant.ofEpochSecond(1_234_567L, 890);
        given(dualState.getFreezeTime()).willReturn(when);
        given(dualState.getLastFrozenTime()).willReturn(when);

        given(app.hashLogger()).willReturn(hashLogger);
        given(app.initializationFlow()).willReturn(initFlow);
        given(app.dualStateAccessor()).willReturn(dualStateAccessor);
        given(platform.getSelfId()).willReturn(selfId);
        given(app.sysFilesManager()).willReturn(systemFilesManager);
        // and:
        APPS.save(selfId.getId(), app);

        // when:
        subject.init(platform, addressBook, dualState, RESTART, currentVersion);
        assertTrue(subject.uniqueTokens().isVirtual());
        verify(mapToDiskMigration)
                .migrateToDiskAsApropos(
                        INSERTIONS_PER_COPY,
                        subject,
                        virtualMapFactory,
                        ServicesState.accountMigrator);

        ServicesState.setVmFactory(VirtualMapFactory::new);
        ServicesState.setMapToDiskMigration(MapMigrationToDisk::migrateToDiskAsApropos);
    }

    @Test
    void copySetsMutabilityAsExpected() {
        // when:
        final var copy = subject.copy();

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
        final var copy = subject.copy();

        // then:
        verify(workingState).updateFrom(copy);
    }

    @Test
    void copiesNonNullChildren() {
        subject.setChild(StateChildIndices.ADDRESS_BOOK, addressBook);
        subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
        subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
        // and:
        subject.setMetadata(metadata);
        subject.setDeserializedStateVersion(10);

        given(addressBook.copy()).willReturn(addressBook);
        given(networkContext.copy()).willReturn(networkContext);
        given(specialFiles.copy()).willReturn(specialFiles);
        given(metadata.copy()).willReturn(metadata);
        given(metadata.app()).willReturn(app);
        given(app.workingState()).willReturn(workingState);

        // when:
        final var copy = subject.copy();

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
    void testLoading0274State() {
        ClassLoaderHelper.loadClassPathDependencies();
        final AtomicReference<SignedState> ref = new AtomicReference<>();
        assertDoesNotThrow(
                () -> ref.set(loadSignedState(signedStateDir + "v0.27.4/SignedState.swh")));
        final var mockPlatform = createMockPlatformWithCrypto();
        ref.get()
                .getSwirldState()
                .init(
                        mockPlatform,
                        createPretendBookFrom(mockPlatform, false),
                        new DualStateImpl(),
                        RESTART,
                        null);
    }

    @Test
    void testGenesisState() {
        ClassLoaderHelper.loadClassPathDependencies();
        final var servicesState = new ServicesState();
        final var recordsRunningHashLeaf = new RecordsRunningHashLeaf();
        recordsRunningHashLeaf.setRunningHash(new RunningHash(EMPTY_HASH));
        servicesState.setChild(
                StateChildIndices.RECORD_STREAM_RUNNING_HASH, recordsRunningHashLeaf);
        final var platform = createMockPlatformWithCrypto();
        final var app = createApp(platform);

        APPS.save(platform.getSelfId().getId(), app);
        assertDoesNotThrow(
                () ->
                        servicesState.init(
                                platform,
                                createPretendBookFrom(platform, true),
                                new DualStateImpl(),
                                InitTrigger.GENESIS,
                                null));
    }

    @Test
    void testUniqueTokensWhenVirtual() {
        final var vmap = new VirtualMap<>();
        subject.setChild(StateChildIndices.UNIQUE_TOKENS, vmap);
        assertTrue(subject.uniqueTokens().isVirtual());
        assertSame(vmap, subject.uniqueTokens().virtualMap());
    }

    @Test
    void testUniqueTokensWhenMerkleMap() {
        final var mmap = new MerkleMap<>();
        subject.setChild(StateChildIndices.UNIQUE_TOKENS, mmap);
        assertFalse(subject.uniqueTokens().isVirtual());
        assertSame(mmap, subject.uniqueTokens().merkleMap());
    }

    private AddressBook createPretendBookFrom(
            final Platform platform, final boolean withKeyDetails) {
        final var pubKey = mock(PublicKey.class);
        given(pubKey.getAlgorithm()).willReturn("EC");
        if (withKeyDetails) {
            given(pubKey.getEncoded()).willReturn(Longs.toByteArray(Long.MAX_VALUE));
        }
        final var nodeId = platform.getSelfId().getId();
        final var address =
                new Address(
                        nodeId,
                        "",
                        "",
                        1L,
                        false,
                        null,
                        -1,
                        Ints.toByteArray(123456789),
                        -1,
                        null,
                        -1,
                        null,
                        -1,
                        new SerializablePublicKey(pubKey),
                        null,
                        new SerializablePublicKey(pubKey),
                        "");
        return new AddressBook(List.of(address));
    }

    private static ServicesApp createApp(Platform platform) {
        return DaggerServicesApp.builder()
                .initialHash(new Hash())
                .platform(platform)
                .crypto(CryptoFactory.getInstance())
                .selfId(platform.getSelfId().getId())
                .staticAccountMemo("memo")
                .bootstrapProps(new BootstrapProperties())
                .build();
    }

    private Platform createMockPlatformWithCrypto() {
        final var platform = mock(Platform.class);
        when(platform.getSelfId()).thenReturn(new NodeId(false, 0));
        when(platform.getCryptography()).thenReturn(new CryptoEngine());
        assertNotNull(platform.getCryptography());
        return platform;
    }

    private static SignedState loadSignedState(final String path) throws IOException {
        var signedPair = SignedStateFileReader.readStateFile(Paths.get(path));
        // Because it's possible we are loading old data, we cannot check equivalence of the hash.
        Assertions.assertNotNull(signedPair.signedState());
        return signedPair.signedState();
    }

    private void setAllMmsTo(final MerkleMap<?, ?> mockMm) {
        subject.setChild(StateChildIndices.ACCOUNTS, mockMm);
        subject.setChild(StateChildIndices.TOKEN_ASSOCIATIONS, mockMm);
        subject.setChild(StateChildIndices.TOKENS, mockMm);
        subject.setChild(StateChildIndices.UNIQUE_TOKENS, mockMm);
        subject.setChild(StateChildIndices.STORAGE, mockMm);
        subject.setChild(StateChildIndices.TOPICS, mockMm);
        subject.setChild(StateChildIndices.SCHEDULE_TXS, mockMm);
        subject.setChild(StateChildIndices.STAKING_INFO, mockMm);
    }

    private void setAllChildren() {
        given(addressBook.getSize()).willReturn(1);
        given(addressBook.getAddress(0)).willReturn(address);
        given(address.getId()).willReturn(0L);
        given(bootstrapProperties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT))
                .willReturn(3_000_000_000L);
        given(bootstrapProperties.getIntProperty(STAKING_REWARD_HISTORY_NUM_STORED_PERIODS))
                .willReturn(2);
        File databaseFolder = new File("database");
        try {
            if (!databaseFolder.exists()) {
                databaseFolder.mkdir();
            }
            FileUtils.cleanDirectory(new File("database"));
        } catch (IllegalArgumentException | IOException e) {
            System.err.println("Exception thrown while cleaning up directory");
            e.printStackTrace();
        }
        subject.createGenesisChildren(addressBook, 0, bootstrapProperties);
    }

    private void mockMigrators() {
        mockMigratorsOnly();
        givenSpecialAccountsForMigration();
    }

    private void mockMigratorsOnly() {
        ServicesState.setAutoRenewalMigrator(autoRenewalMigrator);
        ServicesState.setIterableStorageMigrator(iterableStorageMigrator);
        ServicesState.setOwnedNftsLinkMigrator(nftLinksRepair);
        ServicesState.setVmFactory(vmf);
        ServicesState.setScheduledTransactionsMigrator(scheduledTxnsMigrator);
        ServicesState.setStakingInfoBuilder(stakingInfoBuilder);
    }

    private void unmockMigrators() {
        ServicesState.setMapToDiskMigration(MapMigrationToDisk::migrateToDiskAsApropos);
        ServicesState.setAutoRenewalMigrator(ReleaseThirtyMigration::grantFreeAutoRenew);
        ServicesState.setIterableStorageMigrator(ReleaseTwentySixMigration::makeStorageIterable);
        ServicesState.setOwnedNftsLinkMigrator(ReleaseThirtyMigration::rebuildNftOwners);
        ServicesState.setVmFactory(VirtualMapFactory::new);
        ServicesState.setScheduledTransactionsMigrator(
                LongTermScheduledTransactionsMigration::migrateScheduledTransactions);
        ServicesState.setStakingInfoBuilder(ReleaseTwentySevenMigration::buildStakingInfoMap);
    }

    private void givenSpecialAccountsForMigration() {
        given(accounts.get(EntityNum.fromLong(800L))).willReturn(new MerkleAccount());
        given(accounts.get(EntityNum.fromLong(801L))).willReturn(new MerkleAccount());
    }
}
