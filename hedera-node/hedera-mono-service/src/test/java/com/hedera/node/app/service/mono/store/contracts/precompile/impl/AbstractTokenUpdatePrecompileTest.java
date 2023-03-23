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
package com.hedera.node.app.service.mono.store.contracts.precompile.impl;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.InfrastructureFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.TokenUpdateLogic;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.sigs.KeyValidator;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.sigs.LegacyKeyValidator;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.KeyActivationTest;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.LegacyActivationTest;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.LegacyKeyActivationTest;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.store.tokens.HederaTokenStore;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.UnaryOperator;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractTokenUpdatePrecompileTest {
    @Mock private MessageFrame frame;
    @Mock private WorldLedgers ledgers;
    @Mock private ContractAliases aliases;
    @Mock private EvmSigsVerifier sigsVerifier;
    @Mock private SideEffectsTracker sideEffectsTracker;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private InfrastructureFactory infrastructureFactory;
    @Mock private PrecompilePricingUtils pricingUtils;
    @Mock private KeyValidator keyValidator;
    @Mock private HederaTokenStore tokenStore;
    @Mock private TokenUpdateLogic updateLogic;
    @Mock private BlockValues blockValues;
    @Mock private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts;
    @Mock private LegacyKeyValidator legacyKeyValidator;
    @Mock private LegacyActivationTest legacyActivationTest;

    private SigTestingTokenUpdatePrecompile subject;

    @BeforeEach
    void setUp() {
        subject =
                new SigTestingTokenUpdatePrecompile(
                        keyValidator,
                        legacyKeyValidator,
                        ledgers,
                        aliases,
                        sigsVerifier,
                        sideEffectsTracker,
                        syntheticTxnFactory,
                        infrastructureFactory,
                        pricingUtils);
    }

    @Test
    void validatesAdminKeyAndNewTreasury() {
        final var captor = forClass(KeyActivationTest.class);
        final var legacyCaptor = forClass(LegacyKeyActivationTest.class);

        given(frame.getBlockValues()).willReturn(blockValues);
        given(infrastructureFactory.newHederaTokenStore(any(), any(), any(), any()))
                .willReturn(tokenStore);
        given(infrastructureFactory.newTokenUpdateLogic(tokenStore, ledgers, sideEffectsTracker))
                .willReturn(updateLogic);
        given(updateLogic.validate(any())).willReturn(OK);
        given(keyValidator.validateKey(any(), any(), any(), any(), any(), any())).willReturn(true);
        given(legacyKeyValidator.validateKey(any(), any(), any(), any(), any(), any()))
                .willReturn(true);
        given(ledgers.accounts()).willReturn(accounts);
        given(accounts.exists(newTreasury)).willReturn(true);

        subject.useBodyWithNewTreasury();
        subject.run(frame);

        verify(keyValidator)
                .validateKey(
                        eq(frame),
                        eq(tokenMirrorAddress),
                        captor.capture(),
                        eq(ledgers),
                        eq(aliases),
                        eq(TokenUpdate));
        verify(legacyKeyValidator)
                .validateKey(
                        eq(frame),
                        eq(newTreasuryMirrorAddress),
                        legacyCaptor.capture(),
                        eq(ledgers),
                        eq(aliases),
                        eq(TokenUpdate));
        verify(updateLogic).updateToken(any(), anyLong());
        // and when:
        final var tests = captor.getAllValues();
        final var legacyTests = legacyCaptor.getAllValues();
        tests.get(0)
                .apply(false, tokenMirrorAddress, pretendActiveContract, ledgers, CryptoTransfer);
        verify(sigsVerifier)
                .hasActiveAdminKey(
                        false, tokenMirrorAddress, pretendActiveContract, ledgers, CryptoTransfer);
        legacyTests
                .get(0)
                .apply(
                        false,
                        newTreasuryMirrorAddress,
                        pretendActiveContract,
                        ledgers,
                        legacyActivationTest,
                        TokenUpdate);
        verify(sigsVerifier)
                .hasLegacyActiveKey(
                        false,
                        newTreasuryMirrorAddress,
                        pretendActiveContract,
                        ledgers,
                        legacyActivationTest,
                        TokenUpdate);
    }

    @Test
    void validatesAdminKeyAndNewAutoRenew() {
        final var captor = forClass(KeyActivationTest.class);
        final var legacyCaptor = forClass(LegacyKeyActivationTest.class);

        given(infrastructureFactory.newHederaTokenStore(any(), any(), any(), any()))
                .willReturn(tokenStore);
        given(infrastructureFactory.newTokenUpdateLogic(tokenStore, ledgers, sideEffectsTracker))
                .willReturn(updateLogic);
        given(updateLogic.validate(any())).willReturn(OK);
        given(keyValidator.validateKey(any(), any(), any(), any(), any(), eq(TokenUpdate)))
                .willReturn(true);
        given(legacyKeyValidator.validateKey(any(), any(), any(), any(), any(), eq(TokenUpdate)))
                .willReturn(true);
        given(ledgers.accounts()).willReturn(accounts);
        given(accounts.exists(newAutoRenew)).willReturn(true);

        subject.useBodyWithNewAutoRenew();
        subject.run(frame);

        verify(keyValidator)
                .validateKey(
                        eq(frame),
                        eq(tokenMirrorAddress),
                        captor.capture(),
                        eq(ledgers),
                        eq(aliases),
                        eq(TokenUpdate));
        verify(legacyKeyValidator)
                .validateKey(
                        eq(frame),
                        eq(newAutoRenewMirrorAddress),
                        legacyCaptor.capture(),
                        eq(ledgers),
                        eq(aliases),
                        eq(TokenUpdate));
        verify(updateLogic).updateTokenExpiryInfo(any());
        // and when:
        final var tests = captor.getAllValues();
        final var legacyTests = legacyCaptor.getAllValues();
        tests.get(0)
                .apply(false, tokenMirrorAddress, pretendActiveContract, ledgers, CryptoTransfer);
        verify(sigsVerifier)
                .hasActiveAdminKey(
                        false, tokenMirrorAddress, pretendActiveContract, ledgers, CryptoTransfer);
        legacyTests
                .get(0)
                .apply(
                        false,
                        newAutoRenewMirrorAddress,
                        pretendActiveContract,
                        ledgers,
                        legacyActivationTest,
                        TokenUpdate);
        verify(sigsVerifier)
                .hasLegacyActiveKey(
                        false,
                        newAutoRenewMirrorAddress,
                        pretendActiveContract,
                        ledgers,
                        legacyActivationTest,
                        TokenUpdate);
    }

    @Test
    void validatesOnlyAdminKeyWithNoOtherSigReqs() {
        final var captor = forClass(KeyActivationTest.class);

        given(frame.getBlockValues()).willReturn(blockValues);
        given(infrastructureFactory.newHederaTokenStore(any(), any(), any(), any()))
                .willReturn(tokenStore);
        given(infrastructureFactory.newTokenUpdateLogic(tokenStore, ledgers, sideEffectsTracker))
                .willReturn(updateLogic);
        given(updateLogic.validate(any())).willReturn(OK);
        given(keyValidator.validateKey(any(), any(), any(), any(), any(), eq(TokenUpdate)))
                .willReturn(true);
        given(ledgers.accounts()).willReturn(accounts);

        subject.useBodyWithNoOtherSigReqs();
        subject.run(frame);

        verify(keyValidator)
                .validateKey(
                        eq(frame),
                        eq(tokenMirrorAddress),
                        captor.capture(),
                        eq(ledgers),
                        eq(aliases),
                        eq(TokenUpdate));
        verifyNoMoreInteractions(keyValidator);
        verify(updateLogic).updateToken(any(), anyLong());
        // and when:
        final var tests = captor.getAllValues();
        tests.get(0)
                .apply(false, tokenMirrorAddress, pretendActiveContract, ledgers, CryptoTransfer);
        verify(sigsVerifier)
                .hasActiveAdminKey(
                        false, tokenMirrorAddress, pretendActiveContract, ledgers, CryptoTransfer);
        verifyNoMoreInteractions(sigsVerifier);
    }

    private static class SigTestingTokenUpdatePrecompile extends AbstractTokenUpdatePrecompile {
        protected SigTestingTokenUpdatePrecompile(
                final KeyValidator keyValidator,
                final LegacyKeyValidator legacyKeyValidator,
                final WorldLedgers ledgers,
                final ContractAliases aliases,
                final EvmSigsVerifier sigsVerifier,
                final SideEffectsTracker sideEffectsTracker,
                final SyntheticTxnFactory syntheticTxnFactory,
                final InfrastructureFactory infrastructureFactory,
                final PrecompilePricingUtils pricingUtils) {
            super(
                    keyValidator,
                    legacyKeyValidator,
                    ledgers,
                    aliases,
                    sigsVerifier,
                    sideEffectsTracker,
                    syntheticTxnFactory,
                    infrastructureFactory,
                    pricingUtils);
        }

        @Override
        public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
            throw new NotImplementedException();
        }

        public void useBodyWithNewTreasury() {
            type = UpdateType.UPDATE_TOKEN_INFO;
            tokenId = Id.fromGrpcToken(targetId);
            final var updateOp = baseBuilder().setTreasury(newTreasury);
            transactionBody = TransactionBody.newBuilder().setTokenUpdate(updateOp);
        }

        public void useBodyWithNewAutoRenew() {
            type = UpdateType.UPDATE_TOKEN_EXPIRY;
            tokenId = Id.fromGrpcToken(targetId);
            final var updateOp = baseBuilder().setAutoRenewAccount(newAutoRenew);
            transactionBody = TransactionBody.newBuilder().setTokenUpdate(updateOp);
        }

        public void useBodyWithNoOtherSigReqs() {
            type = UpdateType.UPDATE_TOKEN_INFO;
            tokenId = Id.fromGrpcToken(targetId);
            final var updateOp = baseBuilder();
            transactionBody = TransactionBody.newBuilder().setTokenUpdate(updateOp);
        }

        private TokenUpdateTransactionBody.Builder baseBuilder() {
            return TokenUpdateTransactionBody.newBuilder().setToken(targetId);
        }
    }

    private static final TokenID targetId = TokenID.newBuilder().setTokenNum(666).build();
    private static final Id tokenId = Id.fromGrpcToken(targetId);
    private static final AccountID newTreasury = AccountID.newBuilder().setAccountNum(2345).build();
    private static final AccountID newAutoRenew =
            AccountID.newBuilder().setAccountNum(7777).build();
    private static final Address tokenMirrorAddress = tokenId.asEvmAddress();
    private static final Address newTreasuryMirrorAddress =
            Id.fromGrpcAccount(newTreasury).asEvmAddress();
    private static final Address newAutoRenewMirrorAddress =
            Id.fromGrpcAccount(newAutoRenew).asEvmAddress();
    private static final Address pretendActiveContract = Address.BLAKE2B_F_COMPRESSION;
}
