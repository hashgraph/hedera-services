package com.hedera.services.legacy.core.jproto;

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
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;

import static com.hedera.services.legacy.core.jproto.TxnReceipt.MISSING_RUNNING_HASH;
import static com.hedera.services.legacy.core.jproto.TxnReceipt.MISSING_RUNNING_HASH_VERSION;
import static com.hedera.services.legacy.core.jproto.TxnReceipt.MISSING_SCHEDULED_TXN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

class TxnReceiptTest {
	private static final int MAX_STATUS_BYTES = 128;

	final TransactionID scheduledTxnId = TransactionID.newBuilder()
			.setScheduled(true)
			.setAccountID(IdUtils.asAccount("0.0.2"))
			.build();

	DomainSerdes serdes;
	ExchangeRates mockRates;
	TxnReceipt subject;
	private long[] serialNumbers = new long[] { 1, 2, 3, 4, 5 };

	private TopicID getTopicId(long shard, long realm, long num) {
		return TopicID.newBuilder().setShardNum(shard).setRealmNum(realm).setTopicNum(num).build();
	}

	private EntityId getTopicJAccountId(long shard, long realm, long num) {
		return new EntityId(shard, realm, num);
	}

	private byte[] getSha384Hash() {
		final var hash = new byte[48];
		for (var i = 0; i < hash.length; ++i) {
			hash[i] = (byte) i;
		}
		return hash;
	}

	@BeforeEach
	void setup() {
		serdes = mock(DomainSerdes.class);
		mockRates = mock(ExchangeRates.class);

		TxnReceipt.serdes = serdes;
	}

	@Test
	void canGetStatusAsEnum() {
		final var subject = TxnReceipt.newBuilder().setStatus("INVALID_ACCOUNT_ID").build();
		assertEquals(INVALID_ACCOUNT_ID, subject.getEnumStatus());
	}

	@Test
	void constructorPostConsensusCreateTopic() {
		final var topicId = getTopicJAccountId(1L, 22L, 333L);
		final var sequenceNumber = 0L;
		final var cut = TxnReceipt.newBuilder().setTopicId(topicId).setTopicSequenceNumber(sequenceNumber).build();

		assertAll(() -> assertEquals(topicId, cut.getTopicId()),
				() -> assertEquals(sequenceNumber, cut.getTopicSequenceNumber()),
				() -> assertNull(cut.getTopicRunningHash())
		);
	}

	@Test
	void constructorPostConsensusSubmitMessage() {
		final var sequenceNumber = 55555L;
		final var cut = TxnReceipt.newBuilder().setTopicRunningHash(getSha384Hash()).setTopicSequenceNumber(
				sequenceNumber).build();

		assertAll(() -> assertNull(cut.getTopicId()),
				() -> assertEquals(sequenceNumber, cut.getTopicSequenceNumber()),
				() -> assertArrayEquals(getSha384Hash(), cut.getTopicRunningHash())
		);
	}

	@Test
	void setRunning() {
		final var cut = new TxnReceipt();
		cut.topicRunningHash = getSha384Hash();
		assertArrayEquals(getSha384Hash(), cut.getTopicRunningHash());
	}

	@Test
	void convertToJTransactionReceiptPostConsensusCreateTopic() {
		final var topicId = getTopicId(1L, 22L, 333L);
		final var receipt = TransactionReceipt.newBuilder()
				.setExchangeRate(new ExchangeRates().toGrpc())
				.setTopicID(topicId).build();
		final var cut = TxnReceipt.fromGrpc(receipt);

		assertAll(() -> assertEquals(EntityId.fromGrpcTopicId(topicId), cut.getTopicId()),
				() -> assertNull(cut.getAccountId()),
				() -> assertNull(cut.getFileId()),
				() -> assertNull(cut.getContractId()),
				() -> assertEquals(new ExchangeRates(), cut.getExchangeRates()),
				() -> assertEquals(0L, cut.getTopicSequenceNumber()),
				() -> assertNull(cut.getTopicRunningHash())
		);
	}

