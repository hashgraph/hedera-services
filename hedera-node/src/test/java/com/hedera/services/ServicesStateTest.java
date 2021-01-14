package com.hedera.services;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.context.ServicesContext;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.records.TxnIdRecentHistory;
import com.hedera.services.state.initialization.SystemFilesManager;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.context.properties.PropertySources;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.txns.ProcessLogic;
import com.hedera.services.utils.SystemExits;
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
import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.hedera.services.context.SingletonContextsManager.CONTEXTS;
import static java.util.Collections.EMPTY_LIST;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
class ServicesStateTest {
	Consumer<MerkleNode> mockDigest;
	Supplier<BinaryObjectStore> mockBlobStoreSupplier;
	BinaryObjectStore blobStore;
	Instant now = Instant.now();
	Transaction platformTxn;
	Address address;
	AddressBook book;
	AddressBook bookCopy;
	Platform platform;
	ProcessLogic logic;
	PropertySources propertySources;
	ServicesContext ctx;
	AccountRecordsHistorian historian;
	FCMap<MerkleEntityId, MerkleTopic> topics;
	FCMap<MerkleEntityId, MerkleAccount> accounts;
	FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage;
	FCMap<MerkleEntityId, MerkleTopic> topicsCopy;
	FCMap<MerkleEntityId, MerkleAccount> accountsCopy;
	FCMap<MerkleBlobMeta, MerkleOptionalBlob> storageCopy;
	FCMap<MerkleEntityId, MerkleToken> tokens;
	FCMap<MerkleEntityId, MerkleSchedule> scheduledTxs;
	FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociations;
	FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociationsCopy;
	FCMap<MerkleEntityId, MerkleToken> tokensCopy;
	FCMap<MerkleEntityId, MerkleSchedule> scheduledTxsCopy;
	MerkleDiskFs diskFs;
	MerkleDiskFs diskFsCopy;
	ExchangeRates midnightRates;
	SequenceNumber seqNo;
	MerkleNetworkContext networkCtx;
	MerkleNetworkContext networkCtxCopy;
	NodeId self = new NodeId(false, 1);
	SerializableDataInputStream in;
	SerializableDataOutputStream out;
	SystemExits systemExits;
	SystemFilesManager systemFilesManager;
	Map<TransactionID, TxnIdRecentHistory> txnHistories;

	ServicesState subject;

