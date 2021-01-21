package com.hedera.services.state.submerkle;

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
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.serdes.DomainSerdesTest;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hedera.services.state.submerkle.ExpirableTxnRecord.MAX_INVOLVED_TOKENS;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.UNKNOWN_SUBMITTING_MEMBER;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.willAnswer;

@RunWith(JUnitPlatform.class)
class ExpirableTxnRecordTest {
	long expiry = 1_234_567L;
	long submittingMember = 1L;

	DataInputStream din;

	DomainSerdes serdes;
	TxnId.Provider legacyTxnIdProvider;
	TxnReceipt.Provider legacyReceiptProvider;
	RichInstant.Provider legacyInstantProvider;
	CurrencyAdjustments.Provider legacyAdjustmentsProvider;
	SolidityFnResult.Provider legacyFnResultProvider;

	byte[] pretendHash = "not-really-a-hash".getBytes();

	TokenID tokenA = IdUtils.asToken("1.2.3");
	TokenID tokenB = IdUtils.asToken("1.2.4");
	AccountID sponsor = IdUtils.asAccount("1.2.5");
	AccountID beneficiary = IdUtils.asAccount("1.2.6");
	AccountID magician = IdUtils.asAccount("1.2.7");
	TokenTransferList aTokenTransfers = TokenTransferList.newBuilder()
			.setToken(tokenA)
			.addAllTransfers(
					withAdjustments(sponsor, -1L, beneficiary, 1L, magician, 1000L).getAccountAmountsList())
			.build();
	TokenTransferList bTokenTransfers = TokenTransferList.newBuilder()
			.setToken(tokenB)
			.addAllTransfers(
					withAdjustments(sponsor, -1L, beneficiary, 1L, magician, 1000L).getAccountAmountsList())
			.build();

	ExpirableTxnRecord subject;

	@BeforeEach
	public void setup() {
		subject = subjectRecordWithTokenTransfers();

		din = mock(DataInputStream.class);

		serdes = mock(DomainSerdes.class);
		legacyTxnIdProvider = mock(TxnId.Provider.class);
		legacyReceiptProvider = mock(TxnReceipt.Provider.class);
		legacyInstantProvider = mock(RichInstant.Provider.class);
		legacyFnResultProvider = mock(SolidityFnResult.Provider.class);
		legacyAdjustmentsProvider = mock(CurrencyAdjustments.Provider.class);

		ExpirableTxnRecord.legacyAdjustmentsProvider = legacyAdjustmentsProvider;
		ExpirableTxnRecord.legacyFnResultProvider = legacyFnResultProvider;
		ExpirableTxnRecord.legacyInstantProvider = legacyInstantProvider;
		ExpirableTxnRecord.legacyReceiptProvider = legacyReceiptProvider;
		ExpirableTxnRecord.legacyTxnIdProvider = legacyTxnIdProvider;
		ExpirableTxnRecord.serdes = serdes;
	}

	private ExpirableTxnRecord subjectRecord() {
		var s = ExpirableTxnRecord.fromGprc(
				DomainSerdesTest.recordOne().asGrpc().toBuilder()
						.setTransactionHash(ByteString.copyFrom(pretendHash))
						.setContractCreateResult(DomainSerdesTest.recordTwo().getContractCallResult().toGrpc())
						.build());
		s.setExpiry(expiry);
		s.setSubmittingMember(submittingMember);
		return s;
	}

	private ExpirableTxnRecord subjectRecordWithTokenTransfers() {
		var s = ExpirableTxnRecord.fromGprc(
				DomainSerdesTest.recordOne().asGrpc().toBuilder()
						.setTransactionHash(ByteString.copyFrom(pretendHash))
						.setContractCreateResult(DomainSerdesTest.recordTwo().getContractCallResult().toGrpc())
						.addAllTokenTransferLists(List.of(aTokenTransfers, bTokenTransfers))
						.build());
		s.setExpiry(expiry);
		s.setSubmittingMember(submittingMember);
		return s;
	}

	@Test
	public void hashableMethodsWork() {
		// given:
		Hash pretend = mock(Hash.class);

		// when:
		subject.setHash(pretend);

		// then:
		assertEquals(pretend, subject.getHash());
	}

	@Test
	public void fastCopyableWorks() {
		// expect;
		assertTrue(subject.isImmutable());
		assertSame(subject, subject.copy());
		assertDoesNotThrow(subject::release);
	}

