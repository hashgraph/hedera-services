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
import static com.hedera.test.utils.TxnUtils.timestampFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_START;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_ID_FIELD_NOT_ALLOWED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.anyLong;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.longThat;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.records.RecordCache;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyntaxPrecheckTest {
    private int validityBufferOverride = 7;
    private AccountID node = asAccount("0.0.3");
    private AccountID payer = asAccount("0.0.13257");
    private long duration = 1_234;
    private Instant startTime = Instant.now();
    private TransactionID txnId =
            TransactionID.newBuilder()
                    .setAccountID(payer)
                    .setTransactionValidStart(
                            timestampFrom(startTime.getEpochSecond(), startTime.getNano()))
                    .build();
    private String memo =
            "Our souls, which to advance their state / Were gone out, hung twixt her and me.";
    private TransactionBody txn;

    @Mock private OptionValidator validator;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private RecordCache recordCache;

    private SyntaxPrecheck subject;

    @BeforeEach
    void setup() {
        subject = new SyntaxPrecheck(recordCache, validator, dynamicProperties);

        txn =
                TransactionBody.newBuilder()
                        .setTransactionID(txnId)
                        .setTransactionValidDuration(Duration.newBuilder().setSeconds(duration))
                        .setNodeAccountID(node)
                        .setMemo(memo)
                        .build();
    }

    @Test
    void assertsExtantTransactionId() {
        // when:
        var status = subject.validate(TransactionBody.getDefaultInstance());

        // then:
        assertEquals(INVALID_TRANSACTION_ID, status);
    }

    @Test
    void rejectsUseOfScheduledField() {
        // setup:
        txn =
                txn.toBuilder()
                        .setTransactionID(TransactionID.newBuilder().setScheduled(true).build())
                        .build();

        // when:
        var status = subject.validate(txn);

        // then:
        assertEquals(TRANSACTION_ID_FIELD_NOT_ALLOWED, status);
    }

    @Test
    void rejectsUseOfNonceField() {
        // setup:
        txn =
                txn.toBuilder()
                        .setTransactionID(TransactionID.newBuilder().setNonce(12).build())
                        .build();

        // when:
        var status = subject.validate(txn);

        // then:
        assertEquals(TRANSACTION_ID_FIELD_NOT_ALLOWED, status);
    }

    @Test
    void rejectsDuplicateTxnId() {
        given(recordCache.isReceiptPresent(txnId)).willReturn(true);

        // when:
        var status = subject.validate(txn);

        // then:
        assertEquals(DUPLICATE_TRANSACTION, status);
    }

    @Test
    void assertsValidDuration() {
        given(validator.isPlausibleTxnFee(anyLong())).willReturn(true);
        given(validator.isPlausibleAccount(payer)).willReturn(true);
        given(validator.isThisNodeAccount(node)).willReturn(true);
        given(validator.memoCheck(memo)).willReturn(OK);

        // when:
        var status = subject.validate(txn);

        // then:
        assertEquals(INVALID_TRANSACTION_DURATION, status);
    }

    @Test
    void assertsValidChronology() {
        given(validator.isPlausibleTxnFee(anyLong())).willReturn(true);
        given(validator.isPlausibleAccount(payer)).willReturn(true);
        given(validator.isThisNodeAccount(node)).willReturn(true);
        given(validator.memoCheck(memo)).willReturn(OK);
        given(validator.isValidTxnDuration(duration)).willReturn(true);
        given(dynamicProperties.minValidityBuffer()).willReturn(validityBufferOverride);
        given(
                        validator.chronologyStatusForTxn(
                                argThat(startTime::equals),
                                longThat(l -> l == (duration - validityBufferOverride)),
                                any()))
                .willReturn(INVALID_TRANSACTION_START);

        // when:
        var status = subject.validate(txn);

        // then:
        assertEquals(INVALID_TRANSACTION_START, status);
    }

    @Test
    void assertsPlausibleTxnFee() {
        // when:
        var status = subject.validate(txn);

        // then:
        assertEquals(INSUFFICIENT_TX_FEE, status);
    }

    @Test
    void assertsPlausiblePayer() {
        given(validator.isPlausibleTxnFee(anyLong())).willReturn(true);
        given(validator.isPlausibleAccount(payer)).willReturn(false);

        // when:
        var status = subject.validate(txn);

        // then:
        assertEquals(PAYER_ACCOUNT_NOT_FOUND, status);
    }

    @Test
    void assertsPlausibleNode() {
        given(validator.isPlausibleTxnFee(anyLong())).willReturn(true);
        given(validator.isPlausibleAccount(payer)).willReturn(true);
        given(validator.isThisNodeAccount(node)).willReturn(false);

        // when:
        var status = subject.validate(txn);

        // then:
        assertEquals(INVALID_NODE_ACCOUNT, status);
    }

    @Test
    void assertsValidMemo() {
        given(validator.isPlausibleTxnFee(anyLong())).willReturn(true);
        given(validator.isPlausibleAccount(payer)).willReturn(true);
        given(validator.isThisNodeAccount(node)).willReturn(true);
        given(validator.memoCheck(memo)).willReturn(INVALID_ZERO_BYTE_IN_STRING);

        // when:
        var status = subject.validate(txn);

        // then:
        assertEquals(INVALID_ZERO_BYTE_IN_STRING, status);
    }
}
