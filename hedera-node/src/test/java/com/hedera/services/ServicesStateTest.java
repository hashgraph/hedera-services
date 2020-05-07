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
import com.hedera.services.context.HederaNodeContext;
import com.hedera.services.context.PrimitiveContext;
import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hedera.services.context.properties.PropertySources;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.txns.ProcessLogic;
import com.hedera.services.utils.SystemExits;
import com.hedera.test.factories.txns.PlatformTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hedera.services.legacy.core.MapKey;
import com.hedera.services.legacy.core.StorageKey;
import com.hedera.services.legacy.core.StorageValue;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.legacy.services.context.primitives.ExchangeRateSetWrapper;
import com.hedera.services.legacy.services.context.primitives.SequenceNumber;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.Transaction;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static java.util.Collections.EMPTY_LIST;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
	PrimitiveContext primitives;
	ProcessLogic logic;
	PropertySources propertySources;
	HederaNodeContext ctx;
	FCMap<MapKey, HederaAccount> accounts;
	FCMap<StorageKey, StorageValue> storage;
	NodeId self = new NodeId(false, 1);
	FCDataInputStream in;
	FCDataOutputStream out;
	SystemExits systemExits;

	ServicesState subject;

	@BeforeEach
	private void setup() {
		CONTEXTS.clear();

		out = mock(FCDataOutputStream.class);
		in = mock(FCDataInputStream.class);
		platformTxn = mock(Transaction.class);

		address = mock(Address.class);
		given(address.getMemo()).willReturn("0.0.3");
		bookCopy = mock(AddressBook.class);
		book = mock(AddressBook.class);
		given(book.copy()).willReturn(bookCopy);
		given(book.getAddress(1)).willReturn(address);

		logic = mock(ProcessLogic.class);
		ctx = mock(HederaNodeContext.class);
		given(ctx.id()).willReturn(self);
		given(ctx.logic()).willReturn(logic);

		storage = mock(FCMap.class);
		accounts = mock(FCMap.class);
		propertySources = mock(PropertySources.class);

		platform = mock(Platform.class);
		given(platform.getSelfId()).willReturn(self);

		given(ctx.platform()).willReturn(platform);
		given(ctx.propertySources()).willReturn(propertySources);

		primitives = mock(PrimitiveContext.class);
		given(primitives.getStorage()).willReturn(storage);
		given(primitives.getAccounts()).willReturn(accounts);
		given(primitives.getAddressBook()).willReturn(book);

		systemExits = mock(SystemExits.class);

		subject = new ServicesState();
	}

	@Test
	public void getsNodeAccount() {
		// setup:
		subject.nodeId = self;
		subject.primitives = primitives;

		// when:
		AccountID actual = subject.getNodeAccountId();

		// then:
		assertEquals(IdUtils.asAccount("0.0.3"), actual);
	}

	@Test
	public void failsFastOnReinitializationAttempt() {
		// setup:
		subject.primitives = primitives;
		subject.systemExits = systemExits;

		// given:
		CONTEXTS.store(ctx);

		// when:
		subject.init(platform, book);

		// then:
		verify(systemExits).fail(1);
	}

	@Test
	public void initsAsExpected() {
		// setup:
		subject.primitives = primitives;

		// when:
		subject.init(platform, book);

		// then:
		HederaNodeContext actualCtx = CONTEXTS.lookup(self.getId());
		// and:
		assertFalse(primitives == subject.primitives);
		assertEquals(book, subject.primitives.getAddressBook());
		assertEquals(self, actualCtx.id());
		assertEquals(platform, actualCtx.platform());
	}

	@Test
	public void copyFromRestoresCtxFromSavedState() throws Exception {
		// setup:
		subject.ctx = ctx;
		subject.nodeId = self;
		subject.primitives = primitives;

		// when:
		subject.copyFrom(in);

		// then:
		verify(primitives).copyFrom(in);
		// and:
		HederaNodeContext actualCtx = CONTEXTS.lookup(self.getId());
		assertEquals(platform, actualCtx.platform());
		assertEquals(propertySources, actualCtx.propertySources());
	}

	@Test
	public void copyFromExtraDelegates() throws Exception {
		// setup:
		subject.ctx = ctx;
		subject.nodeId = self;
		subject.primitives = primitives;

		// when:
		subject.copyFromExtra(in);

		// then:
		verify(primitives).copyFromExtra(in);
	}

	@Test
	public void copyToExtraDelegates() throws Exception {
		// setup:
		subject.primitives = primitives;

		// when:
		subject.copyToExtra(out);

		// then:
		verify(primitives).copyToExtra(out);
	}

	@Test
	public void gettersDelegate() {
		// setup:
		subject.primitives = primitives;

		// expect:
		assertEquals(accounts, subject.getAccountMap());
		assertEquals(storage, subject.getStorageMap());
	}

	@Test
	public void copyFromStateThrows() {
		// expect:
		assertThrows(UnsupportedOperationException.class, () -> subject.copyFrom(subject));
	}

	@Test
	public void fastCopyCopiesPrimitives() {
		// setup:
		primitives = new PrimitiveContext(
				0,
				Instant.now(),
				book,
				mock(SequenceNumber.class),
				mock(ExchangeRateSetWrapper.class),
				accounts,
				storage,
				mock(FCMap.class));
		subject.primitives = primitives;
		subject.nodeId = self;

		// when:
		ServicesState copy = (ServicesState)subject.copy();

		// then:
		assertEquals(self, copy.nodeId);
		assertFalse(primitives == copy.primitives);
	}

	@Test
	public void noMoreIsANoop() {
		// expect:
		assertDoesNotThrow(() -> subject.noMoreTransactions());
	}

	@Test
	public void copyToDelegates() throws Exception {
		// setup:
		subject.primitives = primitives;

		// when:
		subject.copyTo(out);

		// then:
		verify(primitives).copyTo(out);
	}

	@Test
	public void deletesCascadeToStorage() {
		// setup:
		subject.primitives = primitives;

		// when:
		subject.delete();

		// then:
		verify(storage).delete();
	}

	@Test
	public void implementsBookCopy() {
		// setup:
		subject.primitives = primitives;

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
