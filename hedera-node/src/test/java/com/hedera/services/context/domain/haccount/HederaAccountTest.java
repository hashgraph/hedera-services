package com.hedera.services.context.domain.haccount;

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

import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.hedera.services.context.domain.serdes.DomainSerdes;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JTransactionRecord;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import com.swirlds.fcmap.fclist.FCLinkedList;
import com.swirlds.fcqueue.FCQueue;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hedera.services.context.domain.serdes.DomainSerdesTest.*;
import static com.hedera.test.utils.SerdeUtils.*;
import static com.hedera.services.context.domain.haccount.HederaAccountSerializer.HEDERA_ACCOUNT_SERIALIZER;
import static com.hedera.services.context.domain.haccount.HederaAccountDeserializer.HEDERA_ACCOUNT_DESERIALIZER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
public class HederaAccountTest {
	final static String LEGACY_KEY_REPR_PATH = "src/test/resources/testfiles/legacyKey.bin";
	final static String LEGACY_ACCOUNT_REPR_PATH = "src/test/resources/testfiles/legacyAccount.bin";
	final static String CURRENT_ACCOUNT_REPR_PATH = "src/test/resources/testfiles/currentAccount.bin";

	@Test
	public void hashIsSafe() {
		// expect:
		assertDoesNotThrow(() -> new HederaAccount().hashCode());
	}

	@Test
	public void unimplementedMethodsThrow() {
		// given:
		HederaAccount account = new HederaAccount();

		// expect:
		assertThrows(UnsupportedOperationException.class, () -> account.copyFrom(null));
		assertThrows(UnsupportedOperationException.class, () -> account.copyFromExtra(null));
		assertThrows(UnsupportedOperationException.class, () -> account.diffCopyTo(null, null));
		assertThrows(UnsupportedOperationException.class, () -> account.diffCopyFrom(null, null));
	}

	@Test
	public void equalsWorksAsExpected() throws Throwable {
		// given:
		HederaAccount account = legacyAccount();

		// expect:
		assertTrue(account.equals(account));
		assertFalse(account.equals(new Object()));
		assertTrue(account.equals(new HederaAccount(account)));
	}

	@Test
	public void doesntThrowOnFcCopyOfLeafWithImmutableFcq() {
		// setup:
		AtomicReference<HederaAccount> copyRef = new AtomicReference<>();

		// given:
		HederaAccount account = new HederaAccount();
		HederaAccount accountWithImmutableFcq = (HederaAccount)account.copy();

		// expect:
		assertDoesNotThrow(() -> copyRef.set((HederaAccount)accountWithImmutableFcq.copy()));
		assertEquals(accountWithImmutableFcq, copyRef.get());
	}

	@Test
	public void copyConstructorDoesntInvokeFcqCopy() {
		// setup:
		FCQueue records = mock(FCQueue.class);

		// given:
		HederaAccount account = new HederaAccount();
		account.setRecords(records);

		// when:
		new HederaAccount(account);

		// then:
		verify(records, never()).copy(anyBoolean());
	}

	@Test
	public void fcCopyInvokesFcqCopyFalse() {
		// setup:
		FCQueue records = mock(FCQueue.class);
		given(records.copy(false)).willReturn(records);

		// given:
		HederaAccount account = new HederaAccount();
		account.setRecords(records);

		// when:
		HederaAccount copyAccount = (HederaAccount)account.copy();

		// then:
		verify(records).copy(false);
		assertEquals(account, copyAccount);
	}

	@Test
	public void legacyAndCurrentDeserializationsMatch() throws Throwable {
		// setup:
		File fLegacy = new File(LEGACY_ACCOUNT_REPR_PATH);
		File fCurrent = new File(CURRENT_ACCOUNT_REPR_PATH);
		ByteSource legacySource = Files.asByteSource(fLegacy);
		ByteSource currentSource = Files.asByteSource(fCurrent);

		// given:
		HederaAccount legacy = deOutcome(in -> HEDERA_ACCOUNT_DESERIALIZER.deserialize(in), legacySource.read());
		HederaAccount current = deOutcome(in -> HEDERA_ACCOUNT_DESERIALIZER.deserialize(in), currentSource.read());

		// expect:
		assertEquals(current, legacy);
	}

	@Test
	public void deletesRecords() {
		// setup:
		FCQueue records = mock(FCQueue.class);

		// given:
		HederaAccount account = new HederaAccount();
		account.setRecords(records);

		// when:
		account.delete();

		// then:
		verify(records).delete();
	}

	@Test
	public void usesRecordsForCopyToAndCopyToExtra() throws Exception {
		// setup:
		FCQueue records = mock(FCQueue.class);
		DomainSerdes serdes = mock(DomainSerdes.class);
		FCDataOutputStream out = mock(FCDataOutputStream.class);
		HEDERA_ACCOUNT_SERIALIZER.serdes = serdes;

		// given:
		HederaAccount account = new HederaAccount();
		account.setRecords(records);

		// when:
		account.copyTo(out);
		account.copyToExtra(out);

		// then:
		verify(records).copyTo(out);
		verify(records).copyToExtra(out);

		// cleanup:
		HEDERA_ACCOUNT_SERIALIZER.serdes = new DomainSerdes();
	}

