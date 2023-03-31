/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.contracts.sources;

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

import static com.hedera.node.app.service.mono.keys.HederaKeyActivation.INVALID_MISSING_SIG;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.KEY;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.keys.ActivationTest;
import com.hedera.node.app.service.mono.keys.HederaKeyActivation;
import com.hedera.node.app.service.mono.keys.LegacyContractIdActivations;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.legacy.core.jproto.JContractAliasKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JContractIDKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JDelegatableContractAliasKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JDelegatableContractIDKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKeyList;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.LegacyActivationTest;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.crypto.TransactionSignature;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TxnAwareEvmSigsVerifierTest {
    private static final Address PRETEND_SENDER_ADDR = Address.ALTBN128_PAIRING;
    private static final Id tokenId = new Id(0, 0, 666);
    private static final Id accountId = new Id(0, 0, 1234);
    private static final Address PRETEND_TOKEN_ADDR = tokenId.asEvmAddress();
    private static final Address PRETEND_ACCOUNT_ADDR = accountId.asEvmAddress();
    private static final Address PRETEND_CONTRACT_ADDR = Address.BLAKE2B_F_COMPRESSION;
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
    @Mock private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger;
    @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;
    @Mock private WorldLedgers ledgers;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private LegacyActivationTest legacyActivationTest;

    private TxnAwareEvmSigsVerifier subject;

    @BeforeEach
    void setup() throws Exception {
        expectedKey = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKey();

        subject =
                new TxnAwareEvmSigsVerifier(
                        activationTest, txnCtx, cryptoValidity, dynamicProperties);
    }

    @Test
    void throwsIfAskedToVerifyMissingToken() {
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(false);

        assertFailsWith(
                () ->
                        subject.hasActiveSupplyKey(
                                true,
                                PRETEND_TOKEN_ADDR,
                                PRETEND_SENDER_ADDR,
                                ledgers,
                                TokenCreate),
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
                                true,
                                PRETEND_TOKEN_ADDR,
                                PRETEND_SENDER_ADDR,
                                ledgers,
                                TokenCreate),
                TOKEN_HAS_NO_SUPPLY_KEY);
    }

    @Test
    void testsSupplyKeyIfPresent() {
        given(dynamicProperties.contractsWithSpecialHapiSigsAccess())
                .willReturn(Set.of(PRETEND_SENDER_ADDR));
        given(dynamicProperties.systemContractsWithTopLevelSigsAccess())
                .willReturn(Set.of(TokenCreate));
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.SUPPLY_KEY)).willReturn(expectedKey);

        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

        final var verdict =
                subject.hasActiveSupplyKey(
                        true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers, TokenCreate);

        assertTrue(verdict);
    }

    @Test
    void onlyUsesTopLevelSigsToTestSupplyKeyIfTokenCreateInAllowList() {
        final ArgumentCaptor<Function<byte[], TransactionSignature>> captor =
                forClass(Function.class);
        given(dynamicProperties.contractsWithSpecialHapiSigsAccess())
                .willReturn(Set.of(PRETEND_SENDER_ADDR));
        given(dynamicProperties.systemContractsWithTopLevelSigsAccess())
                .willReturn(Set.of(TokenUpdate));
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.SUPPLY_KEY)).willReturn(expectedKey);

        given(activationTest.test(eq(expectedKey), captor.capture(), any())).willReturn(true);

        final var verdict =
                subject.hasActiveSupplyKey(
                        true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers, TokenCreate);

        assertTrue(verdict);
        final var neverValidTest = captor.getValue();
        assertSame(INVALID_MISSING_SIG, neverValidTest.apply(new byte[0]));
    }

    @Test
    void supplyKeyFailsWhenTokensLedgerIsNull() {
        given(ledgers.tokens()).willReturn(null);

        assertThrows(
                NullPointerException.class,
                () ->
                        subject.hasActiveSupplyKey(
                                true,
                                PRETEND_TOKEN_ADDR,
                                PRETEND_SENDER_ADDR,
                                ledgers,
                                TokenCreate));
    }

    @Test
    void throwsIfAskedToVerifyTokenWithoutFreezeKey() {
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.FREEZE_KEY)).willReturn(null);

        assertFailsWith(
                () ->
                        subject.hasActiveFreezeKey(
                                true,
                                PRETEND_TOKEN_ADDR,
                                PRETEND_SENDER_ADDR,
                                ledgers,
                                TokenCreate),
                TOKEN_HAS_NO_FREEZE_KEY);
    }

    @Test
    void testsFreezeKeyIfPresent() {
        given(dynamicProperties.contractsWithSpecialHapiSigsAccess())
                .willReturn(Set.of(PRETEND_SENDER_ADDR));
        given(dynamicProperties.systemContractsWithTopLevelSigsAccess())
                .willReturn(Set.of(TokenCreate));
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.FREEZE_KEY)).willReturn(expectedKey);

        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

        final var verdict =
                subject.hasActiveFreezeKey(
                        true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers, TokenCreate);

        assertTrue(verdict);
    }

    @Test
    void throwsIfAskedToVerifyMissingAccount() {
        given(ledgers.accounts()).willReturn(accountsLedger);
        given(accountsLedger.exists(account)).willReturn(false);

        assertFailsWith(
                () ->
                        subject.hasActiveKey(
                                true,
                                PRETEND_ACCOUNT_ADDR,
                                PRETEND_SENDER_ADDR,
                                ledgers,
                                TokenCreate),
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
                                true,
                                PRETEND_TOKEN_ADDR,
                                PRETEND_SENDER_ADDR,
                                ledgers,
                                TokenCreate),
                TOKEN_IS_IMMUTABLE);
    }

    @Test
    void testsAdminKeyIfPresent() {
        given(dynamicProperties.contractsWithSpecialHapiSigsAccess())
                .willReturn(Set.of(PRETEND_SENDER_ADDR));
        given(dynamicProperties.systemContractsWithTopLevelSigsAccess())
                .willReturn(Set.of(TokenCreate));
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.ADMIN_KEY)).willReturn(expectedKey);

        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

        final var verdict =
                subject.hasActiveAdminKey(
                        true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers, TokenCreate);

        assertTrue(verdict);
    }

    @Test
    void testsAccountKeyIfPresent() {
        given(dynamicProperties.systemContractsWithTopLevelSigsAccess())
                .willReturn(Set.of(TokenCreate));
        given(dynamicProperties.contractsWithSpecialHapiSigsAccess())
                .willReturn(Set.of(PRETEND_SENDER_ADDR));
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(ledgers.accounts()).willReturn(accountsLedger);
        given(accountsLedger.exists(account)).willReturn(true);
        given(accountsLedger.get(account, AccountProperty.KEY)).willReturn(expectedKey);
        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

        final var verdict =
                subject.hasActiveKey(
                        true, PRETEND_ACCOUNT_ADDR, PRETEND_SENDER_ADDR, ledgers, TokenCreate);

        assertTrue(verdict);
    }

    @Test
    void testsAccountKeyIfPresentButInvalid() {
        given(dynamicProperties.contractsWithSpecialHapiSigsAccess())
                .willReturn(Set.of(PRETEND_SENDER_ADDR));
        given(dynamicProperties.systemContractsWithTopLevelSigsAccess())
                .willReturn(Set.of(TokenCreate));
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(ledgers.accounts()).willReturn(accountsLedger);
        given(accountsLedger.exists(account)).willReturn(true);
        given(accountsLedger.get(account, AccountProperty.KEY)).willReturn(expectedKey);
        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(false);

        final var verdict =
                subject.hasActiveKey(
                        true, PRETEND_ACCOUNT_ADDR, PRETEND_SENDER_ADDR, ledgers, TokenCreate);

        assertFalse(verdict);
    }

    @Test
    void testsMissingAccountKey() {
        given(ledgers.accounts()).willReturn(accountsLedger);
        given(accountsLedger.exists(account)).willReturn(true);
        given(accountsLedger.get(account, AccountProperty.KEY)).willReturn(null);

        final var verdict =
                subject.hasActiveKey(
                        true, PRETEND_ACCOUNT_ADDR, PRETEND_SENDER_ADDR, ledgers, TokenCreate);

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
    void testsEmptyKeyList() {
        final JKeyList emptyKeyList = new JKeyList();

        given(ledgers.accounts()).willReturn(accountsLedger);
        given(accountsLedger.exists(account)).willReturn(true);
        given(accountsLedger.get(account, AccountProperty.KEY)).willReturn(emptyKeyList);

        final var verdict =
                subject.hasActiveKey(
                        true, PRETEND_ACCOUNT_ADDR, PRETEND_SENDER_ADDR, ledgers, TokenCreate);

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
                        ledgers,
                        TokenCreate);

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
                        ledgers,
                        TokenCreate);

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
                        ledgers,
                        TokenCreate);

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
                        ledgers,
                        TokenCreate);

        assertTrue(noSigRequiredFlag);
        verify(activationTest, never()).test(any(), any(), any());
    }

    @Test
    void testsWhenReceiverSigIsRequired() {
        givenAccessorInCtx();
        given(dynamicProperties.contractsWithSpecialHapiSigsAccess())
                .willReturn(Set.of(PRETEND_SENDER_ADDR));
        given(dynamicProperties.systemContractsWithTopLevelSigsAccess())
                .willReturn(Set.of(TokenCreate));
        givenSigReqCheckable(sigRequired, true, expectedKey);
        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);

        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

        final boolean sigRequiredFlag =
                subject.hasActiveKeyOrNoReceiverSigReq(
                        true,
                        EntityIdUtils.asTypedEvmAddress(sigRequired),
                        PRETEND_SENDER_ADDR,
                        ledgers,
                        TokenCreate);

        assertTrue(sigRequiredFlag);
    }

    @Test
    void filtersPayerSinceSigIsGuaranteed() {
        given(txnCtx.activePayer()).willReturn(payer);

        final boolean payerFlag =
                subject.hasActiveKeyOrNoReceiverSigReq(
                        true,
                        EntityIdUtils.asTypedEvmAddress(payer),
                        PRETEND_SENDER_ADDR,
                        ledgers,
                        TokenCreate);

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
        final var verdict =
                subject.hasActiveKey(
                        true,
                        EntityIdUtils.asTypedEvmAddress(smartContract),
                        EntityIdUtils.asTypedEvmAddress(smartContract),
                        ledgers,
                        TokenCreate);

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
                                true,
                                PRETEND_TOKEN_ADDR,
                                PRETEND_SENDER_ADDR,
                                ledgers,
                                TokenCreate),
                TOKEN_HAS_NO_KYC_KEY);
    }

    @Test
    void legacyActivationObvNeedsNonNull() {
        assertFalse(subject.hasLegacyActivation(PRETEND_CONTRACT_ADDR, null, Set.of()));
        assertFalse(subject.hasLegacyActivation(PRETEND_CONTRACT_ADDR, legacyActivationTest, null));
    }

    @Test
    void testsKycKeyIfPresent() {
        given(dynamicProperties.contractsWithSpecialHapiSigsAccess())
                .willReturn(Set.of(PRETEND_SENDER_ADDR));
        given(dynamicProperties.systemContractsWithTopLevelSigsAccess())
                .willReturn(Set.of(TokenCreate));
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.KYC_KEY)).willReturn(expectedKey);

        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

        final var verdict =
                subject.hasActiveKycKey(
                        true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers, TokenCreate);

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
                                true,
                                PRETEND_TOKEN_ADDR,
                                PRETEND_SENDER_ADDR,
                                ledgers,
                                TokenCreate),
                TOKEN_HAS_NO_PAUSE_KEY);
    }

    @Test
    void testsPauseKeyIfPresent() {
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(dynamicProperties.contractsWithSpecialHapiSigsAccess())
                .willReturn(Set.of(PRETEND_SENDER_ADDR));
        given(dynamicProperties.systemContractsWithTopLevelSigsAccess())
                .willReturn(Set.of(TokenCreate));
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.PAUSE_KEY)).willReturn(expectedKey);

        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

        final var verdict =
                subject.hasActivePauseKey(
                        true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers, TokenCreate);

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
                                true,
                                PRETEND_TOKEN_ADDR,
                                PRETEND_SENDER_ADDR,
                                ledgers,
                                TokenCreate),
                TOKEN_HAS_NO_WIPE_KEY);
    }

    @Test
    void testsWipeKeyIfPresent() {
        given(dynamicProperties.contractsWithSpecialHapiSigsAccess())
                .willReturn(Set.of(PRETEND_SENDER_ADDR));
        given(dynamicProperties.systemContractsWithTopLevelSigsAccess())
                .willReturn(Set.of(TokenCreate));
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(ledgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(token)).willReturn(true);
        given(tokensLedger.get(token, TokenProperty.WIPE_KEY)).willReturn(expectedKey);

        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
        given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

        final var verdict =
                subject.hasActiveWipeKey(
                        true, PRETEND_TOKEN_ADDR, PRETEND_SENDER_ADDR, ledgers, TokenCreate);

        assertTrue(verdict);
    }

    @Test
    void createsValidityTestThatUsesLegacyActivationsIfOnStack() {
        final var controlledId = EntityIdUtils.contractIdFromEvmAddress(PRETEND_CONTRACT_ADDR);
        final var controlledKey = new JContractIDKey(controlledId);

        given(aliases.currentAddress(controlledId)).willReturn(PRETEND_CONTRACT_ADDR);

        final var impliedSubject =
                subject.validityTestFor(
                        false,
                        PRETEND_SENDER_ADDR,
                        aliases,
                        legacyActivationTest,
                        Set.of(PRETEND_CONTRACT_ADDR));

        assertFalse(impliedSubject.test(controlledKey, INVALID_MISSING_SIG));
    }

    @Test
    void createsValidityTestThatOnlyUsesLegacyActivationsIfContractGrandfathered() {
        final var controlledId = EntityIdUtils.contractIdFromEvmAddress(PRETEND_CONTRACT_ADDR);
        final var controlledKey = new JContractIDKey(controlledId);

        given(aliases.currentAddress(controlledId)).willReturn(PRETEND_CONTRACT_ADDR);

        final var impliedSubject =
                subject.validityTestFor(
                        false,
                        PRETEND_SENDER_ADDR,
                        aliases,
                        legacyActivationTest,
                        Set.of(PRETEND_TOKEN_ADDR));

        assertFalse(impliedSubject.test(controlledKey, INVALID_MISSING_SIG));
    }

    @Test
    void usesLegacyActivationsWhenAvailable() {
        given(dynamicProperties.contractsWithSpecialHapiSigsAccess())
                .willReturn(Set.of(PRETEND_SENDER_ADDR));
        given(dynamicProperties.systemContractsWithTopLevelSigsAccess())
                .willReturn(Set.of(TokenCreate));
        subject =
                new TxnAwareEvmSigsVerifier(
                        HederaKeyActivation::isActive, txnCtx, cryptoValidity, dynamicProperties);

        final var controlledId = EntityIdUtils.contractIdFromEvmAddress(PRETEND_CONTRACT_ADDR);
        final var controlledKey = new JContractIDKey(controlledId);

        final var legacyActivations =
                new LegacyContractIdActivations(
                        Map.of(PRETEND_ACCOUNT_ADDR, Set.of(PRETEND_CONTRACT_ADDR)));
        given(dynamicProperties.legacyContractIdActivations()).willReturn(legacyActivations);

        given(ledgers.accounts()).willReturn(accountsLedger);
        given(accountsLedger.exists(account)).willReturn(true);
        given(accountsLedger.get(account, KEY)).willReturn(controlledKey);
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
        given(ledgers.aliases()).willReturn(aliases);

        given(aliases.currentAddress(controlledId)).willReturn(PRETEND_CONTRACT_ADDR);
        given(legacyActivationTest.stackIncludesReceiver(PRETEND_CONTRACT_ADDR)).willReturn(true);

        assertTrue(
                subject.hasLegacyActiveKey(
                        false,
                        PRETEND_ACCOUNT_ADDR,
                        PRETEND_SENDER_ADDR,
                        ledgers,
                        legacyActivationTest,
                        TokenCreate));
    }

    @Test
    void createsValidityTestThatUsesLegacyActivationsIfAvailable() {
        final var controlledId = EntityIdUtils.contractIdFromEvmAddress(PRETEND_CONTRACT_ADDR);
        final var controlledKey = new JContractIDKey(controlledId);

        given(aliases.currentAddress(controlledId)).willReturn(PRETEND_CONTRACT_ADDR);
        given(legacyActivationTest.stackIncludesReceiver(PRETEND_CONTRACT_ADDR)).willReturn(true);

        final var impliedSubject =
                subject.validityTestFor(
                        false,
                        PRETEND_SENDER_ADDR,
                        aliases,
                        legacyActivationTest,
                        Set.of(PRETEND_CONTRACT_ADDR));

        assertTrue(impliedSubject.test(controlledKey, INVALID_MISSING_SIG));
    }

    private void givenAccessorInCtx() {
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        given(txnCtx.activePayer()).willReturn(payer);
    }
}
