/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.test.meta;

import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withFailureReason;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SigTransactionMetadataBuilderTest {
    public static final com.hederahashgraph.api.proto.java.Key A_COMPLEX_KEY =
            com.hederahashgraph.api.proto.java.Key.newBuilder()
                    .setThresholdKey(
                            ThresholdKey.newBuilder()
                                    .setThreshold(2)
                                    .setKeys(
                                            KeyList.newBuilder()
                                                    .addKeys(
                                                            com.hederahashgraph.api.proto.java.Key
                                                                    .newBuilder()
                                                                    .setEd25519(
                                                                            ByteString.copyFrom(
                                                                                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                                                                            .getBytes())))
                                                    .addKeys(
                                                            com.hederahashgraph.api.proto.java.Key
                                                                    .newBuilder()
                                                                    .setEd25519(
                                                                            ByteString.copyFrom(
                                                                                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                                                                                            .getBytes())))))
                    .build();
    private Timestamp consensusTimestamp = Timestamp.newBuilder().setSeconds(1_234_567L).build();
    private Key key = A_COMPLEX_KEY;
    private AccountID payer = AccountID.newBuilder().setAccountNum(3L).build();
    private Long payerNum = 3L;
    @Mock private HederaKey payerKey;
    final AccountID otherAccountId = AccountID.newBuilder().setAccountNum(12345L).build();
    @Mock private HederaKey otherKey;
    @Mock private AccountKeyLookup keyLookup;
    private SigTransactionMetadataBuilder subject;
    private TransactionMetadata meta;

    @BeforeEach
    void setUp() {}

    @Test
    void gettersWorkAsExpectedWhenOnlyPayerKeyExist() {
        final var txn = createAccountTransaction();
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));

        subject =
                new SigTransactionMetadataBuilder<>(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer);
        meta = subject.build();

        assertFalse(meta.failed());
        assertEquals(txn, meta.txnBody());
        assertEquals(List.of(payerKey), meta.requiredKeys());
    }

    @Test
    void nullInputToBuilderArgumentsThrows() {
        final var subject = new SigTransactionMetadataBuilder(keyLookup);
        assertThrows(NullPointerException.class, () -> new SigTransactionMetadataBuilder(null));
        assertThrows(NullPointerException.class, () -> subject.txnBody(null));
        assertThrows(NullPointerException.class, () -> subject.payerKeyFor(null));
        assertThrows(NullPointerException.class, () -> subject.status(null));
        assertThrows(NullPointerException.class, () -> subject.addNonPayerKey(null));
        assertThrows(
                NullPointerException.class,
                () -> subject.addNonPayerKeyIfReceiverSigRequired(null, null));
        assertDoesNotThrow(() -> subject.addNonPayerKey(payer, null));
        assertDoesNotThrow(() -> subject.addNonPayerKeyIfReceiverSigRequired(payer, null));
    }

    @Test
    void gettersWorkAsExpectedWhenPayerIsSet() {
        final var txn = createAccountTransaction();
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));
        subject =
                new SigTransactionMetadataBuilder<>(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer)
                        .addAllReqKeys(List.of(payerKey, otherKey));
        meta = subject.build();

        assertFalse(meta.failed());
        assertEquals(txn, meta.txnBody());
        assertEquals(List.of(payerKey, payerKey, otherKey), meta.requiredKeys());
        assertEquals(payer, meta.payer());
    }

    @Test
    void gettersWorkAsExpectedWhenOtherSigsExist() {
        final var txn = createAccountTransaction();
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));

        subject =
                new SigTransactionMetadataBuilder<>(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer)
                        .addToReqKeys(payerKey);
        meta = subject.build();

        assertFalse(meta.failed());
        assertEquals(txn, meta.txnBody());
        assertEquals(List.of(payerKey, payerKey), meta.requiredKeys());
    }

    @Test
    void failsWhenPayerKeyDoesntExist() {
        final var txn = createAccountTransaction();
        given(keyLookup.getKey(payer)).willReturn(withFailureReason(INVALID_PAYER_ACCOUNT_ID));

        subject =
                new SigTransactionMetadataBuilder<>(keyLookup)
                        .txnBody(txn)
                        .payerKeyFor(payer)
                        .addToReqKeys(payerKey);
        meta = subject.build();

        assertTrue(meta.failed());
        assertEquals(INVALID_PAYER_ACCOUNT_ID, meta.status());

        assertEquals(txn, meta.txnBody());
        assertEquals(
                List.of(),
                meta.requiredKeys()); // No other keys are added when payerKey is not added
    }

    @Test
    void doesntAddToReqKeysIfStatus() {
        given(keyLookup.getKey(payer)).willReturn(withFailureReason(INVALID_PAYER_ACCOUNT_ID));

        subject =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer);
        subject.addToReqKeys(payerKey);

        assertEquals(0, subject.build().requiredKeys().size());
        assertFalse(subject.build().requiredKeys().contains(payerKey));
    }

    @Test
    void addsToReqKeysCorrectly() {
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));

        subject =
                new SigTransactionMetadataBuilder<>(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer);

        assertEquals(1, subject.build().requiredKeys().size());
        assertTrue(subject.build().requiredKeys().contains(payerKey));

        subject.addToReqKeys(otherKey);
        assertEquals(2, subject.build().requiredKeys().size());
        assertTrue(subject.build().requiredKeys().contains(otherKey));
    }

    @Test
    void settersWorkCorrectly() {
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));

        subject =
                new SigTransactionMetadataBuilder<>(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer)
                        .status(INVALID_ACCOUNT_ID);
        assertEquals(INVALID_ACCOUNT_ID, subject.build().status());
    }

    @Test
    void returnsIfGivenKeyIsPayer() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject =
                new SigTransactionMetadataBuilder<>(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer);
        assertIterableEquals(List.of(payerKey), subject.build().requiredKeys());

        subject.addNonPayerKey(payer);
        assertIterableEquals(List.of(payerKey), subject.build().requiredKeys());

        subject.addNonPayerKeyIfReceiverSigRequired(payer, INVALID_ACCOUNT_ID);
        assertIterableEquals(List.of(payerKey), subject.build().requiredKeys());
        assertEquals(OK, subject.build().status());
    }

    @Test
    void returnsIfGivenKeyIsInvalidAccountId() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject =
                new SigTransactionMetadataBuilder<>(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer);
        assertIterableEquals(List.of(payerKey), subject.build().requiredKeys());

        subject.addNonPayerKey(AccountID.getDefaultInstance());
        assertIterableEquals(List.of(payerKey), subject.build().requiredKeys());

        subject.addNonPayerKeyIfReceiverSigRequired(
                AccountID.getDefaultInstance(), INVALID_ACCOUNT_ID);
        assertIterableEquals(List.of(payerKey), subject.build().requiredKeys());
        assertEquals(OK, subject.build().status());

        subject.addNonPayerKey(AccountID.getDefaultInstance());
        assertIterableEquals(List.of(payerKey), subject.build().requiredKeys());

        subject.addNonPayerKeyIfReceiverSigRequired(
                AccountID.getDefaultInstance(), INVALID_ACCOUNT_ID);
        assertIterableEquals(List.of(payerKey), subject.build().requiredKeys());
        assertEquals(OK, subject.build().status());
    }

    @Test
    void doesntLookupIfMetaIsFailedAlready() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject =
                new SigTransactionMetadataBuilder<>(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer);

        assertIterableEquals(List.of(payerKey), subject.build().requiredKeys());
        subject.status(INVALID_ACCOUNT_ID);

        subject.addNonPayerKey(otherAccountId);
        assertIterableEquals(List.of(payerKey), subject.build().requiredKeys());
        subject.status(INVALID_ACCOUNT_ID);

        subject.addNonPayerKeyIfReceiverSigRequired(otherAccountId, INVALID_ALLOWANCE_OWNER_ID);
        assertIterableEquals(List.of(payerKey), subject.build().requiredKeys());
        subject.status(INVALID_ACCOUNT_ID);
    }

    @Test
    void checksFieldsSetWhenBuildingObject() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));
        subject = new SigTransactionMetadataBuilder(keyLookup).payerKeyFor(payer);
        var message = assertThrows(NullPointerException.class, () -> subject.build());
        assertEquals(
                "Transaction body is required to build SigTransactionMetadata",
                message.getMessage());

        subject = new SigTransactionMetadataBuilder(keyLookup).txnBody(createAccountTransaction());
        message = assertThrows(NullPointerException.class, () -> subject.build());
        assertEquals("Payer is required to build SigTransactionMetadata", message.getMessage());
    }

    @Test
    void looksUpOtherKeysIfMetaIsNotFailedAlready() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject =
                new SigTransactionMetadataBuilder<>(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer);
        meta = subject.build();
        assertIterableEquals(List.of(payerKey), meta.requiredKeys());
        assertEquals(OK, meta.status());

        given(keyLookup.getKey(otherAccountId))
                .willReturn(new KeyOrLookupFailureReason(otherKey, null));

        subject.addNonPayerKey(otherAccountId);
        assertIterableEquals(List.of(payerKey, otherKey), subject.build().requiredKeys());
        assertEquals(OK, subject.build().status());

        given(keyLookup.getKeyIfReceiverSigRequired(otherAccountId))
                .willReturn(new KeyOrLookupFailureReason(otherKey, null));
        subject.addNonPayerKeyIfReceiverSigRequired(otherAccountId, INVALID_ALLOWANCE_OWNER_ID);
        assertIterableEquals(List.of(payerKey, otherKey, otherKey), subject.build().requiredKeys());
        assertEquals(OK, subject.build().status());
    }

    @Test
    void doesntFailForInvalidAccount() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer)
                        .addNonPayerKey(AccountID.newBuilder().setAccountNum(0L).build());

        meta = subject.build();
        assertIterableEquals(List.of(payerKey), meta.requiredKeys());
        assertEquals(OK, meta.status());
    }

    @Test
    void doesntFailForAliasedAccount() {
        final var alias = AccountID.newBuilder().setAlias(ByteString.copyFromUtf8("test")).build();
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));
        given(keyLookup.getKey(alias)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer)
                        .addNonPayerKey(alias);

        meta = subject.build();
        assertIterableEquals(List.of(payerKey, payerKey), meta.requiredKeys());
        assertEquals(OK, meta.status());
    }

    @Test
    void failsForInvalidAlias() {
        final var alias = AccountID.newBuilder().setAlias(ByteString.copyFromUtf8("test")).build();
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));
        given(keyLookup.getKey(alias))
                .willReturn(new KeyOrLookupFailureReason(null, INVALID_ACCOUNT_ID));

        subject =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer)
                        .addNonPayerKey(alias);

        meta = subject.build();
        assertIterableEquals(List.of(payerKey), meta.requiredKeys());
        assertEquals(INVALID_ACCOUNT_ID, meta.status());
    }

    @Test
    void setsDefaultFailureStatusIfFailedStatusIsNull() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject =
                new SigTransactionMetadataBuilder<>(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer);
        meta = subject.build();
        assertIterableEquals(List.of(payerKey), meta.requiredKeys());
        assertEquals(OK, meta.status());

        given(keyLookup.getKey(otherAccountId))
                .willReturn(new KeyOrLookupFailureReason(null, INVALID_ACCOUNT_ID));
        subject.addNonPayerKey(otherAccountId);
        meta = subject.build();
        assertIterableEquals(List.of(payerKey), meta.requiredKeys());
        assertEquals(INVALID_ACCOUNT_ID, meta.status());

        // only for testing , resetting the status to OK
        subject.status(OK);
        given(keyLookup.getKeyIfReceiverSigRequired(otherAccountId))
                .willReturn(new KeyOrLookupFailureReason(null, INVALID_ACCOUNT_ID));
        subject.addNonPayerKey(otherAccountId, INVALID_ALLOWANCE_OWNER_ID);
        meta = subject.build();
        assertIterableEquals(List.of(payerKey), meta.requiredKeys());
        assertEquals(INVALID_ALLOWANCE_OWNER_ID, meta.status());

        // only for testing , resetting the status to OK
        subject.status(OK);
        subject.addNonPayerKeyIfReceiverSigRequired(otherAccountId, null);
        meta = subject.build();
        assertIterableEquals(List.of(payerKey), meta.requiredKeys());
        assertEquals(INVALID_ACCOUNT_ID, meta.status());

        // only for testing , resetting the status to OK
        subject.status(OK);
        subject.addNonPayerKeyIfReceiverSigRequired(otherAccountId, INVALID_ALLOWANCE_OWNER_ID);
        meta = subject.build();
        assertIterableEquals(List.of(payerKey), meta.requiredKeys());
        assertEquals(INVALID_ALLOWANCE_OWNER_ID, meta.status());
    }

    private TransactionBody createAccountTransaction() {
        final var transactionID =
                TransactionID.newBuilder()
                        .setAccountID(payer)
                        .setTransactionValidStart(consensusTimestamp);
        final var createTxnBody =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(key)
                        .setReceiverSigRequired(true)
                        .setMemo("Create Account")
                        .build();
        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setCryptoCreateAccount(createTxnBody)
                .build();
    }
}