	@BeforeEach
	private void setup() {
		CONTEXTS.clear();
		mockDigest = (Consumer<MerkleNode>)mock(Consumer.class);
		ServicesState.merkleDigest = mockDigest;
		blobStore = mock(BinaryObjectStore.class);
		mockBlobStoreSupplier = (Supplier<BinaryObjectStore>) mock(Supplier.class);
		given(mockBlobStoreSupplier.get()).willReturn(blobStore);
		ServicesState.blobStoreSupplier = mockBlobStoreSupplier;
		given(blobStore.isInitializing()).willReturn(false);

		out = mock(SerializableDataOutputStream.class);
		in = mock(SerializableDataInputStream.class);
		platformTxn = mock(Transaction.class);

		address = mock(Address.class);
		given(address.getMemo()).willReturn("0.0.3");
		bookCopy = mock(AddressBook.class);
		book = mock(AddressBook.class);
		given(book.copy()).willReturn(bookCopy);
		given(book.getAddress(1)).willReturn(address);

		logic = mock(ProcessLogic.class);
		ctx = mock(ServicesContext.class);
		given(ctx.id()).willReturn(self);
		given(ctx.logic()).willReturn(logic);

		systemFilesManager = mock(SystemFilesManager.class);
		historian = mock(AccountRecordsHistorian.class);
		txnHistories = mock(Map.class);

		topics = mock(FCMap.class);
		tokens = mock(FCMap.class);
		tokensCopy = mock(FCMap.class);
		tokenAssociations = mock(FCMap.class);
		tokenAssociationsCopy = mock(FCMap.class);
		diskFs = mock(MerkleDiskFs.class);
		scheduledTxs = mock(FCMap.class);

		storage = mock(FCMap.class);
		accounts = mock(FCMap.class);
		topicsCopy = mock(FCMap.class);
		storageCopy = mock(FCMap.class);
		accountsCopy = mock(FCMap.class);
		diskFsCopy = mock(MerkleDiskFs.class);
		scheduledTxsCopy = mock(FCMap.class);
		given(topics.copy()).willReturn(topicsCopy);
		given(storage.copy()).willReturn(storageCopy);
		given(accounts.copy()).willReturn(accountsCopy);
		given(tokens.copy()).willReturn(tokensCopy);
		given(tokenAssociations.copy()).willReturn(tokenAssociationsCopy);
		given(diskFs.copy()).willReturn(diskFsCopy);
		given(scheduledTxs.copy()).willReturn(scheduledTxsCopy);

		seqNo = mock(SequenceNumber.class);
		midnightRates = mock(ExchangeRates.class);
		networkCtx = mock(MerkleNetworkContext.class);
		networkCtxCopy = mock(MerkleNetworkContext.class);
		given(networkCtx.copy()).willReturn(networkCtxCopy);
		given(networkCtx.midnightRates()).willReturn(midnightRates);
		given(networkCtx.seqNo()).willReturn(seqNo);

		propertySources = mock(PropertySources.class);

		var crypto = mock(Cryptography.class);
		platform = mock(Platform.class);
		given(platform.getSelfId()).willReturn(self);
		given(platform.getCryptography()).willReturn(crypto);

		given(ctx.platform()).willReturn(platform);
		given(ctx.recordsHistorian()).willReturn(historian);
		given(ctx.txnHistories()).willReturn(txnHistories);
		given(ctx.propertySources()).willReturn(propertySources);
		given(ctx.systemFilesManager()).willReturn(systemFilesManager);

		systemExits = mock(SystemExits.class);

		subject = new ServicesState();
	}


	@Test
	void ensuresNonNullTokenFcmsAfterReadingFromLegacySavedState() {
		// when:
		subject.initialize(null);

		// then:
		assertNotNull(subject.tokens());
		assertNotNull(subject.tokenAssociations());
		assertNotNull(subject.diskFs());
		// and:
		assertTrue(subject.skipDiskFsHashCheck);
	}

	@Test
	public void hasExpectedMinChildCounts() {
		// given:
		subject = new ServicesState(ctx, self, Collections.emptyList());

		// expect:
		assertEquals(ServicesState.ChildIndices.NUM_070_CHILDREN, subject.getMinimumChildCount(1));
		assertEquals(ServicesState.ChildIndices.NUM_080_CHILDREN, subject.getMinimumChildCount(2));
		assertEquals(ServicesState.ChildIndices.NUM_090_CHILDREN, subject.getMinimumChildCount(3));
		assertEquals(ServicesState.ChildIndices.NUM_0100_CHILDREN, subject.getMinimumChildCount(4));
	}

	@Test
	public void fullArgsConstructorUpdatesContext() {
		// when:
		subject = new ServicesState(ctx, self, Collections.emptyList());

		// then:
		verify(ctx).update(subject);
	}

	@Test
	public void getsNodeAccount() {
		// setup:
		subject.nodeId = self;
		subject.setChild(ServicesState.ChildIndices.ADDRESS_BOOK, book);

		// when:
		AccountID actual = subject.getNodeAccountId();

		// then:
		assertEquals(IdUtils.asAccount("0.0.3"), actual);
	}

	@Test
	public void initsAsExpected() {
		// when:
		subject.init(platform, book);

		// then:
		ServicesContext actualCtx = CONTEXTS.lookup(self.getId());
		// and:
		assertFalse(subject.isImmutable());
		assertNotNull(subject.topics());
		assertNotNull(subject.storage());
		assertNotNull(subject.accounts());
		assertNotNull(subject.tokens());
		assertNotNull(subject.scheduleTxs());
		assertEquals(book, subject.addressBook());
		assertEquals(self, actualCtx.id());
		assertEquals(platform, actualCtx.platform());
		assertEquals(1001L, subject.networkCtx().seqNo().current());
		// and:
		verify(mockDigest, never()).accept(any());
	}

