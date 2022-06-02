package com.hedera.services;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.init.ServicesInitFlow;
import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.sigs.order.SigReqsManager;
import com.hedera.services.state.DualStateAccessor;
import com.hedera.services.state.forensics.HashLogger;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleScheduledTransactions;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.migration.LongTermScheduledTransactionsMigration;
import com.hedera.services.state.migration.ReleaseTwentyFiveMigration;
import com.hedera.services.state.migration.ReleaseTwentySevenMigration;
import com.hedera.services.state.migration.ReleaseTwentySixMigration;
import com.hedera.services.state.migration.StateChildIndices;
import com.hedera.services.state.migration.StateVersions;
import com.hedera.services.state.org.StateMetadata;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.state.virtual.VirtualMapFactory;
import com.hedera.services.txns.ProcessLogic;
import com.hedera.services.txns.prefetch.PrefetchProcessor;
import com.hedera.services.txns.span.ExpandHandleSpan;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.SystemExits;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.system.Address;
import com.swirlds.common.system.AddressBook;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.transaction.SwirldTransaction;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.ServicesState.EMPTY_HASH;
import static com.hedera.services.context.AppsManager.APPS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@ExtendWith({ MockitoExtension.class, LogCaptureExtension.class })
class ServicesStateTest {
	private final Instant creationTime = Instant.ofEpochSecond(1_234_567L, 8);
	private final Instant consensusTime = Instant.ofEpochSecond(2_345_678L, 9);
	private final NodeId selfId = new NodeId(false, 1L);
	private static final String bookMemo = "0.0.4";

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
	private SwirldTransaction transaction;
	@Mock
	private SwirldDualState dualState;
	@Mock
	private StateMetadata metadata;
	@Mock
	private ProcessLogic logic;
	@Mock
	private PlatformTxnAccessor txnAccessor;
	@Mock
	private ExpandHandleSpan expandHandleSpan;
	@Mock
	private SigReqsManager sigReqsManager;
	@Mock
	private FCHashMap<ByteString, EntityNum> aliases;
	@Mock
	private MutableStateChildren workingState;
	@Mock
	private DualStateAccessor dualStateAccessor;
	@Mock
	private ServicesInitFlow initFlow;
	@Mock
	private ServicesApp.Builder appBuilder;
	@Mock
	private PrefetchProcessor prefetchProcessor;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock
	private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations;
	@Mock
	private VirtualMapFactory virtualMapFactory;
	@Mock
	private VirtualMap<ContractKey, IterableContractValue> iterableStorage;
	@Mock
	private ServicesState.TokenRelsLinkMigrator tokenRelsLinkMigrator;
	@Mock
	private ServicesState.OwnedNftsLinkMigrator ownedNftsLinkMigrator;
	@Mock
	private ServicesState.StakingInfoBuilder stakingInfoBuilder;
	@Mock
	private ServicesState.IterableStorageMigrator iterableStorageMigrator;
	@Mock
	private Consumer<ServicesState> titleCountsMigrator;
	@Mock
	private ServicesState.ContractAutoRenewalMigrator autoRenewalMigrator;
	@Mock
	private Function<VirtualMapFactory.JasperDbBuilderFactory, VirtualMapFactory> vmf;
	@Mock
	private Consumer<ServicesState> scheduledTxnsMigrator;
	@Mock
	private BootstrapProperties bootstrapProperties;

	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private ServicesState subject;

