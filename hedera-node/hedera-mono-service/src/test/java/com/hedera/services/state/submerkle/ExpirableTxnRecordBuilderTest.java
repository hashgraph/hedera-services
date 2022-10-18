/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpirableTxnRecordBuilderTest {
    private static final long parentConsSec = 1_234_567L;
    private static final int parentConsNanos = 890;
    private static final long packedParentConsTime = packedTime(parentConsSec, parentConsNanos);
    private static final Instant parentConsTime =
            Instant.ofEpochSecond(parentConsSec, parentConsNanos);

    @Mock private TxnReceipt.Builder receiptBuilder;

    private ExpirableTxnRecord.Builder subject;

    @BeforeEach
    void setUp() {
        subject = ExpirableTxnRecord.newBuilder();
    }

    @Test
    void builderPropagatesChildTxnMeta() {
        subject.setNumChildRecords((short) 12);
        subject.setParentConsensusTime(parentConsTime);

        final var result = subject.build();
        assertEquals(12, result.getNumChildRecords());
        assertEquals(packedParentConsTime, result.getPackedParentConsensusTime());
    }

    @Test
    void parentConsensusTimeMappedToAndFromGrpc() {
        final var grpcRecord =
                TransactionRecord.newBuilder()
                        .setReceipt(
                                TransactionReceipt.newBuilder()
                                        .setAccountID(IdUtils.asAccount("0.0.3")))
                        .setTransactionID(
                                TransactionID.newBuilder().setAccountID(IdUtils.asAccount("0.0.2")))
                        .setConsensusTimestamp(MiscUtils.asTimestamp(parentConsTime.plusNanos(1)))
                        .setParentConsensusTimestamp(MiscUtils.asTimestamp(parentConsTime))
                        .setPrngNumber(10)
                        .build();

        final var subject = ExpirableTxnRecordTestHelper.fromGprc(grpcRecord);

        assertEquals(grpcRecord, subject.asGrpc());
    }

    @Test
    void usesReceiptBuilderIfPresent() {
        final var status = "INVALID_ACCOUNT_ID";
        final var statusReceipt = TxnReceipt.newBuilder().setStatus(status);
        subject.setReceiptBuilder(statusReceipt);
        final var record = subject.build();
        assertEquals(status, record.getReceipt().getStatus());
    }

    @Test
    void subtractingOffNoHbarAdjustsIsNoop() {
        final var that = ExpirableTxnRecord.newBuilder();

        final var someAdjusts = new CurrencyAdjustments(new long[] {+1, -1}, new long[] {1L, 2L});
        subject.setHbarAdjustments(someAdjusts);

        subject.excludeHbarChangesFrom(that);

        assertSame(someAdjusts, subject.getHbarAdjustments());
    }

    @Test
    void canSubtractOffExcludedHbarAdjustmentsWithSameStop() {
        final var inThisButNotThat = new EntityId(0, 0, 2);
        final var firstInBoth = new EntityId(0, 0, 3);
        final var secondInBoth = new EntityId(0, 0, 4);
        final var inThatButNotThis = new EntityId(0, 0, 5);
        final var thirdInBoth = new EntityId(0, 0, 6);

        final var thisAdjusts =
                new CurrencyAdjustments(
                        new long[] {-10, +6, +3, +1},
                        new long[] {
                            inThisButNotThat.num(),
                            firstInBoth.num(),
                            secondInBoth.num(),
                            thirdInBoth.num()
                        });
        final var thatAdjusts =
                new CurrencyAdjustments(
                        new long[] {-2, -4, +5, +1},
                        new long[] {
                            firstInBoth.num(),
                            secondInBoth.num(),
                            inThatButNotThis.num(),
                            thirdInBoth.num()
                        });

        final var that = ExpirableTxnRecord.newBuilder();
        that.setHbarAdjustments(thatAdjusts);

        subject.setHbarAdjustments(thisAdjusts);
        subject.excludeHbarChangesFrom(that);

        final var expectedChanges = new long[] {-10, +8, +7, -5};
        final var expectedAccounts =
                new long[] {
                    inThisButNotThat.num(),
                    firstInBoth.num(),
                    secondInBoth.num(),
                    inThatButNotThis.num()
                };
        assertArrayEquals(expectedChanges, subject.getHbarAdjustments().hbars);
        assertArrayEquals(expectedAccounts, subject.getHbarAdjustments().accountNums);
    }

    @Test
    void canSubtractOffExcludedHbarAdjustmentsWithThisEarlyStop() {
        final var firstInBoth = new EntityId(0, 0, 3);
        final var secondInBoth = new EntityId(0, 0, 4);
        final var inThatButNotThis = new EntityId(0, 0, 5);

        final var thisAdjusts =
                new CurrencyAdjustments(
                        new long[] {+6, +3}, new long[] {firstInBoth.num(), secondInBoth.num()});
        final var thatAdjusts =
                new CurrencyAdjustments(
                        new long[] {-2, -4, +5},
                        new long[] {firstInBoth.num(), secondInBoth.num(), inThatButNotThis.num()});

        final var that = ExpirableTxnRecord.newBuilder();
        that.setHbarAdjustments(thatAdjusts);

        subject.setHbarAdjustments(thisAdjusts);
        subject.excludeHbarChangesFrom(that);

        final var expectedChanges = new long[] {+8, +7, -5};
        final var expectedAccounts =
                new long[] {firstInBoth.num(), secondInBoth.num(), inThatButNotThis.num()};
        assertArrayEquals(expectedChanges, subject.getHbarAdjustments().hbars);
        assertArrayEquals(expectedAccounts, subject.getHbarAdjustments().accountNums);
    }

    @Test
    void canSubtractOffExcludedHbarAdjustmentsWithThatEarlyStop() {
        final var firstInThisButNotThat = new EntityId(0, 0, 2);
        final var firstInBoth = new EntityId(0, 0, 3);
        final var secondInBoth = new EntityId(0, 0, 4);
        final var secondInThisButNotThat = new EntityId(0, 0, 6);

        final var thisAdjusts =
                new CurrencyAdjustments(
                        new long[] {+10, +6, +3, -19},
                        new long[] {
                            firstInThisButNotThat.num(),
                            firstInBoth.num(),
                            secondInBoth.num(),
                            secondInThisButNotThat.num()
                        });
        final var thatAdjusts =
                new CurrencyAdjustments(
                        new long[] {+2, +4}, new long[] {firstInBoth.num(), secondInBoth.num()});

        final var that = ExpirableTxnRecord.newBuilder();
        that.setHbarAdjustments(thatAdjusts);

        subject.setHbarAdjustments(thisAdjusts);
        subject.excludeHbarChangesFrom(that);

        final var expectedChanges = new long[] {+10, +4, -1, -19};
        final var expectedAccounts =
                new long[] {
                    firstInThisButNotThat.num(),
                    firstInBoth.num(),
                    secondInBoth.num(),
                    secondInThisButNotThat.num()
                };
        assertArrayEquals(expectedChanges, subject.getHbarAdjustments().hbars);
        assertArrayEquals(expectedAccounts, subject.getHbarAdjustments().accountNums);
    }

    @Test
    void revertClearsAllSideEffects() {
        subject.setTokens(List.of(MISSING_ENTITY_ID));
        subject.setHbarAdjustments(
                new CurrencyAdjustments(new long[] {1}, new long[] {MISSING_ENTITY_ID.num()}));
        subject.setStakingRewardsPaid(
                new CurrencyAdjustments(new long[] {1}, new long[] {MISSING_ENTITY_ID.num()}));
        subject.setReceiptBuilder(receiptBuilder);
        subject.setTokenAdjustments(
                List.of(
                        new CurrencyAdjustments(
                                new long[] {1}, new long[] {MISSING_ENTITY_ID.num()})));
        subject.setContractCallResult(new EvmFnResult());
        subject.setNftTokenAdjustments(List.of(new NftAdjustments()));
        subject.setContractCreateResult(new EvmFnResult());
        subject.setNewTokenAssociations(List.of(new FcTokenAssociation(1, 2)));
        subject.setAssessedCustomFees(
                List.of(new FcAssessedCustomFee(MISSING_ENTITY_ID, 1, new long[] {1L})));
        subject.setAlias(ByteString.copyFromUtf8("aaa"));

        subject.revert();

        verify(receiptBuilder).revert();

        assertNull(subject.getTokens());
        assertNull(subject.getScheduleRef());
        assertNull(subject.getHbarAdjustments());
        assertNull(subject.getStakingRewardsPaid());
        assertNull(subject.getTokenAdjustments());
        assertNotNull(subject.getContractCallResult());
        assertNull(subject.getNftTokenAdjustments());
        assertNull(subject.getContractCreateResult());
        assertNull(subject.getAssessedCustomFees());
        assertTrue(subject.getNewTokenAssociations().isEmpty());
        assertTrue(subject.getAlias().isEmpty());
        assertEquals(0, subject.getPseudoRandomBytes().length);
        assertEquals(-1, subject.getPseudoRandomNumber());
    }

    @Test
    void revertOnlyPossibleWithReceiptBuilder() {
        subject.setReceipt(new TxnReceipt());

        assertThrows(IllegalStateException.class, subject::revert);
    }

    @Test
    void canToggleUnsuccessfulExternalization() {
        final var liveReceipt = TxnReceipt.newBuilder();
        liveReceipt.setStatus(TxnReceipt.SUCCESS_LITERAL);
        subject.setReceiptBuilder(liveReceipt);

        assertFalse(subject.shouldNotBeExternalized());

        subject.onlyExternalizeIfSuccessful();
        assertFalse(subject.shouldNotBeExternalized());

        liveReceipt.setStatus(TxnReceipt.REVERTED_SUCCESS_LITERAL);
        assertTrue(subject.shouldNotBeExternalized());
    }
}
