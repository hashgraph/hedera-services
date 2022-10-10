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
package com.hedera.services.contracts.sources;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import static com.hedera.services.keys.HederaKeyActivation.INVALID_MISSING_SIG;
import static com.hedera.services.ledger.properties.AccountProperty.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.services.ledger.properties.AccountProperty.KEY;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.keys.ActivationTest;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.legacy.core.jproto.JContractAliasKey;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JDelegatableContractAliasKey;
import com.hedera.services.legacy.core.jproto.JDelegatableContractIDKey;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.function.BiPredicate;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TxnAwareEvmSigsVerifierTest {
    private static final Address PRETEND_SENDER_ADDR = Address.ALTBN128_PAIRING;
    private static final Id tokenId = new Id(0, 0, 666);
    private static final Id accountId = new Id(0, 0, 1234);
    private static final Address PRETEND_TOKEN_ADDR = tokenId.asEvmAddress();
    private static final Address PRETEND_ACCOUNT_ADDR = accountId.asEvmAddress();
    private final TokenID token = IdUtils.asToken("0.0.666");
    private final AccountID payer = IdUtils.asAccount("0.0.2");
    private final AccountID account = IdUtils.asAccount("0.0.1234");
    private final AccountID sigRequired = IdUtils.asAccount("0.0.555");
    private final AccountID smartContract = IdUtils.asAccount("0.0.666");
    private final AccountID noSigRequired = IdUtils.asAccount("0.0.777");

    private JKey expectedKey;

    @Mock private BiPredicate<JKey, TransactionSignature> cryptoValidity;
    @Mock private ActivationTest activationTest;
    @Mock private TransactionContext txnCtx;
    @Mock private PlatformTxnAccessor accessor;
    @Mock private Function<byte[], TransactionSignature> pkToCryptoSigsFn;
    @Mock private ContractAliases aliases;
    @Mock private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
    @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;
    @Mock private WorldLedgers ledgers;

    private TxnAwareEvmSigsVerifier subject;

    @BeforeEach
    void setup() throws Exception {
        expectedKey = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKey();

        subject = new TxnAwareEvmSigsVerifier(activationTest, txnCtx, cryptoValidity);
    }

    @Test
    void throwsIfAskedToVerifyMissingToken() {
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(false);

        assertFailsWith(
                () ->
                        subject.hasActiveSupplyKey(
                                true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers),
                INVALID_TOKEN_ID);
    }

    @Test
    void throwsIfAskedToVerifyTokenWithoutSupplyKey() {
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.SUPPLY_KEY)).willReturn(null);

        assertFailsWith(
                () ->
                        subject.hasActiveSupplyKey(
                                true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers),
                TOKEN_HAS_NO_SUPPLY_KEY);
    }

    @Test
    void testsSupplyKeyIfPresent() {
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.SUPPLY_KEY)).willReturn(expectedKey);

        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

        final var verdict =
                subject.hasActiveSupplyKey(true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers);

        assertTrue(verdict);
    }

    @Test
    void supplyKeyFailsWhenTokensLedgerIsNull() {
        given(ledgers.tokens()).willReturn(null);

        assertThrows(
                NullPointerException.class,
                () ->
                        subject.hasActiveSupplyKey(
                                true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers));
    }

    @Test
    void throwsIfAskedToVerifyTokenWithoutFreezeKey() {
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.FREEZE_KEY)).willReturn(null);

        assertFailsWith(
                () ->
                        subject.hasActiveFreezeKey(
                                true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers),
                TOKEN_HAS_NO_FREEZE_KEY);
    }

    @Test
    void testsFreezeKeyIfPresent() {
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.FREEZE_KEY)).willReturn(expectedKey);

        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

        final var verdict =
                subject.hasActiveFreezeKey(true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers);

        assertTrue(verdict);
    }

    @Test
    void throwsIfAskedToVerifyMissingAccount() {
        given(ledgers.accounts()).willReturn(accountsLedger);
        given(accountsLedger.exists(account)).willReturn(false);

        assertFailsWith(
                () ->
                        subject.hasActiveKey(
                                true, PRETEND_ACCOUNT_ADDR, PRETEND_SENDER_ADDR, ledgers),
                INVALID_ACCOUNT_ID);
    }

    @Test
    void throwsIfAskedToVerifyTokenWithoutAdminKey() {
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.ADMIN_KEY)).willReturn(null);

        assertFailsWith(
                () ->
                        subject.hasActiveAdminKey(
                                true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers),
                TOKEN_IS_IMMUTABLE);
    }

    @Test
    void testsAdminKeyIfPresent() {
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.ADMIN_KEY)).willReturn(expectedKey);

        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

        final var verdict =
                subject.hasActiveAdminKey(true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers);

        assertTrue(verdict);
    }

    @Test
    void testsAccountKeyIfPresent() {
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(ledgers.accounts()).willReturn(accountsLedger);
        given(accountsLedger.exists(account)).willReturn(true);
        given(accountsLedger.get(account, AccountProperty.KEY)).willReturn(expectedKey);
        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

        final var verdict =
                subject.hasActiveKey(true, PRETEND_ACCOUNT_ADDR, PRETEND_SENDER_ADDR, ledgers);

        assertTrue(verdict);
    }

    @Test
    void testsAccountKeyIfPresentButInvalid() {
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(ledgers.accounts()).willReturn(accountsLedger);
        given(accountsLedger.exists(account)).willReturn(true);
        given(accountsLedger.get(account, AccountProperty.KEY)).willReturn(expectedKey);
        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(false);

        final var verdict =
                subject.hasActiveKey(true, PRETEND_ACCOUNT_ADDR, PRETEND_SENDER_ADDR, ledgers);

        assertFalse(verdict);
    }

    @Test
    void testsMissingAccountKey() {
        given(ledgers.accounts()).willReturn(accountsLedger);
        given(accountsLedger.exists(account)).willReturn(true);
        given(accountsLedger.get(account, AccountProperty.KEY)).willReturn(null);

        final var verdict =
                subject.hasActiveKey(true, PRETEND_ACCOUNT_ADDR, PRETEND_SENDER_ADDR, ledgers);

        assertFalse(verdict);
    }

    @Test
    void testsCryptoKeyIfPresent() {
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

        final var verdict = subject.cryptoKeyIsActive(expectedKey);

        assertTrue(verdict);
    }

    @Test
    void testsCryptoKeyIfPresentButInvalid() {
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(false);

        final var verdict = subject.cryptoKeyIsActive(expectedKey);

        assertFalse(verdict);
    }

    @Test
    void testsNullCryptoKey() {
        final var verdict = subject.cryptoKeyIsActive(null);

        assertFalse(verdict);
    }

    @Test
    void filtersContracts() {
        given(txnCtx.activePayer()).willReturn(payer);
        givenSigReqCheckable(smartContract, false, null);

        final var contractFlag =
                subject.hasActiveKeyOrNoReceiverSigReq(
                        true,
                        EntityIdUtils.asTypedEvmAddress(smartContract),
                        PRETEND_SENDER_ADDR,
                        ledgers);

        assertTrue(contractFlag);
        verify(activationTest, never()).test(any(), any(), any());
    }

    private void givenSigReqCheckable(
            final AccountID id, final boolean receiverSigRequired, @Nullable final JKey key) {
        given(ledgers.accounts()).willReturn(accountsLedger);
        given(accountsLedger.contains(id)).willReturn(true);
        given(accountsLedger.get(id, IS_RECEIVER_SIG_REQUIRED)).willReturn(receiverSigRequired);
        if (receiverSigRequired) {
            given(accountsLedger.get(id, KEY)).willReturn(key);
        }
    }

    @Test
    void filtersNoSigRequired() {
        given(txnCtx.activePayer()).willReturn(payer);
        givenSigReqCheckable(noSigRequired, false, null);

        final var noSigRequiredFlag =
                subject.hasActiveKeyOrNoReceiverSigReq(
                        true,
                        EntityIdUtils.asTypedEvmAddress(noSigRequired),
                        PRETEND_SENDER_ADDR,
                        ledgers);

        assertTrue(noSigRequiredFlag);
        verify(activationTest, never()).test(any(), any(), any());
    }

    @Test
    void filtersMissing() {
        given(txnCtx.activePayer()).willReturn(payer);
        given(ledgers.accounts()).willReturn(accountsLedger);

        final var noSigRequiredFlag =
                subject.hasActiveKeyOrNoReceiverSigReq(
                        true,
                        EntityIdUtils.asTypedEvmAddress(noSigRequired),
                        PRETEND_SENDER_ADDR,
                        ledgers);

        assertTrue(noSigRequiredFlag);
        verify(activationTest, never()).test(any(), any(), any());
    }

    @Test
    void filtersNoSigRequiredWhenLedgersAreNotNullButAccountsLedgerIsNull() {
        given(txnCtx.activePayer()).willReturn(payer);
        given(ledgers.accounts()).willReturn(null);

        final var noSigRequiredFlag =
                subject.hasActiveKeyOrNoReceiverSigReq(
                        true,
                        EntityIdUtils.asTypedEvmAddress(noSigRequired),
                        PRETEND_SENDER_ADDR,
                        ledgers);

        assertTrue(noSigRequiredFlag);
        verify(activationTest, never()).test(any(), any(), any());
    }

    @Test
    void testsWhenReceiverSigIsRequired() {
        givenAccessorInCtx();
        givenSigReqCheckable(sigRequired, true, expectedKey);
        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);

        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

        boolean sigRequiredFlag =
                subject.hasActiveKeyOrNoReceiverSigReq(
                        true,
                        EntityIdUtils.asTypedEvmAddress(sigRequired),
                        PRETEND_SENDER_ADDR,
                        ledgers);

        assertTrue(sigRequiredFlag);
    }

    @Test
    void filtersPayerSinceSigIsGuaranteed() {
        given(txnCtx.activePayer()).willReturn(payer);

        boolean payerFlag =
                subject.hasActiveKeyOrNoReceiverSigReq(
                        true, EntityIdUtils.asTypedEvmAddress(payer), PRETEND_SENDER_ADDR, ledgers);

        assertTrue(payerFlag);

        verify(activationTest, never()).test(any(), any(), any());
    }

    @Test
    void createsValidityTestThatOnlyAcceptsContractIdKeyWhenBothRecipientAndContractAreActive() {
        final var uncontrolledId = EntityIdUtils.contractIdFromEvmAddress(Address.BLS12_G1ADD);
        final var controlledId = EntityIdUtils.contractIdFromEvmAddress(PRETEND_SENDER_ADDR);
        final var controlledKey = new JContractIDKey(controlledId);
        final var uncontrolledKey = new JContractIDKey(uncontrolledId);

        given(aliases.currentAddress(controlledId)).willReturn(PRETEND_SENDER_ADDR);
        given(aliases.currentAddress(uncontrolledId)).willReturn(PRETEND_TOKEN_ADDR);

        final var validityTestForNormalCall =
                subject.validityTestFor(false, PRETEND_SENDER_ADDR, aliases);
        final var validityTestForDelegateCall =
                subject.validityTestFor(true, PRETEND_SENDER_ADDR, aliases);

        assertTrue(validityTestForNormalCall.test(controlledKey, INVALID_MISSING_SIG));
        assertFalse(validityTestForDelegateCall.test(controlledKey, INVALID_MISSING_SIG));
        assertFalse(validityTestForNormalCall.test(uncontrolledKey, INVALID_MISSING_SIG));
        assertFalse(validityTestForDelegateCall.test(uncontrolledKey, INVALID_MISSING_SIG));
    }

    @Test
    void testsAccountAddressAndActiveContractIfEquals() {
        given(ledgers.accounts()).willReturn(accountsLedger);
        given(accountsLedger.exists(smartContract)).willReturn(true);

        final var verdict =
                subject.hasActiveKey(
                        true,
                        EntityIdUtils.asTypedEvmAddress(smartContract),
                        EntityIdUtils.asTypedEvmAddress(smartContract),
                        ledgers);

        assertTrue(verdict);
    }

    @Test
    void createsValidityTestThatAcceptsContractKeysWithJustRecipientActive() {
        final var uncontrolledId = EntityIdUtils.contractIdFromEvmAddress(Address.BLS12_G1ADD);
        final var controlledId = EntityIdUtils.contractIdFromEvmAddress(PRETEND_SENDER_ADDR);
        final var controlledKey = new JDelegatableContractIDKey(controlledId);
        final var uncontrolledKey = new JContractIDKey(uncontrolledId);
        final var otherControlledKey =
                new JContractAliasKey(0, 0, Address.BLS12_G1ADD.toArrayUnsafe());
        final var otherControlledId = otherControlledKey.getContractID();
        final var otherControlDelegateKey =
                new JDelegatableContractAliasKey(0, 0, Address.BLS12_G1ADD.toArrayUnsafe());

        given(aliases.currentAddress(controlledId)).willReturn(PRETEND_SENDER_ADDR);
        given(aliases.currentAddress(uncontrolledId)).willReturn(PRETEND_TOKEN_ADDR);
        given(aliases.currentAddress(otherControlledId)).willReturn(PRETEND_SENDER_ADDR);

        final var validityTestForNormalCall =
                subject.validityTestFor(false, PRETEND_SENDER_ADDR, aliases);
        final var validityTestForDelegateCall =
                subject.validityTestFor(true, PRETEND_SENDER_ADDR, aliases);

        assertTrue(validityTestForNormalCall.test(controlledKey, INVALID_MISSING_SIG));
        assertTrue(validityTestForDelegateCall.test(controlledKey, INVALID_MISSING_SIG));
        assertFalse(validityTestForNormalCall.test(uncontrolledKey, INVALID_MISSING_SIG));
        assertFalse(validityTestForDelegateCall.test(uncontrolledKey, INVALID_MISSING_SIG));
        assertTrue(validityTestForNormalCall.test(otherControlledKey, INVALID_MISSING_SIG));
        assertFalse(validityTestForDelegateCall.test(otherControlledKey, INVALID_MISSING_SIG));
        assertTrue(validityTestForDelegateCall.test(otherControlDelegateKey, INVALID_MISSING_SIG));
    }

    @Test
    void validityTestsRelyOnCryptoValidityOtherwise() {
        final var mockSig = mock(TransactionSignature.class);
        final var mockKey = new JEd25519Key("01234567890123456789012345678901".getBytes());
        given(cryptoValidity.test(mockKey, mockSig)).willReturn(true);

        final var validityTestForNormalCall =
                subject.validityTestFor(false, PRETEND_SENDER_ADDR, aliases);
        final var validityTestForDelegateCall =
                subject.validityTestFor(true, PRETEND_SENDER_ADDR, aliases);

        assertTrue(validityTestForNormalCall.test(mockKey, mockSig));
        assertTrue(validityTestForDelegateCall.test(mockKey, mockSig));
    }

    @Test
    void throwsIfAskedToVerifyTokenWithoutKycKey() {
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.KYC_KEY)).willReturn(null);

        assertFailsWith(
                () ->
                        subject.hasActiveKycKey(
                                true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers),
                TOKEN_HAS_NO_KYC_KEY);
    }

    @Test
    void testsKycKeyIfPresent() {
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.KYC_KEY)).willReturn(expectedKey);

        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

        final var verdict =
                subject.hasActiveKycKey(true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers);

        assertTrue(verdict);
    }

    @Test
    void throwsIfAskedToVerifyTokenWithoutPauseKey() {
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.PAUSE_KEY)).willReturn(null);

        assertFailsWith(
                () ->
                        subject.hasActivePauseKey(
                                true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers),
                TOKEN_HAS_NO_PAUSE_KEY);
    }

    @Test
    void testsPauseKeyIfPresent() {
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.PAUSE_KEY)).willReturn(expectedKey);

        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

        final var verdict =
                subject.hasActivePauseKey(true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers);

        assertTrue(verdict);
    }

    @Test
    void throwsIfAskedToVerifyTokenWithoutWipeKey() {
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.WIPE_KEY)).willReturn(null);

        assertFailsWith(
                () ->
                        subject.hasActiveWipeKey(
                                true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers),
                TOKEN_HAS_NO_WIPE_KEY);
    }

    @Test
    void testsWipeKeyIfPresent() {
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.WIPE_KEY)).willReturn(expectedKey);

        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

        final var verdict =
                subject.hasActiveWipeKey(true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers);

        assertTrue(verdict);
    }

    private void givenAccessorInCtx() {
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(txnCtx.activePayer()).willReturn(payer);
    }
}
