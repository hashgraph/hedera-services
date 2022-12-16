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
package com.hedera.node.app.service.mono.store.contracts.precompile;

import static com.hedera.node.app.service.mono.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO_V2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.invalidTokenIdResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokenAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokenUpdateExpiryInfoWrapper;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokenUpdateExpiryInfoWrapperWithInvalidTokenID;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.UpdateTokenExpiryInfoPrecompile.decodeUpdateTokenExpiryInfo;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.UpdateTokenExpiryInfoPrecompile.decodeUpdateTokenExpiryInfoV2;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.NftProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.expiry.ExpiringCreations;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.UpdateTokenExpiryInfoPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.store.tokens.HederaTokenStore;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Collections;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateTokenExpiryInfoPrecompileTest {
    @Mock private HederaTokenStore hederaTokenStore;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame frame;
    @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private EncodingFacade encoder;
    @Mock private EvmEncodingFacade evmEncoder;
    @Mock private TokenUpdateLogic updateLogic;
    @Mock private SideEffectsTracker sideEffects;
    @Mock private ExpirableTxnRecord.Builder mockRecordBuilder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private HederaStackedWorldStateUpdater worldUpdater;
    @Mock private WorldLedgers wrappedLedgers;
    @Mock private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nfts;

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel>
            tokenRels;

    @Mock private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts;
    @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
    @Mock private ExpiringCreations creator;
    @Mock private FeeCalculator feeCalculator;
    @Mock private StateView stateView;
    @Mock private ContractAliases aliases;
    @Mock private UsagePricesProvider resourceCosts;
    @Mock private InfrastructureFactory infrastructureFactory;
    @Mock private AssetsLoader assetLoader;
    @Mock private HbarCentExchange exchange;
    @Mock private ExchangeRate exchangeRate;
    @Mock private AccessorFactory accessorFactory;

    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    public static final Bytes UPDATE_EXPIRY_INFO_FOR_TOKEN_INPUT =
            Bytes.fromHexString(
                    "0x593d6e8200000000000000000000000000000000000000000000000000000000000008d300000000000000000000000000000000000000000000000000000000bbf7edc700000000000000000000000000000000000000000000000000000000000008d000000000000000000000000000000000000000000000000000000000002820a8");
    public static final Bytes UPDATE_EXPIRY_INFO_FOR_TOKEN_INPUT_V2 =
            Bytes.fromHexString(
                    "0xd27be6cd00000000000000000000000000000000000000000000000000000000000008d3000000000000000000000000000000000000000000000000000000000bf7edc700000000000000000000000000000000000000000000000000000000000008d00000000000000000000000000000000000000000000000000000000000000008");

    private HTSPrecompiledContract subject;
    private MockedStatic<UpdateTokenExpiryInfoPrecompile> updateTokenExpiryInfoPrecompile;

    @BeforeEach
    void setUp() {
        final PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(
                        assetLoader,
                        exchange,
                        () -> feeCalculator,
                        resourceCosts,
                        stateView,
                        accessorFactory);
        subject =
                new HTSPrecompiledContract(
                        dynamicProperties,
                        gasCalculator,
                        recordsHistorian,
                        sigsVerifier,
                        encoder,
                        evmEncoder,
                        syntheticTxnFactory,
                        creator,
                        () -> feeCalculator,
                        stateView,
                        precompilePricingUtils,
                        infrastructureFactory);

        updateTokenExpiryInfoPrecompile = Mockito.mockStatic(UpdateTokenExpiryInfoPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        if (!updateTokenExpiryInfoPrecompile.isClosed()) {
            updateTokenExpiryInfoPrecompile.close();
        }
    }

    @Test
    void updateTokenExpiryInfoHappyPath() {
        // given
        final var input = Bytes.of(Integers.toBytes(ABI_ID_UPDATE_TOKEN_EXPIRY_INFO));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        givenFrameContext();
        givenLedgers();
        givenMinimalContextForSuccessfulCall();
        givenMinimalRecordStructureForSuccessfulCall();
        givenUpdateTokenContext();
        givenPricingUtilsContext();
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        subject.getPrecompile().getMinimumFeeInTinybars(Timestamp.getDefaultInstance());
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(successResult, result);
    }

    @Test
    void updateTokenExpiryInfoV2HappyPath() {
        // given
        final var input = Bytes.of(Integers.toBytes(ABI_ID_UPDATE_TOKEN_EXPIRY_INFO_V2));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        givenFrameContext();
        givenLedgers();
        givenMinimalContextForSuccessfulCall();
        givenMinimalRecordStructureForSuccessfulCall();
        givenUpdateTokenContextV2();
        givenPricingUtilsContext();
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        subject.getPrecompile().getMinimumFeeInTinybars(Timestamp.getDefaultInstance());
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(successResult, result);
    }

    @Test
    void updateTokenExpiryInfoFailsWithInvalidTokenID() {
        // given
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        final var input = Bytes.of(Integers.toBytes(ABI_ID_UPDATE_TOKEN_EXPIRY_INFO));
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        updateTokenExpiryInfoPrecompile
                .when(() -> decodeUpdateTokenExpiryInfo(any(), any()))
                .thenReturn(tokenUpdateExpiryInfoWrapperWithInvalidTokenID);
        givenPricingUtilsContext();
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        subject.getPrecompile().getMinimumFeeInTinybars(Timestamp.getDefaultInstance());
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(invalidTokenIdResult, result);
    }

    @Test
    void decodeUpdateExpiryInfoForTokenInput() {
        updateTokenExpiryInfoPrecompile.close();
        final var decodedInput =
                decodeUpdateTokenExpiryInfo(UPDATE_EXPIRY_INFO_FOR_TOKEN_INPUT, identity());

        assertTrue(decodedInput.tokenID().getTokenNum() > 0);
        assertTrue(decodedInput.expiry().second() > 0);
        assertTrue(decodedInput.expiry().autoRenewAccount().getAccountNum() > 0);
        assertTrue(decodedInput.expiry().autoRenewPeriod() > 0);
    }

    @Test
    void decodeUpdateExpiryInfoV2ForTokenInput() {
        updateTokenExpiryInfoPrecompile.close();
        final var decodedInput =
                decodeUpdateTokenExpiryInfoV2(UPDATE_EXPIRY_INFO_FOR_TOKEN_INPUT_V2, identity());

        assertTrue(decodedInput.tokenID().getTokenNum() > 0);
        assertTrue(decodedInput.expiry().second() > 0);
        assertTrue(decodedInput.expiry().autoRenewAccount().getAccountNum() > 0);
        assertTrue(decodedInput.expiry().autoRenewPeriod() > 0);
    }

    private void givenFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getContractAddress()).willReturn(contractAddr);
        given(frame.getRecipientAddress()).willReturn(fungibleTokenAddr);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
    }

    private void givenMinimalContextForSuccessfulCall() {
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }

    private void givenUpdateTokenContext() {
        given(sigsVerifier.hasActiveAdminKey(true, tokenAddress, fungibleTokenAddr, wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);
        given(
                        infrastructureFactory.newTokenUpdateLogic(
                                hederaTokenStore, wrappedLedgers, sideEffects))
                .willReturn(updateLogic);
        given(updateLogic.validate(any())).willReturn(ResponseCodeEnum.OK);
        updateTokenExpiryInfoPrecompile
                .when(() -> decodeUpdateTokenExpiryInfo(any(), any()))
                .thenReturn(tokenUpdateExpiryInfoWrapper);
        given(syntheticTxnFactory.createTokenUpdateExpiryInfo(tokenUpdateExpiryInfoWrapper))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setTokenUpdate(TokenUpdateTransactionBody.newBuilder()));
    }

    private void givenUpdateTokenContextV2() {
        given(sigsVerifier.hasActiveAdminKey(true, tokenAddress, fungibleTokenAddr, wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);
        given(
                        infrastructureFactory.newTokenUpdateLogic(
                                hederaTokenStore, wrappedLedgers, sideEffects))
                .willReturn(updateLogic);
        given(updateLogic.validate(any())).willReturn(ResponseCodeEnum.OK);
        updateTokenExpiryInfoPrecompile
                .when(() -> decodeUpdateTokenExpiryInfoV2(any(), any()))
                .thenReturn(tokenUpdateExpiryInfoWrapper);
        given(syntheticTxnFactory.createTokenUpdateExpiryInfo(tokenUpdateExpiryInfoWrapper))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setTokenUpdate(TokenUpdateTransactionBody.newBuilder()));
    }

    private void givenMinimalRecordStructureForSuccessfulCall() {
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
    }

    private void givenPricingUtilsContext() {
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }

    private void givenLedgers() {
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(wrappedLedgers.tokenRels()).willReturn(tokenRels);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(wrappedLedgers.tokens()).willReturn(tokens);
    }
}
