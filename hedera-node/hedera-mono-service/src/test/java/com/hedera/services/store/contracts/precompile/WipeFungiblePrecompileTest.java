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
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.AMOUNT;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.DEFAULT_GAS_PRICE;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.accountId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.expirableTxnRecordBuilder;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleId;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleWipe;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleWipeAmountOversize;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleWipeMaxAmount;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.impl.WipeFungiblePrecompile.decodeWipe;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.evm.contracts.execution.HederaBlockValues;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.pricing.AssetsLoader;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.impl.WipeFungiblePrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.NftId;
import com.hedera.services.txns.token.WipeLogic;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeObject;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WipeFungiblePrecompileTest {

    private final Bytes pretendArguments =
            Bytes.of(Integers.toBytes(ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE));

    @Mock private AccountStore accountStore;
    @Mock private TypedTokenStore tokenStore;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame frame;
    @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private EncodingFacade encoder;
    @Mock private WipeLogic wipeLogic;
    @Mock private SideEffectsTracker sideEffects;
    @Mock private TransactionBody.Builder mockSynthBodyBuilder;
    @Mock private ExpirableTxnRecord.Builder mockRecordBuilder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private HederaStackedWorldStateUpdater worldUpdater;
    @Mock private WorldLedgers wrappedLedgers;
    @Mock private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nfts;
    @Mock private AccessorFactory accessorFactory;

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus>
            tokenRels;

    @Mock private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts;
    @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
    @Mock private ExpiringCreations creator;
    @Mock private ImpliedTransfersMarshal impliedTransfersMarshal;
    @Mock private FeeCalculator feeCalculator;
    @Mock private FeeObject mockFeeObject;
    @Mock private StateView stateView;
    @Mock private ContractAliases aliases;
    @Mock private UsagePricesProvider resourceCosts;
    @Mock private InfrastructureFactory infrastructureFactory;
    @Mock private AssetsLoader assetLoader;
    @Mock private HbarCentExchange exchange;
    @Mock private ExchangeRate exchangeRate;

    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / DEFAULT_GAS_PRICE * 6 / 5;
    private static final Bytes FUNGIBLE_WIPE_INPUT =
            Bytes.fromHexString(
                    "0x9790686d00000000000000000000000000000000000000000000000000000000000006aa00000000000000000000000000000000000000000000000000000000000006a8000000000000000000000000000000000000000000000000000000000000000a");

    private HTSPrecompiledContract subject;
    private MockedStatic<WipeFungiblePrecompile> wipeFungiblePrecompile;

    @BeforeEach
    void setUp() throws IOException {
        Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
        canonicalPrices.put(
                HederaFunctionality.TokenAccountWipe,
                Map.of(SubType.TOKEN_FUNGIBLE_COMMON, BigDecimal.valueOf(0)));
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
        PrecompilePricingUtils precompilePricingUtils =
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
                        impliedTransfersMarshal,
                        () -> feeCalculator,
                        stateView,
                        precompilePricingUtils,
                        infrastructureFactory);

        wipeFungiblePrecompile = Mockito.mockStatic(WipeFungiblePrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        wipeFungiblePrecompile.close();
    }

    @Test
    void fungibleWipeHappyPathWorks() {
        givenFungibleFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(
                        sigsVerifier.hasActiveWipeKey(
                                true, fungibleTokenAddr, fungibleTokenAddr, wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newWipeLogic(accountStore, tokenStore)).willReturn(wipeLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(expirableTxnRecordBuilder);
        given(wipeLogic.validateSyntax(any())).willReturn(OK);

        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        assertEquals(successResult, result);
        verify(wipeLogic).wipe(fungibleId, accountId, AMOUNT, List.of());
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(
                        recordsHistorian, expirableTxnRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void fungibleWipeFailureAmountOversize() {
        // given:
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        doCallRealMethod().when(frame).setRevertReason(any());
        wipeFungiblePrecompile
                .when(() -> decodeWipe(eq(pretendArguments), any()))
                .thenReturn(fungibleWipeAmountOversize);
        // when:
        final var result = subject.computePrecompile(pretendArguments, frame);
        // then:
        assertNull(result.getOutput());
        verify(wrappedLedgers, never()).commit();
        verify(worldUpdater, never())
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void fungibleWipeMissedSpecializedAccessorCausePrecompileFailure() {
        // given:
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);

        wipeFungiblePrecompile
                .when(() -> decodeWipe(eq(pretendArguments), any()))
                .thenReturn(fungibleWipeMaxAmount);
        given(syntheticTxnFactory.createWipe(fungibleWipeMaxAmount))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(frame.getBlockValues())
                .willReturn(new HederaBlockValues(10L, 123L, Instant.ofEpochSecond(123L)));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(DEFAULT_GAS_PRICE);
        when(accessorFactory.uncheckedSpecializedAccessor(any()))
                .thenThrow(new IllegalArgumentException("error"));

        // then:
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.computePrecompile(pretendArguments, frame));
        verify(wrappedLedgers, never()).commit();
        verify(worldUpdater, never())
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void fungibleWipeForMaxAmountWorks() {
        givenFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        wipeFungiblePrecompile
                .when(() -> decodeWipe(eq(pretendArguments), any()))
                .thenReturn(fungibleWipeMaxAmount);
        given(syntheticTxnFactory.createWipe(fungibleWipeMaxAmount))
                .willReturn(mockSynthBodyBuilder);
        given(
                        sigsVerifier.hasActiveWipeKey(
                                true, fungibleTokenAddr, fungibleTokenAddr, wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newWipeLogic(accountStore, tokenStore)).willReturn(wipeLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(expirableTxnRecordBuilder);
        given(wipeLogic.validateSyntax(any())).willReturn(OK);

        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        assertEquals(successResult, result);
        verify(wipeLogic).wipe(fungibleId, accountId, Long.MAX_VALUE, List.of());
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(
                        recordsHistorian, expirableTxnRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void gasRequirementReturnsCorrectValueForWipeFungibleToken() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        wipeFungiblePrecompile
                .when(() -> decodeWipe(eq(pretendArguments), any()))
                .thenReturn(fungibleWipe);
        given(syntheticTxnFactory.createWipe(fungibleWipe))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()));
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        long result = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void decodeFungibleWipeInput() {
        wipeFungiblePrecompile
                .when(() -> decodeWipe(FUNGIBLE_WIPE_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput = decodeWipe(FUNGIBLE_WIPE_INPUT, identity());

        assertTrue(decodedInput.token().getTokenNum() > 0);
        assertTrue(decodedInput.account().getAccountNum() > 0);
        assertEquals(10, decodedInput.amount());
        assertEquals(0, decodedInput.serialNumbers().size());
        assertEquals(FUNGIBLE_COMMON, decodedInput.type());
    }

    private void givenFungibleFrameContext() {
        givenFrameContext();
        wipeFungiblePrecompile
                .when(() -> decodeWipe(eq(pretendArguments), any()))
                .thenReturn(fungibleWipe);
        given(syntheticTxnFactory.createWipe(fungibleWipe)).willReturn(mockSynthBodyBuilder);
    }

    private void givenFrameContext() {
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getContractAddress()).willReturn(contractAddr);
        given(frame.getRecipientAddress()).willReturn(fungibleTokenAddr);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
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
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
    }
}