	@Test
	public void v070DeserializeWorks() throws IOException {
		// setup:
		subject = subjectRecord();
		SerializableDataInputStream fin = mock(SerializableDataInputStream.class);

		given(serdes.readNullableSerializable(fin))
				.willReturn(subject.getReceipt())
				.willReturn(subject.getTxnId())
				.willReturn(subject.getHbarAdjustments())
				.willReturn(subject.getContractCallResult())
				.willReturn(subject.getContractCreateResult());
		given(fin.readByteArray(ExpirableTxnRecord.MAX_TXN_HASH_BYTES))
				.willReturn(subject.getTxnHash());
		given(serdes.readNullableInstant(fin))
				.willReturn(subject.getConsensusTimestamp());
		given(fin.readLong()).willReturn(subject.getFee())
				.willReturn(subject.getExpiry())
				.willReturn(subject.getSubmittingMember());
		given(serdes.readNullableString(fin, ExpirableTxnRecord.MAX_MEMO_BYTES))
				.willReturn(subject.getMemo());
		// and:
		var deserializedRecord = new ExpirableTxnRecord();

		// when:
		deserializedRecord.deserialize(fin, ExpirableTxnRecord.RELEASE_070_VERSION);

		// then:
		assertEquals(subject, deserializedRecord);
	}

	@Test
	public void v080DeserializeWorks() throws IOException {
		// setup:
		SerializableDataInputStream fin = mock(SerializableDataInputStream.class);

		given(serdes.readNullableSerializable(fin))
				.willReturn(subject.getReceipt())
				.willReturn(subject.getTxnId())
				.willReturn(subject.getHbarAdjustments())
				.willReturn(subject.getContractCallResult())
				.willReturn(subject.getContractCreateResult());
		given(fin.readSerializableList(MAX_INVOLVED_TOKENS))
				.willReturn(List.of(
						(SelfSerializable) subject.getTokens().get(0),
						(SelfSerializable) subject.getTokens().get(1)))
				.willReturn(List.of(
						(SelfSerializable) subject.getTokenAdjustments().get(0),
						(SelfSerializable) subject.getTokenAdjustments().get(1)));
		given(fin.readByteArray(ExpirableTxnRecord.MAX_TXN_HASH_BYTES))
				.willReturn(subject.getTxnHash());
		given(serdes.readNullableInstant(fin))
				.willReturn(subject.getConsensusTimestamp());
		given(fin.readLong()).willReturn(subject.getFee())
				.willReturn(subject.getExpiry())
				.willReturn(subject.getSubmittingMember());
		given(serdes.readNullableString(fin, ExpirableTxnRecord.MAX_MEMO_BYTES))
				.willReturn(subject.getMemo());
		// and:
		var deserializedRecord = new ExpirableTxnRecord();

		// when:
		deserializedRecord.deserialize(fin, ExpirableTxnRecord.RELEASE_080_VERSION);

		// then:
		assertEquals(subject, deserializedRecord);
	}

	@Test
	public void serializeWorks() throws IOException {
		// setup:
		SerializableDataOutputStream fout = mock(SerializableDataOutputStream.class);
		// and:
		InOrder inOrder = Mockito.inOrder(serdes, fout);

		// when:
		subject.serialize(fout);

		// then:
		inOrder.verify(serdes).writeNullableSerializable(subject.getReceipt(), fout);
		inOrder.verify(fout).writeByteArray(subject.getTxnHash());
		inOrder.verify(serdes).writeNullableSerializable(subject.getTxnId(), fout);
		inOrder.verify(serdes).writeNullableString(subject.getMemo(), fout);
		inOrder.verify(fout).writeLong(subject.getFee());
		inOrder.verify(serdes).writeNullableSerializable(subject.getHbarAdjustments(), fout);
		inOrder.verify(serdes).writeNullableSerializable(subject.getContractCallResult(), fout);
		inOrder.verify(serdes).writeNullableSerializable(subject.getContractCreateResult(), fout);
		inOrder.verify(fout).writeLong(subject.getExpiry());
		inOrder.verify(fout).writeLong(subject.getSubmittingMember());
		inOrder.verify(fout).writeSerializableList(
				subject.getTokens(), true, true);
		inOrder.verify(fout).writeSerializableList(
				subject.getTokenAdjustments(), true, true);
	}