	@Test
	void scheduleCreateInterconversionWorks() {
		final var scheduleId = IdUtils.asSchedule("0.0.123");

		final var receipt = TransactionReceipt.newBuilder()
				.setExchangeRate(new ExchangeRates().toGrpc())
				.setScheduleID(scheduleId)
				.setScheduledTransactionID(scheduledTxnId)
				.build();
		final var cut = TxnReceipt.fromGrpc(receipt);
		final var back = TxnReceipt.convert(cut);

		assertEquals(receipt, back);
	}

	@Test
	void postConsensusSubmitMessageInterconversionWorks() {
		final var topicSequenceNumber = 4444L;
		final var topicRunningHash = getSha384Hash();

		final var receipt = TransactionReceipt.newBuilder()
				.setExchangeRate(new ExchangeRates().toGrpc())
				.setTopicSequenceNumber(topicSequenceNumber)
				.setTopicRunningHash(ByteString.copyFrom(topicRunningHash))
				.setTopicRunningHashVersion(2L)
				.build();
		final var cut = TxnReceipt.fromGrpc(receipt);
		final var back = TxnReceipt.convert(cut);

		assertEquals(receipt, back);
	}

	@Test
	void postConsensusTokenMintBurnWipeInterconversionWorks() {
		final var totalSupply = 12345L;

		final var receipt = TransactionReceipt.newBuilder()
				.setExchangeRate(new ExchangeRates().toGrpc())
				.setNewTotalSupply(totalSupply)
				.build();
		final var cut = TxnReceipt.fromGrpc(receipt);
		final var back = TxnReceipt.convert(cut);

		assertEquals(receipt, back);
	}

	@Test
	void postConsensusTokenNftInterconversionWorks() {
		final var receipt = TransactionReceipt.newBuilder()
				.setExchangeRate(new ExchangeRates().toGrpc())
				.addAllSerialNumbers(List.of(1L, 2L, 3L, 4L, 5L))
				.build();
		final var cut = TxnReceipt.fromGrpc(receipt);
		final var back = TxnReceipt.convert(cut);

		assertEquals(receipt, back);
	}

	@Test
	void postConsensusTokenCreationInterconversionWorks() {
		final TokenID.Builder tokenIdBuilder = TokenID.newBuilder().setTokenNum(1001L).setRealmNum(0).setShardNum(0);

		final var receipt = TransactionReceipt.newBuilder()
				.setExchangeRate(new ExchangeRates().toGrpc())
				.setTokenID(tokenIdBuilder)
				.build();
		final var cut = TxnReceipt.fromGrpc(receipt);
		final var back = TxnReceipt.convert(cut);

		assertEquals(receipt, back);
	}


	@Test
	void convertToJTransactionReceiptPostConsensusSubmitMessage() {
		final var topicSequenceNumber = 4444L;
		final var topicRunningHash = getSha384Hash();

		final var receipt = TransactionReceipt.newBuilder()
				.setTopicSequenceNumber(topicSequenceNumber)
				.setTopicRunningHash(ByteString.copyFrom(topicRunningHash))
				.setTopicRunningHashVersion(2L)
				.build();
		final var cut = TxnReceipt.fromGrpc(receipt);

		assertAll(
				() -> assertEquals(2L, cut.getRunningHashVersion()),
				() -> assertNull(cut.getTopicId()),
				() -> assertEquals(topicSequenceNumber, cut.getTopicSequenceNumber()),
				() -> assertArrayEquals(topicRunningHash, cut.getTopicRunningHash())
		);
	}

	@Test
	void convertToTransactionReceiptPostConsensusCreateTopic() {
		final var topicId = getTopicJAccountId(1L, 22L, 333L);
		final var receipt = new TxnReceipt();
		receipt.status = "OK";
		receipt.topicId = topicId;
		final var cut = TxnReceipt.convert(receipt);

		assertAll(() -> assertEquals(topicId.shard(), cut.getTopicID().getShardNum()),
				() -> assertEquals(topicId.realm(), cut.getTopicID().getRealmNum()),
				() -> assertEquals(topicId.num(), cut.getTopicID().getTopicNum()),
				() -> assertEquals(0L, cut.getTopicSequenceNumber()),
				() -> assertEquals(0, cut.getTopicRunningHash().size())
		);
	}

