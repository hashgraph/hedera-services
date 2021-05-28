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
import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.ServicesContext;
import com.hedera.services.context.properties.PropertySources;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.records.TxnIdRecentHistory;
import com.hedera.services.sigs.factories.SigFactoryCreator;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.state.expiry.ExpiryManager;
import com.hedera.services.state.logic.NetworkCtxManager;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.stream.RecordStreamManager;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.txns.ProcessLogic;
import com.hedera.services.utils.SystemExits;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.factories.txns.PlatformTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.blob.BinaryObjectStore;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.SwirldTransaction;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.fcmap.FCMap;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;

import javax.inject.Inject;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.hedera.services.ServicesState.MERKLE_VERSION;
import static com.hedera.services.ServicesState.RELEASE_0100_VERSION;
import static com.hedera.services.ServicesState.RELEASE_0110_VERSION;
import static com.hedera.services.ServicesState.RELEASE_0120_VERSION;
import static com.hedera.services.ServicesState.RELEASE_0130_VERSION;
import static com.hedera.services.ServicesState.RELEASE_0140_VERSION;
import static com.hedera.services.ServicesState.RELEASE_070_VERSION;
import static com.hedera.services.ServicesState.RELEASE_080_VERSION;
import static com.hedera.services.ServicesState.RELEASE_090_VERSION;
import static com.hedera.services.context.SingletonContextsManager.CONTEXTS;
import static java.util.Collections.EMPTY_LIST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;


@ExtendWith(LogCaptureExtension.class)
class ServicesStateTest {
	final private AccountID nodeAccount = IdUtils.asAccount("0.0.3");

	private Consumer<MerkleNode> mockDigest;
	private Supplier<BinaryObjectStore> mockBlobStoreSupplier;
	private BinaryObjectStore blobStore;
	private Instant now = Instant.now();
	private SwirldTransaction platformTxn;
	private Address address;
	private AddressBook book;
	private AddressBook bookCopy;
	private Platform platform;
	private ProcessLogic logic;
	private PropertySources propertySources;
	private ServicesContext ctx;
	private AccountRecordsHistorian historian;
	private ExpiryManager expiryManager;
	private FCMap<MerkleEntityId, MerkleTopic> topics;
	private FCMap<MerkleEntityId, MerkleAccount> accounts;
	private FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage;
	private FCMap<MerkleEntityId, MerkleTopic> topicsCopy;
	private FCMap<MerkleEntityId, MerkleAccount> accountsCopy;
	private FCMap<MerkleBlobMeta, MerkleOptionalBlob> storageCopy;
	private FCMap<MerkleEntityId, MerkleToken> tokens;
	private FCMap<MerkleEntityId, MerkleSchedule> scheduledTxs;
	private FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociations;
	private FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociationsCopy;
	private FCMap<MerkleEntityId, MerkleToken> tokensCopy;
	private FCMap<MerkleEntityId, MerkleSchedule> scheduledTxsCopy;
	private MerkleDiskFs diskFs;
	private MerkleDiskFs diskFsCopy;
	private RecordsRunningHashLeaf runningHashLeaf;
	private RecordsRunningHashLeaf runningHashLeafCopy;
	private RunningHash runningHash;
	private Hash recordsHash;
	private ExchangeRates midnightRates;
	private SequenceNumber seqNo;
	private MerkleNetworkContext networkCtx;
	private MerkleNetworkContext networkCtxCopy;
	private NodeId self = new NodeId(false, 0);
	private RecordStreamManager recordStreamManager;
	private Map<TransactionID, TxnIdRecentHistory> txnHistories;
	private NetworkCtxManager networkCtxManager;

	@Inject
	private LogCaptor logCaptor;

	@LoggingSubject
	private ServicesState subject;

