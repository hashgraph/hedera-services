package com.hedera.services.state.submerkle;

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
import com.hedera.services.state.serdes.DomainSerdesTest;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static com.hedera.services.state.merkle.internals.BitPackUtils.packedTime;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.MAX_ASSESSED_CUSTOM_FEES_CHANGES;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.MAX_INVOLVED_TOKENS;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.MISSING_ALIAS;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.MISSING_PARENT_CONSENSUS_TIMESTAMP;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.NO_CHILD_TRANSACTIONS;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.UNKNOWN_SUBMITTING_MEMBER;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.allToGrpc;
import static com.hedera.services.state.submerkle.ExpirableTxnRecordTestHelper.fromGprc;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hedera.test.utils.TxnUtils.withNftAdjustments;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.times;

class ExpirableTxnRecordTest {
	private static final long expiry = 1_234_567L;
	private static final long submittingMember = 1L;
	private static final long packedParentConsTime = packedTime(expiry, 890);
	private static final short numChildRecords = 2;

	private static final byte[] pretendHash = "not-really-a-hash".getBytes();

	private static final TokenID nft = IdUtils.asToken("1.2.2");
	private static final TokenID tokenA = IdUtils.asToken("1.2.3");
	private static final TokenID tokenB = IdUtils.asToken("1.2.4");
	private static final AccountID sponsor = IdUtils.asAccount("1.2.5");
	private static final AccountID beneficiary = IdUtils.asAccount("1.2.6");
	private static final AccountID magician = IdUtils.asAccount("1.2.7");
	private static final List<TokenAssociation> newRelationships = List.of(new FcTokenAssociation(
			10, 11).toGrpc());

	private static final EntityId feeCollector = new EntityId(1, 2, 8);
	private static final EntityId token = new EntityId(1, 2, 9);
	private static final long units = 123L;

	private static final TokenTransferList nftTokenTransfers = TokenTransferList.newBuilder()
			.setToken(nft)
			.addNftTransfers(
					withNftAdjustments(nft, sponsor, beneficiary, 1L, sponsor, beneficiary, 2L, sponsor, beneficiary,
							3L).getNftTransfers(0)
			)
			.build();
	private static final TokenTransferList aTokenTransfers = TokenTransferList.newBuilder()
			.setToken(tokenA)
			.addAllTransfers(
					withAdjustments(sponsor, -1L, beneficiary, 1L, magician, 1000L).getAccountAmountsList())
			.build();
	private static final TokenTransferList bTokenTransfers = TokenTransferList.newBuilder()
			.setToken(tokenB)
			.addAllTransfers(
					withAdjustments(sponsor, -1L, beneficiary, 1L, magician, 1000L).getAccountAmountsList())
			.build();
	private static final ScheduleID scheduleID = IdUtils.asSchedule("5.6.7");
	private static final FcAssessedCustomFee balanceChange =
			new FcAssessedCustomFee(feeCollector, token, units, new long[] { 234L });

	private DomainSerdes serdes;
	private ExpirableTxnRecord subject;

	@BeforeEach
	void setup() {
		subject = subjectRecordWithTokenTransfersAndScheduleRefCustomFees();

		serdes = mock(DomainSerdes.class);

		ExpirableTxnRecord.serdes = serdes;
	}

	private static ExpirableTxnRecord subjectRecordWithTokenTransfers() {
		final var s = ExpirableTxnRecordTestHelper.fromGprc(
				DomainSerdesTest.recordOne().asGrpc().toBuilder()
						.setTransactionHash(ByteString.copyFrom(pretendHash))
						.setContractCreateResult(DomainSerdesTest.recordTwo().getContractCallResult().toGrpc())
						.addAllTokenTransferLists(List.of(aTokenTransfers, bTokenTransfers))
						.build());
		setNonGrpcDefaultsOn(s);
		return s;
	}

	private static ExpirableTxnRecord subjectRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations() {
		final var source = grpcRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations();
		final var s = ExpirableTxnRecordTestHelper.fromGprc(source);
		setNonGrpcDefaultsOn(s);
		return s;
	}

	private static TransactionRecord grpcRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations() {
		return DomainSerdesTest.recordOne().asGrpc().toBuilder()
				.setTransactionHash(ByteString.copyFrom(pretendHash))
				.setContractCreateResult(DomainSerdesTest.recordTwo().getContractCallResult().toGrpc())
				.addAllTokenTransferLists(List.of(aTokenTransfers, bTokenTransfers, nftTokenTransfers))
				.setScheduleRef(scheduleID)
				.addAssessedCustomFees(balanceChange.toGrpc())
				.addAllAutomaticTokenAssociations(newRelationships)
				.setAlias(ByteString.copyFromUtf8("test"))
				.build();
	}