	@Test
	public void initializesContext() {
		InOrder inOrder = inOrder(ctx, txnHistories, historian, systemFilesManager);

		given(ctx.nodeAccount()).willReturn(AccountID.getDefaultInstance());
		// and:
		CONTEXTS.store(ctx);

		// when:
		subject.init(platform, book);

		// then:
		inOrder.verify(ctx).nodeAccount();
		inOrder.verify(ctx).update(subject);
		inOrder.verify(ctx).rebuildBackingStoresIfPresent();
		inOrder.verify(historian).reviewExistingRecords();
		inOrder.verify(systemFilesManager).loadAllSystemFiles();
	}

	@Test
	public void doesntInitializeFilesIfStoreStillInitializing() {
		InOrder inOrder = inOrder(ctx, txnHistories, historian, systemFilesManager);

		given(blobStore.isInitializing()).willReturn(true);
		given(ctx.nodeAccount()).willReturn(AccountID.getDefaultInstance());
		// and:
		CONTEXTS.store(ctx);

		// when:
		subject.init(platform, book);

		// then:
		inOrder.verify(ctx).nodeAccount();
		inOrder.verify(ctx).update(subject);
		inOrder.verify(ctx).rebuildBackingStoresIfPresent();
		inOrder.verify(historian).reviewExistingRecords();
		inOrder.verify(systemFilesManager, never()).loadAllSystemFiles();
	}

	@Test
	public void catchesNonProtoExceptionInExpandSigs() {
		// setup:
		var platformTxn = mock(Transaction.class);

		given(platformTxn.getContents()).willReturn(
				com.hederahashgraph.api.proto.java.Transaction.getDefaultInstance().toByteArray());
		given(ctx.lookupRetryingKeyOrder()).willThrow(IllegalStateException.class);
		// and:
		subject.ctx = ctx;

		// expect:
		assertDoesNotThrow(() -> subject.expandSignatures(platformTxn));
	}

	@Test
	public void catchesProtobufParseException() {
		// setup:
		var platformTxn = mock(Transaction.class);

		given(platformTxn.getContents()).willReturn("not-a-grpc-txn".getBytes());

		// expect:
		assertDoesNotThrow(() -> subject.expandSignatures(platformTxn));
	}

	@Test
	public void skipsHashCheckIfNotAppropriate() {
		// setup:
		var mockLog = mock(Logger.class);
		ServicesMain.log = mockLog;
		given(ctx.nodeAccount()).willReturn(AccountID.getDefaultInstance());
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

		// when:
		subject.init(platform, book);

		// then:
		verify(diskFs, never()).checkHashesAgainstDiskContents();

		// cleanup:
		ServicesMain.log = LogManager.getLogger(ServicesMain.class);
		ServicesState.merkleDigest = CryptoFactory.getInstance()::digestTreeSync;
	}

	@Test
	public void logsNonNullHashesFromSavedState() {
		// setup:
		var mockLog = mock(Logger.class);
		ServicesMain.log = mockLog;
		given(ctx.nodeAccount()).willReturn(AccountID.getDefaultInstance());
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

		// when:
		subject.init(platform, book);

		// then:
		InOrder inOrder = inOrder(scheduledTxs, diskFs, ctx, mockDigest, accounts, storage, topics,
				tokens, tokenAssociations, networkCtx, book, mockLog);
		inOrder.verify(diskFs).setFsBaseDir(any());
		inOrder.verify(ctx).nodeAccount();
		inOrder.verify(diskFs).setFsNodeScopedDir(any());
		inOrder.verify(diskFs).checkHashesAgainstDiskContents();
		inOrder.verify(mockDigest).accept(subject);
		inOrder.verify(accounts).getHash();
		inOrder.verify(storage).getHash();
		inOrder.verify(topics).getHash();
		inOrder.verify(tokens).getHash();
		inOrder.verify(tokenAssociations).getHash();
		inOrder.verify(diskFs).getHash();
		inOrder.verify(scheduledTxs).getHash();
		inOrder.verify(networkCtx).getHash();
		inOrder.verify(book).getHash();
		inOrder.verify(mockLog).info(argThat((String s) -> s.startsWith("[SwirldState Hashes]")));
		inOrder.verify(ctx).update(subject);

		// cleanup:
		ServicesMain.log = LogManager.getLogger(ServicesMain.class);
		ServicesState.merkleDigest = CryptoFactory.getInstance()::digestTreeSync;
	}

