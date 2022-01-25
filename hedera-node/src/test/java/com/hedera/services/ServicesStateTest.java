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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.init.ServicesInitFlow;
import com.hedera.services.sigs.order.SigReqsManager;
import com.hedera.services.state.DualStateAccessor;
import com.hedera.services.state.StateAccessor;
import com.hedera.services.state.forensics.HashLogger;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.migration.ReleaseTwentyTwoMigration;
import com.hedera.services.state.migration.StateChildIndices;
import com.hedera.services.state.migration.StateVersions;
import com.hedera.services.state.org.StateMetadata;
import com.hedera.services.txns.ProcessLogic;
import com.hedera.services.txns.prefetch.PrefetchProcessor;
import com.hedera.services.txns.span.ExpandHandleSpan;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.services.utils.SystemExits;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.SwirldDualState;
import com.swirlds.common.SwirldTransaction;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.merkle.map.MerkleMap;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;

import static com.hedera.services.ServicesState.EMPTY_HASH;
import static com.hedera.services.context.AppsManager.APPS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
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
	private MerkleDiskFs diskFs;
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
	private StateAccessor workingState;
	@Mock
	private DualStateAccessor dualStateAccessor;
	@Mock
	private ServicesInitFlow initFlow;
	@Mock
	private ServicesApp.Builder appBuilder;
	@Mock
	private ServicesState.BinaryObjectStoreMigrator blobMigrator;
	@Mock
	private PrefetchProcessor prefetchProcessor;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;

	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private ServicesState subject = new ServicesState();


	@AfterEach
	void cleanup() {
		if (APPS.includes(selfId.getId())) {
			APPS.clear(selfId.getId());
		}
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
	void minimumVersionIsRelease0190() {
		// expect:
		assertEquals(StateVersions.RELEASE_0190_AND_020_VERSION, subject.getMinimumSupportedVersion());
	}

	@Test
	void minimumChildCountsAsExpected() {
		assertEquals(
				StateChildIndices.NUM_PRE_0220_CHILDREN,
				subject.getMinimumChildCount(StateVersions.RELEASE_0190_AND_020_VERSION));
		assertEquals(
				StateChildIndices.NUM_0220_CHILDREN,
				subject.getMinimumChildCount(StateVersions.RELEASE_0220_VERSION));
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
	void defersInitWhenInitializingFromRelease0190() {
		subject.addDeserializedChildren(Collections.emptyList(), StateVersions.RELEASE_0190_AND_020_VERSION);

		subject.init(platform, addressBook, dualState);

		assertSame(platform, subject.getPlatformForDeferredInit());
		assertSame(addressBook, subject.getAddressBookForDeferredInit());
		assertSame(dualState, subject.getDualStateForDeferredInit());
	}

	@Test
	void doesntThrowWhenDualStateIsNull() {
		subject.setChild(StateChildIndices.SPECIAL_FILES, diskFs);
		subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
		subject.setChild(StateChildIndices.ACCOUNTS, accounts);

		given(app.hashLogger()).willReturn(hashLogger);
		given(app.initializationFlow()).willReturn(initFlow);
		given(app.workingState()).willReturn(workingState);
		given(app.dualStateAccessor()).willReturn(dualStateAccessor);
		given(platform.getSelfId()).willReturn(selfId);

		APPS.save(selfId.getId(), app);

		assertDoesNotThrow(() -> subject.init(platform, addressBook, null));
	}

	@Test
	void doesntMigrateWhenInitializingFromRelease0220() {
		// given:
		subject.addDeserializedChildren(Collections.emptyList(), StateVersions.RELEASE_0220_VERSION);

		// expect:
		assertDoesNotThrow(subject::migrate);
	}

	@Test
	void migratesWhenInitializingFromRelease0210() {
		ServicesState.setBlobMigrator(blobMigrator);

		subject = mock(ServicesState.class);
		doCallRealMethod().when(subject).migrate();
		given(subject.getDeserializedVersion()).willReturn(StateVersions.RELEASE_0210_VERSION);
		given(subject.getPlatformForDeferredInit()).willReturn(platform);
		given(subject.getAddressBookForDeferredInit()).willReturn(addressBook);
		given(subject.getDualStateForDeferredInit()).willReturn(dualState);

		subject.migrate();

		verify(blobMigrator).migrateFromBinaryObjectStore(
				subject, StateVersions.RELEASE_0210_VERSION);
		verify(subject).init(platform, addressBook, dualState);
		ServicesState.setBlobMigrator(ReleaseTwentyTwoMigration::migrateFromBinaryObjectStore);
	}

	@Test
	void genesisInitCreatesChildren() {
		// setup:
		ServicesState.setAppBuilder(() -> appBuilder);

		given(addressBook.getAddress(selfId.getId())).willReturn(address);
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
		given(app.workingState()).willReturn(workingState);
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
		assertNull(subject.networkCtx().consensusTimeOfLastHandledTxn());
		assertEquals(StateVersions.CURRENT_VERSION, subject.networkCtx().getStateVersion());
		assertEquals(1001L, subject.networkCtx().seqNo().current());
		assertNotNull(subject.specialFiles());
		// and:
		verify(workingState).updateChildrenFrom(subject);
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
		subject.setChild(StateChildIndices.SPECIAL_FILES, diskFs);
		subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
		subject.setChild(StateChildIndices.ACCOUNTS, accounts);

		given(app.hashLogger()).willReturn(hashLogger);
		given(app.initializationFlow()).willReturn(initFlow);
		given(app.workingState()).willReturn(workingState);
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

		subject.setChild(StateChildIndices.SPECIAL_FILES, diskFs);
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
		subject.setChild(StateChildIndices.SPECIAL_FILES, diskFs);
		subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
		subject.setChild(StateChildIndices.ACCOUNTS, accounts);

		final var when = Instant.ofEpochSecond(1_234_567L, 890);
		given(dualState.getFreezeTime()).willReturn(when);
		given(dualState.getLastFrozenTime()).willReturn(when);
		given(networkContext.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);

		given(app.hashLogger()).willReturn(hashLogger);
		given(app.initializationFlow()).willReturn(initFlow);
		given(app.workingState()).willReturn(workingState);
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
	void nonGenesisInitDoesntClearPreparedUpgradeIfBothFreezeAndLastFrozenAreNull() {
		subject.setChild(StateChildIndices.SPECIAL_FILES, diskFs);
		subject.setChild(StateChildIndices.NETWORK_CTX, networkContext);
		subject.setChild(StateChildIndices.ACCOUNTS, accounts);

		given(networkContext.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);

		given(app.hashLogger()).willReturn(hashLogger);
		given(app.initializationFlow()).willReturn(initFlow);
		given(app.workingState()).willReturn(workingState);
		given(app.dualStateAccessor()).willReturn(dualStateAccessor);
		given(platform.getSelfId()).willReturn(selfId);
		// and:
		APPS.save(selfId.getId(), app);

		// when:
		subject.init(platform, addressBook, dualState);

		verify(networkContext, never()).discardPreparedUpgradeMeta();
	}

	@Test
	void forwardsFcomtrAsExpected() {
		// setup:
		final FCOneToManyRelation<EntityNum, Long> a = new FCOneToManyRelation<>();
		final FCOneToManyRelation<EntityNum, Long> b = new FCOneToManyRelation<>();
		final FCOneToManyRelation<EntityNum, Long> c = new FCOneToManyRelation<>();
		// and:
		subject.setMetadata(metadata);

		given(metadata.getUniqueTokenAssociations()).willReturn(a);
		given(metadata.getUniqueOwnershipAssociations()).willReturn(b);
		given(metadata.getUniqueTreasuryOwnershipAssociations()).willReturn(c);

		// expect:
		assertSame(a, subject.uniqueTokenAssociations());
		assertSame(b, subject.uniqueOwnershipAssociations());
		assertSame(c, subject.uniqueTreasuryOwnershipAssociations());
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
		verify(workingState).updateChildrenFrom(copy);
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
	}
}
