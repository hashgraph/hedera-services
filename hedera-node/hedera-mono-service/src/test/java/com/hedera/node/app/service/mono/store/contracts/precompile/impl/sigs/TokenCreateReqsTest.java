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
package com.hedera.services.store.contracts.precompile.impl.sigs;

import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.services.contracts.sources.EvmSigsVerifier;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.utils.LegacyActivationTest;
import com.hedera.services.store.contracts.precompile.utils.LegacyKeyActivationTest;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenCreateReqsTest {
    @Mock private MessageFrame frame;
    @Mock private LegacyKeyValidator legacyKeyValidator;
    @Mock private ContractAliases aliases;
    @Mock private EvmSigsVerifier sigsVerifier;
    @Mock private WorldLedgers ledgers;
    @Mock private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts;
    @Mock private LegacyActivationTest legacyActivationTest;

    private TokenCreateReqs subject;

    @BeforeEach
    void setUp() {
        subject = new TokenCreateReqs(frame, legacyKeyValidator, aliases, sigsVerifier, ledgers);
    }

    @Test
    void requiresAutoRenewAccountToSign() {
        final var captor = forClass(LegacyKeyActivationTest.class);

        given(legacyKeyValidator.validateKey(any(), any(), any(), any(), any())).willReturn(true);
        given(ledgers.accounts()).willReturn(accounts);
        given(accounts.exists(autoRenew)).willReturn(true);

        final var op = baseCreateOp().setAutoRenewAccount(autoRenew);

        subject.assertNonAdminOrTreasurySigs(op.build());

        verify(legacyKeyValidator)
                .validateKey(
                        eq(frame),
                        eq(autoRenewMirrorAddress),
                        captor.capture(),
                        eq(ledgers),
                        eq(aliases));
        // and when:
        final var tests = captor.getAllValues();
        tests.get(0)
                .apply(
                        false,
                        autoRenewMirrorAddress,
                        pretendActiveContract,
                        ledgers,
                        legacyActivationTest);
        verify(sigsVerifier)
                .hasLegacyActiveKey(
                        false,
                        autoRenewMirrorAddress,
                        pretendActiveContract,
                        ledgers,
                        legacyActivationTest);
    }

    @Test
    void requiresFractionalFeeCollectorToSignButNotOtherDenominatedFixed() {
        final var captor = forClass(LegacyKeyActivationTest.class);

        given(legacyKeyValidator.validateKey(any(), any(), any(), any(), any())).willReturn(true);
        given(ledgers.accounts()).willReturn(accounts);
        given(accounts.exists(aCollector)).willReturn(true);

        final var op =
                baseCreateOp()
                        .addCustomFees(aFractionalFee(aCollector))
                        .addCustomFees(anOtherDenominatedFixedFee(bCollector));

        subject.assertNonAdminOrTreasurySigs(op.build());

        verify(legacyKeyValidator)
                .validateKey(
                        eq(frame),
                        eq(aCollectorAddress),
                        captor.capture(),
                        eq(ledgers),
                        eq(aliases));
        verifyNoMoreInteractions(legacyKeyValidator);
        // and when:
        final var tests = captor.getAllValues();
        tests.get(0)
                .apply(
                        false,
                        aCollectorAddress,
                        pretendActiveContract,
                        ledgers,
                        legacyActivationTest);
        verify(sigsVerifier)
                .hasLegacyActiveKey(
                        false,
                        aCollectorAddress,
                        pretendActiveContract,
                        ledgers,
                        legacyActivationTest);
    }

    @Test
    void requiresSelfDenominatedFixedToSignButNotOtherDenominatedRoyaltyFallback() {
        final var captor = forClass(LegacyKeyActivationTest.class);

        given(legacyKeyValidator.validateKey(any(), any(), any(), any(), any())).willReturn(true);
        given(ledgers.accounts()).willReturn(accounts);
        given(accounts.exists(aCollector)).willReturn(true);

        final var op =
                baseCreateOp()
                        .addCustomFees(aSelfDenominatedFixedFee(aCollector))
                        .addCustomFees(aRoyaltyWithOtherDenominatedFallback(bCollector));

        subject.assertNonAdminOrTreasurySigs(op.build());

        verify(legacyKeyValidator)
                .validateKey(
                        eq(frame),
                        eq(aCollectorAddress),
                        captor.capture(),
                        eq(ledgers),
                        eq(aliases));
        verifyNoMoreInteractions(legacyKeyValidator);
        // and when:
        final var tests = captor.getAllValues();
        tests.get(0)
                .apply(
                        false,
                        aCollectorAddress,
                        pretendActiveContract,
                        ledgers,
                        legacyActivationTest);
        verify(sigsVerifier)
                .hasLegacyActiveKey(
                        false,
                        aCollectorAddress,
                        pretendActiveContract,
                        ledgers,
                        legacyActivationTest);
    }

    @Test
    void requiresSelfDenominatedFallbackToSignButNotRoyaltyWithoutFallback() {
        final var captor = forClass(LegacyKeyActivationTest.class);

        given(legacyKeyValidator.validateKey(any(), any(), any(), any(), any())).willReturn(true);
        given(ledgers.accounts()).willReturn(accounts);
        given(accounts.exists(aCollector)).willReturn(true);

        final var op =
                baseCreateOp()
                        .addCustomFees(aRoyaltyWithSelfDenominatedFallback(aCollector))
                        .addCustomFees(aRoyaltyWithNoFallback(bCollector));

        subject.assertNonAdminOrTreasurySigs(op.build());

        verify(legacyKeyValidator)
                .validateKey(
                        eq(frame),
                        eq(aCollectorAddress),
                        captor.capture(),
                        eq(ledgers),
                        eq(aliases));
        verifyNoMoreInteractions(legacyKeyValidator);
        // and when:
        final var tests = captor.getAllValues();
        tests.get(0)
                .apply(
                        false,
                        aCollectorAddress,
                        pretendActiveContract,
                        ledgers,
                        legacyActivationTest);
        verify(sigsVerifier)
                .hasLegacyActiveKey(
                        false,
                        aCollectorAddress,
                        pretendActiveContract,
                        ledgers,
                        legacyActivationTest);
    }

    private TokenCreateTransactionBody.Builder baseCreateOp() {
        return TokenCreateTransactionBody.newBuilder();
    }

    private CustomFee aFractionalFee(final AccountID collector) {
        return FcCustomFee.fractionalFee(
                        1, 10, 0, 0, false, EntityId.fromGrpcAccountId(collector), true)
                .asGrpc();
    }

    private CustomFee aSelfDenominatedFixedFee(final AccountID collector) {
        return FcCustomFee.fixedFee(
                        1, EntityId.MISSING_ENTITY_ID, EntityId.fromGrpcAccountId(collector), true)
                .asGrpc();
    }

    private CustomFee anOtherDenominatedFixedFee(final AccountID collector) {
        return FcCustomFee.fixedFee(
                        1,
                        EntityId.fromGrpcTokenId(denomination),
                        EntityId.fromGrpcAccountId(collector),
                        true)
                .asGrpc();
    }

    private CustomFee aRoyaltyWithSelfDenominatedFallback(final AccountID collector) {
        final var collectorId = EntityId.fromGrpcAccountId(collector);
        return FcCustomFee.royaltyFee(
                        1,
                        10,
                        FcCustomFee.fixedFee(1, EntityId.MISSING_ENTITY_ID, collectorId, true)
                                .getFixedFeeSpec(),
                        collectorId,
                        true)
                .asGrpc();
    }

    private CustomFee aRoyaltyWithOtherDenominatedFallback(final AccountID collector) {
        final var collectorId = EntityId.fromGrpcAccountId(collector);
        return FcCustomFee.royaltyFee(
                        1,
                        10,
                        FcCustomFee.fixedFee(
                                        1,
                                        EntityId.fromGrpcTokenId(denomination),
                                        collectorId,
                                        true)
                                .getFixedFeeSpec(),
                        collectorId,
                        true)
                .asGrpc();
    }

    private CustomFee aRoyaltyWithNoFallback(final AccountID collector) {
        final var collectorId = EntityId.fromGrpcAccountId(collector);
        return FcCustomFee.royaltyFee(1, 10, null, collectorId, true).asGrpc();
    }

    private static final AccountID autoRenew = AccountID.newBuilder().setAccountNum(7777).build();
    private static final Address autoRenewMirrorAddress =
            Id.fromGrpcAccount(autoRenew).asEvmAddress();
    private static final Address pretendActiveContract = Address.BLAKE2B_F_COMPRESSION;
    private static final AccountID aCollector = AccountID.newBuilder().setAccountNum(8888).build();
    private static final AccountID bCollector = AccountID.newBuilder().setAccountNum(9999).build();
    private static final TokenID denomination = TokenID.newBuilder().setTokenNum(9999).build();
    private static final Address aCollectorAddress = Id.fromGrpcAccount(aCollector).asEvmAddress();
}