	@BeforeEach
	void setUp() {
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
	void doesNoMigrationsFromCurrentVersion() {
		mockMigrators();

		subject.setDeserializedVersion(StateVersions.CURRENT_VERSION);
		subject.migrate();

		verifyNoInteractions(
				autoRenewalMigrator, titleCountsMigrator, iterableStorageMigrator, tokenRelsLinkMigrator);

		unmockMigrators();
	}

	@Test
	void doesAllMigrationsExceptAutoRenewFromRelease024VersionIfExpiryNotJustEnabled() {
		mockMigrators();
		final var inOrder = inOrder(
				titleCountsMigrator, iterableStorageMigrator, tokenRelsLinkMigrator, vmf, workingState, scheduledTxnsMigrator);

		ServicesState.setExpiryJustEnabled(false);
		subject.setChild(StateChildIndices.ACCOUNTS, accounts);
		subject.setChild(StateChildIndices.TOKEN_ASSOCIATIONS, tokenAssociations);
		subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
		subject.setChild(StateChildIndices.SCHEDULE_TXS, mock(MerkleMap.class));
		subject.setMetadata(metadata);
		subject.setDeserializedVersion(StateVersions.RELEASE_024X_VERSION);

		given(metadata.app()).willReturn(app);
		given(app.workingState()).willReturn(workingState);
		given(virtualMapFactory.newVirtualizedIterableStorage()).willReturn(iterableStorage);
		given(vmf.apply(any())).willReturn(virtualMapFactory);
		doAnswer(inv -> {
			subject.setChild(StateChildIndices.SCHEDULE_TXS, new MerkleScheduledTransactions(1));
			return null;
		}).when(scheduledTxnsMigrator).accept(subject);

		subject.migrate();

		inOrder.verify(tokenRelsLinkMigrator).buildAccountTokenAssociationsLinkedList(accounts, tokenAssociations);
		inOrder.verify(titleCountsMigrator).accept(subject);
		inOrder.verify(iterableStorageMigrator).makeStorageIterable(
				eq(subject), any(), any(), eq(iterableStorage));
		inOrder.verify(workingState).updatePrimitiveChildrenFrom(subject);
		inOrder.verify(scheduledTxnsMigrator).accept(subject);

		verifyNoInteractions(autoRenewalMigrator);

		unmockMigrators();
	}

	@Test
	void doesAllMigrationsFromRelease024VersionIfExpiryJustEnabled() {
		mockMigrators();
		final var inOrder = inOrder(
				autoRenewalMigrator, titleCountsMigrator, scheduledTxnsMigrator,
				iterableStorageMigrator, tokenRelsLinkMigrator, vmf, workingState);

		ServicesState.setExpiryJustEnabled(true);
		subject.setChild(StateChildIndices.ACCOUNTS, accounts);
		subject.setChild(StateChildIndices.TOKEN_ASSOCIATIONS, tokenAssociations);
		subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
		subject.setChild(StateChildIndices.SCHEDULE_TXS, mock(MerkleMap.class));
		subject.setMetadata(metadata);
		subject.setDeserializedVersion(StateVersions.RELEASE_024X_VERSION);
		given(networkContext.consensusTimeOfLastHandledTxn()).willReturn(consensusTime);

		given(metadata.app()).willReturn(app);
		given(app.workingState()).willReturn(workingState);
		given(virtualMapFactory.newVirtualizedIterableStorage()).willReturn(iterableStorage);
		given(vmf.apply(any())).willReturn(virtualMapFactory);
		doAnswer(inv -> {
			subject.setChild(StateChildIndices.SCHEDULE_TXS, new MerkleScheduledTransactions(1));
			return null;
		}).when(scheduledTxnsMigrator).accept(subject);

		subject.migrate();
		ServicesState.setExpiryJustEnabled(false);

		inOrder.verify(tokenRelsLinkMigrator).buildAccountTokenAssociationsLinkedList(accounts, tokenAssociations);
		inOrder.verify(titleCountsMigrator).accept(subject);
		inOrder.verify(iterableStorageMigrator).makeStorageIterable(
				eq(subject), any(), any(), eq(iterableStorage));
		inOrder.verify(autoRenewalMigrator).grantFreeAutoRenew(subject, consensusTime);
		inOrder.verify(workingState).updatePrimitiveChildrenFrom(subject);
		inOrder.verify(scheduledTxnsMigrator).accept(subject);

		unmockMigrators();
	}

	@Test
	void doesScheduledTxnMigrationRegardlessOfVersion() {
		mockMigrators();

		subject.setChild(StateChildIndices.SCHEDULE_TXS, mock(MerkleMap.class));
		subject.setDeserializedVersion(StateVersions.CURRENT_VERSION);
		doAnswer(inv -> {
			subject.setChild(StateChildIndices.SCHEDULE_TXS, new MerkleScheduledTransactions(1));
			return null;
		}).when(scheduledTxnsMigrator).accept(subject);

		subject.migrate();

		verify(titleCountsMigrator, never()).accept(any());
		verify(iterableStorageMigrator, never()).makeStorageIterable(any(), any(), any(), any());
		verify(autoRenewalMigrator, never()).grantFreeAutoRenew(any(), any());

		verify(scheduledTxnsMigrator).accept(subject);

		unmockMigrators();
	}

	private void mockMigrators() {
		ServicesState.setAutoRenewalMigrator(autoRenewalMigrator);
		ServicesState.setTitleCountsMigrator(titleCountsMigrator);
		ServicesState.setIterableStorageMigrator(iterableStorageMigrator);
		ServicesState.setTokenRelsLinkMigrator(tokenRelsLinkMigrator);
		ServicesState.setOwnedNftsLinkMigrator(ownedNftsLinkMigrator);
		ServicesState.setVmFactory(vmf);
		ServicesState.setScheduledTransactionsMigrator(scheduledTxnsMigrator);
		ServicesState.setStakingInfoBuilder(stakingInfoBuilder);
	}

	private void unmockMigrators() {
		ServicesState.setAutoRenewalMigrator(ReleaseTwentySixMigration::grantFreeAutoRenew);
		ServicesState.setTitleCountsMigrator(ReleaseTwentyFiveMigration::initTreasuryTitleCounts);
		ServicesState.setIterableStorageMigrator(ReleaseTwentySixMigration::makeStorageIterable);
		ServicesState.setTokenRelsLinkMigrator(ReleaseTwentyFiveMigration::buildAccountTokenAssociationsLinkedList);
		ServicesState.setOwnedNftsLinkMigrator(ReleaseTwentySixMigration::buildAccountNftsOwnedLinkedList);
		ServicesState.setVmFactory(VirtualMapFactory::new);
		ServicesState.setScheduledTransactionsMigrator(
				LongTermScheduledTransactionsMigration::migrateScheduledTransactions);
		ServicesState.setStakingInfoBuilder(ReleaseTwentySevenMigration::buildStakingInfoMap);
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
		Assertions.assertDoesNotThrow(subject::onRelease);
	}

	@Test
	void onReleaseForwardsToMetadataIfNonNull() {
		// setup:
		subject.setMetadata(metadata);

		// when:
		subject.onRelease();

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
		verify(metadata).archive();
		verify(mockMm, times(6)).archive();
	}

	@Test
	void noMoreTransactionsIsNoop() {
		// expect:
		assertDoesNotThrow(subject::noMoreTransactions);
	}

	@Test
	void expandsSigsAsExpected() throws InvalidProtocolBufferException {
		// setup:
		subject.setMetadata(metadata);

		given(metadata.app()).willReturn(app);
		given(app.expandHandleSpan()).willReturn(expandHandleSpan);
		given(app.prefetchProcessor()).willReturn(prefetchProcessor);
		given(app.sigReqsManager()).willReturn(sigReqsManager);
		given(expandHandleSpan.track(transaction)).willReturn(txnAccessor);

		// when:
		subject.expandSignatures(transaction);

		// then:
		verify(sigReqsManager).expandSigsInto(txnAccessor);
	}

	@Test
	void warnsOfIpbe() throws InvalidProtocolBufferException {
		// setup:
		subject.setMetadata(metadata);

		given(metadata.app()).willReturn(app);
		given(app.expandHandleSpan()).willReturn(expandHandleSpan);
		given(expandHandleSpan.track(transaction)).willThrow(InvalidProtocolBufferException.class);

		// when:
		subject.expandSignatures(transaction);

		// then:
		assertThat(
				logCaptor.warnLogs(),
				contains(Matchers.startsWith("Method expandSignatures called with non-gRPC txn")));
	}

	@Test
	void warnsOfRace() throws InvalidProtocolBufferException {
		// setup:
		subject.setMetadata(metadata);

		given(metadata.app()).willReturn(app);
		given(app.expandHandleSpan()).willReturn(expandHandleSpan);
		given(app.expandHandleSpan()).willReturn(expandHandleSpan);
		given(expandHandleSpan.track(transaction)).willThrow(ConcurrentModificationException.class);

		// when:
		subject.expandSignatures(transaction);

		// then:
		assertThat(
				logCaptor.warnLogs(),
				contains(Matchers.startsWith("Unable to expand signatures, will be verified synchronously")));
	}

	@Test
	void handleNonConsensusTransactionAsExpected() {
		// setup:
		subject.setMetadata(metadata);

		// when:
		subject.handleTransaction(
				1L, false, creationTime, null, transaction, dualState);

		// then:
		verifyNoInteractions(metadata);
	}

	@Test
	void handleConsensusTransactionAsExpected() {
		// setup:
		subject.setMetadata(metadata);

		given(metadata.app()).willReturn(app);
		given(app.logic()).willReturn(logic);
		given(app.dualStateAccessor()).willReturn(dualStateAccessor);

		// when:
		subject.handleTransaction(
				1L, true, creationTime, consensusTime, transaction, dualState);

		// then:
		verify(dualStateAccessor).setDualState(dualState);
		verify(logic).incorporateConsensusTxn(transaction, consensusTime, 1L);
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
	void minimumVersionIsRelease0240() {
		// expect:
		assertEquals(StateVersions.RELEASE_024X_VERSION, subject.getMinimumSupportedVersion());
	}

	@Test
	void minimumChildCountsAsExpected() {
		assertEquals(
				StateChildIndices.NUM_POST_0210_CHILDREN,
				subject.getMinimumChildCount(StateVersions.MINIMUM_SUPPORTED_VERSION));
		assertEquals(
				StateChildIndices.NUM_POST_0210_CHILDREN,
				subject.getMinimumChildCount(StateVersions.RELEASE_0260_VERSION));
		assertEquals(
				StateChildIndices.NUM_POST_0270_CHILDREN,
				subject.getMinimumChildCount(StateVersions.CURRENT_VERSION));
		assertThrows(IllegalArgumentException.class,
				() -> subject.getMinimumChildCount(StateVersions.MINIMUM_SUPPORTED_VERSION - 1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.getMinimumChildCount(StateVersions.CURRENT_VERSION + 1));
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
		given(platform.getSelfId()).willReturn(selfId);

		APPS.save(selfId.getId(), app);

		assertDoesNotThrow(() -> subject.init(platform, addressBook, null));
	}

	@Test
	void genesisInitCreatesChildren() {
		// setup:
		ServicesState.setAppBuilder(() -> appBuilder);

		given(addressBook.getSize()).willReturn(3);
		given(addressBook.getAddress(anyLong())).willReturn(address);
		given(address.getMemo()).willReturn(bookMemo);
		given(appBuilder.bootstrapProps(any())).willReturn(appBuilder);
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

		// when:
		subject.genesisInit(platform, addressBook, dualState);

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
		assertEquals(StateVersions.CURRENT_VERSION, subject.networkCtx().getStateVersion());
		assertEquals(1001L, subject.networkCtx().seqNo().current());
		assertNotNull(subject.specialFiles());
		// and:
		verify(dualStateAccessor).setDualState(dualState);
		verify(initFlow).runWith(subject);
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
		subject.init(platform, addressBook, dualState);

		// then:
		assertSame(addressBook, subject.addressBook());
		assertSame(app, subject.getMetadata().app());
		// and:
		verify(initFlow).runWith(subject);
		verify(hashLogger).logHashesFor(subject);
		verify(networkContext).setStateVersion(StateVersions.CURRENT_VERSION);
	}

	@Test
	void nonGenesisInitExitsIfStateVersionLaterThanCurrentSoftware() {
		final var mockExit = mock(SystemExits.class);

		subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
		subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
		subject.setChild(StateChildIndices.ACCOUNTS, accounts);
		given(networkContext.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION + 1);

		given(platform.getSelfId()).willReturn(selfId);
		given(app.systemExits()).willReturn(mockExit);
		given(app.dualStateAccessor()).willReturn(dualStateAccessor);
		// and:
		APPS.save(selfId.getId(), app);

		// when:
		subject.init(platform, addressBook, dualState);

		verify(mockExit).fail(1);
	}

	@Test
	void nonGenesisInitClearsPreparedUpgradeIfNonNullLastFrozenMatchesFreezeTime() {
		subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
		subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
		subject.setChild(StateChildIndices.ACCOUNTS, accounts);

		final var when = Instant.ofEpochSecond(1_234_567L, 890);
		given(dualState.getFreezeTime()).willReturn(when);
		given(dualState.getLastFrozenTime()).willReturn(when);
		given(networkContext.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);

		given(app.hashLogger()).willReturn(hashLogger);
		given(app.initializationFlow()).willReturn(initFlow);
		given(app.dualStateAccessor()).willReturn(dualStateAccessor);
		given(platform.getSelfId()).willReturn(selfId);
		// and:
		APPS.save(selfId.getId(), app);

		// when:
		subject.init(platform, addressBook, dualState);

		verify(networkContext).discardPreparedUpgradeMeta();
		verify(dualState).setFreezeTime(null);
	}

	@Test
	void nonGenesisInitWithOldVersionMarksMigrationRecordsNotStreamed() {
		subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
		subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
		subject.setChild(StateChildIndices.ACCOUNTS, accounts);

		final var when = Instant.ofEpochSecond(1_234_567L, 890);
		given(dualState.getFreezeTime()).willReturn(when);
		given(dualState.getLastFrozenTime()).willReturn(when);
		given(networkContext.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION - 1);

		given(app.hashLogger()).willReturn(hashLogger);
		given(app.initializationFlow()).willReturn(initFlow);
		given(app.dualStateAccessor()).willReturn(dualStateAccessor);
		given(platform.getSelfId()).willReturn(selfId);
		// and:
		APPS.save(selfId.getId(), app);

		// when:
		subject.init(platform, addressBook, dualState);

		verify(networkContext).discardPreparedUpgradeMeta();
		verify(networkContext).markMigrationRecordsNotYetStreamed();
		verify(dualState).setFreezeTime(null);
	}

	@Test
	void nonGenesisInitDoesntClearPreparedUpgradeIfBothFreezeAndLastFrozenAreNull() {
		subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
		subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
		subject.setChild(StateChildIndices.ACCOUNTS, accounts);

		given(networkContext.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);

		given(app.hashLogger()).willReturn(hashLogger);
		given(app.initializationFlow()).willReturn(initFlow);
		given(app.dualStateAccessor()).willReturn(dualStateAccessor);
		given(platform.getSelfId()).willReturn(selfId);
		// and:
		APPS.save(selfId.getId(), app);

		// when:
		subject.init(platform, addressBook, dualState);

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

		given(networkContext.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);

		given(app.hashLogger()).willReturn(hashLogger);
		given(app.initializationFlow()).willReturn(initFlow);
		given(app.dualStateAccessor()).willReturn(dualStateAccessor);
		given(platform.getSelfId()).willReturn(selfId);
		// and:
		APPS.save(selfId.getId(), app);

		// when:
		subject.init(platform, addressBook, dualState);

		var scheduledTxns = subject.scheduleTxs();

		assertInstanceOf(MerkleScheduledTransactions.class, scheduledTxns);
		assertEquals(2, scheduledTxns.getNumSchedules());
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
		// setup:
		subject.setChild(StateChildIndices.ADDRESS_BOOK, addressBook);
		subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
		subject.setChild(StateChildIndices.SPECIAL_FILES, specialFiles);
		// and:
		subject.setMetadata(metadata);
		subject.setDeserializedVersion(10);

		given(addressBook.copy()).willReturn(addressBook);
		given(networkContext.copy()).willReturn(networkContext);
		given(specialFiles.copy()).willReturn(specialFiles);
		given(metadata.copy()).willReturn(metadata);
		given(metadata.app()).willReturn(app);
		given(app.workingState()).willReturn(workingState);

		// when:
		final var copy = subject.copy();

		// then:
		assertEquals(10, copy.getDeserializedVersion());
		assertSame(metadata, copy.getMetadata());
		verify(metadata).copy();
		// and:
		assertSame(addressBook, copy.addressBook());
		assertSame(networkContext, copy.networkCtx());
		assertSame(specialFiles, copy.specialFiles());
	}

	private List<MerkleNode> legacyChildrenWith(
			AddressBook addressBook,
			MerkleNetworkContext networkContext,
			MerkleMap<EntityNumPair, MerkleUniqueToken> nfts,
			MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels,
			boolean withNfts
	) {
		final List<MerkleNode> legacyChildren = new ArrayList<>();
		legacyChildren.add(addressBook);
		legacyChildren.add(networkContext);
		legacyChildren.add(null);
		legacyChildren.add(null);
		legacyChildren.add(null);
		legacyChildren.add(null);
		legacyChildren.add(tokenRels);
		legacyChildren.add(null);
		legacyChildren.add(null);
		legacyChildren.add(null);
		if (withNfts) {
			legacyChildren.add(nfts);
		}
		return legacyChildren;
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
		given(bootstrapProperties.getLongProperty("ledger.totalTinyBarFloat")).willReturn(3_000_000_000L);
		given(bootstrapProperties.getIntProperty("staking.rewardHistory.numStoredPeriods")).willReturn(2);
		subject.createGenesisChildren(addressBook, 0, bootstrapProperties);
	}
}