	@Test
	public void resetsFcqUsingFcll() {
		// setup:
		JTransactionRecord earlyExpiry = new JTransactionRecord();
		earlyExpiry.setExpirationTime(1_234_567L);
		JTransactionRecord lateExpiry = new JTransactionRecord();
		lateExpiry.setExpirationTime(7_654_321L);

		// given:
		HederaAccount account = new HederaAccount();

		// when:
		account.resetRecordsToContain(asFcll(lateExpiry, earlyExpiry));
		// and:
		FCQueue<JTransactionRecord> records = account.getRecords();

		// then:
		assertEquals(2, records.size());
		assertEquals(earlyExpiry, records.poll());
		assertEquals(lateExpiry, records.poll());
	}

	@Test
	public void getsEmptyIteratorToStart() {
		// expect:
		assertFalse(new HederaAccount().recordIterator().hasNext());
	}

	@Test
	public void getsRecordIterator() {
		// given:
		HederaAccount account = new HederaAccount();
		account.resetRecordsToContain(asFcll(recordOne(), recordTwo()));

		// when:
		Iterator<JTransactionRecord> iterator = account.recordIterator();

		// then:
		assertEquals(recordOne(), iterator.next());
		assertEquals(recordTwo(), iterator.next());
		assertFalse(iterator.hasNext());
	}

	@Test
	public void getsRecordList() {
		// given:
		HederaAccount account = new HederaAccount();
		account.resetRecordsToContain(asFcll(recordOne(), recordTwo()));

		// when:
		List<JTransactionRecord> records = account.recordList();

		// then:
		assertEquals(List.of(recordOne(), recordTwo()), records);
	}

	@Test
	public void returnsMinusOneForMissingExpiry() {
		// expect:
		assertEquals(-1L, new HederaAccount().expiryOfEarliestRecord());
	}

	@Test
	public void returnsEarliestExpiry() {
		// setup:
		JTransactionRecord earlyExpiry = new JTransactionRecord();
		earlyExpiry.setExpirationTime(1_234_567L);
		JTransactionRecord lateExpiry = new JTransactionRecord();
		lateExpiry.setExpirationTime(7_654_321L);

		// given:
		HederaAccount account = new HederaAccount();
		account.resetRecordsToContain(asFcll(lateExpiry, earlyExpiry));

		// when:
		long ere = account.expiryOfEarliestRecord();

		// then:
		assertEquals(earlyExpiry.getExpirationTime(), ere);
	}

	@Test
	public void copyToDeserializes() throws Throwable {
		// given:
		HederaAccount accountIn = legacyAccount();

		// when:
		byte[] repr	= serOutcome(out -> {
			FCDataOutputStream fcOut = (FCDataOutputStream)out;
			accountIn.copyTo(fcOut);
			accountIn.copyToExtra(fcOut);
		});
		// and:
		HederaAccount accountOut = deOutcome(in -> HEDERA_ACCOUNT_DESERIALIZER.deserialize(in), repr);

		// then:
		assertEquals(accountIn, accountOut);
	}

	@Test
	public void deserializesLegacyAccount() throws Throwable {
		File f = new File(LEGACY_ACCOUNT_REPR_PATH);
		InputStream legacyAccountIn = Files.asByteSource(f).openBufferedStream();
		FCDataInputStream in = new FCDataInputStream(legacyAccountIn);
		HederaAccount loadedAccount = HederaAccount.deserialize(in);

		assertEquals(legacyAccount(), loadedAccount);
	}

	@Test
	public void indicatesNumRecords() throws Throwable {
		// given:
		HederaAccount account = legacyAccount();

		// when:
		String desc = account.toString();

		// then:
		assertTrue(desc.contains("numRecords=2"));
	}

	public static HederaAccount legacyAccount() throws Exception {
		HederaAccount account = new HederaAccountCustomizer()
				.proxy(new JAccountID(1,2, 3))
				.key(legacyKey())
				.memo("This was Mr. Bleaney's room...")
				.isSmartContract(true)
				.isDeleted(true)
				.isReceiverSigRequired(true)
				.fundsSentRecordThreshold(1_234L)
				.fundsReceivedRecordThreshold(5_432L)
				.expiry(1_234_567_890L)
				.autoRenewPeriod(666L)
				.customizing(new HederaAccount());
		account.setBalance(888L);
		account.resetRecordsToContain(asFcll(recordOne(), recordTwo()));
		return account;
	}

	public static FCLinkedList<JTransactionRecord> asFcll(JTransactionRecord... records) {
		FCLinkedList<JTransactionRecord> fcll = new FCLinkedList<>(JTransactionRecord::deserialize);
		for (JTransactionRecord record : records) {
			fcll.add(record);
		}
		return fcll;
	}

	private static JKey legacyKey() throws Exception {
		File legacyKey = new File(LEGACY_KEY_REPR_PATH);
		try (DataInputStream in = new DataInputStream(Files.asByteSource(legacyKey).openBufferedStream())) {
			return new DomainSerdes().deserializeKey(in);
		}
	}
}
