/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.state.submerkle;

import static com.hedera.services.state.merkle.internals.BitPackUtils.packedTime;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.MISSING_PARENT_CONSENSUS_TIMESTAMP;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.MISSING_PSEUDORANDOM_BYTES;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.UNKNOWN_SUBMITTING_MEMBER;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.allToGrpc;
import static com.hedera.services.state.submerkle.ExpirableTxnRecordTestHelper.fromGprc;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hedera.test.utils.TxnUtils.withNftAdjustments;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.mock;

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.crypto.Hash;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExpirableTxnRecordTest {
    private static final long expiry = 1_234_567L;
    private static final long submittingMember = 1L;
    private static final long packedParentConsTime = packedTime(expiry, 890);
    private static final short numChildRecords = 2;

    private static final byte[] pretendHash = "not-really-a-hash".getBytes();

    private static final byte[] pseudoRandomBytes = TxnUtils.randomUtf8Bytes(48);

    private static final TokenID nft = IdUtils.asToken("0.0.2");
    private static final TokenID tokenA = IdUtils.asToken("0.0.3");
    private static final TokenID tokenB = IdUtils.asToken("0.0.4");
    private static final AccountID sponsor = IdUtils.asAccount("0.0.5");
    private static final AccountID beneficiary = IdUtils.asAccount("0.0.6");
    private static final AccountID magician = IdUtils.asAccount("0.0.7");
    private static final AccountID spender = IdUtils.asAccount("0.0.8");
    private static final AccountID owner = IdUtils.asAccount("0.0.9");
    private static final EntityNum spenderNum = EntityNum.fromAccountId(spender);
    private static final EntityNum ownerNum = EntityNum.fromAccountId(owner);
    private static final List<TokenAssociation> newRelationships =
            List.of(new FcTokenAssociation(10, 11).toGrpc());

    private static final EntityId feeCollector = new EntityId(1, 2, 8);
    private static final EntityId token = new EntityId(1, 2, 9);
    private static final long units = 123L;
    private static final long initialAllowance = 100L;

    private static final AccountAmount reward1 =
            AccountAmount.newBuilder().setAccountID(sponsor).setAmount(100L).build();
    private static final AccountAmount reward2 =
            AccountAmount.newBuilder().setAccountID(spender).setAmount(1_000L).build();
    private static final TokenTransferList nftTokenTransfers =
            TokenTransferList.newBuilder()
                    .setToken(nft)
                    .addNftTransfers(
                            withNftAdjustments(
                                            nft,
                                            sponsor,
                                            beneficiary,
                                            1L,
                                            sponsor,
                                            beneficiary,
                                            2L,
                                            sponsor,
                                            beneficiary,
                                            3L)
                                    .getNftTransfers(0))
                    .build();
    private static final TokenTransferList aTokenTransfers =
            TokenTransferList.newBuilder()
                    .setToken(tokenA)
                    .addAllTransfers(
                            withAdjustments(sponsor, -1L, beneficiary, 1L, magician, 1000L)
                                    .getAccountAmountsList())
                    .build();
    private static final TokenTransferList bTokenTransfers =
            TokenTransferList.newBuilder()
                    .setToken(tokenB)
                    .addAllTransfers(
                            withAdjustments(sponsor, -1L, beneficiary, 1L, magician, 1000L)
                                    .getAccountAmountsList())
                    .build();
    private static final ScheduleID scheduleID = IdUtils.asSchedule("5.6.7");
    private static final FcAssessedCustomFee balanceChange =
            new FcAssessedCustomFee(feeCollector, token, units, new long[] {234L});
    private static final FcTokenAllowanceId fungibleAllowanceId =
            FcTokenAllowanceId.from(EntityNum.fromTokenId(tokenA), spenderNum);
    private static final Map<EntityNum, Map<EntityNum, Long>> cryptoAllowances =
            new TreeMap<>() {
                {
                    put(
                            ownerNum,
                            new TreeMap<>() {
                                {
                                    put(spenderNum, initialAllowance);
                                }
                            });
                }
            };
    private static final Map<EntityNum, Map<FcTokenAllowanceId, Long>> fungibleAllowances =
            new TreeMap<>() {
                {
                    put(
                            ownerNum,
                            new TreeMap<>() {
                                {
                                    put(fungibleAllowanceId, initialAllowance);
                                }
                            });
                }
            };

    private ExpirableTxnRecord subject;

    @BeforeEach
    void setup() {
        subject = subjectRecordWithTokenTransfersAndScheduleRefCustomFees();
    }

    private static ExpirableTxnRecord
            subjectRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations() {
        final var source = grpcRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations();
        final var s = ExpirableTxnRecordTestHelper.fromGprc(source);
        setNonGrpcDefaultsOn(s);
        return s;
    }

    private static TransactionRecord
            grpcRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations() {
        return TxnUtils.recordOne().asGrpc().toBuilder()
                .setTransactionHash(ByteString.copyFrom(pretendHash))
                .setContractCreateResult(TxnUtils.recordTwo().getContractCallResult().toGrpc())
                .addAllTokenTransferLists(
                        List.of(aTokenTransfers, bTokenTransfers, nftTokenTransfers))
                .setScheduleRef(scheduleID)
                .addAssessedCustomFees(balanceChange.toGrpc())
                .addAllAutomaticTokenAssociations(newRelationships)
                .addAllPaidStakingRewards(List.of(reward1, reward2))
                .setAlias(ByteString.copyFromUtf8("test"))
                .setEthereumHash(ByteString.copyFrom(pretendHash))
                .setPrngBytes(ByteStringUtils.wrapUnsafely(pseudoRandomBytes))
                .setPrngNumber(10)
                .build();
    }

    private static ExpirableTxnRecord subjectRecordWithTokenTransfersAndScheduleRefCustomFees() {
        final var s =
                fromGprc(
                        TxnUtils.recordOne().asGrpc().toBuilder()
                                .setTransactionHash(ByteString.copyFrom(pretendHash))
                                .setContractCreateResult(
                                        TxnUtils.recordTwo().getContractCallResult().toGrpc())
                                .addAllTokenTransferLists(List.of(aTokenTransfers, bTokenTransfers))
                                .setScheduleRef(scheduleID)
                                .addAssessedCustomFees(balanceChange.toGrpc())
                                .addAllPaidStakingRewards(List.of(reward1, reward2))
                                .setAlias(
                                        ByteString.copyFrom(
                                                "test".getBytes(StandardCharsets.UTF_8)))
                                .setEthereumHash(ByteString.copyFrom(pretendHash))
                                .setPrngNumber(10)
                                .setPrngBytes(ByteStringUtils.wrapUnsafely(pseudoRandomBytes))
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
    void consensusSecondGetterWorks() {
        final var tinySubject =
                ExpirableTxnRecord.newBuilder()
                        .setConsensusTime(new RichInstant(1_234_567, 890))
                        .build();
        assertEquals(1_234_567, tinySubject.getConsensusSecond());
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
    void serializableDetWorks() {
        assertEquals(ExpirableTxnRecord.CURRENT_VERSION, subject.getVersion());
        assertEquals(ExpirableTxnRecord.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    }

    @Test
    void asGrpcWorks() {
        final var expected =
                grpcRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations().toBuilder()
                        .setParentConsensusTimestamp(MiscUtils.asTimestamp(packedParentConsTime))
                        .setEthereumHash(ByteString.copyFrom(pretendHash))
                        .build();

        subject = subjectRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations();
        subject.setExpiry(0L);
        subject.setSubmittingMember(UNKNOWN_SUBMITTING_MEMBER);
        subject.setPseudoRandomNumber(10);

        final var grpcSubject = subject.asGrpc();

        var multiple = allToGrpc(List.of(subject, subject));

        assertEquals(expected, grpcSubject);
        assertEquals(List.of(expected, expected), multiple);
    }

    @Test
    void asGrpcWithBothPseudoRandomNumbersSetWorks() {
        final var expected =
                grpcRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations().toBuilder()
                        .setParentConsensusTimestamp(MiscUtils.asTimestamp(packedParentConsTime))
                        .setEthereumHash(ByteString.copyFrom(pretendHash))
                        .setPrngBytes(ByteString.copyFrom(MISSING_PSEUDORANDOM_BYTES))
                        .setPrngNumber(10)
                        .build();

        subject = subjectRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations();
        subject.setExpiry(0L);
        subject.setSubmittingMember(UNKNOWN_SUBMITTING_MEMBER);
        subject.setPseudoRandomBytes(MISSING_PSEUDORANDOM_BYTES);
        subject.setPseudoRandomNumber(10);

        final var grpcSubject = subject.asGrpc();

        var multiple = allToGrpc(List.of(subject, subject));

        assertEquals(expected, grpcSubject);
        assertEquals(List.of(expected, expected), multiple);
    }

    @Test
    void asGrpcWithBothPseudoRandomBytesSetWorks() {
        final var expected =
                grpcRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations().toBuilder()
                        .setParentConsensusTimestamp(MiscUtils.asTimestamp(packedParentConsTime))
                        .setEthereumHash(ByteString.copyFrom(pretendHash))
                        .setPrngNumber(-1)
                        .setPrngBytes(ByteStringUtils.wrapUnsafely(pseudoRandomBytes))
                        .build();

        subject = subjectRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations();
        subject.setPseudoRandomNumber(-1);
        subject.setExpiry(0L);
        subject.setSubmittingMember(UNKNOWN_SUBMITTING_MEMBER);
        subject.setPseudoRandomBytes(pseudoRandomBytes);

        final var grpcSubject = subject.asGrpc();

        var multiple = allToGrpc(List.of(subject, subject));

        assertEquals(expected, grpcSubject);
        assertEquals(List.of(expected, expected), multiple);
    }

    @Test
    void nullEqualsWorks() {
        final var sameButDifferent = subject;
        assertNotEquals(subject, null);
        assertNotEquals(subject, new Object());
        assertEquals(subject, sameButDifferent);
    }

    @Test
    void equalsDetectsFeeDiff() {
        final var a = new ExpirableTxnRecord();
        final var b = ExpirableTxnRecord.newBuilder().setFee(123).build();
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsChildRecordsDif() {
        final var a = new ExpirableTxnRecord();
        final var b = ExpirableTxnRecord.newBuilder().setNumChildRecords((short) 123).build();
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsPackedConsTimeDif() {
        final var a = new ExpirableTxnRecord();
        final var b =
                ExpirableTxnRecord.newBuilder()
                        .setParentConsensusTime(Instant.ofEpochSecond(1_234_567))
                        .build();
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsDiffExpiry() {
        final var a = new ExpirableTxnRecord();
        final var b = new ExpirableTxnRecord();
        b.setExpiry(1_234_567L);
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsDiffSubmitter() {
        final var a = new ExpirableTxnRecord();
        final var b = new ExpirableTxnRecord();
        b.setSubmittingMember(1_234_567L);
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsDiffReceipt() {
        final var a = new ExpirableTxnRecord();
        final var b = ExpirableTxnRecord.newBuilder().setReceipt(new TxnReceipt()).build();
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsDiffHash() {
        final var a = new ExpirableTxnRecord();
        final var b = ExpirableTxnRecord.newBuilder().setTxnHash(new byte[] {(byte) 0xFF}).build();
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsDiffTxnId() {
        final var a = new ExpirableTxnRecord();
        final var b = ExpirableTxnRecord.newBuilder().setTxnId(new TxnId()).build();
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsDiffConsTime() {
        final var a = new ExpirableTxnRecord();
        final var b =
                ExpirableTxnRecord.newBuilder()
                        .setConsensusTime(new RichInstant(1_234_567, 890))
                        .build();
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsDiffMemo() {
        final var a = new ExpirableTxnRecord();
        final var b = ExpirableTxnRecord.newBuilder().setMemo("HI").build();
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsDiffCallResult() {
        final var a = new ExpirableTxnRecord();
        final var b =
                ExpirableTxnRecord.newBuilder().setContractCallResult(new EvmFnResult()).build();
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsDiffCreateResult() {
        final var a = new ExpirableTxnRecord();
        final var b =
                ExpirableTxnRecord.newBuilder().setContractCreateResult(new EvmFnResult()).build();
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsDiffHbarAdjusts() {
        final var a = new ExpirableTxnRecord();
        final var b =
                ExpirableTxnRecord.newBuilder()
                        .setHbarAdjustments(new CurrencyAdjustments())
                        .build();
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsDiffTokens() {
        final var a = new ExpirableTxnRecord();
        final var b = ExpirableTxnRecord.newBuilder().setTokens(new ArrayList<>()).build();
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsDiffPseudoRandomData() {
        final var a = new ExpirableTxnRecord();
        var b = ExpirableTxnRecord.newBuilder().setPseudoRandomNumber(10).build();
        assertNotEquals(a, b);

        b = ExpirableTxnRecord.newBuilder().setPseudoRandomBytes(pseudoRandomBytes).build();
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsDiffTokenAdjusts() {
        final var a = new ExpirableTxnRecord();
        final var b =
                ExpirableTxnRecord.newBuilder().setTokenAdjustments(new ArrayList<>()).build();
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsDiffNftAdjusts() {
        final var a = new ExpirableTxnRecord();
        final var b =
                ExpirableTxnRecord.newBuilder().setNftTokenAdjustments(new ArrayList<>()).build();
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsDiffAssessedFees() {
        final var a = new ExpirableTxnRecord();
        final var b =
                ExpirableTxnRecord.newBuilder().setAssessedCustomFees(new ArrayList<>()).build();
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsDiffTokenAssociations() {
        final var a = new ExpirableTxnRecord();
        final var b =
                ExpirableTxnRecord.newBuilder().setNewTokenAssociations(new ArrayList<>()).build();
        assertNotEquals(a, b);
    }

    @Test
    void equalsDetectsDiffAlias() {
        final var a = new ExpirableTxnRecord();
        final var b =
                ExpirableTxnRecord.newBuilder().setAlias(ByteString.copyFromUtf8("asdf")).build();
        assertNotEquals(a, b);
    }

    @Test
    void objectContractWorks() {
        final var two = TxnUtils.recordOne();
        final var three = subjectRecordWithTokenTransfersAndScheduleRefCustomFees();

        assertNotEquals(two, three);
        assertNotEquals(two.hashCode(), three.hashCode());
    }

    @Test
    void toStringWorksWithParentConsTime() {
        subject = subjectRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations();
        final var desired =
                "ExpirableTxnRecord{numChildRecords=2,"
                    + " receipt=TxnReceipt{status=INVALID_ACCOUNT_ID,"
                    + " accountCreated=EntityId{shard=0, realm=0, num=3}, newTotalTokenSupply=0},"
                    + " fee=555, txnHash=6e6f742d7265616c6c792d612d68617368,"
                    + " txnId=TxnId{payer=EntityId{shard=0, realm=0, num=0},"
                    + " validStart=RichInstant{seconds=9999999999, nanos=0}, scheduled=false,"
                    + " nonce=0}, consensusTimestamp=RichInstant{seconds=9999999999, nanos=0},"
                    + " expiry=1234567, submittingMember=1, memo=Alpha bravo charlie,"
                    + " contractCreation=EvmFnResult{gasUsed=55, bloom=, result=, error=null,"
                    + " contractId=EntityId{shard=4, realm=3, num=2}, createdContractIds=[],"
                    + " logs=[EvmLog{data=4e6f6e73656e736963616c21, bloom=, contractId=null,"
                    + " topics=[]}], evmAddress=, gas=1000000, amount=0,"
                    + " functionParameters=53656e7369626c6521, senderId=null},"
                    + " hbarAdjustments=CurrencyAdjustments{readable=[0.0.2 -> -4, 0.0.1001 <- +2,"
                    + " 0.0.1002 <- +2]}, stakingRewardsPaid=CurrencyAdjustments{readable=[0.0.5 <-"
                    + " +100, 0.0.8 <- +1000]}, scheduleRef=EntityId{shard=5, realm=6, num=7},"
                    + " alias=test, ethereumHash=6e6f742d7265616c6c792d612d68617368,"
                    + " pseudoRandomNumber=10, pseudoRandomBytes=,"
                    + " parentConsensusTime=1970-01-15T06:56:07.000000890Z,"
                    + " tokenAdjustments=0.0.3(CurrencyAdjustments{readable=[0.0.5 -> -1, 0.0.6 <-"
                    + " +1, 0.0.7 <- +1000]}), 0.0.4(CurrencyAdjustments{readable=[0.0.5 -> -1,"
                    + " 0.0.6 <- +1, 0.0.7 <- +1000]}), 0.0.2(NftAdjustments{readable=[1 0.0.5"
                    + " 0.0.6]}), assessedCustomFees=(FcAssessedCustomFee{token=EntityId{shard=1,"
                    + " realm=2, num=9}, account=EntityId{shard=1, realm=2, num=8}, units=123,"
                    + " effective payer accounts=[234]}),"
                    + " newTokenAssociations=(FcTokenAssociation{token=10, account=11})}";

        assertEquals(desired, subject.toString());
    }

    @Test
    void toStringWorksWithoutParentConsTime() {
        subject = subjectRecordWithTokenTransfersScheduleRefCustomFeesAndTokenAssociations();
        subject.setPackedParentConsensusTime(MISSING_PARENT_CONSENSUS_TIMESTAMP);
        final var desired =
                "ExpirableTxnRecord{numChildRecords=2,"
                    + " receipt=TxnReceipt{status=INVALID_ACCOUNT_ID,"
                    + " accountCreated=EntityId{shard=0, realm=0, num=3}, newTotalTokenSupply=0},"
                    + " fee=555, txnHash=6e6f742d7265616c6c792d612d68617368,"
                    + " txnId=TxnId{payer=EntityId{shard=0, realm=0, num=0},"
                    + " validStart=RichInstant{seconds=9999999999, nanos=0}, scheduled=false,"
                    + " nonce=0}, consensusTimestamp=RichInstant{seconds=9999999999, nanos=0},"
                    + " expiry=1234567, submittingMember=1, memo=Alpha bravo charlie,"
                    + " contractCreation=EvmFnResult{gasUsed=55, bloom=, result=, error=null,"
                    + " contractId=EntityId{shard=4, realm=3, num=2}, createdContractIds=[],"
                    + " logs=[EvmLog{data=4e6f6e73656e736963616c21, bloom=, contractId=null,"
                    + " topics=[]}], evmAddress=, gas=1000000, amount=0,"
                    + " functionParameters=53656e7369626c6521, senderId=null},"
                    + " hbarAdjustments=CurrencyAdjustments{readable=[0.0.2 -> -4, 0.0.1001 <- +2,"
                    + " 0.0.1002 <- +2]}, stakingRewardsPaid=CurrencyAdjustments{readable=[0.0.5 <-"
                    + " +100, 0.0.8 <- +1000]}, scheduleRef=EntityId{shard=5, realm=6, num=7},"
                    + " alias=test, ethereumHash=6e6f742d7265616c6c792d612d68617368,"
                    + " pseudoRandomNumber=10, pseudoRandomBytes=,"
                    + " tokenAdjustments=0.0.3(CurrencyAdjustments{readable=[0.0.5 -> -1, 0.0.6 <-"
                    + " +1, 0.0.7 <- +1000]}), 0.0.4(CurrencyAdjustments{readable=[0.0.5 -> -1,"
                    + " 0.0.6 <- +1, 0.0.7 <- +1000]}), 0.0.2(NftAdjustments{readable=[1 0.0.5"
                    + " 0.0.6]}), assessedCustomFees=(FcAssessedCustomFee{token=EntityId{shard=1,"
                    + " realm=2, num=9}, account=EntityId{shard=1, realm=2, num=8}, units=123,"
                    + " effective payer accounts=[234]}),"
                    + " newTokenAssociations=(FcTokenAssociation{token=10, account=11})}";
        assertEquals(desired, subject.toString());
    }
}