	@Test
	void convertToTransactionReceiptPostConsensusSubmitMessage() {
		final var sequenceNumber = 666666L;
		final var receipt = new TxnReceipt();
		receipt.status = "OK";
		receipt.topicSequenceNumber = sequenceNumber;
		receipt.topicRunningHash = getSha384Hash();
		final var cut = TxnReceipt.convert(receipt);

		assertAll(() -> assertFalse(cut.hasTopicID()),
				() -> assertEquals(sequenceNumber, cut.getTopicSequenceNumber()),
				() -> assertArrayEquals(getSha384Hash(), cut.getTopicRunningHash().toByteArray())
		);
	}


	@Test
	void equalsDefaults() {
		assertEquals(new TxnReceipt(), new TxnReceipt());
	}

	@Test
	void hashCodeWithNulls() {
		final var cut = new TxnReceipt();
		assertNull(cut.getTopicId());
		assertNull(cut.getTopicRunningHash());

		Assertions.assertDoesNotThrow(() -> cut.hashCode());
	}

	@Test
	void toStringWithNulls() {
		final var cut = new TxnReceipt();
		assertNull(cut.getTopicId());
		assertNull(cut.getTopicRunningHash());

		assertAll(() -> Assertions.assertDoesNotThrow(() -> cut.toString()),
				() -> assertNotNull(cut.toString()));
	}

	@Test
	void scheduleConstructor() {
		final var scheduleId = EntityId.fromGrpcScheduleId(IdUtils.asSchedule("0.0.123"));
		final var cut = TxnReceipt.newBuilder()
				.setStatus("SUCCESS")
				.setScheduleId(scheduleId)
				.setTopicRunningHash(MISSING_RUNNING_HASH)
				.setTopicSequenceNumber(0L)
				.setRunningHashVersion(0)
				.setNewTotalSupply(0L)
				.setScheduledTxnId(TxnId.fromGrpc(scheduledTxnId))
				.build();

		assertAll(() -> Assertions.assertDoesNotThrow(() -> cut.toString()),
				() -> assertNotNull(cut.toString()));
	}

	@Test
	void hcsConstructor() {
		final var topicId = EntityId.fromGrpcTopicId(TopicID.newBuilder().setTopicNum(1L).build());
		final var sequenceNumber = 2L;
		final var runningHash = new byte[3];
		final var cut = TxnReceipt.newBuilder()
				.setStatus("SUCCESS")
				.setTopicId(topicId)
				.setTopicRunningHash(runningHash)
				.setTopicSequenceNumber(sequenceNumber)
				.build();

		assertEquals(topicId, cut.getTopicId());
		assertEquals(sequenceNumber, cut.getTopicSequenceNumber());
		assertEquals(runningHash, cut.getTopicRunningHash());

		assertAll(() -> Assertions.assertDoesNotThrow(() -> cut.toString()),
				() -> assertNotNull(cut.toString()));
	}

	@Test
	void tokenConstructorWithTokenId() {
		final var tokenId = EntityId.fromGrpcTokenId(
				TokenID.newBuilder().setTokenNum(1001L).setRealmNum(0).setShardNum(0).build());
		final var cut = TxnReceipt.newBuilder()
				.setStatus("SUCCESS")
				.setTokenId(tokenId)
				.setNewTotalSupply(0L)
				.build();

		assertEquals(tokenId, cut.getTokenId());

		assertAll(() -> Assertions.assertDoesNotThrow(() -> cut.toString()),
				() -> assertNotNull(cut.toString()));
	}

	@Test
	void tokenConstructorWithTotalSupply() {
		final var tokenId = EntityId.fromGrpcTokenId(
				TokenID.newBuilder().setTokenNum(1001L).setRealmNum(0).setShardNum(0).build());
		final var cut = TxnReceipt.newBuilder()
				.setStatus("SUCCESS")
				.setTokenId(tokenId)
				.setRunningHashVersion(MISSING_RUNNING_HASH_VERSION)
				.setTopicSequenceNumber(0L)
				.setNewTotalSupply(1000L)
				.setScheduledTxnId(MISSING_SCHEDULED_TXN_ID)
				.build();

		assertEquals(1000L, cut.getNewTotalSupply());

		assertAll(() -> Assertions.assertDoesNotThrow(() -> cut.toString()),
				() -> assertNotNull(cut.toString()));
	}

