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
package com.hedera.services.txns.submission;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.records.RecordCache;
import com.hedera.services.stats.MiscSpeedometers;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.UncheckedSubmitBody;
import com.swirlds.common.system.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PlatformSubmissionManagerTest {
    TransactionID txnId = TransactionID.newBuilder().setAccountID(asAccount("0.0.2")).build();
    TransactionID uncheckedTxnId =
            TransactionID.newBuilder().setAccountID(asAccount("1.0.2")).build();
    Transaction signedTxn =
            Transaction.newBuilder()
                    .setBodyBytes(
                            TransactionBody.newBuilder()
                                    .setCryptoTransfer(
                                            CryptoTransferTransactionBody.getDefaultInstance())
                                    .setTransactionID(txnId)
                                    .build()
                                    .toByteString())
                    .build();
    Transaction uncheckedSubTxn =
            Transaction.newBuilder()
                    .setBodyBytes(
                            TransactionBody.newBuilder()
                                    .setTransactionID(uncheckedTxnId)
                                    .setUncheckedSubmit(
                                            UncheckedSubmitBody.newBuilder()
                                                    .setTransactionBytes(signedTxn.toByteString()))
                                    .build()
                                    .toByteString())
                    .build();
    Transaction invalidUncheckedSubTxn =
            Transaction.newBuilder()
                    .setBodyBytes(
                            TransactionBody.newBuilder()
                                    .setTransactionID(uncheckedTxnId)
                                    .setUncheckedSubmit(
                                            UncheckedSubmitBody.newBuilder()
                                                    .setTransactionBytes(
                                                            ByteString.copyFrom(
                                                                    "INVALID".getBytes())))
                                    .build()
                                    .toByteString())
                    .build();
    SignedTxnAccessor accessor;
    SignedTxnAccessor uncheckedAccessor;
    SignedTxnAccessor invalidUncheckedAccessor;

    Platform platform;
    RecordCache recordCache;
    MiscSpeedometers speedometers;

    PlatformSubmissionManager subject;

    @BeforeEach
    void setup() throws InvalidProtocolBufferException {
        platform = mock(Platform.class);
        recordCache = mock(RecordCache.class);
        speedometers = mock(MiscSpeedometers.class);

        accessor = SignedTxnAccessor.from(signedTxn.toByteArray(), signedTxn);
        uncheckedAccessor = SignedTxnAccessor.from(uncheckedSubTxn.toByteArray(), uncheckedSubTxn);
        invalidUncheckedAccessor =
                SignedTxnAccessor.from(
                        invalidUncheckedSubTxn.toByteArray(), invalidUncheckedSubTxn);

        subject = new PlatformSubmissionManager(platform, recordCache, speedometers);
    }

    @Test
    void updatesRecordCacheWhenTxnIsCreated() {
        // setup:
        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);

        given(platform.createTransaction(captor.capture())).willReturn(true);

        // when:
        var result = subject.trySubmission(accessor);

        // then:
        assertArrayEquals(signedTxn.toByteArray(), captor.getValue());
        assertEquals(OK, result);
        // and:
        verify(recordCache).addPreConsensus(accessor.getTxnId());
    }

    @Test
    void updatesNotCreatedStatOnFail() {
        given(platform.createTransaction(any())).willReturn(false);

        // when:
        var result = subject.trySubmission(accessor);

        // then:
        assertEquals(PLATFORM_TRANSACTION_NOT_CREATED, result);
        // and:
        verify(recordCache, never()).addPreConsensus(any());
        verify(speedometers).cyclePlatformTxnRejections();
    }

    @Test
    void submitsChildInsteadOfParentForUnchecked() {
        // setup:
        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);

        given(platform.createTransaction(captor.capture())).willReturn(true);

        // when:
        var result = subject.trySubmission(uncheckedAccessor);

        // then:
        assertArrayEquals(signedTxn.toByteArray(), captor.getValue());
        assertEquals(OK, result);
        // and:
        verify(recordCache).addPreConsensus(accessor.getTxnId());
    }

    @Test
    void handlesInvalidUncheckedSubmitAsExpected() {
        given(platform.createTransaction(any())).willReturn(true);

        // when:
        var result = subject.trySubmission(invalidUncheckedAccessor);

        // then:
        assertEquals(PLATFORM_TRANSACTION_NOT_CREATED, result);
        // and:
        verify(recordCache, never()).addPreConsensus(accessor.getTxnId());
        verify(speedometers).cyclePlatformTxnRejections();
    }
}