	@Test
	public void hashesPrintedAsExpected() {
		// setup:
		var mockLog = mock(Logger.class);
		ServicesMain.log = mockLog;
		Hash ctxHash = new Hash("sdfysdfysdfysdfysdfysdfysdfysdfysdfysdfysdfysdfy".getBytes());
		Hash bookHash = new Hash("sdfzsdfzsdfzsdfzsdfzsdfzsdfzsdfzsdfzsdfzsdfzsdfz".getBytes());
		Hash topicRootHash = new Hash("sdfgsdfgsdfgsdfgsdfgsdfgsdfgsdfgsdfgsdfgsdfgsdfg".getBytes());
		Hash tokensRootHash = new Hash("szfgszfgszfgszfgszfgszfgszfgszfgszfgszfgszfgszfg".getBytes());
		Hash storageRootHash = new Hash("fdsafdsafdsafdsafdsafdsafdsafdsafdsafdsafdsafdsa".getBytes());
		Hash accountsRootHash = new Hash("asdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdfasdf".getBytes());
		Hash tokenRelsRootHash = new Hash("asdhasdhasdhasdhasdhasdhasdhasdhasdhasdhasdhasdh".getBytes());
		Hash specialFileSystemHash = new Hash("123456781234567812345678123456781234567812345678".getBytes());
		Hash scheduledTxsRootHash = new Hash("qlqlqlqlqlqlllqqllqlqlqlqllqlqlqlqllqlqlqllqqlql".getBytes());

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

		// and:
		var expected = String.format("[SwirldState Hashes]\n" +
				"  Overall           :: %s\n" +
				"  Accounts          :: %s\n" +
				"  Storage           :: %s\n" +
				"  Topics            :: %s\n" +
				"  Tokens            :: %s\n" +
				"  TokenAssociations :: %s\n" +
				"  DiskFs            :: %s\n" +
				"  ScheduledTxs      :: %s\n" +
				"  NetworkContext    :: %s\n" +
				"  AddressBook       :: %s",
				overallHash,
				accountsRootHash,
				storageRootHash,
				topicRootHash,
				tokensRootHash,
				tokenRelsRootHash,
				specialFileSystemHash,
				scheduledTxsRootHash,
				ctxHash,
				bookHash);
		subject.setHash(overallHash);

		given(topics.getHash()).willReturn(topicRootHash);
		given(accounts.getHash()).willReturn(accountsRootHash);
		given(storage.getHash()).willReturn(storageRootHash);
		given(tokens.getHash()).willReturn(tokensRootHash);
		given(tokenAssociations.getHash()).willReturn(tokenRelsRootHash);
		given(networkCtx.getHash()).willReturn(ctxHash);
		given(book.getHash()).willReturn(bookHash);
		given(diskFs.getHash()).willReturn(specialFileSystemHash);
		given(scheduledTxs.getHash()).willReturn(scheduledTxsRootHash);

		// when:
		subject.printHashes();

		// then:
		verify(mockLog).info(expected);

		// cleanup:
		ServicesMain.log = LogManager.getLogger(ServicesMain.class);
	}

	@Test
	public void fastCopyCopiesPrimitives() {
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
		subject.nodeId = self;
		subject.ctx = ctx;

		// when:
		ServicesState copy = (ServicesState) subject.copy();

		// then:
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
	}

	@Test
	public void noMoreIsANoop() {
		// expect:
		assertDoesNotThrow(() -> subject.noMoreTransactions());
	}