	@Test
	void serializeWorks() throws IOException {
		// setup:
		SerializableDataOutputStream fout = mock(SerializableDataOutputStream.class);
		// and:
		InOrder inOrder = Mockito.inOrder(serdes, fout);

		subject = TxnReceipt.newBuilder()
				.setStatus("SUCCESS")
				.setExchangeRates(mockRates)
				.setTopicSequenceNumber(-1)
				.setRunningHashVersion(-1)
				.setTopicSequenceNumber(0L)
				.setNewTotalSupply(100L)
				.setScheduledTxnId(TxnId.fromGrpc(scheduledTxnId))
				.setSerialNumbers(serialNumbers)
				.build();

		// when:
		subject.serialize(fout);

		// then:
		inOrder.verify(fout).writeNormalisedString(subject.getStatus());
		inOrder.verify(fout).writeSerializable(mockRates, true);
		inOrder.verify(serdes, times(6)).writeNullableSerializable(null, fout);
		inOrder.verify(fout).writeBoolean(false);
		inOrder.verify(fout).writeLong(subject.getNewTotalSupply());
		inOrder.verify(serdes).writeNullableSerializable(subject.getScheduledTxnId(), fout);
		inOrder.verify(fout).writeLongArray(serialNumbers);
	}

	@Test
	void v0120DeserializeWorks() throws IOException {
		final var scheduleId = EntityId.fromGrpcScheduleId(IdUtils.asSchedule("0.0.312"));
		SerializableDataInputStream fin = mock(SerializableDataInputStream.class);

		subject = TxnReceipt.newBuilder()
				.setStatus("SUCCESS")
				.setScheduleId(scheduleId)
				.setExchangeRates(mockRates)
				.setTopicSequenceNumber(-1)
				.setRunningHashVersion(-1)
				.setTopicSequenceNumber(0L)
				.setNewTotalSupply(0L)
				.setScheduledTxnId(TxnId.fromGrpc(scheduledTxnId))
				.build();


		given(fin.readByteArray(MAX_STATUS_BYTES)).willReturn(subject.getStatus().getBytes());
		given(fin.readSerializable(anyBoolean(), any())).willReturn(mockRates);
		given(serdes.readNullableSerializable(fin))
				.willReturn(subject.getAccountId())
				.willReturn(subject.getFileId())
				.willReturn(subject.getContractId())
				.willReturn(subject.getTopicId())
				.willReturn(subject.getTokenId())
				.willReturn(subject.getScheduleId())
				.willReturn(subject.getScheduledTxnId());
		given(fin.readBoolean()).willReturn(true);
		given(fin.readLong()).willReturn(subject.getTopicSequenceNumber());
		given(fin.readLong()).willReturn(subject.getRunningHashVersion());
		given(fin.readAllBytes()).willReturn(subject.getTopicRunningHash());
		given(fin.readLong()).willReturn(subject.getNewTotalSupply());

		// and:
		TxnReceipt txnReceipt = new TxnReceipt();

		// when:
		txnReceipt.deserialize(fin, TxnReceipt.RELEASE_0120_VERSION);

		// then:
		assertEquals(subject.getNewTotalSupply(), txnReceipt.getNewTotalSupply());
		assertEquals(subject.getStatus(), txnReceipt.getStatus());
		assertEquals(subject.getExchangeRates(), txnReceipt.getExchangeRates());
		assertEquals(subject.getTokenId(), txnReceipt.getTokenId());
		assertEquals(subject.getScheduledTxnId(), txnReceipt.getScheduledTxnId());
	}

