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
package com.hedera.services.sigs.order;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.config.EntityNumbers;
import com.hedera.services.config.MockEntityNumbers;
import com.hedera.services.txns.auth.SystemOpPolicies;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PolicyBasedSigWaiversTest {
    private final EntityNumbers entityNumbers = new MockEntityNumbers();

    private final SystemOpPolicies opPolicies = new SystemOpPolicies(entityNumbers);

    private PolicyBasedSigWaivers subject;

    @BeforeEach
    void setUp() {
        subject = new PolicyBasedSigWaivers(entityNumbers, opPolicies);
    }

    @Test
    void waivesWaclSigForSuperuserSystemFileAppend() {
        // setup:
        final var txn = fileAppendTxn(entityNumbers.files().addressBook());

        // expect:
        assertTrue(subject.isAppendFileWaclWaived(txn, treasury));
    }

    @Test
    void waivesWaclSigForAuthorizedAppend() {
        // setup:
        final var txn = fileAppendTxn(entityNumbers.files().addressBook());

        // expect:
        assertTrue(subject.isAppendFileWaclWaived(txn, addressBookAdmin));
    }

    @Test
    void doesntWaivesWaclSigForSuperuserNonSystemFileAppend() {
        // setup:
        final var txn = fileAppendTxn(1234L);

        // expect:
        assertFalse(subject.isAppendFileWaclWaived(txn, treasury));
    }

    @Test
    void doesntWaivesWaclSigForCivilianAppend() {
        // setup:
        final var txn = fileAppendTxn(1234L);

        // expect:
        assertFalse(subject.isAppendFileWaclWaived(txn, civilian));
    }

    @Test
    void waivesWaclSigForSuperuserSystemFileUpdate() {
        // setup:
        final var txn = fileUpdateTxn(entityNumbers.files().addressBook());

        // expect:
        assertTrue(subject.isTargetFileWaclWaived(txn, treasury));
    }

    @Test
    void waivesWaclSigForAuthorizedUpdate() {
        // setup:
        final var txn = fileUpdateTxn(entityNumbers.files().addressBook());

        // expect:
        assertTrue(subject.isTargetFileWaclWaived(txn, addressBookAdmin));
    }

    @Test
    void doesntWaivesWaclSigForSuperuserNonSystemFileUpdate() {
        // setup:
        final var txn = fileUpdateTxn(1234L);

        // expect:
        assertFalse(subject.isTargetFileWaclWaived(txn, treasury));
    }

    @Test
    void doesntWaivesWaclSigForCivilianUpdate() {
        // setup:
        final var txn = fileUpdateTxn(1234L);

        // expect:
        assertFalse(subject.isTargetFileWaclWaived(txn, civilian));
    }

    @Test
    void waivesTargetSigForTreasuryUpdateOfNonTreasurySystemAccount() {
        // setup:
        final var txn = cryptoUpdateTxn(entityNumbers.accounts().systemAdmin());

        // expect:
        assertTrue(subject.isTargetAccountKeyWaived(txn, treasury));
    }

    @Test
    void waivesNewKeySigForTreasuryUpdateOfNonTreasurySystemAccount() {
        // setup:
        final var txn = cryptoUpdateTxn(entityNumbers.accounts().systemAdmin());

        // expect:
        assertTrue(subject.isNewAccountKeyWaived(txn, treasury));
    }

    @Test
    void doesntWaiveNewKeySigForTreasuryUpdateOfTreasurySystemAccount() {
        // setup:
        final var txn = cryptoUpdateTxn(entityNumbers.accounts().treasury());

        // expect:
        assertFalse(subject.isNewAccountKeyWaived(txn, treasury));
    }

    @Test
    void doesntWaiveNewKeySigForTreasuryUpdateOfNonSystemAccount() {
        // setup:
        final var txn = cryptoUpdateTxn(1234L);

        // expect:
        assertFalse(subject.isNewAccountKeyWaived(txn, treasury));
    }

    @Test
    void doesntWaivesNewKeySigForCivilianUpdate() {
        // setup:
        final var txn = cryptoUpdateTxn(1234L);

        // expect:
        assertFalse(subject.isNewAccountKeyWaived(txn, treasury));
    }

    @Test
    void allMethodsRequireExpectedTxnType() {
        final var txn = TransactionBody.getDefaultInstance();
        // expect:
        assertThrows(
                IllegalArgumentException.class, () -> subject.isAppendFileWaclWaived(txn, null));
        assertThrows(
                IllegalArgumentException.class, () -> subject.isTargetAccountKeyWaived(txn, null));
        assertThrows(
                IllegalArgumentException.class, () -> subject.isNewAccountKeyWaived(txn, null));
        assertThrows(
                IllegalArgumentException.class, () -> subject.isTargetFileWaclWaived(txn, null));
        assertThrows(IllegalArgumentException.class, () -> subject.isNewFileWaclWaived(txn, null));
    }

    private final AccountID treasury = IdUtils.asAccount("0.0.2");
    private final AccountID addressBookAdmin = IdUtils.asAccount("0.0.55");
    private final AccountID civilian = IdUtils.asAccount("0.0.1234");
    private static final Key NEW_KEY =
            Key.newBuilder()
                    .setEd25519(ByteString.copyFromUtf8("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                    .build();

    private TransactionBody cryptoUpdateTxn(long targetNum) {
        final var op =
                CryptoUpdateTransactionBody.newBuilder()
                        .setAccountIDToUpdate(AccountID.newBuilder().setAccountNum(targetNum));
        return TransactionBody.newBuilder().setCryptoUpdateAccount(op).build();
    }

    private TransactionBody fileAppendTxn(long targetNum) {
        final var op =
                FileAppendTransactionBody.newBuilder()
                        .setFileID(FileID.newBuilder().setFileNum(targetNum));
        return TransactionBody.newBuilder().setFileAppend(op).build();
    }

    private TransactionBody fileUpdateTxn(long targetNum) {
        final var op =
                FileUpdateTransactionBody.newBuilder()
                        .setFileID(FileID.newBuilder().setFileNum(targetNum));
        return TransactionBody.newBuilder().setFileUpdate(op).build();
    }
}
