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
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_BURN_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.BurnPrecompile.decodeBurn;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.BurnPrecompile.decodeBurnV2;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.ledger.properties.NftProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty;
import com.hedera.node.app.service.mono.legacy.core.jproto.TxnReceipt;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.expiry.ExpiringCreations;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.TypedTokenStore;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.BurnPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.txns.token.BurnLogic;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BurnPrecompilesTest {

    private final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_BURN_TOKEN));

    @Mock private AccountStore accountStore;
    @Mock private TypedTokenStore tokenStore;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame frame;
    @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private EncodingFacade encoder;
    @Mock private BurnLogic burnLogic;
    @Mock private SideEffectsTracker sideEffects;
    @Mock private TransactionBody.Builder mockSynthBodyBuilder;
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
    @Mock private FeeObject mockFeeObject;
    @Mock private StateView stateView;
    @Mock private ContractAliases aliases;
    @Mock private UsagePricesProvider resourceCosts;
    @Mock private InfrastructureFactory infrastructureFactory;
    @Mock private AssetsLoader assetLoader;
    @Mock private HbarCentExchange exchange;
    @Mock private ExchangeRate exchangeRate;
    @Mock private AccessorFactory accessorFactory;

    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE)
                    / HTSTestsUtil.DEFAULT_GAS_PRICE
                    * 6
                    / 5;
    private static final Bytes FUNGIBLE_BURN_INPUT =
            Bytes.fromHexString(
                    "0xacb9cff90000000000000000000000000000000000000000000000000000000000000498000000000000000000000000000000000000000000000000000000000000002100000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes FUNGIBLE_BURN_INPUT_V2 =
            Bytes.fromHexString(
                    "0xd6910d060000000000000000000000000000000000000000000000000000000000000498000000000000000000000000000000000000000000000000000000000000002100000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes NON_FUNGIBLE_BURN_INPUT =
            Bytes.fromHexString(
                    "0xacb9cff9000000000000000000000000000000000000000000000000000000000000049e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000007b00000000000000000000000000000000000000000000000000000000000000ea");
    private static final Bytes NON_FUNGIBLE_BURN_INPUT_V2 =
            Bytes.fromHexString(
                    "0xd6910d06000000000000000000000000000000000000000000000000000000000000049e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000007b00000000000000000000000000000000000000000000000000000000000000ea");

    private HTSPrecompiledContract subject;
    private MockedStatic<BurnPrecompile> burnPrecompile;

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
        canonicalPrices.put(
                HederaFunctionality.TokenBurn,
                Map.of(SubType.TOKEN_FUNGIBLE_COMMON, BigDecimal.valueOf(0)));
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
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
                        syntheticTxnFactory,
                        creator,
                        () -> feeCalculator,
                        stateView,
                        precompilePricingUtils,
                        infrastructureFactory);

        burnPrecompile = Mockito.mockStatic(BurnPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        if (!burnPrecompile.isClosed()) {
            burnPrecompile.close();
        }
    }

    @Test
    void nftBurnFailurePathWorks() {
        givenNonfungibleFrameContext();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(
                        sigsVerifier.hasActiveSupplyKey(
                                true,
                                HTSTestsUtil.nonFungibleTokenAddr,
                                HTSTestsUtil.recipientAddr,
                                wrappedLedgers))
                .willThrow(new InvalidTransactionException(INVALID_SIGNATURE));
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(creator.createUnsuccessfulSyntheticRecord(INVALID_SIGNATURE))
                .willReturn(mockRecordBuilder);
        given(encoder.encodeBurnFailure(INVALID_SIGNATURE))
                .willReturn(HTSTestsUtil.invalidSigResult);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        Assertions.assertEquals(HTSTestsUtil.invalidSigResult, result);
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void nftBurnFailurePathWorksWithNullLedgers() {
        givenNonfungibleFrameContext();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(
                        sigsVerifier.hasActiveSupplyKey(
                                true,
                                HTSTestsUtil.nonFungibleTokenAddr,
                                HTSTestsUtil.recipientAddr,
                                null))
                .willThrow(new NullPointerException());
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(creator.createUnsuccessfulSyntheticRecord(FAIL_INVALID))
                .willReturn(mockRecordBuilder);
        given(encoder.encodeBurnFailure(FAIL_INVALID)).willReturn(HTSTestsUtil.failInvalidResult);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        Assertions.assertEquals(HTSTestsUtil.failInvalidResult, result);
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void nftBurnHappyPathWorks() {
        givenNonfungibleFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(
                        sigsVerifier.hasActiveSupplyKey(
                                true,
                                HTSTestsUtil.nonFungibleTokenAddr,
                                HTSTestsUtil.recipientAddr,
                                wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newBurnLogic(accountStore, tokenStore)).willReturn(burnLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        final var receiptBuilder = TxnReceipt.newBuilder().setNewTotalSupply(123L);
        given(mockRecordBuilder.getReceiptBuilder()).willReturn(receiptBuilder);
        given(encoder.encodeBurnSuccess(123L)).willReturn(HTSTestsUtil.successResult);
        given(burnLogic.validateSyntax(any())).willReturn(OK);

        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        Assertions.assertEquals(HTSTestsUtil.successResult, result);
        verify(burnLogic).burn(HTSTestsUtil.nonFungibleId, 0, HTSTestsUtil.targetSerialNos);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void nftBurnWorksForInvalidSyntax() {
        givenNonfungibleFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(
                        sigsVerifier.hasActiveSupplyKey(
                                true,
                                HTSTestsUtil.nonFungibleTokenAddr,
                                HTSTestsUtil.recipientAddr,
                                wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newBurnLogic(accountStore, tokenStore)).willReturn(burnLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(burnLogic.validateSyntax(any())).willReturn(INVALID_TOKEN_ID);
        given(creator.createUnsuccessfulSyntheticRecord(INVALID_TOKEN_ID))
                .willReturn(mockRecordBuilder);

        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, а -> а);
        subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);
        subject.computeInternal(frame);

        verify(wrappedLedgers, never()).commit();
    }

    @Test
    void fungibleBurnHappyPathWorks() {
        givenFungibleFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(
                        sigsVerifier.hasActiveSupplyKey(
                                true,
                                HTSTestsUtil.fungibleTokenAddr,
                                HTSTestsUtil.recipientAddr,
                                wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newBurnLogic(accountStore, tokenStore)).willReturn(burnLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(HTSTestsUtil.expirableTxnRecordBuilder);
        given(encoder.encodeBurnSuccess(49)).willReturn(HTSTestsUtil.burnSuccessResultWith49Supply);
        given(burnLogic.validateSyntax(any())).willReturn(OK);

        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        Assertions.assertEquals(HTSTestsUtil.burnSuccessResultWith49Supply, result);
        verify(burnLogic).burn(HTSTestsUtil.fungibleId, HTSTestsUtil.AMOUNT, List.of());
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(
                        recordsHistorian,
                        HTSTestsUtil.expirableTxnRecordBuilder,
                        mockSynthBodyBuilder);
    }

    @Test
    void fungibleBurnFailureAmountOversize() {
        // given:
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getSenderAddress()).willReturn(HTSTestsUtil.contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        doCallRealMethod().when(frame).setExceptionalHaltReason(any());
        burnPrecompile
                .when(() -> decodeBurn(pretendArguments))
                .thenReturn(HTSTestsUtil.fungibleBurnAmountOversize);
        // when:
        final var result = subject.computePrecompile(pretendArguments, frame);
        // then:
        assertNull(result.getOutput());
        verify(frame)
                .setExceptionalHaltReason(
                        Optional.of(HederaExceptionalHaltReason.ERROR_DECODING_PRECOMPILE_INPUT));
        verify(wrappedLedgers, never()).commit();
        verify(worldUpdater, never())
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void fungibleBurnForMaxAmountWorks() {
        givenFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        burnPrecompile
                .when(() -> decodeBurn(pretendArguments))
                .thenReturn(HTSTestsUtil.fungibleBurnMaxAmount);
        given(syntheticTxnFactory.createBurn(HTSTestsUtil.fungibleBurnMaxAmount))
                .willReturn(mockSynthBodyBuilder);
        given(
                        sigsVerifier.hasActiveSupplyKey(
                                true,
                                HTSTestsUtil.fungibleTokenAddr,
                                HTSTestsUtil.recipientAddr,
                                wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newBurnLogic(accountStore, tokenStore)).willReturn(burnLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(HTSTestsUtil.expirableTxnRecordBuilder);
        given(encoder.encodeBurnSuccess(anyLong()))
                .willReturn(HTSTestsUtil.burnSuccessResultWithLongMaxValueSupply);
        given(burnLogic.validateSyntax(any())).willReturn(OK);

        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        Assertions.assertEquals(HTSTestsUtil.burnSuccessResultWithLongMaxValueSupply, result);
        verify(burnLogic).burn(HTSTestsUtil.fungibleId, Long.MAX_VALUE, List.of());
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(
                        recordsHistorian,
                        HTSTestsUtil.expirableTxnRecordBuilder,
                        mockSynthBodyBuilder);
    }

    @Test
    void gasRequirementReturnsCorrectValueForBurnToken() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_BURN_TOKEN));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        burnPrecompile
                .when(() -> decodeBurn(pretendArguments))
                .thenReturn(HTSTestsUtil.fungibleBurn);
        given(syntheticTxnFactory.createBurn(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setTokenBurn(TokenBurnTransactionBody.newBuilder()));
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(HTSTestsUtil.DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final long result =
                subject.getPrecompile().getGasRequirement(HTSTestsUtil.TEST_CONSENSUS_TIME);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void decodeFungibleBurnInput() {
        burnPrecompile.close();
        final var decodedInput = decodeBurn(FUNGIBLE_BURN_INPUT);

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(33, decodedInput.amount());
        assertEquals(0, decodedInput.serialNos().size());
        assertEquals(FUNGIBLE_COMMON, decodedInput.type());
    }

    @Test
    void decodeFungibleBurnInputV2() {
        burnPrecompile.close();
        final var decodedInput = decodeBurnV2(FUNGIBLE_BURN_INPUT_V2);

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(33, decodedInput.amount());
        assertEquals(0, decodedInput.serialNos().size());
        assertEquals(FUNGIBLE_COMMON, decodedInput.type());
    }

    @Test
    void decodeNonFungibleBurnInput() {
        burnPrecompile.close();
        final var decodedInput = decodeBurn(NON_FUNGIBLE_BURN_INPUT);

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(-1, decodedInput.amount());
        assertEquals(2, decodedInput.serialNos().size());
        assertEquals(123, decodedInput.serialNos().get(0));
        assertEquals(234, decodedInput.serialNos().get(1));
        assertEquals(NON_FUNGIBLE_UNIQUE, decodedInput.type());
    }

    @Test
    void decodeNonFungibleBurnInputV2() {
        burnPrecompile.close();
        final var decodedInput = decodeBurnV2(NON_FUNGIBLE_BURN_INPUT_V2);

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(-1, decodedInput.amount());
        assertEquals(2, decodedInput.serialNos().size());
        assertEquals(123, decodedInput.serialNos().get(0));
        assertEquals(234, decodedInput.serialNos().get(1));
        assertEquals(NON_FUNGIBLE_UNIQUE, decodedInput.type());
    }

    private void givenNonfungibleFrameContext() {
        givenFrameContext();
        burnPrecompile
                .when(() -> decodeBurn(pretendArguments))
                .thenReturn(HTSTestsUtil.nonFungibleBurn);
        given(syntheticTxnFactory.createBurn(HTSTestsUtil.nonFungibleBurn))
                .willReturn(mockSynthBodyBuilder);
    }

    private void givenFungibleFrameContext() {
        givenFrameContext();
        burnPrecompile
                .when(() -> decodeBurn(pretendArguments))
                .thenReturn(HTSTestsUtil.fungibleBurn);
        given(syntheticTxnFactory.createBurn(HTSTestsUtil.fungibleBurn))
                .willReturn(mockSynthBodyBuilder);
    }

    private void givenFrameContext() {
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getSenderAddress()).willReturn(HTSTestsUtil.contractAddress);
        given(frame.getContractAddress()).willReturn(HTSTestsUtil.contractAddr);
        given(frame.getRecipientAddress()).willReturn(HTSTestsUtil.recipientAddr);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
    }

    private void givenLedgers() {
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(wrappedLedgers.tokenRels()).willReturn(tokenRels);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(wrappedLedgers.tokens()).willReturn(tokens);
    }

    private void givenPricingUtilsContext() {
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);
    }

    private void givenMinFrameContext() {
        given(frame.getSenderAddress()).willReturn(HTSTestsUtil.contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
    }
}