	private static final Hash EMPTY_HASH = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);

	@BeforeEach
	@SuppressWarnings("unchecked")
	private void setup() {
		CONTEXTS.clear();
		mockDigest = (Consumer<MerkleNode>) mock(Consumer.class);
		blobStore = mock(BinaryObjectStore.class);
		mockBlobStoreSupplier = (Supplier<BinaryObjectStore>) mock(Supplier.class);
		given(mockBlobStoreSupplier.get()).willReturn(blobStore);
		ServicesState.blobStoreSupplier = mockBlobStoreSupplier;
		given(blobStore.isInitializing()).willReturn(false);

		platformTxn = mock(SwirldTransaction.class);

		address = mock(Address.class);
		given(address.getMemo()).willReturn("0.0.3");
		bookCopy = mock(AddressBook.class);
		book = mock(AddressBook.class);
		given(book.copy()).willReturn(bookCopy);
		given(book.getAddress(0)).willReturn(address);
		given(book.getSize()).willReturn(1);

		logic = mock(ProcessLogic.class);
		ctx = mock(ServicesContext.class);
		given(ctx.sigFactoryCreator()).willReturn(new SigFactoryCreator());
		given(ctx.id()).willReturn(self);
		given(ctx.logic()).willReturn(logic);

		historian = mock(AccountRecordsHistorian.class);
		txnHistories = mock(Map.class);
		expiryManager = mock(ExpiryManager.class);
		recordStreamManager = mock(RecordStreamManager.class);
		networkCtxManager = mock(NetworkCtxManager.class);

		topics = mock(FCMap.class);
		tokens = mock(FCMap.class);
		tokensCopy = mock(FCMap.class);
		tokenAssociations = mock(FCMap.class);
		tokenAssociationsCopy = mock(FCMap.class);
		diskFs = mock(MerkleDiskFs.class);
		scheduledTxs = mock(FCMap.class);
		runningHashLeaf = mock(RecordsRunningHashLeaf.class);
		runningHash = mock(RunningHash.class);
		recordsHash = mock(Hash.class);
		given(runningHash.getHash()).willReturn(recordsHash);
		given(runningHashLeaf.getRunningHash()).willReturn(runningHash);

		storage = mock(FCMap.class);
		accounts = mock(FCMap.class);
		topicsCopy = mock(FCMap.class);
		storageCopy = mock(FCMap.class);
		accountsCopy = mock(FCMap.class);
		diskFsCopy = mock(MerkleDiskFs.class);
		scheduledTxsCopy = mock(FCMap.class);
		runningHashLeafCopy = mock(RecordsRunningHashLeaf.class);

		given(topics.copy()).willReturn(topicsCopy);
		given(storage.copy()).willReturn(storageCopy);
		given(accounts.copy()).willReturn(accountsCopy);
		given(tokens.copy()).willReturn(tokensCopy);
		given(tokenAssociations.copy()).willReturn(tokenAssociationsCopy);
		given(diskFs.copy()).willReturn(diskFsCopy);
		given(scheduledTxs.copy()).willReturn(scheduledTxsCopy);
		given(runningHashLeaf.copy()).willReturn(runningHashLeafCopy);

		seqNo = mock(SequenceNumber.class);
		midnightRates = mock(ExchangeRates.class);
		networkCtx = mock(MerkleNetworkContext.class);
		networkCtxCopy = mock(MerkleNetworkContext.class);
		given(networkCtx.copy()).willReturn(networkCtxCopy);
		given(networkCtx.midnightRates()).willReturn(midnightRates);
		given(networkCtx.seqNo()).willReturn(seqNo);
		given(networkCtx.getStateVersion()).willReturn(-1);
		given(ctx.networkCtx()).willReturn(networkCtx);

		propertySources = mock(PropertySources.class);

		var crypto = mock(Cryptography.class);
		platform = mock(Platform.class);
		given(platform.getSelfId()).willReturn(self);
		given(platform.getCryptography()).willReturn(crypto);

		given(ctx.platform()).willReturn(platform);
		given(ctx.recordsHistorian()).willReturn(historian);
		given(ctx.txnHistories()).willReturn(txnHistories);
		given(ctx.expiries()).willReturn(expiryManager);
		given(ctx.propertySources()).willReturn(propertySources);
		given(ctx.networkCtxManager()).willReturn(networkCtxManager);
		given(ctx.recordStreamManager()).willReturn(recordStreamManager);

		subject = new ServicesState();
	}

	@Test
	void hasExpectedMinChildCounts() {
		// given:
		subject = new ServicesState();
		// and:
		int invalidVersion = ServicesState.MERKLE_VERSION + 1;

		// expect:
		assertEquals(ServicesState.ChildIndices.NUM_070_CHILDREN, subject.getMinimumChildCount(RELEASE_070_VERSION));
		assertEquals(ServicesState.ChildIndices.NUM_080_CHILDREN, subject.getMinimumChildCount(RELEASE_080_VERSION));
		assertEquals(ServicesState.ChildIndices.NUM_090_CHILDREN, subject.getMinimumChildCount(RELEASE_090_VERSION));
		assertEquals(ServicesState.ChildIndices.NUM_0100_CHILDREN, subject.getMinimumChildCount(RELEASE_0100_VERSION));
		assertEquals(ServicesState.ChildIndices.NUM_0110_CHILDREN, subject.getMinimumChildCount(RELEASE_0110_VERSION));
		assertEquals(ServicesState.ChildIndices.NUM_0120_CHILDREN, subject.getMinimumChildCount(RELEASE_0120_VERSION));
		assertEquals(ServicesState.ChildIndices.NUM_0130_CHILDREN, subject.getMinimumChildCount(RELEASE_0130_VERSION));
		assertEquals(ServicesState.ChildIndices.NUM_0140_CHILDREN, subject.getMinimumChildCount(RELEASE_0140_VERSION));

		Throwable throwable = assertThrows(IllegalArgumentException.class,
				() -> subject.getMinimumChildCount(invalidVersion));
		assertEquals(
				String.format(ServicesState.UNSUPPORTED_VERSION_MSG_TPL, invalidVersion),
				throwable.getMessage());
	}

	@Test
	void fullArgsConstructorUpdatesContext() {
		// when:
		subject = new ServicesState(ctx, self, Collections.emptyList(), new ServicesState());

		// then:
		verify(ctx).update(subject);
	}

	@Test
	void getsNodeAccount() {
		// setup:
		subject.nodeId = self;
		subject.setChild(ServicesState.ChildIndices.ADDRESS_BOOK, book);

		// when:
		AccountID actual = subject.getNodeAccountId();

		// then:
		assertEquals(nodeAccount, actual);
	}

	@Test
	void initializesFullContextIfBlobStoreReady() {
		// setup:
		var throttling = mock(FunctionalityThrottling.class);
		var nodeInfo = mock(NodeInfo.class);

		InOrder inOrder = inOrder(ctx, txnHistories, historian, networkCtxManager, expiryManager, networkCtx);

		given(ctx.handleThrottling()).willReturn(throttling);
		given(ctx.nodeInfo()).willReturn(nodeInfo);
		given(nodeInfo.selfAccount()).willReturn(AccountID.getDefaultInstance());
		// and:
		CONTEXTS.store(ctx);

		// when:
		subject.init(platform, book);

		// then:
		inOrder.verify(ctx).setRecordsInitialHash(EMPTY_HASH);
		inOrder.verify(ctx).update(subject);
		inOrder.verify(ctx).rebuildBackingStoresIfPresent();
		inOrder.verify(ctx).rebuildStoreViewsIfPresent();
		inOrder.verify(historian).reviewExistingRecords();
		inOrder.verify(expiryManager).reviewExistingShortLivedEntities();
		inOrder.verify(networkCtxManager).setObservableFilesNotLoaded();
		inOrder.verify(networkCtxManager).loadObservableSysFilesIfNeeded();
		// and:
		assertEquals(MERKLE_VERSION, subject.networkCtx().getStateVersion());
	}

	@Test
	void doesntInitializeFilesIfStoreStillInitializing() {
		InOrder inOrder = inOrder(ctx, txnHistories, historian, networkCtxManager);

		given(blobStore.isInitializing()).willReturn(true);
		// and:
		CONTEXTS.store(ctx);

		// when:
		subject.init(platform, book);

		// then:
		inOrder.verify(ctx).update(subject);
		inOrder.verify(ctx).rebuildBackingStoresIfPresent();
		inOrder.verify(historian).reviewExistingRecords();
		inOrder.verify(networkCtxManager, never()).loadObservableSysFilesIfNeeded();
	}

	@Test
	void catchesNonProtoExceptionInExpandSigs() {
		// setup:
		var platformTxn = mock(SwirldTransaction.class);

		given(platformTxn.getContents()).willReturn(
				com.hederahashgraph.api.proto.java.Transaction.getDefaultInstance().toByteArray());
		given(ctx.lookupRetryingKeyOrder()).willThrow(IllegalStateException.class);
		// and:
		subject.ctx = ctx;

		// expect:
		assertDoesNotThrow(() -> subject.expandSignatures(platformTxn));
	}

	@Test
	void catchesProtobufParseException() {
		// setup:
		var platformTxn = mock(SwirldTransaction.class);

		given(platformTxn.getContents()).willReturn("not-a-grpc-txn".getBytes());

		// expect:
		assertDoesNotThrow(() -> subject.expandSignatures(platformTxn));
	}

	@Test
	void invokesMigrationsAsApropos() {
		// setup:
		var nodeInfo = mock(NodeInfo.class);
		given(ctx.nodeInfo()).willReturn(nodeInfo);
		given(nodeInfo.selfAccount()).willReturn(nodeAccount);
		CONTEXTS.store(ctx);
		// and:
		subject.skipDiskFsHashCheck = true;
		// and:
		subject.setChild(ServicesState.ChildIndices.TOPICS, topics);
		subject.setChild(ServicesState.ChildIndices.STORAGE, storage);
		subject.setChild(ServicesState.ChildIndices.ACCOUNTS, accounts);
		subject.setChild(ServicesState.ChildIndices.ADDRESS_BOOK, book);
		subject.setChild(ServicesState.ChildIndices.NETWORK_CTX, networkCtx);
		subject.setChild(ServicesState.ChildIndices.TOKENS, tokens);
		subject.setChild(ServicesState.ChildIndices.TOKEN_ASSOCIATIONS, tokenAssociations);
		subject.setChild(ServicesState.ChildIndices.DISK_FS, diskFs);
		subject.setChild(ServicesState.ChildIndices.SCHEDULE_TXS, scheduledTxs);
		subject.setChild(ServicesState.ChildIndices.RECORD_STREAM_RUNNING_HASH, runningHashLeaf);

		// when:
		subject.init(platform, book);
		// and when:
		given(networkCtx.getStateVersion()).willReturn(ServicesState.MERKLE_VERSION);
		subject.init(platform, book);

		// then:
		verify(diskFs, never()).checkHashesAgainstDiskContents();
		verify(diskFs, times(1)).migrateLegacyDiskFsFromV13LocFor(
				MerkleDiskFs.DISK_FS_ROOT_DIR,
				"0.0.3");
		// and:
		verify(networkCtx).updateLastScannedEntity(1000L);
	}

	@Test
	void justWarnOnFailedDiskFsMigration() {
		// setup:
		var nodeInfo = mock(NodeInfo.class);
		given(ctx.nodeInfo()).willReturn(nodeInfo);
		given(nodeInfo.selfAccount()).willReturn(nodeAccount);
		willThrow(UncheckedIOException.class).given(diskFs).migrateLegacyDiskFsFromV13LocFor(
				MerkleDiskFs.DISK_FS_ROOT_DIR, "0.0.3");
		CONTEXTS.store(ctx);
		// and:
		subject.skipDiskFsHashCheck = true;
		// and:
		subject.setChild(ServicesState.ChildIndices.TOPICS, topics);
		subject.setChild(ServicesState.ChildIndices.STORAGE, storage);
		subject.setChild(ServicesState.ChildIndices.ACCOUNTS, accounts);
		subject.setChild(ServicesState.ChildIndices.ADDRESS_BOOK, book);
		subject.setChild(ServicesState.ChildIndices.NETWORK_CTX, networkCtx);
		subject.setChild(ServicesState.ChildIndices.TOKENS, tokens);
		subject.setChild(ServicesState.ChildIndices.TOKEN_ASSOCIATIONS, tokenAssociations);
		subject.setChild(ServicesState.ChildIndices.DISK_FS, diskFs);
		subject.setChild(ServicesState.ChildIndices.SCHEDULE_TXS, scheduledTxs);
		subject.setChild(ServicesState.ChildIndices.RECORD_STREAM_RUNNING_HASH, runningHashLeaf);

		// when:
		subject.init(platform, book);
		// and when:
		given(networkCtx.getStateVersion()).willReturn(ServicesState.MERKLE_VERSION);
		subject.init(platform, book);

		// then:
		assertThat(logCaptor.warnLogs(),
				contains(Matchers.startsWith("Legacy diskFs directory not migrated, was it missing?")));
	}

	@Test
	void logsNonNullHashesFromSavedState() {
		// setup:
		var nodeInfo = mock(NodeInfo.class);
		given(ctx.nodeInfo()).willReturn(nodeInfo);
		given(nodeInfo.selfAccount()).willReturn(nodeAccount);
		CONTEXTS.store(ctx);

		// and:
		subject.setChild(ServicesState.ChildIndices.TOPICS, topics);
		subject.setChild(ServicesState.ChildIndices.STORAGE, storage);
		subject.setChild(ServicesState.ChildIndices.ACCOUNTS, accounts);
		subject.setChild(ServicesState.ChildIndices.ADDRESS_BOOK, book);
		subject.setChild(ServicesState.ChildIndices.NETWORK_CTX, networkCtx);
		subject.setChild(ServicesState.ChildIndices.TOKENS, tokens);
		subject.setChild(ServicesState.ChildIndices.TOKEN_ASSOCIATIONS, tokenAssociations);
		subject.setChild(ServicesState.ChildIndices.DISK_FS, diskFs);
		subject.setChild(ServicesState.ChildIndices.SCHEDULE_TXS, scheduledTxs);
		subject.setChild(ServicesState.ChildIndices.RECORD_STREAM_RUNNING_HASH, runningHashLeaf);

		// when:
		subject.init(platform, book);

		// then:
		InOrder inOrder = inOrder(
				scheduledTxs, runningHashLeaf, diskFs, ctx, mockDigest,
				accounts, storage, topics, tokens, tokenAssociations, networkCtx, book);
		inOrder.verify(diskFs).checkHashesAgainstDiskContents();
		inOrder.verify(ctx).setRecordsInitialHash(recordsHash);
		inOrder.verify(accounts).getHash();
		inOrder.verify(storage).getHash();
		inOrder.verify(topics).getHash();
		inOrder.verify(tokens).getHash();
		inOrder.verify(tokenAssociations).getHash();
		inOrder.verify(diskFs).getHash();
		inOrder.verify(scheduledTxs).getHash();
		inOrder.verify(networkCtx).getHash();
		inOrder.verify(book).getHash();
		inOrder.verify(runningHashLeaf).getHash();
		inOrder.verify(ctx).update(subject);
		// and:
		assertThat(
				logCaptor.infoLogs(),
				contains(
						equalTo("Init called on Services node 0 WITH Merkle saved state"),
						startsWith("[SwirldState Hashes]"),
						startsWith("Mock for MerkleNetworkContext"),
						equalTo("--> Context initialized accordingly on Services node 0")));
	}

	@Test
	void hashesPrintedAsExpected() {
		// setup:
		Hash ctxHash = new Hash("sdfysdfysdfysdfysdfysdfysdfysdfysdfysdfysdfysdfy".getBytes());
		Hash bookHash = new Hash("sdfzsdfzsdfzsdfzsdfzsdfzsdfzsdfzsdfzsdfzsdfzsdfz".getBytes());
		Hash topicRootHash = new Hash("sdfgsdfgsdfgsdfgsdfgsdfgsdfgsdfgsdfgsdfgsdfgsdfg".getBytes());
		Hash tokensRootHash = new Hash("szfgszfgszfgszfgszfgszfgszfgszfgszfgszfgszfgszfg".getBytes());
		Hash storageRootHash = new Hash("fdsafdsafdsafdsafdsafdsafdsafdsafdsafdsafdsafdsa".getBytes());
		Hash accountsRootHash = new Hash("asdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdf".getBytes());
		Hash tokenRelsRootHash = new Hash("asdhasdhasdhasdhasdhasdhasdhasdhasdhasdhasdhasdh".getBytes());
		Hash specialFileSystemHash = new Hash("123456781234567812345678123456781234567812345678".getBytes());
		Hash scheduledTxsRootHash = new Hash("qlqlqlqlqlqlllqqllqlqlqlqllqlqlqlqllqlqlqllqqlql".getBytes());

		Hash runningHashLeafHash = new Hash("qasdhasdhasdhasdhasdhasdhasdhasdhasdhasdhasdhasd".getBytes());
		RunningHash runningHash = mock(RunningHash.class);
		Hash hashInRunningHash = new Hash("ttqasdhasdhasdhasdhasdhasdhasdhasdhasdhasdhasdha".getBytes());
		// and:
		Hash overallHash = new Hash("a!dfa!dfa!dfa!dfa!dfa!dfa!dfa!dfa!dfa!dfa!dfa!df".getBytes());
		// and:
		subject.setChild(ServicesState.ChildIndices.TOPICS, topics);
		subject.setChild(ServicesState.ChildIndices.STORAGE, storage);
		subject.setChild(ServicesState.ChildIndices.ACCOUNTS, accounts);
		subject.setChild(ServicesState.ChildIndices.TOKENS, tokens);
		subject.setChild(ServicesState.ChildIndices.ADDRESS_BOOK, book);
		subject.setChild(ServicesState.ChildIndices.NETWORK_CTX, networkCtx);
		subject.setChild(ServicesState.ChildIndices.TOKEN_ASSOCIATIONS, tokenAssociations);
		subject.setChild(ServicesState.ChildIndices.DISK_FS, diskFs);
		subject.setChild(ServicesState.ChildIndices.SCHEDULE_TXS, scheduledTxs);

		subject.setChild(ServicesState.ChildIndices.RECORD_STREAM_RUNNING_HASH, runningHashLeaf);
		// and:
		var expected = String.format("[SwirldState Hashes]\n" +
						"  Overall                :: %s\n" +
						"  Accounts               :: %s\n" +
						"  Storage                :: %s\n" +
						"  Topics                 :: %s\n" +
						"  Tokens                 :: %s\n" +
						"  TokenAssociations      :: %s\n" +
						"  DiskFs                 :: %s\n" +
						"  ScheduledTxs           :: %s\n" +
						"  NetworkContext         :: %s\n" +
						"  AddressBook            :: %s\n" +
						"  RecordsRunningHashLeaf :: %s\n" +
						"    ↪ Running hash       :: %s",

				overallHash,
				accountsRootHash,
				storageRootHash,
				topicRootHash,
				tokensRootHash,
				tokenRelsRootHash,
				specialFileSystemHash,
				scheduledTxsRootHash,
				ctxHash,
				bookHash,
				runningHashLeafHash,
				hashInRunningHash);
		subject.setHash(overallHash);

		given(topics.getHash()).willReturn(topicRootHash);
		given(accounts.getHash()).willReturn(accountsRootHash);
		given(storage.getHash()).willReturn(storageRootHash);
		given(tokens.getHash()).willReturn(tokensRootHash);
		given(tokenAssociations.getHash()).willReturn(tokenRelsRootHash);
		given(networkCtx.getHash()).willReturn(ctxHash);
		given(networkCtx.toString()).willReturn("Not really a network context representation!");
		given(book.getHash()).willReturn(bookHash);
		given(diskFs.getHash()).willReturn(specialFileSystemHash);
		given(scheduledTxs.getHash()).willReturn(scheduledTxsRootHash);

		given(runningHashLeaf.getHash()).willReturn(runningHashLeafHash);
		given(runningHashLeaf.getRunningHash()).willReturn(runningHash);
		given(runningHash.getHash()).willReturn(hashInRunningHash);
		// when:
		subject.logSummary();

		// then:
		assertThat(
				logCaptor.infoLogs(),
				contains(equalTo(expected), equalTo("Not really a network context representation!")));
	}

	@Test
	void fastCopyCopiesPrimitives() {
		// setup:
		subject.setChild(ServicesState.ChildIndices.TOPICS, topics);
		subject.setChild(ServicesState.ChildIndices.STORAGE, storage);
		subject.setChild(ServicesState.ChildIndices.ACCOUNTS, accounts);
		subject.setChild(ServicesState.ChildIndices.ADDRESS_BOOK, book);
		subject.setChild(ServicesState.ChildIndices.NETWORK_CTX, networkCtx);
		subject.setChild(ServicesState.ChildIndices.TOKENS, tokens);
		subject.setChild(ServicesState.ChildIndices.TOKEN_ASSOCIATIONS, tokenAssociations);
		subject.setChild(ServicesState.ChildIndices.DISK_FS, diskFs);
		subject.setChild(ServicesState.ChildIndices.SCHEDULE_TXS, scheduledTxs);
		subject.setChild(ServicesState.ChildIndices.RECORD_STREAM_RUNNING_HASH, runningHashLeaf);
		subject.nodeId = self;
		subject.ctx = ctx;

		// when:
		ServicesState copy = subject.copy();

		// then:
		assertEquals(subject.getNumberOfChildren(), copy.getNumberOfChildren());
		assertTrue(subject.isImmutable());
		assertEquals(self, copy.nodeId);
		assertEquals(bookCopy, copy.addressBook());
		assertEquals(networkCtxCopy, copy.networkCtx());
		assertEquals(topicsCopy, copy.topics());
		assertEquals(storageCopy, copy.storage());
		assertEquals(accountsCopy, copy.accounts());
		assertSame(tokensCopy, copy.tokens());
		assertSame(tokenAssociationsCopy, copy.tokenAssociations());
		assertSame(diskFsCopy, copy.diskFs());
		assertSame(scheduledTxsCopy, copy.scheduleTxs());
		assertSame(runningHashLeafCopy, copy.runningHashLeaf());
	}

	@Test
	void noMoreIsANoop() {
		// expect:
		assertDoesNotThrow(() -> subject.noMoreTransactions());
	}

	@Test
	void sanityChecks() {
		assertEquals(ServicesState.MERKLE_VERSION, subject.getVersion());
		assertEquals(ServicesState.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	@Test
	void deleteCascadesToAllFcms() {
		// setup:
		subject.setChild(ServicesState.ChildIndices.STORAGE, storage);
		subject.setChild(ServicesState.ChildIndices.TOPICS, topics);
		subject.setChild(ServicesState.ChildIndices.ACCOUNTS, accounts);
		subject.setChild(ServicesState.ChildIndices.TOKENS, tokens);
		subject.setChild(ServicesState.ChildIndices.TOKEN_ASSOCIATIONS, tokenAssociations);
		subject.setChild(ServicesState.ChildIndices.SCHEDULE_TXS, scheduledTxs);

		// when:
		subject.release();

		// then:
		verify(storage).decrementReferenceCount();
		verify(accounts).decrementReferenceCount();
		verify(topics).decrementReferenceCount();
		verify(tokens).decrementReferenceCount();
		verify(tokenAssociations).decrementReferenceCount();
		verify(scheduledTxs).decrementReferenceCount();
	}

	@Test
	void implementsBookCopy() {
		// setup:
		subject.setChild(ServicesState.ChildIndices.ADDRESS_BOOK, book);

		// when:
		AddressBook actualCopy = subject.getAddressBookCopy();

		// then:
		assertEquals(bookCopy, actualCopy);
	}

	@Test
	void doesNothingIfNotConsensus() {
		// setup:
		subject.ctx = ctx;

		// when:
		subject.handleTransaction(1, false, now, now, platformTxn, null);

		// then:
		verify(logic, never()).incorporateConsensusTxn(platformTxn, now, 1);
	}

	@Test
	void incorporatesConsensus() {
		// setup:
		subject.ctx = ctx;

		// when:
		subject.handleTransaction(1, true, now, now, platformTxn, null);

		// then:
		verify(logic).incorporateConsensusTxn(platformTxn, now, 1);
	}

	@Test
	void expandsSigs() {
		// setup:
		ByteString mockPk = ByteString.copyFrom("not-a-real-pkPrefix".getBytes());
		ByteString mockSig = ByteString.copyFrom("not-a-real-sig".getBytes());
		com.hederahashgraph.api.proto.java.Transaction signedTxn =
				com.hederahashgraph.api.proto.java.Transaction.newBuilder()
						.setSigMap(SignatureMap.newBuilder()
								.addSigPair(SignaturePair.newBuilder()
										.setPubKeyPrefix(mockPk)
										.setEd25519(mockSig)))
						.build();
		platformTxn = PlatformTxnFactory.from(signedTxn);
		JKey key = new JEd25519Key(mockPk.toByteArray());
		SigningOrderResult<SignatureStatus> payerOrderResult = new SigningOrderResult<>(List.of(key));
		SigningOrderResult<SignatureStatus> otherOrderResult = new SigningOrderResult<>(EMPTY_LIST);
		HederaSigningOrder keyOrderer = mock(HederaSigningOrder.class);

		given(keyOrderer.keysForPayer(any(), any())).willReturn((SigningOrderResult) payerOrderResult);
		given(keyOrderer.keysForOtherParties(any(), any())).willReturn((SigningOrderResult) otherOrderResult);
		given(ctx.lookupRetryingKeyOrder()).willReturn(keyOrderer);

		// and:
		subject.ctx = ctx;

		// when:
		subject.expandSignatures(platformTxn);

		// then:
		assertEquals(1, platformTxn.getSignatures().size());
		assertEquals(mockPk, ByteString.copyFrom(platformTxn.getSignatures().get(0).getExpandedPublicKeyDirect()));
		verify(ctx).sigFactoryCreator();
	}

	@Test
	void expandsSigsWithSignedTransactionBytes() {
		// setup:
		ByteString mockPk = ByteString.copyFrom("not-a-real-pkPrefix".getBytes());
		ByteString mockSig = ByteString.copyFrom("not-a-real-sig".getBytes());
		SignatureMap signMap = SignatureMap.newBuilder()
				.addSigPair(SignaturePair.newBuilder()
						.setPubKeyPrefix(mockPk)
						.setEd25519(mockSig)).build();
		SignedTransaction signedTxn = SignedTransaction.newBuilder().setSigMap(signMap).build();
		com.hederahashgraph.api.proto.java.Transaction txn =
				com.hederahashgraph.api.proto.java.Transaction.newBuilder()
						.setSignedTransactionBytes(signedTxn.toByteString())
						.build();
		platformTxn = PlatformTxnFactory.from(txn);
		JKey key = new JEd25519Key(mockPk.toByteArray());
		SigningOrderResult<SignatureStatus> payerOrderResult = new SigningOrderResult<>(List.of(key));
		SigningOrderResult<SignatureStatus> otherOrderResult = new SigningOrderResult<>(EMPTY_LIST);
		HederaSigningOrder keyOrderer = mock(HederaSigningOrder.class);

		given(keyOrderer.keysForPayer(any(), any())).willReturn((SigningOrderResult) payerOrderResult);
		given(keyOrderer.keysForOtherParties(any(), any())).willReturn((SigningOrderResult) otherOrderResult);
		given(ctx.lookupRetryingKeyOrder()).willReturn(keyOrderer);

		// and:
		subject.ctx = ctx;

		// when:
		subject.expandSignatures(platformTxn);

		// then:
		assertEquals(1, platformTxn.getSignatures().size());
		assertEquals(mockPk, ByteString.copyFrom(platformTxn.getSignatures().get(0).getExpandedPublicKeyDirect()));
	}

	@AfterEach
	void cleanup() {
		CONTEXTS.clear();
		ServicesState.blobStoreSupplier = BinaryObjectStore::getInstance;
	}
}
