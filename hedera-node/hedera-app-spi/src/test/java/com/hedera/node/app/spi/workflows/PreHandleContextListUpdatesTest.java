/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.workflows;

import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withFailureReason;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.key.HederaKey;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreHandleContextListUpdatesTest {
    public static final com.hederahashgraph.api.proto.java.Key A_COMPLEX_KEY =
            com.hederahashgraph.api.proto.java.Key.newBuilder()
                    .setThresholdKey(ThresholdKey.newBuilder()
                            .setThreshold(2)
                            .setKeys(KeyList.newBuilder()
                                    .addKeys(com.hederahashgraph.api.proto.java.Key.newBuilder()
                                            .setEd25519(
                                                    ByteString.copyFrom("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes())))
                                    .addKeys(com.hederahashgraph.api.proto.java.Key.newBuilder()
                                            .setEd25519(ByteString.copyFrom(
                                                    "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes())))))
                    .build();
    private Timestamp consensusTimestamp =
            Timestamp.newBuilder().setSeconds(1_234_567L).build();
    private Key key = A_COMPLEX_KEY;
    private AccountID payer = AccountID.newBuilder().setAccountNum(3L).build();
    private Long payerNum = 3L;

    @Mock
    private HederaKey payerKey;

    final AccountID otherAccountId =
            AccountID.newBuilder().setAccountNum(12345L).build();
    final ContractID otherContractId =
            ContractID.newBuilder().setContractNum(123456L).build();

    @Mock
    private HederaKey otherKey;

    @Mock
    private AccountKeyLookup keyLookup;

    private PreHandleContext subject;

    @BeforeEach
    void setUp() {}

    @Test
    void gettersWorkAsExpectedWhenOnlyPayerKeyExist() {
        final var txn = createAccountTransaction();
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));

        subject = new PreHandleContext(keyLookup, createAccountTransaction(), payer);

        assertFalse(subject.failed());
        assertEquals(txn, subject.getTxn());
        assertEquals(payerKey, subject.getPayerKey());
        assertEquals(List.of(), subject.getRequiredNonPayerKeys());
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void nullInputToBuilderArgumentsThrows() {
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));
        final var subject = new PreHandleContext(keyLookup, createAccountTransaction(), payer);
        assertThrows(NullPointerException.class, () -> new PreHandleContext(null, createAccountTransaction(), payer));
        assertThrows(NullPointerException.class, () -> new PreHandleContext(keyLookup, null, payer));
        assertThrows(
                NullPointerException.class,
                () -> new PreHandleContext(keyLookup, createAccountTransaction(), (AccountID) null));
        assertThrows(NullPointerException.class, () -> subject.status(null));
        assertThrows(NullPointerException.class, () -> subject.addNonPayerKey((AccountID) null));
        assertThrows(NullPointerException.class, () -> subject.addNonPayerKeyIfReceiverSigRequired(null, null));
        assertDoesNotThrow(() -> subject.addNonPayerKey(payer, null));
        assertDoesNotThrow(() -> subject.addNonPayerKeyIfReceiverSigRequired(payer, null));
    }

    @Test
    void gettersWorkAsExpectedWhenPayerIsSet() {
        final var txn = createAccountTransaction();
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));
        subject = new PreHandleContext(keyLookup, createAccountTransaction(), payer)
                .addAllReqKeys(List.of(payerKey, otherKey));

        assertFalse(subject.failed());
        assertEquals(txn, subject.getTxn());
        assertEquals(payerKey, subject.getPayerKey());
        assertEquals(List.of(payerKey, otherKey), subject.getRequiredNonPayerKeys());
        assertEquals(payer, subject.getPayer());
    }

    @Test
    void gettersWorkAsExpectedWhenOtherSigsExist() {
        final var txn = createAccountTransaction();
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));

        subject = new PreHandleContext(keyLookup, createAccountTransaction(), payer).addToReqNonPayerKeys(payerKey);

        assertFalse(subject.failed());
        assertEquals(txn, subject.getTxn());
        assertEquals(payerKey, subject.getPayerKey());
        assertEquals(List.of(payerKey), subject.getRequiredNonPayerKeys());
    }

    @Test
    void failsWhenPayerKeyDoesntExist() {
        final var txn = createAccountTransaction();
        given(keyLookup.getKey(payer)).willReturn(withFailureReason(INVALID_PAYER_ACCOUNT_ID));

        subject = new PreHandleContext(keyLookup, txn, payer).addToReqNonPayerKeys(payerKey);

        assertTrue(subject.failed());
        assertNull(subject.getPayerKey());
        assertEquals(INVALID_PAYER_ACCOUNT_ID, subject.getStatus());

        assertEquals(txn, subject.getTxn());
        assertEquals(List.of(), subject.getRequiredNonPayerKeys()); // No other keys are added when payerKey is not
        // added
    }

    @Test
    void doesntAddToReqKeysIfStatus() {
        given(keyLookup.getKey(payer)).willReturn(withFailureReason(INVALID_PAYER_ACCOUNT_ID));

        subject = new PreHandleContext(keyLookup, createAccountTransaction(), payer);
        subject.addToReqNonPayerKeys(payerKey);

        assertEquals(0, subject.getRequiredNonPayerKeys().size());
        assertNull(subject.getPayerKey());
        assertFalse(subject.getRequiredNonPayerKeys().contains(payerKey));
    }

    @Test
    void addsToReqKeysCorrectly() {
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));

        subject = new PreHandleContext(keyLookup, createAccountTransaction(), payer);

        assertEquals(0, subject.getRequiredNonPayerKeys().size());
        assertEquals(payerKey, subject.getPayerKey());

        subject.addToReqNonPayerKeys(otherKey);
        assertEquals(1, subject.getRequiredNonPayerKeys().size());
        assertEquals(payerKey, subject.getPayerKey());
        assertTrue(subject.getRequiredNonPayerKeys().contains(otherKey));
    }

    @Test
    void settersWorkCorrectly() {
        given(keyLookup.getKey(payer)).willReturn(withKey(payerKey));

        subject = new PreHandleContext(keyLookup, createAccountTransaction(), payer).status(INVALID_ACCOUNT_ID);
        assertEquals(INVALID_ACCOUNT_ID, subject.getStatus());
    }

    @Test
    void returnsIfGivenKeyIsPayer() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject = new PreHandleContext(keyLookup, createAccountTransaction(), payer);
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());

        subject.addNonPayerKey(payer);
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());

        subject.addNonPayerKeyIfReceiverSigRequired(payer, INVALID_ACCOUNT_ID);
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());
    }

    @Test
    void returnsIfGivenKeyIsInvalidAccountId() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject = new PreHandleContext(keyLookup, createAccountTransaction(), payer);

        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());

        subject.addNonPayerKey(AccountID.getDefaultInstance());
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());

        subject.addNonPayerKeyIfReceiverSigRequired(AccountID.getDefaultInstance(), INVALID_ACCOUNT_ID);
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());

        subject.addNonPayerKey(AccountID.getDefaultInstance());
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());

        subject.addNonPayerKeyIfReceiverSigRequired(AccountID.getDefaultInstance(), INVALID_ACCOUNT_ID);
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());
    }

    @Test
    void addsContractIdKey() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));
        given(keyLookup.getKey(otherContractId)).willReturn(new KeyOrLookupFailureReason(otherKey, null));
        given(keyLookup.getKeyIfReceiverSigRequired(otherContractId))
                .willReturn(new KeyOrLookupFailureReason(otherKey, null));

        subject = new PreHandleContext(keyLookup, createAccountTransaction(), payer);

        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());

        subject.addNonPayerKey(otherContractId);
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(otherKey), subject.getRequiredNonPayerKeys());

        subject.addNonPayerKeyIfReceiverSigRequired(otherContractId);
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(otherKey, otherKey), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());
    }

    @Test
    void doesntLookupIfMetaIsFailedAlready() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject = new PreHandleContext(keyLookup, createAccountTransaction(), payer);

        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        subject.status(INVALID_ACCOUNT_ID);

        subject.addNonPayerKey(otherAccountId);
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        subject.status(INVALID_ACCOUNT_ID);

        subject.addNonPayerKeyIfReceiverSigRequired(otherAccountId, INVALID_ALLOWANCE_OWNER_ID);
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        subject.status(INVALID_ACCOUNT_ID);

        subject.addNonPayerKey(otherContractId);
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        subject.status(INVALID_CONTRACT_ID);

        subject.addNonPayerKeyIfReceiverSigRequired(otherContractId);
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        subject.status(INVALID_CONTRACT_ID);
    }

    @Test
    void looksUpOtherKeysIfMetaIsNotFailedAlready() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject = new PreHandleContext(keyLookup, createAccountTransaction(), payer);
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());

        given(keyLookup.getKey(otherAccountId)).willReturn(new KeyOrLookupFailureReason(otherKey, null));

        subject.addNonPayerKey(otherAccountId);
        assertIterableEquals(List.of(otherKey), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());

        given(keyLookup.getKeyIfReceiverSigRequired(otherAccountId))
                .willReturn(new KeyOrLookupFailureReason(otherKey, null));
        subject.addNonPayerKeyIfReceiverSigRequired(otherAccountId, INVALID_ALLOWANCE_OWNER_ID);
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(otherKey, otherKey), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());
    }

    @Test
    void doesntFailForInvalidAccount() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject = new PreHandleContext(keyLookup, createAccountTransaction(), payer)
                .addNonPayerKey(AccountID.newBuilder().setAccountNum(0L).build());

        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());
    }

    @Test
    void doesntFailForInvalidContract() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject = new PreHandleContext(keyLookup, createAccountTransaction(), payer);

        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());
    }

    @Test
    void doesntFailForAliasedAccount() {
        final var alias =
                AccountID.newBuilder().setAlias(ByteString.copyFromUtf8("test")).build();
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));
        given(keyLookup.getKey(alias)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject = new PreHandleContext(keyLookup, createAccountTransaction(), payer).addNonPayerKey(alias);

        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(payerKey), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());
    }

    @Test
    void doesntFailForAliasedContract() {
        final var alias = ContractID.newBuilder()
                .setEvmAddress(ByteString.copyFromUtf8("test"))
                .build();
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));
        given(keyLookup.getKey(alias)).willReturn(new KeyOrLookupFailureReason(otherKey, null));

        subject = new PreHandleContext(keyLookup, createAccountTransaction(), payer).addNonPayerKey(alias);

        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(otherKey), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());
    }

    @Test
    void failsForInvalidAlias() {
        final var alias =
                AccountID.newBuilder().setAlias(ByteString.copyFromUtf8("test")).build();
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));
        given(keyLookup.getKey(alias)).willReturn(new KeyOrLookupFailureReason(null, INVALID_ACCOUNT_ID));

        subject = new PreHandleContext(keyLookup, createAccountTransaction(), payer).addNonPayerKey(alias);

        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(INVALID_ACCOUNT_ID, subject.getStatus());
    }

    @Test
    void setsDefaultFailureStatusIfFailedStatusIsNull() {
        given(keyLookup.getKey(payer)).willReturn(new KeyOrLookupFailureReason(payerKey, null));

        subject = new PreHandleContext(keyLookup, createAccountTransaction(), payer);
        assertEquals(payerKey, subject.getPayerKey());
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(OK, subject.getStatus());

        given(keyLookup.getKey(otherAccountId)).willReturn(new KeyOrLookupFailureReason(null, INVALID_ACCOUNT_ID));
        subject.addNonPayerKey(otherAccountId);
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(INVALID_ACCOUNT_ID, subject.getStatus());

        // only for testing , resetting the status to OK
        subject.status(OK);
        given(keyLookup.getKeyIfReceiverSigRequired(otherAccountId))
                .willReturn(new KeyOrLookupFailureReason(null, INVALID_ACCOUNT_ID));
        subject.addNonPayerKey(otherAccountId, INVALID_ALLOWANCE_OWNER_ID);
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(INVALID_ALLOWANCE_OWNER_ID, subject.getStatus());

        // only for testing , resetting the status to OK
        subject.status(OK);
        subject.addNonPayerKeyIfReceiverSigRequired(otherAccountId, null);
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(INVALID_ACCOUNT_ID, subject.getStatus());

        // only for testing , resetting the status to OK
        subject.status(OK);
        subject.addNonPayerKeyIfReceiverSigRequired(otherAccountId, INVALID_ALLOWANCE_OWNER_ID);
        assertIterableEquals(List.of(), subject.getRequiredNonPayerKeys());
        assertEquals(INVALID_ALLOWANCE_OWNER_ID, subject.getStatus());
    }

    private TransactionBody createAccountTransaction() {
        final var transactionID =
                TransactionID.newBuilder().setAccountID(payer).setTransactionValidStart(consensusTimestamp);
        final var createTxnBody = CryptoCreateTransactionBody.newBuilder()
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
