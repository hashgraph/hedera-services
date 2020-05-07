package com.hedera.services.context;

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

import com.hedera.services.legacy.services.context.primitives.ExchangeRateSetWrapper;
import com.hedera.services.legacy.services.context.primitives.SequenceNumber;
import com.swirlds.common.AddressBook;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
public class PrimitiveContextTest {
	private FCMap storage;
	private FCMap accounts;
	private FCMap topics;
	private InOrder inOrder;
	private AddressBook addressBook;
	private SequenceNumber seqNo;
	private ExchangeRateSetWrapper exchangeRateSets;
	private PrimitiveContext subject;
	private FCDataInputStream inputStream;
	private FCDataOutputStream outputStream;

	@BeforeEach
	private void setup() {
		seqNo = mock(SequenceNumber.class);
		storage = mock(FCMap.class);
		accounts = mock(FCMap.class);
		topics = mock(FCMap.class);
		addressBook = mock(AddressBook.class);
		exchangeRateSets = mock(ExchangeRateSetWrapper.class);
		inputStream = mock(FCDataInputStream.class);
		outputStream = mock(FCDataOutputStream.class);
	}

	@Test
	public void gettersWork() {
		// given:
		Instant dataDrivenNow = Instant.now();
		// and:
		givenSubjectWith(PrimitiveContext.CURRENT_VERSION, dataDrivenNow);

		// expect:
		assertEquals(storage, subject.getStorage());
		assertEquals(addressBook, subject.getAddressBook());
		assertEquals(accounts, subject.getAccounts());
		assertEquals(topics, subject.getTopics());
	}

	@Test
	public void copyToWritesFalseAndSkipsConsensusTimeIfNull() throws Exception {
		givenSubjectWith(PrimitiveContext.LEGACY_VERSION, null);
		givenInOrderOnOutputStream();

		// when:
		subject.copyTo(outputStream);

		// then:
		inOrder.verify(outputStream).writeLong(PrimitiveContext.CURRENT_VERSION);
		inOrder.verify(seqNo).copyTo(outputStream);
		inOrder.verify(addressBook).copyTo(outputStream);
		inOrder.verify(accounts).copyTo(outputStream);
		inOrder.verify(storage).copyTo(outputStream);
		inOrder.verify(outputStream).writeBoolean(true);
		inOrder.verify(exchangeRateSets).copyTo(outputStream);
		inOrder.verify(outputStream).writeBoolean(false);
		inOrder.verify(topics).copyTo(outputStream);
		verifyNoMoreInteractions(outputStream);
	}

	@Test
	public void copyToWritesConsensusTimeIfNonNull() throws Exception {
		// given:
		Instant dataDrivenNow = Instant.now();
		// and:
		givenSubjectWith(PrimitiveContext.LEGACY_VERSION, dataDrivenNow);
		givenInOrderOnOutputStream();

		// when:
		subject.copyTo(outputStream);

		// then:
		inOrder.verify(outputStream).writeLong(PrimitiveContext.CURRENT_VERSION);
		inOrder.verify(seqNo).copyTo(outputStream);
		inOrder.verify(addressBook).copyTo(outputStream);
		inOrder.verify(accounts).copyTo(outputStream);
		inOrder.verify(storage).copyTo(outputStream);
		inOrder.verify(outputStream).writeBoolean(true);
		inOrder.verify(exchangeRateSets).copyTo(outputStream);
		inOrder.verify(outputStream).writeBoolean(true);
		inOrder.verify(outputStream).writeLong(dataDrivenNow.getEpochSecond());
		inOrder.verify(outputStream).writeLong((long)dataDrivenNow.getNano());
		inOrder.verify(topics).copyTo(outputStream);
		verifyNoMoreInteractions(outputStream);
	}

	@Test
	public void copyFromReadsExchangeRateIfSavedVersionIndicatesButNotMissingConsensusTime() throws Exception {
		givenInOrderOnInputStream();
		givenSubjectWith(PrimitiveContext.LEGACY_VERSION, null);
		given(inputStream.readLong()).willReturn(PrimitiveContext.VERSION_WITH_EXCHANGE_RATE);
		given(inputStream.readBoolean()).willReturn(true).willReturn(false);

		// when:
		subject.copyFrom(inputStream);

		// then:
		inOrder.verify(inputStream).readLong();
		inOrder.verify(seqNo).copyFrom(inputStream);
		inOrder.verify(addressBook, never()).copyFrom(inputStream);
		inOrder.verify(accounts).copyFrom(inputStream);
		inOrder.verify(storage).copyFrom(inputStream);
		inOrder.verify(inputStream).readBoolean();
		inOrder.verify(exchangeRateSets).copyFrom(inputStream);
		inOrder.verify(inputStream).readBoolean();
	}

	@Test
	public void copyFromReadsExchangeRateIfSavedVersionIndicatesAndAvailConsensusTime() throws Exception {
		// given:
		Instant dataDrivenNow = Instant.now();
		// and:
		givenInOrderOnInputStream();
		givenSubjectWith(PrimitiveContext.LEGACY_VERSION, null);
		given(inputStream.readBoolean()).willReturn(true);
		given(inputStream.readLong())
				.willReturn(PrimitiveContext.VERSION_WITH_EXCHANGE_RATE)
				.willReturn(0L)
				.willReturn(dataDrivenNow.getEpochSecond())
				.willReturn((long)dataDrivenNow.getNano());

		// when:
		subject.copyFrom(inputStream);

		// then:
		inOrder.verify(inputStream).readLong();
		inOrder.verify(seqNo).copyFrom(inputStream);
		inOrder.verify(addressBook, never()).copyFrom(inputStream);
		inOrder.verify(accounts).copyFrom(inputStream);
		inOrder.verify(storage).copyFrom(inputStream);
		inOrder.verify(inputStream).readBoolean();
		inOrder.verify(exchangeRateSets).copyFrom(inputStream);
		inOrder.verify(inputStream).readBoolean();
		inOrder.verify(inputStream, times(2)).readLong();
		assertEquals(dataDrivenNow, subject.consensusTimeOfLastHandledTxn);
		assertEquals(0, subject.getTopics().getSize());
	}

