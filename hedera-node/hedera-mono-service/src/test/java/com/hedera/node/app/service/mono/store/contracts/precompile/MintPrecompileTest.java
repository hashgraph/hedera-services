/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_MINT_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.AMOUNT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.DEFAULT_GAS_PRICE;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.expirableTxnRecordBuilder;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.failInvalidResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungibleId;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungibleMint;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungibleMintAmountOversize;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungibleMintMaxAmount;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungibleSuccessResultWith10Supply;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungibleSuccessResultWithLongMaxValueSupply;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.invalidSigResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.newMetadata;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.nftMint;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.nonFungibleId;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.nonFungibleTokenAddr;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.pendingChildConsTime;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.recipientAddr;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.MintPrecompile.decodeMint;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.MintPrecompile.decodeMintV2;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
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
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
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
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.MintPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.txns.token.MintLogic;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
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
class MintPrecompileTest {
    @Mock private AccountStore accountStore;
    @Mock private TypedTokenStore tokenStore;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame frame;
    @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private EncodingFacade encoder;
    @Mock private EvmEncodingFacade evmEncoder;
    @Mock private MintLogic mintLogic;
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
    @Mock private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    private static final long TEST_SERVICE_FEE = 5_000_000;
    private static final long TEST_NETWORK_FEE = 400_000;
    private static final long TEST_NODE_FEE = 300_000;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private static final long EXPECTED_GAS_PRICE =
            (TEST_SERVICE_FEE + TEST_NETWORK_FEE + TEST_NODE_FEE) / DEFAULT_GAS_PRICE * 6 / 5;
    private static final Bytes ZERO_FUNGIBLE_MINT_INPUT =
            Bytes.fromHexString(
                    "0x278e0b88000000000000000000000000000000000000000000000000000000000000043e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000");