	private static ExpirableTxnRecord subjectRecordWithTokenTransfersAndScheduleRefCustomFees() {
		final var s = fromGprc(
				DomainSerdesTest.recordOne().asGrpc().toBuilder()
						.setTransactionHash(ByteString.copyFrom(pretendHash))
						.setContractCreateResult(DomainSerdesTest.recordTwo().getContractCallResult().toGrpc())
						.addAllTokenTransferLists(List.of(aTokenTransfers, bTokenTransfers))
						.setScheduleRef(scheduleID)
						.addAssessedCustomFees(balanceChange.toGrpc())
						.setAlias(ByteString.copyFrom("test".getBytes(StandardCharsets.UTF_8)))
						.build());
		setNonGrpcDefaultsOn(s);
		return s;
	}

	private static void setNonGrpcDefaultsOn(final ExpirableTxnRecord subject) {
		subject.setExpiry(expiry);
		subject.setSubmittingMember(submittingMember);
		subject.setNumChildRecords(numChildRecords);
		subject.setPackedParentConsensusTime(packedParentConsTime);
	}

	@Test
	void hashableMethodsWork() {
		final var pretend = mock(Hash.class);

		subject.setHash(pretend);

		assertEquals(pretend, subject.getHash());
	}

	@Test
	void fastCopyableWorks() {
		assertTrue(subject.isImmutable());
		assertSame(subject, subject.copy());
		assertDoesNotThrow(subject::release);
	}

	@Test
	void v0120DeserializeWorks() throws IOException {
		subject = subjectRecordWithTokenTransfers();
		subject.setNumChildRecords(NO_CHILD_TRANSACTIONS);
		subject.setPackedParentConsensusTime(MISSING_PARENT_CONSENSUS_TIMESTAMP);
		final var fin = mock(SerializableDataInputStream.class);
		given(serdes.readNullableSerializable(fin))
				.willReturn(subject.getReceipt())
				.willReturn(subject.getTxnId())
				.willReturn(subject.getHbarAdjustments())
				.willReturn(subject.getContractCallResult())
				.willReturn(subject.getContractCreateResult())
				.willReturn(subject.getScheduleRef());
		given(fin.readSerializableList(MAX_INVOLVED_TOKENS))
				.willReturn(List.of(
						subject.getTokens().get(0),
						subject.getTokens().get(1)))
				.willReturn(List.of(
						subject.getTokenAdjustments().get(0),
						subject.getTokenAdjustments().get(1)));
		given(fin.readByteArray(ExpirableTxnRecord.MAX_TXN_HASH_BYTES))
				.willReturn(subject.getTxnHash());
		given(serdes.readNullableInstant(fin))
				.willReturn(subject.getConsensusTimestamp());
		given(fin.readLong()).willReturn(subject.getFee())
				.willReturn(subject.getExpiry())
				.willReturn(subject.getSubmittingMember());
		given(serdes.readNullableString(fin, ExpirableTxnRecord.MAX_MEMO_BYTES))
				.willReturn(subject.getMemo());
		final var deserializedRecord = new ExpirableTxnRecord();

		deserializedRecord.deserialize(fin, ExpirableTxnRecord.RELEASE_0120_VERSION);
		deserializedRecord.setNewTokenAssociations(Collections.emptyList());

		assertEquals(subject, deserializedRecord);
	}

