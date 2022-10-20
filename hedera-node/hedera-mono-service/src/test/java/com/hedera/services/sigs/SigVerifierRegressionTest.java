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
package com.hedera.services.sigs;

import static com.hedera.services.sigs.metadata.DelegatingSigMetadataLookup.defaultLookupsFor;
import static com.hedera.test.factories.scenarios.BadPayerScenarios.INVALID_PAYER_ID_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.CRYPTO_TRANSFER_RECEIVER_SIG_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.QUERY_PAYMENT_INVALID_SENDER_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.QUERY_PAYMENT_MISSING_SIGS_SCENARIO;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.VALID_QUERY_PAYMENT_SCENARIO;
import static com.hedera.test.factories.scenarios.SystemDeleteScenarios.AMBIGUOUS_SIG_MAP_SCENARIO;
import static com.hedera.test.factories.scenarios.SystemDeleteScenarios.FULL_PAYER_SIGS_VIA_MAP_SCENARIO;
import static com.hedera.test.factories.scenarios.SystemDeleteScenarios.INVALID_PAYER_SIGS_VIA_MAP_SCENARIO;
import static com.hedera.test.factories.scenarios.SystemDeleteScenarios.MISSING_PAYER_SIGS_VIA_MAP_SCENARIO;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_NODE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.config.EntityNumbers;
import com.hedera.services.config.MockEntityNumbers;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.hedera.services.sigs.order.PolicyBasedSigWaivers;
import com.hedera.services.sigs.order.SigRequirements;
import com.hedera.services.sigs.order.SignatureWaivers;
import com.hedera.services.sigs.utils.PrecheckUtils;
import com.hedera.services.sigs.verification.PrecheckKeyReqs;
import com.hedera.services.sigs.verification.PrecheckVerifier;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.txns.auth.SystemOpPolicies;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.engine.CryptoEngine;
import com.swirlds.merkle.map.MerkleMap;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class SigVerifierRegressionTest {
    private PrecheckKeyReqs precheckKeyReqs;
    private PrecheckVerifier precheckVerifier;
    private SigRequirements keyOrder;
    private Predicate<TransactionBody> isQueryPayment;
    private PlatformTxnAccessor platformTxn;
    private MerkleMap<EntityNum, MerkleAccount> accounts;
    private AliasManager aliasManager;

    private EntityNumbers mockEntityNumbers = new MockEntityNumbers();
    private SystemOpPolicies mockSystemOpPolicies = new SystemOpPolicies(mockEntityNumbers);
    private SignatureWaivers mockSignatureWaivers =
            new PolicyBasedSigWaivers(mockEntityNumbers, mockSystemOpPolicies);

    @Test
    void rejectsInvalidTxn() throws Throwable {
        // given:
        Transaction invalidSignedTxn =
                Transaction.newBuilder()
                        .setBodyBytes(ByteString.copyFrom("NONSENSE".getBytes()))
                        .build();

        // expect:
        assertFalse(sigVerifies(invalidSignedTxn));
    }

    @Test
    void acceptsValidNonCryptoTransferPayerSig() throws Throwable {
        setupFor(FULL_PAYER_SIGS_VIA_MAP_SCENARIO);

        // expect:
        assertTrue(sigVerifies(platformTxn.getSignedTxnWrapper()));
    }

    @Test
    void rejectsIncompleteNonCryptoTransferPayerSig() throws Throwable {
        setupFor(MISSING_PAYER_SIGS_VIA_MAP_SCENARIO);

        // expect:
        assertFalse(sigVerifies(platformTxn.getSignedTxnWrapper()));
    }

    @Test
    void rejectsInvalidNonCryptoTransferPayerSig() throws Throwable {
        setupFor(INVALID_PAYER_SIGS_VIA_MAP_SCENARIO);

        // expect:
        assertFalse(sigVerifies(platformTxn.getSignedTxnWrapper()));
    }

    @Test
    void acceptsNonQueryPaymentTransfer() throws Throwable {
        setupFor(CRYPTO_TRANSFER_RECEIVER_SIG_SCENARIO);

        // expect:
        assertTrue(sigVerifies(platformTxn.getSignedTxnWrapper()));
    }

    @Test
    void acceptsQueryPaymentTransfer() throws Throwable {
        setupFor(VALID_QUERY_PAYMENT_SCENARIO);

        // expect:
        assertTrue(sigVerifies(platformTxn.getSignedTxnWrapper()));
    }

    @Test
    void rejectsInvalidPayerAccount() throws Throwable {
        setupFor(INVALID_PAYER_ID_SCENARIO);

        // expect:
        assertFalse(sigVerifies(platformTxn.getSignedTxnWrapper()));
    }

    @Test
    void throwsOnInvalidSenderAccount() throws Throwable {
        // given:
        setupFor(QUERY_PAYMENT_INVALID_SENDER_SCENARIO);

        // expect:
        assertThrows(
                InvalidAccountIDException.class,
                () -> sigVerifies(platformTxn.getSignedTxnWrapper()));
    }

    @Test
    void throwsOnInvalidSigMap() throws Throwable {
        // given:
        setupFor(AMBIGUOUS_SIG_MAP_SCENARIO);

        // expect:
        assertThrows(
                KeyPrefixMismatchException.class,
                () -> sigVerifies(platformTxn.getSignedTxnWrapper()));
    }

    @Test
    void rejectsQueryPaymentTransferWithMissingSigs() throws Throwable {
        setupFor(QUERY_PAYMENT_MISSING_SIGS_SCENARIO);

        // expect:
        assertFalse(sigVerifies(platformTxn.getSignedTxnWrapper()));
    }

    private boolean sigVerifies(Transaction signedTxn) throws Exception {
        try {
            SignedTxnAccessor accessor = SignedTxnAccessor.from(signedTxn.toByteArray(), signedTxn);
            return precheckVerifier.hasNecessarySignatures(accessor);
        } catch (InvalidProtocolBufferException ignore) {
            return false;
        }
    }

    private void setupFor(TxnHandlingScenario scenario) throws Throwable {
        accounts = scenario.accounts();
        platformTxn = scenario.platformTxn();
        aliasManager = mock(AliasManager.class);
        keyOrder =
                new SigRequirements(
                        defaultLookupsFor(
                                aliasManager,
                                null,
                                () -> AccountStorageAdapter.fromInMemory(accounts),
                                () -> null,
                                ref -> null,
                                ref -> null),
                        mockSignatureWaivers);
        final var nodeInfo = mock(NodeInfo.class);
        given(nodeInfo.selfAccount()).willReturn(DEFAULT_NODE);
        isQueryPayment = PrecheckUtils.queryPaymentTestFor(nodeInfo);
        SyncVerifier syncVerifier = new CryptoEngine()::verifySync;
        precheckKeyReqs = new PrecheckKeyReqs(keyOrder, isQueryPayment);
        precheckVerifier = new PrecheckVerifier(syncVerifier, precheckKeyReqs);
    }
}
