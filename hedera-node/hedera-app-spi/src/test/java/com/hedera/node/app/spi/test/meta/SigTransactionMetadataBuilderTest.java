/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withFailureReason;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withKey;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.AccountID.Builder;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hashgraph.pbj.runtime.io.Bytes;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SigTransactionMetadataBuilderTest {
    private static final AccountID DEFAULT_ACCOUNT_ID = AccountID.newBuilder().build();

    private static final Key COMPLEX_KEY_FIRST =
            new Key.Builder().ed25519(asBytes("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")).build();
    private static final Key COMPLEX_KEY_SECOND =
            new Key.Builder().ed25519(asBytes("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")).build();
    public static final Key A_COMPLEX_KEY =
            new Key.Builder()
                    .thresholdKey(
                            new ThresholdKey.Builder()
                                    .threshold(2)
                                    .keys(
                                            new KeyList.Builder()
                                                    .keys(
                                                            List.of(
                                                                    COMPLEX_KEY_FIRST,
                                                                    COMPLEX_KEY_SECOND))
                                                    .build())
                                    .build())
                    .build();
    private Timestamp consensusTimestamp = Timestamp.newBuilder().seconds(1_234_567L).build();
    private Key key = A_COMPLEX_KEY;
    private AccountID payer = AccountID.newBuilder().accountNum(3L).build();
    private Long payerNum = 3L;
    @Mock private HederaKey payerKey;
    final AccountID otherAccountId = AccountID.newBuilder().accountNum(12345L).build();
    final ContractID otherContractId = new ContractID.Builder().contractNum(123456L).build();
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
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer);
        meta = subject.build();

        assertFalse(meta.failed());
        assertEquals(txn, meta.txnBody());
        assertEquals(payerKey, meta.payerKey());
        assertEquals(List.of(), meta.requiredNonPayerKeys());
    }

    @Test
    void nullInputToBuilderArgumentsThrows() {
        final var subject = new SigTransactionMetadataBuilder(keyLookup);
        assertThrows(NullPointerException.class, () -> new SigTransactionMetadataBuilder(null));
        assertThrows(NullPointerException.class, () -> subject.txnBody(null));
        assertThrows(NullPointerException.class, () -> subject.payerKeyFor(null));
        assertThrows(NullPointerException.class, () -> subject.status(null));
        assertThrows(NullPointerException.class, () -> subject.addNonPayerKey((AccountID) null));
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
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer)
                        .addAllReqKeys(List.of(payerKey, otherKey));
        meta = subject.build();

        assertFalse(meta.failed());
        assertEquals(txn, meta.txnBody());
        assertEquals(payerKey, meta.payerKey());
        assertEquals(List.of(payerKey, otherKey), meta.requiredNonPayerKeys());
        assertEquals(payer, meta.payer());
    }

    @Test
    void gettersWorkAsExpectedWhenOtherSigsExist() {
        final var txn = createAccountTransaction();
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));

        subject =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer)
                        .addToReqNonPayerKeys(payerKey);
        meta = subject.build();

        assertFalse(meta.failed());
        assertEquals(txn, meta.txnBody());
        assertEquals(payerKey, meta.payerKey());
        assertEquals(List.of(payerKey), meta.requiredNonPayerKeys());
    }

    @Test
    void failsWhenPayerKeyDoesntExist() {
        final var txn = createAccountTransaction();
        given(keyLookup.getKey(payer)).willReturn(withFailureReason(INVALID_PAYER_ACCOUNT_ID));

        subject =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(txn)
                        .payerKeyFor(payer)
                        .addToReqNonPayerKeys(payerKey);
        meta = subject.build();

        assertTrue(meta.failed());
        assertNull(meta.payerKey());
        assertEquals(INVALID_PAYER_ACCOUNT_ID, meta.status());

        assertEquals(txn, meta.txnBody());
        assertEquals(
                List.of(),
                meta.requiredNonPayerKeys()); // No other keys are added when payerKey is not added
    }

    @Test
    void doesntAddToReqKeysIfStatus() {
        given(keyLookup.getKey(payer)).willReturn(withFailureReason(INVALID_PAYER_ACCOUNT_ID));

        subject =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer);
        subject.addToReqNonPayerKeys(payerKey);

        assertEquals(0, subject.build().requiredNonPayerKeys().size());
        assertNull(subject.build().payerKey());
        assertFalse(subject.build().requiredNonPayerKeys().contains(payerKey));
    }

    @Test
    void addsToReqKeysCorrectly() {
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));

        subject =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer);

        assertEquals(0, subject.build().requiredNonPayerKeys().size());
        assertEquals(payerKey, subject.build().payerKey());

        subject.addToReqNonPayerKeys(otherKey);
        assertEquals(1, subject.build().requiredNonPayerKeys().size());
        assertEquals(payerKey, subject.build().payerKey());
        assertTrue(subject.build().requiredNonPayerKeys().contains(otherKey));
    }

    @Test
    void settersWorkCorrectly() {
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));

        subject =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer)
                        .status(INVALID_ACCOUNT_ID);
        assertEquals(INVALID_ACCOUNT_ID, subject.build().status());
    }

    @Test
    void returnsIfGivenKeyIsPayer() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer);
        assertEquals(payerKey, subject.build().payerKey());
        assertIterableEquals(List.of(), subject.build().requiredNonPayerKeys());

        subject.addNonPayerKey(payer);
        assertEquals(payerKey, subject.build().payerKey());
        assertIterableEquals(List.of(), subject.build().requiredNonPayerKeys());

        subject.addNonPayerKeyIfReceiverSigRequired(payer, INVALID_ACCOUNT_ID);
        assertIterableEquals(List.of(), subject.build().requiredNonPayerKeys());
        assertEquals(OK, subject.build().status());
    }

    @Test
    void returnsIfGivenKeyIsInvalidAccountId() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer);

        assertEquals(payerKey, subject.build().payerKey());
        assertIterableEquals(List.of(), subject.build().requiredNonPayerKeys());

        subject.addNonPayerKey(DEFAULT_ACCOUNT_ID);
        assertEquals(payerKey, subject.build().payerKey());
        assertIterableEquals(List.of(), subject.build().requiredNonPayerKeys());

        subject.addNonPayerKeyIfReceiverSigRequired(DEFAULT_ACCOUNT_ID, INVALID_ACCOUNT_ID);
        assertEquals(payerKey, subject.build().payerKey());
        assertIterableEquals(List.of(), subject.build().requiredNonPayerKeys());
        assertEquals(OK, subject.build().status());

        subject.addNonPayerKey(DEFAULT_ACCOUNT_ID);
        assertEquals(payerKey, subject.build().payerKey());
        assertIterableEquals(List.of(), subject.build().requiredNonPayerKeys());

        subject.addNonPayerKeyIfReceiverSigRequired(DEFAULT_ACCOUNT_ID, INVALID_ACCOUNT_ID);
        assertEquals(payerKey, subject.build().payerKey());
        assertIterableEquals(List.of(), subject.build().requiredNonPayerKeys());
        assertEquals(OK, subject.build().status());
    }

    @Test
    void addsContractIdKey() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));
        given(keyLookup.getKey(otherContractId))
                .willReturn(new KeyOrLookupFailureReason(otherKey, null));
        given(keyLookup.getKeyIfReceiverSigRequired(otherContractId))
                .willReturn(new KeyOrLookupFailureReason(otherKey, null));

        subject =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer);

        assertEquals(payerKey, subject.build().payerKey());
        assertIterableEquals(List.of(), subject.build().requiredNonPayerKeys());

        subject.addNonPayerKey(otherContractId);
        assertEquals(payerKey, subject.build().payerKey());
        assertIterableEquals(List.of(otherKey), subject.build().requiredNonPayerKeys());

        subject.addNonPayerKeyIfReceiverSigRequired(otherContractId);
        assertEquals(payerKey, subject.build().payerKey());
        assertIterableEquals(List.of(otherKey, otherKey), subject.build().requiredNonPayerKeys());
        assertEquals(OK, subject.build().status());
    }

    @Test
    void doesntLookupIfMetaIsFailedAlready() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer);

        assertEquals(payerKey, subject.build().payerKey());
        assertIterableEquals(List.of(), subject.build().requiredNonPayerKeys());
        subject.status(INVALID_ACCOUNT_ID);

        subject.addNonPayerKey(otherAccountId);
        assertIterableEquals(List.of(), subject.build().requiredNonPayerKeys());
        subject.status(INVALID_ACCOUNT_ID);

        subject.addNonPayerKeyIfReceiverSigRequired(otherAccountId, INVALID_ALLOWANCE_OWNER_ID);
        assertIterableEquals(List.of(), subject.build().requiredNonPayerKeys());
        subject.status(INVALID_ACCOUNT_ID);

        subject.addNonPayerKey(otherContractId);
        assertIterableEquals(List.of(), subject.build().requiredNonPayerKeys());
        subject.status(INVALID_CONTRACT_ID);

        subject.addNonPayerKeyIfReceiverSigRequired(otherContractId);
        assertIterableEquals(List.of(), subject.build().requiredNonPayerKeys());
        subject.status(INVALID_CONTRACT_ID);
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
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer);
        meta = subject.build();
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
        assertEquals(OK, meta.status());

        given(keyLookup.getKey(otherAccountId))
                .willReturn(new KeyOrLookupFailureReason(otherKey, null));

        subject.addNonPayerKey(otherAccountId);
        assertIterableEquals(List.of(otherKey), subject.build().requiredNonPayerKeys());
        assertEquals(OK, subject.build().status());

        given(keyLookup.getKeyIfReceiverSigRequired(otherAccountId))
                .willReturn(new KeyOrLookupFailureReason(otherKey, null));
        subject.addNonPayerKeyIfReceiverSigRequired(otherAccountId, INVALID_ALLOWANCE_OWNER_ID);
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(otherKey, otherKey), subject.build().requiredNonPayerKeys());
        assertEquals(OK, subject.build().status());
    }

    @Test
    void doesntFailForInvalidAccount() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer)
                        .addNonPayerKey(AccountID.newBuilder().accountNum(0L).build());

        meta = subject.build();
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
        assertEquals(OK, meta.status());
    }

    @Test
    void doesntFailForInvalidContract() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer)
                        .addNonPayerKey(new ContractID.Builder().contractNum(0L).build());

        meta = subject.build();
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
        assertEquals(OK, meta.status());
    }

    @Test
    void doesntFailForAliasedAccount() {
        final var alias = AccountID.newBuilder().alias(asBytes("test")).build();
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));
        given(keyLookup.getKey(alias)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer)
                        .addNonPayerKey(alias);

        meta = subject.build();
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(payerKey), meta.requiredNonPayerKeys());
        assertEquals(OK, meta.status());
    }

    @Test
    void doesntFailForAliasedContract() {
        final var alias = new ContractID.Builder().evmAddress(asBytes("test")).build();
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));
        given(keyLookup.getKey(alias)).willReturn(new KeyOrLookupFailureReason(otherKey, null));

        subject =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer)
                        .addNonPayerKey(alias);

        meta = subject.build();
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(otherKey), meta.requiredNonPayerKeys());
        assertEquals(OK, meta.status());
    }

    @Test
    void failsForInvalidAlias() {
        final var alias = new Builder().alias(asBytes("test")).build();
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));
        given(keyLookup.getKey(alias))
                .willReturn(new KeyOrLookupFailureReason(null, INVALID_ACCOUNT_ID));

        subject =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer)
                        .addNonPayerKey(alias);

        meta = subject.build();
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
        assertEquals(INVALID_ACCOUNT_ID, meta.status());
    }

    @Test
    void setsDefaultFailureStatusIfFailedStatusIsNull() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject =
                new SigTransactionMetadataBuilder(keyLookup)
                        .txnBody(createAccountTransaction())
                        .payerKeyFor(payer);
        meta = subject.build();
        assertEquals(payerKey, meta.payerKey());
        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
        assertEquals(OK, meta.status());

        given(keyLookup.getKey(otherAccountId))
                .willReturn(new KeyOrLookupFailureReason(null, INVALID_ACCOUNT_ID));
        subject.addNonPayerKey(otherAccountId);
        meta = subject.build();
        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
        assertEquals(INVALID_ACCOUNT_ID, meta.status());

        // only for testing , resetting the status to OK
        subject.status(OK);
        given(keyLookup.getKeyIfReceiverSigRequired(otherAccountId))
                .willReturn(new KeyOrLookupFailureReason(null, INVALID_ACCOUNT_ID));
        subject.addNonPayerKey(otherAccountId, INVALID_ALLOWANCE_OWNER_ID);
        meta = subject.build();
        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
        assertEquals(INVALID_ALLOWANCE_OWNER_ID, meta.status());

        // only for testing , resetting the status to OK
        subject.status(OK);
        subject.addNonPayerKeyIfReceiverSigRequired(otherAccountId, null);
        meta = subject.build();
        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
        assertEquals(INVALID_ACCOUNT_ID, meta.status());

        // only for testing , resetting the status to OK
        subject.status(OK);
        subject.addNonPayerKeyIfReceiverSigRequired(otherAccountId, INVALID_ALLOWANCE_OWNER_ID);
        meta = subject.build();
        assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
        assertEquals(INVALID_ALLOWANCE_OWNER_ID, meta.status());
    }

    private TransactionBody createAccountTransaction() {
        final var transactionID =
                TransactionID.newBuilder()
                        .accountID(payer)
                        .transactionValidStart(consensusTimestamp)
                        .build();
        final var createTxnBody =
                CryptoCreateTransactionBody.newBuilder()
                        .key(key)
                        .receiverSigRequired(true)
                        .memo("Create Account")
                        .build();
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .cryptoCreateAccount(createTxnBody)
                .build();
    }

    private static Bytes asBytes(String s) {
        return Bytes.wrap(s.getBytes(StandardCharsets.UTF_8));
    }
}