	@Test
	void v0160DeserializeWorks() throws IOException {
		subject = subjectRecordWithTokenTransfersAndScheduleRefCustomFees();
		subject.setNumChildRecords(NO_CHILD_TRANSACTIONS);
		subject.setPackedParentConsensusTime(MISSING_PARENT_CONSENSUS_TIMESTAMP);
		subject.setAlias(MISSING_ALIAS);

		final var fin = mock(SerializableDataInputStream.class);
		given(serdes.readNullableSerializable(fin))
				.willReturn(subject.getReceipt())
				.willReturn(subject.getTxnId())
				.willReturn(subject.getHbarAdjustments())
				.willReturn(subject.getContractCallResult())
				.willReturn(subject.getContractCreateResult())
				.willReturn(subject.getScheduleRef());
		given(fin.readSerializableList(MAX_INVOLVED_TOKENS))
				.willReturn(List.of(
						subject.getTokens().get(0),
						subject.getTokens().get(1)))
				.willReturn(List.of(
						subject.getTokenAdjustments().get(0),
						subject.getTokenAdjustments().get(1)))
				.willReturn(List.of(
						subject.getNftTokenAdjustments().get(0),
						subject.getNftTokenAdjustments().get(1)))
				.willReturn(null);
		given(fin.readByteArray(ExpirableTxnRecord.MAX_TXN_HASH_BYTES))
				.willReturn(subject.getTxnHash());
		given(serdes.readNullableInstant(fin))
				.willReturn(subject.getConsensusTimestamp());
		given(fin.readLong()).willReturn(subject.getFee())
				.willReturn(subject.getExpiry())
				.willReturn(subject.getSubmittingMember());
		given(serdes.readNullableString(fin, ExpirableTxnRecord.MAX_MEMO_BYTES))
				.willReturn(subject.getMemo());
		given(fin.readSerializableList(MAX_ASSESSED_CUSTOM_FEES_CHANGES))
				.willReturn(List.of(subject.getCustomFeesCharged().get(0)));
		final var deserializedRecord = new ExpirableTxnRecord();

		deserializedRecord.deserialize(fin, ExpirableTxnRecord.RELEASE_0160_VERSION);

		assertEquals(subject, deserializedRecord);
	}

	@Test
	void v0180DeserializeWorks() throws IOException {
		subject = subjectRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations();
		subject.setNumChildRecords(NO_CHILD_TRANSACTIONS);
		subject.setPackedParentConsensusTime(MISSING_PARENT_CONSENSUS_TIMESTAMP);
		subject.setAlias(MISSING_ALIAS);
		final var fin = mock(SerializableDataInputStream.class);
		given(serdes.readNullableSerializable(fin))
				.willReturn(subject.getReceipt())
				.willReturn(subject.getTxnId())
				.willReturn(subject.getHbarAdjustments())
				.willReturn(subject.getContractCallResult())
				.willReturn(subject.getContractCreateResult())
				.willReturn(subject.getScheduleRef());
		given(fin.readSerializableList(MAX_INVOLVED_TOKENS))
				.willReturn(List.of(
						subject.getTokens().get(0),
						subject.getTokens().get(1),
						subject.getTokens().get(2)))
				.willReturn(List.of(
						subject.getTokenAdjustments().get(0),
						subject.getTokenAdjustments().get(1),
						subject.getTokenAdjustments().get(2)))
				.willReturn(List.of(
						subject.getNftTokenAdjustments().get(0),
						subject.getNftTokenAdjustments().get(1),
						subject.getNftTokenAdjustments().get(2)
				));
		given(fin.readByteArray(ExpirableTxnRecord.MAX_TXN_HASH_BYTES))
				.willReturn(subject.getTxnHash());
		given(serdes.readNullableInstant(fin))
				.willReturn(subject.getConsensusTimestamp());
		given(fin.readLong()).willReturn(subject.getFee())
				.willReturn(subject.getExpiry())
				.willReturn(subject.getSubmittingMember());
		given(serdes.readNullableString(fin, ExpirableTxnRecord.MAX_MEMO_BYTES))
				.willReturn(subject.getMemo());
		given(fin.readSerializableList(MAX_ASSESSED_CUSTOM_FEES_CHANGES))
				.willReturn(List.of(subject.getCustomFeesCharged().get(0)));
		given(fin.readSerializableList(Integer.MAX_VALUE))
				.willReturn(List.of(subject.getNewTokenAssociations().get(0)));
		final var deserializedRecord = new ExpirableTxnRecord();

		deserializedRecord.deserialize(fin, ExpirableTxnRecord.RELEASE_0180_VERSION);

		assertEquals(deserializedRecord, subject);
	}