	@Test
	public void copyFromResetsExchangeRateAndConsensusTimeIfLegacyVersion() throws Exception {
		// given:
		Instant dataDrivenNow = Instant.now();
		// and:
		givenInOrderOnInputStream();
		givenSubjectWith(PrimitiveContext.CURRENT_VERSION, dataDrivenNow);
		given(inputStream.readLong()).willReturn(PrimitiveContext.VERSION_WITH_EXCHANGE_RATE - 1);
		// and:
		ExchangeRateSetWrapper oldExchangeRateSets = subject.midnightRateSet;

		// when:
		subject.copyFrom(inputStream);

		// then:
		inOrder.verify(inputStream).readLong();
		inOrder.verify(seqNo).copyFrom(inputStream);
		inOrder.verify(addressBook, never()).copyFrom(inputStream);
		inOrder.verify(accounts).copyFrom(inputStream);
		inOrder.verify(storage).copyFrom(inputStream);
		assertNull(subject.consensusTimeOfLastHandledTxn);
		assertFalse(subject.midnightRateSet == oldExchangeRateSets);
	}

	@Test
	public void copyFromExtraTogglesCopyChecksForLegacy() throws Exception {
		givenInOrderOnInputStream();
		givenSubjectWith(PrimitiveContext.CURRENT_VERSION, null);
		given(inputStream.readLong()).willReturn(PrimitiveContext.LEGACY_VERSION);

		// when:
		subject.copyFromExtra(inputStream);

		// then:
		inOrder.verify(inputStream).readLong();
		inOrder.verify(seqNo).copyFromExtra(inputStream);
		inOrder.verify(addressBook, never()).copyFromExtra(inputStream);
		inOrder.verify(accounts).disableCopyCheck();
		inOrder.verify(storage).disableCopyCheck();
		inOrder.verify(accounts).copyFromExtra(inputStream);
		inOrder.verify(storage).copyFromExtra(inputStream);
		inOrder.verify(accounts).enableCopyCheck();
		inOrder.verify(storage).enableCopyCheck();
		assertEquals(0, subject.getTopics().getSize());
		verifyNoMoreInteractions(inputStream);
	}

	@Test
	public void copyFromExtraLeavesCopyChecksAsIsForCurrent() throws Exception {
		givenInOrderOnInputStream();
		givenSubjectWith(PrimitiveContext.CURRENT_VERSION, null);
		given(inputStream.readLong()).willReturn(PrimitiveContext.CURRENT_VERSION);

		// when:
		subject.copyFromExtra(inputStream);

		// then:
		inOrder.verify(inputStream).readLong();
		inOrder.verify(seqNo).copyFromExtra(inputStream);
		inOrder.verify(addressBook, never()).copyFromExtra(inputStream);
		inOrder.verify(accounts).copyFromExtra(inputStream);
		inOrder.verify(storage).copyFromExtra(inputStream);
		inOrder.verify(topics).copyFromExtra(inputStream);
		verifyNoMoreInteractions(inputStream);
		verifyNoMoreInteractions(accounts);
		verifyNoMoreInteractions(storage);
	}

	@Test
	public void copyToExtraBehavesAsExpected() throws Exception {
		givenSubjectWith(PrimitiveContext.CURRENT_VERSION, null);
		givenInOrderOnOutputStream();

		// when:
		subject.copyToExtra(outputStream);

		// then:
		inOrder.verify(outputStream).writeLong(PrimitiveContext.CURRENT_VERSION);
		inOrder.verify(seqNo).copyToExtra(outputStream);
		inOrder.verify(addressBook).copyToExtra(outputStream);
		inOrder.verify(accounts).copyToExtra(outputStream);
		inOrder.verify(storage).copyToExtra(outputStream);
		inOrder.verify(topics).copyToExtra(outputStream);
		verifyNoMoreInteractions(outputStream);
	}

	@Test
	public void copyConstructorWorks() {
		givenSubjectWith(PrimitiveContext.CURRENT_VERSION, Instant.now());

		// when:
		PrimitiveContext copy = new PrimitiveContext(subject);

		// then:
		assertTrue(subject.consensusTimeOfLastHandledTxn == copy.consensusTimeOfLastHandledTxn);
		assertTrue(subject.versionAtStateInit == copy.versionAtStateInit);
		verify(addressBook).copy();
		verify(seqNo).copy();
		verify(accounts).copy();
		verify(storage).copy();
		verify(exchangeRateSets).copy();
		verify(topics).copy();
	}

	private void givenInOrderOnOutputStream() {
		inOrder = inOrder(
			outputStream,
			seqNo,
			addressBook,
			exchangeRateSets,
			accounts,
			storage,
			topics);
	}

	private void givenInOrderOnInputStream() {
		inOrder = inOrder(
				inputStream,
				seqNo,
				addressBook,
				exchangeRateSets,
				accounts,
				storage,
				topics);
	}
	private void givenSubjectWith(long versionAtStateInit, Instant consensusTimeOfLastHandledTxn) {
		subject = new PrimitiveContext(
				versionAtStateInit,
				consensusTimeOfLastHandledTxn,
				addressBook,
				seqNo,
				exchangeRateSets,
				accounts,
				storage,
				topics);
	}
}