    private static final Bytes FUNGIBLE_MINT_INPUT =
            Bytes.fromHexString(
                    "0x278e0b88000000000000000000000000000000000000000000000000000000000000043e000000000000000000000000000000000000000000000000000000000000000f00000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes FUNGIBLE_MINT_INPUT_V2 =
            Bytes.fromHexString(
                    "0xe0f4059a000000000000000000000000000000000000000000000000000000000000043e000000000000000000000000000000000000000000000000000000000000000f00000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes ZERO_FUNGIBLE_MINT_INPUT_V2 =
            Bytes.fromHexString(
                    "0xe0f4059a000000000000000000000000000000000000000000000000000000000000043e000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000600000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes NON_FUNGIBLE_MINT_INPUT =
            Bytes.fromHexString(
                    "0x278e0b88000000000000000000000000000000000000000000000000000000000000042e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000124e4654206d65746164617461207465737431000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000124e4654206d657461646174612074657374320000000000000000000000000000");
    private static final Bytes NON_FUNGIBLE_MINT_INPUT_V2 =
            Bytes.fromHexString(
                    "0xe0f4059a000000000000000000000000000000000000000000000000000000000000042e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000124e4654206d65746164617461207465737431000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000124e4654206d657461646174612074657374320000000000000000000000000000");
    private static final Bytes BOTH_INPUTS_INPUT_V2 =
            Bytes.fromHexString(
                    "0xe0f4059a000000000000000000000000000000000000000000000000000000000000042e0000000000000000000000000000000000000000000000000000000000000000f00000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000124e4654206d65746164617461207465737431000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000124e4654206d657461646174612074657374320000000000000000000000000000");

    private HTSPrecompiledContract subject;
    private MockedStatic<MintPrecompile> mintPrecompile;

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
        canonicalPrices.put(
                HederaFunctionality.TokenMint,
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
                        evmEncoder,
                        syntheticTxnFactory,
                        creator,
                        () -> feeCalculator,
                        stateView,
                        precompilePricingUtils,
                        infrastructureFactory,
                    evmHTSPrecompiledContract);

        mintPrecompile = Mockito.mockStatic(MintPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        if (!mintPrecompile.isClosed()) {
            mintPrecompile.close();
        }
    }

    @Test
    void mintFailurePathWorks() {
        final Bytes pretendArguments = givenNonFungibleFrameContext();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(
                        sigsVerifier.hasActiveSupplyKey(
                                true, nonFungibleTokenAddr, recipientAddr, wrappedLedgers))
                .willThrow(new InvalidTransactionException(INVALID_SIGNATURE));
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(creator.createUnsuccessfulSyntheticRecord(INVALID_SIGNATURE))
                .willReturn(mockRecordBuilder);
        given(encoder.encodeMintFailure(INVALID_SIGNATURE)).willReturn(invalidSigResult);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(invalidSigResult, result);

        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void mintRandomFailurePathWorks() {
        final Bytes pretendArguments = givenNonFungibleFrameContext();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(sigsVerifier.hasActiveSupplyKey(Mockito.anyBoolean(), any(), any(), any()))
                .willThrow(new IllegalArgumentException("random error"));
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(creator.createUnsuccessfulSyntheticRecord(FAIL_INVALID))
                .willReturn(mockRecordBuilder);
        given(encoder.encodeMintFailure(FAIL_INVALID)).willReturn(failInvalidResult);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(failInvalidResult, result);
    }

    @Test
    void nftMintHappyPathWorks() {
        final Bytes pretendArguments = givenNonFungibleFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(
                        sigsVerifier.hasActiveSupplyKey(
                                true, nonFungibleTokenAddr, recipientAddr, wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newMintLogic(accountStore, tokenStore)).willReturn(mintLogic);
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
                .willReturn(mockRecordBuilder);
        final var mints =
                new long[] {
                    1L, 2L,
                };
        given(mockRecordBuilder.getReceiptBuilder())
                .willReturn(TxnReceipt.newBuilder().setSerialNumbers(mints));
        given(encoder.encodeMintSuccess(0L, mints)).willReturn(successResult);
        given(recordsHistorian.nextFollowingChildConsensusTime()).willReturn(pendingChildConsTime);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(mintLogic.validateSyntax(any())).willReturn(OK);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(mintLogic).mint(nonFungibleId, 3, 0, newMetadata, pendingChildConsTime);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void nftMintBadSyntaxWorks() {
        final Bytes pretendArguments = givenNonFungibleFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(
                        sigsVerifier.hasActiveSupplyKey(
                                true, nonFungibleTokenAddr, recipientAddr, wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newMintLogic(accountStore, tokenStore)).willReturn(mintLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(mintLogic.validateSyntax(any())).willReturn(INVALID_TOKEN_ID);
        given(creator.createUnsuccessfulSyntheticRecord(INVALID_TOKEN_ID))
                .willReturn(mockRecordBuilder);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, а -> а);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        subject.computeInternal(frame);

        // then:
        verify(wrappedLedgers, never()).commit();
    }

    @Test
    void fungibleMintHappyPathWorks() {
        final Bytes pretendArguments = givenFungibleFrameContext();
        givenLedgers();
        givenFungibleCollaborators();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(encoder.encodeMintSuccess(anyLong(), any()))
                .willReturn(fungibleSuccessResultWith10Supply);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(mintLogic.validateSyntax(any())).willReturn(OK);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);
        // then:
        assertEquals(fungibleSuccessResultWith10Supply, result);
        // and:
        verify(mintLogic).mint(fungibleId, 0, AMOUNT, Collections.emptyList(), Instant.EPOCH);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(
                        recordsHistorian, expirableTxnRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void mintFailsWithMissingParentUpdater() {
        final Bytes pretendArguments = givenFungibleFrameContext();
        givenLedgers();
        givenFungibleCollaborators();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(encoder.encodeMintSuccess(anyLong(), any()))
                .willReturn(fungibleSuccessResultWith10Supply);
        given(worldUpdater.parentUpdater()).willReturn(Optional.empty());
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(mintLogic.validateSyntax(any())).willReturn(OK);

        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        assertFailsWith(() -> subject.computeInternal(frame), FAIL_INVALID);
    }

    @Test
    void fungibleMintFailureAmountLimitOversize() {
        final Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_MINT_TOKEN));
        // given:
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        doCallRealMethod().when(frame).setExceptionalHaltReason(any());
        mintPrecompile
                .when(() -> decodeMint(pretendArguments))
                .thenReturn(fungibleMintAmountOversize);
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
    void fungibleMintForMaxAmountWorks() {
        // given:
        givenLedgers();
        givenPricingUtilsContext();
        final Bytes pretendArguments = givenFrameContext();
        givenFungibleCollaborators();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        mintPrecompile.when(() -> decodeMint(pretendArguments)).thenReturn(fungibleMintMaxAmount);
        given(syntheticTxnFactory.createMint(fungibleMintMaxAmount))
                .willReturn(mockSynthBodyBuilder);
        given(encoder.encodeMintSuccess(anyLong(), any()))
                .willReturn(fungibleSuccessResultWithLongMaxValueSupply);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(mintLogic.validateSyntax(any())).willReturn(OK);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);
        // then:
        assertEquals(fungibleSuccessResultWithLongMaxValueSupply, result);
        // and:
        verify(mintLogic)
                .mint(fungibleId, 0, Long.MAX_VALUE, Collections.emptyList(), Instant.EPOCH);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(
                        recordsHistorian, expirableTxnRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void gasRequirementReturnsCorrectValueForMintToken() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_MINT_TOKEN));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        mintPrecompile.when(() -> decodeMint(any())).thenReturn(fungibleMint);
        given(syntheticTxnFactory.createMint(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setTokenMint(TokenMintTransactionBody.newBuilder()));
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final long result = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void decodeFungibleMintInput() {
        mintPrecompile.close();
        final var decodedInput = decodeMint(FUNGIBLE_MINT_INPUT);

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(15, decodedInput.amount());
        assertEquals(FUNGIBLE_COMMON, decodedInput.type());
    }

    @Test
    void decodeFungibleMintInputV2() {
        mintPrecompile.close();
        final var decodedInput = decodeMintV2(FUNGIBLE_MINT_INPUT_V2);

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(15, decodedInput.amount());
        assertEquals(FUNGIBLE_COMMON, decodedInput.type());
    }

    @Test
    void decodeNonFungibleMintInput() {
        mintPrecompile.close();
        final var decodedInput = decodeMint(NON_FUNGIBLE_MINT_INPUT);
        final var metadata1 = ByteString.copyFrom("NFT metadata test1".getBytes());
        final var metadata2 = ByteString.copyFrom("NFT metadata test2".getBytes());
        final List<ByteString> metadata = Arrays.asList(metadata1, metadata2);

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(metadata, decodedInput.metadata());
        assertEquals(NON_FUNGIBLE_UNIQUE, decodedInput.type());
    }

    @Test
    void decodeFungibleMintZeroInputV2() {
        mintPrecompile.close();
        final var decodedInput = decodeMintV2(ZERO_FUNGIBLE_MINT_INPUT_V2);
        final List<ByteString> metadata = new ArrayList<>();

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(metadata, decodedInput.metadata());
        assertEquals(FUNGIBLE_COMMON, decodedInput.type());
    }

    @Test
    void decodeFungibleMintZeroInput() {
        mintPrecompile.close();
        final var decodedInput = decodeMint(ZERO_FUNGIBLE_MINT_INPUT);
        final var metadata = new ArrayList<>();

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(metadata, decodedInput.metadata());
        assertEquals(FUNGIBLE_COMMON, decodedInput.type());
    }

    @Test
    void decodeNonFungibleMintInputV2() {
        mintPrecompile.close();
        final var decodedInput = decodeMintV2(NON_FUNGIBLE_MINT_INPUT_V2);
        final var metadata1 = ByteString.copyFrom("NFT metadata test1".getBytes());
        final var metadata2 = ByteString.copyFrom("NFT metadata test2".getBytes());
        final List<ByteString> metadata = Arrays.asList(metadata1, metadata2);

        assertTrue(decodedInput.tokenType().getTokenNum() > 0);
        assertEquals(metadata, decodedInput.metadata());
        assertEquals(NON_FUNGIBLE_UNIQUE, decodedInput.type());
    }

    private Bytes givenNonFungibleFrameContext() {
        final Bytes pretendArguments = givenFrameContext();
        mintPrecompile.when(() -> decodeMint(pretendArguments)).thenReturn(nftMint);
        given(syntheticTxnFactory.createMint(nftMint)).willReturn(mockSynthBodyBuilder);
        return pretendArguments;
    }

    private Bytes givenFungibleFrameContext() {
        final Bytes pretendArguments = givenFrameContext();
        mintPrecompile.when(() -> decodeMint(pretendArguments)).thenReturn(fungibleMint);
        given(syntheticTxnFactory.createMint(fungibleMint)).willReturn(mockSynthBodyBuilder);
        return pretendArguments;
    }

    private Bytes givenFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getContractAddress()).willReturn(contractAddr);
        given(frame.getRecipientAddress()).willReturn(recipientAddr);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(300L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        return Bytes.of(Integers.toBytes(ABI_ID_MINT_TOKEN));
    }

    private void givenLedgers() {
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(wrappedLedgers.tokenRels()).willReturn(tokenRels);
        given(wrappedLedgers.nfts()).willReturn(nfts);
        given(wrappedLedgers.tokens()).willReturn(tokens);
    }

    private void givenFungibleCollaborators() {
        given(
                        sigsVerifier.hasActiveSupplyKey(
                                true, fungibleTokenAddr, recipientAddr, wrappedLedgers))
                .willReturn(true);
        given(infrastructureFactory.newAccountStore(accounts)).willReturn(accountStore);
        given(
                        infrastructureFactory.newTokenStore(
                                accountStore, sideEffects, tokens, nfts, tokenRels))
                .willReturn(tokenStore);
        given(infrastructureFactory.newMintLogic(accountStore, tokenStore)).willReturn(mintLogic);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(expirableTxnRecordBuilder);
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