	@Test
	void v0210DeserializeWorksWithAllChildRecordMeta() throws IOException {
		subject = subjectRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations();
		final var fin = mock(SerializableDataInputStream.class);
		given(serdes.readNullableSerializable(fin))
				.willReturn(subject.getReceipt())
				.willReturn(subject.getTxnId())
				.willReturn(subject.getHbarAdjustments())
				.willReturn(subject.getContractCallResult())
				.willReturn(subject.getContractCreateResult())
				.willReturn(subject.getScheduleRef());
		given(fin.readSerializableList(MAX_INVOLVED_TOKENS))
				.willReturn(List.of(
						subject.getTokens().get(0),
						subject.getTokens().get(1),
						subject.getTokens().get(2)))
				.willReturn(List.of(
						subject.getTokenAdjustments().get(0),
						subject.getTokenAdjustments().get(1),
						subject.getTokenAdjustments().get(2)))
				.willReturn(List.of(
						subject.getNftTokenAdjustments().get(0),
						subject.getNftTokenAdjustments().get(1),
						subject.getNftTokenAdjustments().get(2)
				));
		given(fin.readByteArray(ExpirableTxnRecord.MAX_TXN_HASH_BYTES))
				.willReturn(subject.getTxnHash());
		given(serdes.readNullableInstant(fin))
				.willReturn(subject.getConsensusTimestamp());
		given(fin.readLong()).willReturn(subject.getFee())
				.willReturn(subject.getExpiry())
				.willReturn(subject.getSubmittingMember())
				.willReturn(subject.getPackedParentConsensusTime());
		given(fin.readShort()).willReturn(subject.getNumChildRecords());
		given(serdes.readNullableString(fin, ExpirableTxnRecord.MAX_MEMO_BYTES))
				.willReturn(subject.getMemo());
		given(fin.readSerializableList(MAX_ASSESSED_CUSTOM_FEES_CHANGES))
				.willReturn(List.of(subject.getCustomFeesCharged().get(0)));
		given(fin.readSerializableList(Integer.MAX_VALUE))
				.willReturn(List.of(subject.getNewTokenAssociations().get(0)));
		given(fin.readBoolean()).willReturn(true);
		given(fin.readByteArray(Integer.MAX_VALUE))
				.willReturn(subject.getAlias().toByteArray());
		final var deserializedRecord = new ExpirableTxnRecord();

		deserializedRecord.deserialize(fin, ExpirableTxnRecord.RELEASE_0210_VERSION);

		assertEquals(deserializedRecord, subject);
	}

	@Test
	void v0210DeserializeWorksWithNoChildRecordMeta() throws IOException {
		subject = subjectRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations();
		subject.setNumChildRecords(NO_CHILD_TRANSACTIONS);
		subject.setPackedParentConsensusTime(MISSING_PARENT_CONSENSUS_TIMESTAMP);
		final var fin = mock(SerializableDataInputStream.class);
		given(serdes.readNullableSerializable(fin))
				.willReturn(subject.getReceipt())
				.willReturn(subject.getTxnId())
				.willReturn(subject.getHbarAdjustments())
				.willReturn(subject.getContractCallResult())
				.willReturn(subject.getContractCreateResult())
				.willReturn(subject.getScheduleRef());
		given(fin.readSerializableList(MAX_INVOLVED_TOKENS))
				.willReturn(List.of(
						subject.getTokens().get(0),
						subject.getTokens().get(1),
						subject.getTokens().get(2)))
				.willReturn(List.of(
						subject.getTokenAdjustments().get(0),
						subject.getTokenAdjustments().get(1),
						subject.getTokenAdjustments().get(2)))
				.willReturn(List.of(
						subject.getNftTokenAdjustments().get(0),
						subject.getNftTokenAdjustments().get(1),
						subject.getNftTokenAdjustments().get(2)
				));
		given(fin.readByteArray(ExpirableTxnRecord.MAX_TXN_HASH_BYTES))
				.willReturn(subject.getTxnHash());
		given(serdes.readNullableInstant(fin))
				.willReturn(subject.getConsensusTimestamp());
		given(fin.readLong()).willReturn(subject.getFee())
				.willReturn(subject.getExpiry())
				.willReturn(subject.getSubmittingMember())
				.willReturn(subject.getPackedParentConsensusTime());
		given(fin.readShort()).willReturn(subject.getNumChildRecords());
		given(serdes.readNullableString(fin, ExpirableTxnRecord.MAX_MEMO_BYTES))
				.willReturn(subject.getMemo());
		given(fin.readSerializableList(MAX_ASSESSED_CUSTOM_FEES_CHANGES))
				.willReturn(List.of(subject.getCustomFeesCharged().get(0)));
		given(fin.readSerializableList(Integer.MAX_VALUE))
				.willReturn(List.of(subject.getNewTokenAssociations().get(0)));
		given(fin.readByteArray(Integer.MAX_VALUE))
				.willReturn(subject.getAlias().toByteArray());
		final var deserializedRecord = new ExpirableTxnRecord();

		deserializedRecord.deserialize(fin, ExpirableTxnRecord.RELEASE_0210_VERSION);

		assertEquals(deserializedRecord, subject);
	}