	@Test
	public void serializableDetWorks() {
		// expect;
		assertEquals(ExpirableTxnRecord.MERKLE_VERSION, subject.getVersion());
		assertEquals(ExpirableTxnRecord.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	@Test
	public void grpcInterconversionWorks() {
		// given:
		subject.setExpiry(0L);
		subject.setSubmittingMember(UNKNOWN_SUBMITTING_MEMBER);

		// expect:
		assertEquals(subject, ExpirableTxnRecord.fromGprc(subject.asGrpc()));
	}

	@Test
	public void objectContractWorks() {
		// given:
		var one = subject;
		var two = DomainSerdesTest.recordOne();
		var three = subjectRecordWithTokenTransfers();

		// when:
		assertEquals(one, one);
		assertNotEquals(one, null);
		assertNotEquals(one, new Object());
		assertEquals(one, three);
		assertNotEquals(one, two);
		// and:
		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(one.hashCode(), three.hashCode());
	}

	@Test
	public void toStringHasntChanged() {
		// expect:
		assertEquals(
				"ExpirableTxnRecord{receipt=TxnReceipt{status=INVALID_ACCOUNT_ID, " +
						"exchangeRates=ExchangeRates{currHbarEquiv=0, currCentEquiv=0, currExpiry=0, " +
						"nextHbarEquiv=0, nextCentEquiv=0, nextExpiry=0}, " +
						"accountCreated=EntityId{shard=0, realm=0, num=3}, newTotalTokenSupply=0}, " +
						"txnHash=6e6f742d7265616c6c792d612d68617368, " +
						"txnId=TxnId{payer=EntityId{shard=0, realm=0, num=0}, " +
						"validStart=RichInstant{seconds=9999999999, nanos=0}, scheduled=false}, " +
						"consensusTimestamp=RichInstant{seconds=9999999999, nanos=0}, " +
						"expiry=1234567, submittingMember=1, memo=Alpha bravo charlie, " +
						"contractCreation=SolidityFnResult{gasUsed=55, bloom=, " +
						"result=, error=null, contractId=EntityId{shard=4, realm=3, num=2}, createdContractIds=[], " +
						"logs=[SolidityLog{data=4e6f6e73656e736963616c21, bloom=, contractId=null, topics=[]}]}, " +
						"hbarAdjustments=CurrencyAdjustments{readable=[0.0.2 -> -4, 0.0.1001 <- +2, 0.0.1002 <- +2]}," +
						" " +
						"tokenAdjustments=" +
						"1.2.3(CurrencyAdjustments{readable=[1.2.5 -> -1, 1.2.6 <- +1, 1.2.7 <- +1000]}), " +
						"1.2.4(CurrencyAdjustments{readable=[1.2.5 -> -1, 1.2.6 <- +1, 1.2.7 <- +1000]})}",
				subject.toString());
	}

	@Test
	public void legacyProviderWorks() throws IOException {
		// setup:
		var readFullyOccurrence = new AtomicInteger(0);
		subject = subjectRecord();

		given(din.readLong())
				.willReturn(-2L)
				.willReturn(-1L)
				.willReturn(subject.getFee())
				.willReturn(subject.getExpiry());
		given(din.readBoolean()).willReturn(true);
		// and:
		given(legacyReceiptProvider.deserialize(din)).willReturn(subject.getReceipt());
		// and:
		given(legacyFnResultProvider.deserialize(din))
				.willReturn(subject.getContractCallResult())
				.willReturn(subject.getContractCreateResult());
		// and:
		given(legacyTxnIdProvider.deserialize(din)).willReturn(subject.getTxnId());
		// and:
		given(legacyInstantProvider.deserialize(din)).willReturn(subject.getConsensusTimestamp());
		// and:
		given(legacyAdjustmentsProvider.deserialize(din)).willReturn(subject.getHbarAdjustments());
		// and:
		given(din.readInt())
				.willReturn(pretendHash.length)
				.willReturn(subject.getMemo().getBytes().length);
		// and:
		willAnswer(invocation -> {
			var buffer = (byte[]) invocation.getArgument(0);
			if (readFullyOccurrence.getAndIncrement() == 0) {
				System.arraycopy(pretendHash, 0, buffer, 0, pretendHash.length);
			} else {
				System.arraycopy(
						subject.getMemo().getBytes(), 0,
						buffer, 0, subject.getMemo().getBytes().length);
			}
			return null;
		}).given(din).readFully(any());

		// when:
		var fromLegacy = ExpirableTxnRecord.LEGACY_PROVIDER.deserialize(din);

		// then:
		subject.setSubmittingMember(UNKNOWN_SUBMITTING_MEMBER);
		assertEquals(subject, fromLegacy);
	}

	@AfterEach
	public void cleanup() {
		ExpirableTxnRecord.legacyTxnIdProvider = TxnId.LEGACY_PROVIDER;
		ExpirableTxnRecord.legacyReceiptProvider = TxnReceipt.LEGACY_PROVIDER;
		ExpirableTxnRecord.legacyInstantProvider = RichInstant.LEGACY_PROVIDER;
		ExpirableTxnRecord.legacyAdjustmentsProvider = CurrencyAdjustments.LEGACY_PROVIDER;
		ExpirableTxnRecord.legacyFnResultProvider = SolidityFnResult.LEGACY_PROVIDER;
		ExpirableTxnRecord.serdes = new DomainSerdes();
	}
}
