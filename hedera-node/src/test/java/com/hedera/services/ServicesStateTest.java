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
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.context.ServicesContext;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.context.properties.PropertySources;
import com.hedera.services.legacy.logic.ApplicationConstants;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.txns.ProcessLogic;
import com.hedera.services.utils.SystemExits;
import com.hedera.test.factories.txns.PlatformTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.Transaction;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import static java.util.Collections.EMPTY_LIST;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import static com.hedera.services.context.SingletonContextsManager.CONTEXTS;

@RunWith(JUnitPlatform.class)
class ServicesStateTest {
	Instant now = Instant.now();
	Transaction platformTxn;
	Address address;
	AddressBook book;
	AddressBook bookCopy;
	Platform platform;
	ProcessLogic logic;
	PropertySources propertySources;
	ServicesContext ctx;
	FCMap<MerkleEntityId, MerkleTopic> topics;
	FCMap<MerkleEntityId, MerkleAccount> accounts;
	FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage;
	FCMap<MerkleEntityId, MerkleTopic> topicsCopy;
	FCMap<MerkleEntityId, MerkleAccount> accountsCopy;
	FCMap<MerkleBlobMeta, MerkleOptionalBlob> storageCopy;
	ExchangeRates midnightRates;
	SequenceNumber seqNo;
	MerkleNetworkContext networkCtx;
	MerkleNetworkContext networkCtxCopy;
	NodeId self = new NodeId(false, 1);
	SerializableDataInputStream in;
	SerializableDataOutputStream out;
	SystemExits systemExits;

	ServicesState subject;

	@BeforeEach
	private void setup() {
		CONTEXTS.clear();

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

		topics = mock(FCMap.class);
		storage = mock(FCMap.class);
		accounts = mock(FCMap.class);
		topicsCopy = mock(FCMap.class);
		storageCopy = mock(FCMap.class);
		accountsCopy = mock(FCMap.class);
		given(topics.copy()).willReturn(topicsCopy);
		given(storage.copy()).willReturn(storageCopy);
		given(accounts.copy()).willReturn(accountsCopy);

		seqNo = mock(SequenceNumber.class);
		midnightRates = mock(ExchangeRates.class);
		networkCtx = mock(MerkleNetworkContext.class);
		networkCtxCopy = mock(MerkleNetworkContext.class);
		given(networkCtx.copy()).willReturn(networkCtxCopy);
		given(networkCtx.midnightRates()).willReturn(midnightRates);
		given(networkCtx.seqNo()).willReturn(seqNo);

		propertySources = mock(PropertySources.class);

		platform = mock(Platform.class);
		given(platform.getSelfId()).willReturn(self);

		given(ctx.platform()).willReturn(platform);
		given(ctx.propertySources()).willReturn(propertySources);

		systemExits = mock(SystemExits.class);

		subject = new ServicesState();
	}

	@Test
	public void getsNodeAccount() {
		// setup:
		subject.nodeId = self;
		subject.setChild(ServicesState.ADDRESS_BOOK_CHILD_INDEX, book);

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
		assertEquals(book, subject.addressBook());
		assertEquals(self, actualCtx.id());
		assertEquals(platform, actualCtx.platform());
		assertEquals(ApplicationConstants.HEDERA_START_SEQUENCE, subject.networkCtx().seqNo().current());
	}

	@Test
	public void copyFromStateThrows() {
		// expect:
		assertThrows(UnsupportedOperationException.class, () -> subject.copyFrom(subject));
	}

	@Test
	public void fastCopyCopiesPrimitives() {
		// setup:
		subject.setChild(ServicesState.TOPICS_CHILD_INDEX, topics);
		subject.setChild(ServicesState.STORAGE_CHILD_INDEX, storage);
		subject.setChild(ServicesState.ACCOUNTS_CHILD_INDEX, accounts);
		subject.setChild(ServicesState.ADDRESS_BOOK_CHILD_INDEX, book);
		subject.setChild(ServicesState.NETWORK_CTX_CHILD_INDEX, networkCtx);
		subject.nodeId = self;

		// when:
		ServicesState copy = (ServicesState)subject.copy();

		// then:
		assertTrue(copy.isImmutable());
		assertEquals(self, copy.nodeId);
		assertEquals(bookCopy, copy.addressBook());
		assertEquals(networkCtxCopy, copy.networkCtx());
		assertEquals(topicsCopy, copy.topics());
		assertEquals(storageCopy, copy.storage());
		assertEquals(accountsCopy, copy.accounts());
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
		assertThrows(UnsupportedOperationException.class, () -> subject.copyTo(null));
		assertThrows(UnsupportedOperationException.class, () -> subject.copyToExtra(null));
	}

	@Test
	public void deletesCascadeToStorage() {
		// setup:
		subject.setChild(ServicesState.STORAGE_CHILD_INDEX, storage);

		// when:
		subject.delete();

		// then:
		verify(storage).delete();
	}

	@Test
	public void copiesFromExtraCorrectly() throws IOException {
		// setup:
		SerializableDataInputStream in = mock(SerializableDataInputStream.class);
		InOrder inOrder = inOrder(in, topics, storage, accounts, bookCopy);
		ServicesState.legacyTmpBookSupplier = () -> bookCopy;
		// and:
		subject.setChild(ServicesState.TOPICS_CHILD_INDEX, topics);
		subject.setChild(ServicesState.STORAGE_CHILD_INDEX, storage);
		subject.setChild(ServicesState.ACCOUNTS_CHILD_INDEX, accounts);

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
		subject.setChild(ServicesState.TOPICS_CHILD_INDEX, topics);
		subject.setChild(ServicesState.STORAGE_CHILD_INDEX, storage);
		subject.setChild(ServicesState.ACCOUNTS_CHILD_INDEX, accounts);
		subject.setChild(ServicesState.ADDRESS_BOOK_CHILD_INDEX, book);
		subject.setChild(ServicesState.NETWORK_CTX_CHILD_INDEX, networkCtx);
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
		subject.setChild(ServicesState.ADDRESS_BOOK_CHILD_INDEX, book);

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

		given(keyOrderer.keysForPayer(any(), any())).willReturn((SigningOrderResult)payerOrderResult);
		given(keyOrderer.keysForOtherParties(any(), any())).willReturn((SigningOrderResult)otherOrderResult);
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
	private void cleanup() {
		CONTEXTS.clear();
	}
}