	@Test
	void serializeWorksWithBothChildAndParentMeta() throws IOException {
		final var fout = mock(SerializableDataOutputStream.class);
		final var inOrder = Mockito.inOrder(serdes, fout);

		subject.serialize(fout);

		inOrder.verify(serdes).writeNullableSerializable(subject.getReceipt(), fout);
		inOrder.verify(fout).writeByteArray(subject.getTxnHash());
		inOrder.verify(serdes).writeNullableSerializable(subject.getTxnId(), fout);
		inOrder.verify(serdes).writeNullableInstant(subject.getConsensusTimestamp(), fout);
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
		inOrder.verify(serdes).writeNullableSerializable(EntityId.fromGrpcScheduleId(scheduleID), fout);
		inOrder.verify(fout).writeSerializableList(
				subject.getNftTokenAdjustments(), true, true);
		inOrder.verify(fout).writeSerializableList(subject.getCustomFeesCharged(), true, true);
		inOrder.verify(fout).writeBoolean(true);
		inOrder.verify(fout).writeShort(numChildRecords);
		inOrder.verify(fout).writeBoolean(true);
		inOrder.verify(fout).writeLong(packedParentConsTime);
		inOrder.verify(fout).writeByteArray(subject.getAlias().toByteArray());
	}

	@Test
	void serializeWorksWithNeitherChildAndParentMeta() throws IOException {
		final var fout = mock(SerializableDataOutputStream.class);
		final var inOrder = Mockito.inOrder(serdes, fout);

		subject.setNumChildRecords(NO_CHILD_TRANSACTIONS);
		subject.setPackedParentConsensusTime(MISSING_PARENT_CONSENSUS_TIMESTAMP);
		subject.serialize(fout);

		inOrder.verify(serdes).writeNullableSerializable(subject.getReceipt(), fout);
		inOrder.verify(fout).writeByteArray(subject.getTxnHash());
		inOrder.verify(serdes).writeNullableSerializable(subject.getTxnId(), fout);
		inOrder.verify(serdes).writeNullableInstant(subject.getConsensusTimestamp(), fout);
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
		inOrder.verify(serdes).writeNullableSerializable(EntityId.fromGrpcScheduleId(scheduleID), fout);
		inOrder.verify(fout).writeSerializableList(
				subject.getNftTokenAdjustments(), true, true);
		inOrder.verify(fout).writeSerializableList(subject.getCustomFeesCharged(), true, true);
		inOrder.verify(fout, times(2)).writeBoolean(false);
		inOrder.verify(fout).writeByteArray(subject.getAlias().toByteArray());
	}