	@Test
	void v0160DeserializeWorks() throws IOException {
		final var scheduleId = EntityId.fromGrpcScheduleId(IdUtils.asSchedule("0.0.312"));
		SerializableDataInputStream fin = mock(SerializableDataInputStream.class);

		subject = TxnReceipt.newBuilder()
				.setStatus("SUCCESS")
				.setScheduleId(scheduleId)
				.setExchangeRates(mockRates)
				.setTopicSequenceNumber(-1)
				.setRunningHashVersion(-1)
				.setTopicSequenceNumber(0L)
				.setNewTotalSupply(0L)
				.setScheduledTxnId(TxnId.fromGrpc(scheduledTxnId))
				.setSerialNumbers(new long[] { 1, 2, 3, 4, 5 })
				.build();


		given(fin.readByteArray(MAX_STATUS_BYTES)).willReturn(subject.getStatus().getBytes());
		given(fin.readSerializable(anyBoolean(), any())).willReturn(mockRates);
		given(serdes.readNullableSerializable(fin))
				.willReturn(subject.getAccountId())
				.willReturn(subject.getFileId())
				.willReturn(subject.getContractId())
				.willReturn(subject.getTopicId())
				.willReturn(subject.getTokenId())
				.willReturn(subject.getScheduleId())
				.willReturn(subject.getScheduledTxnId());
		given(fin.readBoolean()).willReturn(true);
		given(fin.readLong()).willReturn(subject.getTopicSequenceNumber());
		given(fin.readLong()).willReturn(subject.getRunningHashVersion());
		given(fin.readAllBytes()).willReturn(subject.getTopicRunningHash());
		given(fin.readLong()).willReturn(subject.getNewTotalSupply());
		given(fin.readLongArray(anyInt())).willReturn(subject.getSerialNumbers());

		// and:
		TxnReceipt txnReceipt = new TxnReceipt();

		// when:
		txnReceipt.deserialize(fin, TxnReceipt.RELEASE_0160_VERSION);

		// then:
		assertEquals(subject.getNewTotalSupply(), txnReceipt.getNewTotalSupply());
		assertEquals(subject.getStatus(), txnReceipt.getStatus());
		assertEquals(subject.getExchangeRates(), txnReceipt.getExchangeRates());
		assertEquals(subject.getTokenId(), txnReceipt.getTokenId());
		assertEquals(subject.getScheduledTxnId(), txnReceipt.getScheduledTxnId());
		assertArrayEquals(subject.getSerialNumbers(), txnReceipt.getSerialNumbers());
	}

	@Test
	void v0100DeserializeWorks() throws IOException {
		final var tokenId = EntityId.fromGrpcTokenId(
				TokenID.newBuilder().setTokenNum(1001L).setRealmNum(0).setShardNum(0).build());
		SerializableDataInputStream fin = mock(SerializableDataInputStream.class);

		subject = TxnReceipt.newBuilder()
				.setStatus("SUCCESS")
				.setScheduleId(tokenId)
				.setExchangeRates(mockRates)
				.setTopicSequenceNumber(-1)
				.setRunningHashVersion(-1)
				.setTopicSequenceNumber(0L)
				.setNewTotalSupply(100L)
				.setScheduledTxnId(MISSING_SCHEDULED_TXN_ID)
				.build();

		given(fin.readByteArray(MAX_STATUS_BYTES)).willReturn(subject.getStatus().getBytes());
		given(fin.readSerializable(anyBoolean(), any())).willReturn(mockRates);
		given(serdes.readNullableSerializable(fin))
				.willReturn(subject.getAccountId())
				.willReturn(subject.getFileId())
				.willReturn(subject.getContractId())
				.willReturn(subject.getTopicId())
				.willReturn(subject.getTokenId());
		given(fin.readBoolean()).willReturn(true);
		given(fin.readLong()).willReturn(subject.getTopicSequenceNumber());
		given(fin.readLong()).willReturn(subject.getRunningHashVersion());
		given(fin.readAllBytes()).willReturn(subject.getTopicRunningHash());
		given(fin.readLong()).willReturn(subject.getNewTotalSupply());

		// and:
		TxnReceipt txnReceipt = new TxnReceipt();

		// when:
		txnReceipt.deserialize(fin, TxnReceipt.RELEASE_0100_VERSION);

		// then:
		assertEquals(subject.getNewTotalSupply(), txnReceipt.getNewTotalSupply());
		assertEquals(subject.getStatus(), txnReceipt.getStatus());
		assertEquals(subject.getExchangeRates(), txnReceipt.getExchangeRates());
		assertEquals(subject.getTokenId(), txnReceipt.getTokenId());
	}
}