	@Test
	public void sanityChecks() {
		assertEquals(ServicesState.MERKLE_VERSION, subject.getVersion());
		assertEquals(ServicesState.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	@Test
	public void deleteCascadesToAllFcms() {
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
	public void copiesFromExtraCorrectly() throws IOException {
		// setup:
		SerializableDataInputStream in = mock(SerializableDataInputStream.class);
		InOrder inOrder = inOrder(in, topics, storage, accounts, bookCopy);
		ServicesState.legacyTmpBookSupplier = () -> bookCopy;
		// and:
		subject.setChild(ServicesState.ChildIndices.TOPICS, topics);
		subject.setChild(ServicesState.ChildIndices.STORAGE, storage);
		subject.setChild(ServicesState.ChildIndices.ACCOUNTS, accounts);

		// when:
		subject.copyFromExtra(in);

		// then:
		inOrder.verify(in).readLong();
		inOrder.verify(bookCopy).copyFromExtra(in);
		inOrder.verify(accounts).copyFromExtra(in);
		inOrder.verify(storage).copyFromExtra(in);
		inOrder.verify(topics).copyFromExtra(in);
	}

	@Test
	public void copiesFromCorrectly() throws IOException {
		// setup:
		SerializableDataInputStream in = mock(SerializableDataInputStream.class);
		InOrder inOrder = inOrder(in, topics, storage, accounts, bookCopy, networkCtx, seqNo, midnightRates);
		ServicesState.legacyTmpBookSupplier = () -> bookCopy;
		// and:
		subject.ctx = ctx;
		subject.nodeId = self;
		subject.setChild(ServicesState.ChildIndices.TOPICS, topics);
		subject.setChild(ServicesState.ChildIndices.STORAGE, storage);
		subject.setChild(ServicesState.ChildIndices.ACCOUNTS, accounts);
		subject.setChild(ServicesState.ChildIndices.ADDRESS_BOOK, book);
		subject.setChild(ServicesState.ChildIndices.NETWORK_CTX, networkCtx);
		// and:
		var lastHandleTime = Instant.now();

		given(in.readInstant()).willReturn(lastHandleTime);
		given(in.readBoolean()).willReturn(true);

		// when:
		subject.copyFrom(in);

		// then:
		inOrder.verify(in).readLong();
		inOrder.verify(seqNo).deserialize(in);
		inOrder.verify(bookCopy).copyFrom(in);
		inOrder.verify(accounts).copyFrom(in);
		inOrder.verify(storage).copyFrom(in);
		inOrder.verify(in).readBoolean();
		inOrder.verify(midnightRates).deserialize(in, ExchangeRates.MERKLE_VERSION);
		inOrder.verify(in).readBoolean();
		inOrder.verify(in).readInstant();
		inOrder.verify(networkCtx).setConsensusTimeOfLastHandledTxn(lastHandleTime);
		inOrder.verify(topics).copyFrom(in);
	}

	@Test
	public void implementsBookCopy() {
		// setup:
		subject.setChild(ServicesState.ChildIndices.ADDRESS_BOOK, book);

		// when:
		AddressBook actualCopy = subject.getAddressBookCopy();

		// then:
		assertEquals(bookCopy, actualCopy);
	}

	@Test
	public void doesNothingIfNotConsensus() {
		// setup:
		subject.ctx = ctx;

		// when:
		subject.handleTransaction(
				1, false, now, now, platformTxn, null);

		// then:
		verify(logic, never()).incorporateConsensusTxn(platformTxn, now, 1);
	}

	@Test
	public void incorporatesConsensus() {
		// setup:
		subject.ctx = ctx;

		// when:
		subject.handleTransaction(
				1, true, now, now, platformTxn, null);

		// then:
		verify(logic).incorporateConsensusTxn(platformTxn, now, 1);
	}

	@Test
	public void expandsSigs() {
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
	}

	@Test
	public void expandsSigsWithSignedTransactionBytes() throws InvalidProtocolBufferException {
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
	public void cleanup() {
		CONTEXTS.clear();
		ServicesState.merkleDigest = CryptoFactory.getInstance()::digestTreeSync;
		ServicesState.blobStoreSupplier = BinaryObjectStore::getInstance;
	}
}