	@Test
	void serializableDetWorks() {
		assertEquals(ExpirableTxnRecord.MERKLE_VERSION, subject.getVersion());
		assertEquals(ExpirableTxnRecord.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}

	@Test
	void asGrpcWorks() {
		final var expected = grpcRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations()
				.toBuilder()
				.setParentConsensusTimestamp(MiscUtils.asTimestamp(packedParentConsTime))
				.build();

		subject = subjectRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations();
		subject.setExpiry(0L);
		subject.setSubmittingMember(UNKNOWN_SUBMITTING_MEMBER);

		final var grpcSubject = subject.asGrpc();

		var multiple = allToGrpc(List.of(subject, subject));

		assertEquals(expected, grpcSubject);
		assertEquals(List.of(expected, expected), multiple);
	}

	@Test
	void makeupNftAdjustmentsUsesNullForNullFungibleAdjusts() {
		assertNull(subject.makeupNftAdjustsMatching(null));
	}

	@Test
	void nullEqualsWorks() {
		assertNotEquals(null, subject);
		assertEquals(subject, subject);
	}

	@Test
	void objectContractWorks() {
		final var one = subject;
		final var two = DomainSerdesTest.recordOne();
		final var three = subjectRecordWithTokenTransfersAndScheduleRefCustomFees();

		assertNotEquals(null, one);
		assertNotEquals(new Object(), one);
		assertEquals(one, three);
		assertNotEquals(one, two);

		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(one.hashCode(), three.hashCode());
	}

	@Test
	void toStringWorksWithParentConsTime() {
		subject = subjectRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations();
		final var desired = "ExpirableTxnRecord{numChildRecords=2, receipt=TxnReceipt{status=INVALID_ACCOUNT_ID, " +
				"accountCreated=EntityId{shard=0, realm=0, num=3}, newTotalTokenSupply=0}, fee=555, " +
				"txnHash=6e6f742d7265616c6c792d612d68617368, txnId=TxnId{payer=EntityId{shard=0, realm=0, num=0}, " +
				"validStart=RichInstant{seconds=9999999999, nanos=0}, scheduled=false, nonce=0}, consensusTimestamp=" +
				"RichInstant{seconds=9999999999, nanos=0}, expiry=1234567, submittingMember=1, memo=Alpha bravo " +
				"charlie, " +
				"contractCreation=SolidityFnResult{gasUsed=55, bloom=, result=, error=null, contractId=" +
				"EntityId{shard=4, realm=3, num=2}, createdContractIds=[], logs=[" +
				"SolidityLog{data=4e6f6e73656e736963616c21, bloom=, contractId=null, topics=[]}]}, hbarAdjustments=" +
				"CurrencyAdjustments{readable=[0.0.2 -> -4, 0.0.1001 <- +2, 0.0.1002 <- +2]}, scheduleRef=" +
				"EntityId{shard=5, realm=6, num=7}, alias=test, parentConsensusTime=1970-01-15T06:56:07.000000890Z, " +
				"tokenAdjustments=1.2.3(CurrencyAdjustments{readable=[1.2.5 -> -1, 1.2.6 <- +1, 1.2.7 <- +1000]}), " +
				"1.2.4(CurrencyAdjustments{readable=[1.2.5 -> -1, 1.2.6 <- +1, 1.2.7 <- +1000]}), " +
				"1.2.2(NftAdjustments{readable=[1 1.2.5 1.2.6]}), assessedCustomFees=(FcAssessedCustomFee{token=" +
				"EntityId{shard=1, realm=2, num=9}, account=EntityId{shard=1, realm=2, num=8}, units=123, effective " +
				"payer accounts=[234]}), newTokenAssociations=(FcTokenAssociation{token=10, account=11})}";

		assertEquals(desired, subject.toString());
	}

	@Test
	void toStringWorksWithoutParentConsTime() {
		subject = subjectRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations();
		subject.setPackedParentConsensusTime(MISSING_PARENT_CONSENSUS_TIMESTAMP);
		final var desired = "ExpirableTxnRecord{numChildRecords=2, receipt=TxnReceipt{status=INVALID_ACCOUNT_ID, " +
				"accountCreated=EntityId{shard=0, realm=0, num=3}, newTotalTokenSupply=0}, fee=555, " +
				"txnHash=6e6f742d7265616c6c792d612d68617368, txnId=TxnId{payer=EntityId{shard=0, realm=0, num=0}, " +
				"validStart=RichInstant{seconds=9999999999, nanos=0}, scheduled=false, nonce=0}, " +
				"consensusTimestamp=RichInstant{seconds=9999999999, nanos=0}, expiry=1234567, submittingMember=1, " +
				"memo=Alpha bravo charlie, contractCreation=SolidityFnResult{gasUsed=55, bloom=, result=, error=null, " +
				"contractId=EntityId{shard=4, realm=3, num=2}, createdContractIds=[], " +
				"logs=[SolidityLog{data=4e6f6e73656e736963616c21, bloom=, contractId=null, topics=[]}]}, " +
				"hbarAdjustments=CurrencyAdjustments{readable=[0.0.2 -> -4, 0.0.1001 <- +2, 0.0.1002 <- +2]}, " +
				"scheduleRef=EntityId{shard=5, realm=6, num=7}, alias=test, tokenAdjustments=1.2.3" +
				"(CurrencyAdjustments{readable=[1.2.5 -> -1, 1.2.6 <- +1, 1.2.7 <- +1000]}), 1.2.4" +
				"(CurrencyAdjustments{readable=[1.2.5 -> -1, 1.2.6 <- +1, 1.2.7 <- +1000]}), 1.2.2" +
				"(NftAdjustments{readable=[1 1.2.5 1.2.6]}), assessedCustomFees=" +
				"(FcAssessedCustomFee{token=EntityId{shard=1, realm=2, num=9}, account=EntityId{shard=1, realm=2, " +
				"num=8}, units=123, effective payer accounts=[234]}), newTokenAssociations=" +
				"(FcTokenAssociation{token=10, account=11})}";
		assertEquals(desired, subject.toString());
	}

	@AfterEach
	void cleanup() {
		ExpirableTxnRecord.serdes = new DomainSerdes();
	}
}
