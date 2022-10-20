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
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CRYPTO_TRANSFER;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_NFT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_NFTS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_TOKENS;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_EMPTY_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_FUNGIBLE_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_HBAR_FUNGIBLE_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_HBAR_NFT_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_HBAR_ONLY_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_NFTS_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_NFT_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_RECEIVER_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_SENDER_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.CRYPTO_TRANSFER_TOKEN_WRAPPER;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.DEFAULT_GAS_PRICE;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddr;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.feeCollector;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.hbarAndNftsTransferChanges;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.hbarAndTokenChanges;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.hbarOnlyChanges;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nftTransferChanges;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nftTransferList;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nftsTransferChanges;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nftsTransferList;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.parentContractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.receiver;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.sender;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenTransferChanges;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokensTransferChanges;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokensTransferChangesSenderOnly;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokensTransferList;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokensTransferListReceiverOnly;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokensTransferListSenderOnly;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeCryptoTransfer;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeTransferNFT;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeTransferNFTs;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeTransferToken;
import static com.hedera.services.store.contracts.precompile.impl.TransferPrecompile.decodeTransferTokens;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfers;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMeta;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.TransferLogic;
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
import com.hedera.services.state.submerkle.EvmFnResult;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.impl.TransferPrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeObject;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferPrecompilesTest {
    @Mock private HederaTokenStore hederaTokenStore;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame frame;
    @Mock private MessageFrame parentFrame;
    @Mock private Deque<MessageFrame> frameDeque;
    @Mock private Iterator<MessageFrame> dequeIterator;
    @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private EncodingFacade encoder;
    @Mock private TransferLogic transferLogic;
    @Mock private SideEffectsTracker sideEffects;
    @Mock private TransactionBody.Builder mockSynthBodyBuilder;
    @Mock private CryptoTransferTransactionBody cryptoTransferTransactionBody;
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
    @Mock private ImpliedTransfers impliedTransfers;
    @Mock private ImpliedTransfersMeta impliedTransfersMeta;
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
    private static final Bytes POSITIVE_FUNGIBLE_AMOUNT_AND_NFT_TRANSFER_CRYPTO_TRANSFER_INPUT =
            Bytes.fromHexString(
                    "0x189a554c00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000004a4000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000004a1000000000000000000000000000000000000000000000000000000000000002b000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000004a100000000000000000000000000000000000000000000000000000000000004a10000000000000000000000000000000000000000000000000000000000000048");
    private static final Bytes NEGATIVE_FUNGIBLE_AMOUNT_CRYPTO_TRANSFER_INPUT =
            Bytes.fromHexString(
                    "0x189a554c00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000004c0000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000004bdffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffce0000000000000000000000000000000000000000000000000000000000000000");
    private static final Bytes TRANSFER_TOKEN_INPUT =
            Bytes.fromHexString(
                    "0xeca3691700000000000000000000000000000000000000000000000000000000000004380000000000000000000000000000000000000000000000000000000000000435000000000000000000000000000000000000000000000000000000000000043a0000000000000000000000000000000000000000000000000000000000000014");
    private static final Bytes POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT =
            Bytes.fromHexString(
                    "0x82bba4930000000000000000000000000000000000000000000000000000000000000444000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000044100000000000000000000000000000000000000000000000000000000000004410000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000014");
    private static final Bytes POSITIVE_NEGATIVE_AMOUNT_TRANSFER_TOKENS_INPUT =
            Bytes.fromHexString(
                    "0x82bba49300000000000000000000000000000000000000000000000000000000000004d8000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c0000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000004d500000000000000000000000000000000000000000000000000000000000004d500000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000014ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffec");
    private static final Bytes TRANSFER_NFT_INPUT =
            Bytes.fromHexString(
                    "0x5cfc901100000000000000000000000000000000000000000000000000000000000004680000000000000000000000000000000000000000000000000000000000000465000000000000000000000000000000000000000000000000000000000000046a0000000000000000000000000000000000000000000000000000000000000065");
    private static final Bytes TRANSFER_NFTS_INPUT =
            Bytes.fromHexString(
                    "0x2c4ba191000000000000000000000000000000000000000000000000000000000000047a000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000001400000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000047700000000000000000000000000000000000000000000000000000000000004770000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000047c000000000000000000000000000000000000000000000000000000000000047c0000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000007b00000000000000000000000000000000000000000000000000000000000000ea");

    private HTSPrecompiledContract subject;
    private MockedStatic<TransferPrecompile> transferPrecompile;

    @BeforeEach
    void setUp() {
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

        transferPrecompile = Mockito.mockStatic(TransferPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        transferPrecompile.close();
    }

    @Test
    void transferFailsFastGivenWrongSyntheticValidity() {
        givenPricingUtilsContext();
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKENS));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getRemainingGas()).willReturn(300L);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);

        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(tokensTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        transferPrecompile
                .when(() -> decodeTransferTokens(eq(pretendArguments), any()))
                .thenReturn(CRYPTO_TRANSFER_FUNGIBLE_WRAPPER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN);

        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(creator.createUnsuccessfulSyntheticRecord(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN))
                .willReturn(mockRecordBuilder);
        given(dynamicProperties.shouldExportPrecompileResults()).willReturn(true);
        given(frame.getRemainingGas()).willReturn(100L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(frame.getInputData()).willReturn(pretendArguments);

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(
                UInt256.valueOf(ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN_VALUE), result);
        ArgumentCaptor<EvmFnResult> captor = ArgumentCaptor.forClass(EvmFnResult.class);
        verify(mockRecordBuilder).setContractCallResult(captor.capture());
        assertEquals(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN.name(), captor.getValue().getError());
        assertEquals(100L, captor.getValue().getGas());
        assertEquals(0L, captor.getValue().getAmount());
        assertEquals(pretendArguments.toArrayUnsafe(), captor.getValue().getFunctionParameters());
    }

    @Test
    void transferTokenHappyPathWorks() throws InvalidProtocolBufferException {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKENS));
        givenMinimalFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(tokensTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(sigsVerifier.hasActiveKey(Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeTransferTokens(eq(pretendArguments), any()))
                .thenReturn(CRYPTO_TRANSFER_FUNGIBLE_WRAPPER);

        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokensTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(tokensTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void abortsIfImpliedCustomFeesCannotBeAssessed() throws InvalidProtocolBufferException {
        givenPricingUtilsContext();
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKENS));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getRemainingGas()).willReturn(300L);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);

        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(tokensTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        transferPrecompile
                .when(() -> decodeTransferTokens(eq(pretendArguments), any()))
                .thenReturn(CRYPTO_TRANSFER_FUNGIBLE_WRAPPER);

        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code())
                .willReturn(CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createUnsuccessfulSyntheticRecord(
                                CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS))
                .willReturn(mockRecordBuilder);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);
        final var statusResult =
                UInt256.valueOf(CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS.getNumber());
        assertEquals(statusResult, result);
    }

    @Test
    void transferTokenWithSenderOnlyHappyPathWorks() throws InvalidProtocolBufferException {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKENS));

        givenMinimalFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(tokensTransferListSenderOnly)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeTransferTokens(eq(pretendArguments), any()))
                .thenReturn(CRYPTO_TRANSFER_SENDER_WRAPPER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);

        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokensTransferChangesSenderOnly);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(tokensTransferChangesSenderOnly);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void transferTokenWithReceiverOnlyHappyPathWorks() throws InvalidProtocolBufferException {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKENS));

        givenMinimalFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(tokensTransferListReceiverOnly)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeTransferTokens(eq(pretendArguments), any()))
                .thenReturn(CRYPTO_TRANSFER_RECEIVER_WRAPPER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);

        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokensTransferChangesSenderOnly);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(tokensTransferChangesSenderOnly);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void transferNftsHappyPathWorks() throws InvalidProtocolBufferException {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFTS));

        givenMinimalFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(nftsTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(sigsVerifier.hasActiveKey(Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeTransferNFTs(eq(pretendArguments), any()))
                .thenReturn(CRYPTO_TRANSFER_NFTS_WRAPPER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);

        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);

        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(nftsTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(nftsTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void transferNftHappyPathWorks() throws InvalidProtocolBufferException {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFT));

        final var recipientAddr = Address.ALTBN128_ADD;
        final var senderId = Id.fromGrpcAccount(sender);
        final var receiverId = Id.fromGrpcAccount(receiver);
        givenMinimalFrameContext();
        given(frame.getRecipientAddress()).willReturn(recipientAddr);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(nftTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(sigsVerifier.hasActiveKey(Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeTransferNFT(eq(pretendArguments), any()))
                .thenReturn(CRYPTO_TRANSFER_NFT_WRAPPER);

        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);
        given(worldUpdater.aliases()).willReturn(aliases);

        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(nftTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(nftTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
        verify(sigsVerifier)
                .hasActiveKey(true, senderId.asEvmAddress(), recipientAddr, wrappedLedgers);
        verify(sigsVerifier)
                .hasActiveKeyOrNoReceiverSigReq(
                        true, receiverId.asEvmAddress(), recipientAddr, wrappedLedgers);
        verify(sigsVerifier)
                .hasActiveKey(true, receiverId.asEvmAddress(), recipientAddr, wrappedLedgers);
        verify(sigsVerifier, never())
                .hasActiveKeyOrNoReceiverSigReq(
                        true,
                        EntityIdUtils.asTypedEvmAddress(feeCollector),
                        recipientAddr,
                        wrappedLedgers);
    }

    @Test
    void cryptoTransferHappyPathWorks() throws InvalidProtocolBufferException {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER));

        givenMinimalFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(nftTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(sigsVerifier.hasActiveKey(Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeCryptoTransfer(eq(pretendArguments), any()))
                .thenReturn(CRYPTO_TRANSFER_NFT_WRAPPER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);
        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(nftTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(nftTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void hbarOnlyTransferHappyPathWorks() throws InvalidProtocolBufferException {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER));

        givenMinimalFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(syntheticTxnFactory.createCryptoTransfer(Collections.emptyList()))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(sigsVerifier.hasActiveKey(Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeCryptoTransfer(eq(pretendArguments), any()))
                .thenReturn(CRYPTO_TRANSFER_HBAR_ONLY_WRAPPER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);
        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(hbarOnlyChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(hbarOnlyChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void hbarFungibleTransferHappyPathWorks() throws InvalidProtocolBufferException {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER));

        givenMinimalFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(
                        syntheticTxnFactory.createCryptoTransfer(
                                Collections.singletonList(tokensTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(sigsVerifier.hasActiveKey(Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeCryptoTransfer(eq(pretendArguments), any()))
                .thenReturn(CRYPTO_TRANSFER_HBAR_FUNGIBLE_WRAPPER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);
        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(hbarAndTokenChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(hbarAndTokenChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void hbarNFTTransferHappyPathWorks() throws InvalidProtocolBufferException {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER));

        givenMinimalFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(syntheticTxnFactory.createCryptoTransfer(Collections.singletonList(nftsTransferList)))
                .willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(sigsVerifier.hasActiveKey(Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        transferPrecompile
                .when(() -> decodeCryptoTransfer(eq(pretendArguments), any()))
                .thenReturn(CRYPTO_TRANSFER_HBAR_NFT_WRAPPER);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);
        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(hbarAndNftsTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(hbarAndNftsTransferChanges);
        verify(wrappedLedgers).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void transferFailsAndCatchesProperly() throws InvalidProtocolBufferException {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKEN));

        givenMinimalFrameContext();
        givenLedgers();
        givenPricingUtilsContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(
                        sigsVerifier.hasActiveKeyOrNoReceiverSigReq(
                                Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        given(sigsVerifier.hasActiveKey(Mockito.anyBoolean(), any(), any(), any()))
                .willReturn(true);
        given(impliedTransfersMarshal.validityWithCurrentProps(cryptoTransferTransactionBody))
                .willReturn(OK);
        given(infrastructureFactory.newHederaTokenStore(sideEffects, tokens, nfts, tokenRels))
                .willReturn(hederaTokenStore);
        given(
                        infrastructureFactory.newTransferLogic(
                                hederaTokenStore, sideEffects, nfts, accounts, tokenRels))
                .willReturn(transferLogic);
        transferPrecompile
                .when(() -> decodeTransferToken(eq(pretendArguments), any()))
                .thenReturn(CRYPTO_TRANSFER_TOKEN_WRAPPER);
        given(
                        impliedTransfersMarshal.assessCustomFeesAndValidate(
                                anyInt(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(impliedTransfers);
        given(impliedTransfers.getAllBalanceChanges()).willReturn(tokenTransferChanges);
        given(impliedTransfers.getMeta()).willReturn(impliedTransfersMeta);
        given(impliedTransfersMeta.code()).willReturn(OK);
        given(syntheticTxnFactory.createCryptoTransfer(any())).willReturn(mockSynthBodyBuilder);
        given(mockSynthBodyBuilder.getCryptoTransfer()).willReturn(cryptoTransferTransactionBody);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockSynthBodyBuilder.build())
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(cryptoTransferTransactionBody)
                                .build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(creator.createUnsuccessfulSyntheticRecord(ResponseCodeEnum.FAIL_INVALID))
                .willReturn(mockRecordBuilder);
        given(frame.getSenderAddress()).willReturn(contractAddress);

        doThrow(new InvalidTransactionException(ResponseCodeEnum.FAIL_INVALID))
                .when(transferLogic)
                .doZeroSum(tokenTransferChanges);
        given(aliases.resolveForEvm(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.aliases()).willReturn(aliases);
        when(accessorFactory.uncheckedSpecializedAccessor(any())).thenCallRealMethod();
        when(accessorFactory.constructSpecializedAccessor(any())).thenCallRealMethod();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertNotEquals(successResult, result);
        // and:
        verify(transferLogic).doZeroSum(tokenTransferChanges);
        verify(wrappedLedgers, never()).commit();
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void transferWithWrongInput() {
        Bytes pretendArguments = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKEN));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
        transferPrecompile
                .when(() -> decodeTransferToken(eq(pretendArguments), any()))
                .thenThrow(new IndexOutOfBoundsException());

        subject.prepareFields(frame);
        var result = subject.computePrecompile(pretendArguments, frame);

        assertDoesNotThrow(() -> subject.prepareComputation(pretendArguments, a -> a));
        assertNull(result.getOutput());
    }

    @Test
    void gasRequirementReturnsCorrectValueForTransferNfts() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFTS));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        transferPrecompile
                .when(() -> decodeTransferNFTs(eq(input), any()))
                .thenReturn(CRYPTO_TRANSFER_EMPTY_WRAPPER);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        long result = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForTransferNft() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFT));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        transferPrecompile
                .when(() -> decodeTransferNFT(eq(input), any()))
                .thenReturn(CRYPTO_TRANSFER_EMPTY_WRAPPER);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        long result = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForSingleCryptoTransfer() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        transferPrecompile
                .when(() -> decodeCryptoTransfer(eq(input), any()))
                .thenReturn(CRYPTO_TRANSFER_EMPTY_WRAPPER);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        long result = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForMultipleCryptoTransfers() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(
                                        CryptoTransferTransactionBody.newBuilder()
                                                .addTokenTransfers(
                                                        TokenTransferList.newBuilder().build())
                                                .addTokenTransfers(
                                                        TokenTransferList.newBuilder().build())
                                                .addTokenTransfers(
                                                        TokenTransferList.newBuilder().build())));
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        transferPrecompile
                .when(() -> decodeCryptoTransfer(eq(input), any()))
                .thenReturn(CRYPTO_TRANSFER_EMPTY_WRAPPER);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        long result = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForTransferMultipleTokens() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKENS));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        transferPrecompile
                .when(() -> decodeTransferTokens(eq(input), any()))
                .thenReturn(CRYPTO_TRANSFER_EMPTY_WRAPPER);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        long result = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void gasRequirementReturnsCorrectValueForTransferSingleToken() {
        // given
        givenMinFrameContext();
        givenPricingUtilsContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKEN));
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(feeCalculator.computeFee(any(), any(), any(), any()))
                .willReturn(new FeeObject(TEST_NODE_FEE, TEST_NETWORK_FEE, TEST_SERVICE_FEE));
        given(feeCalculator.estimatedGasPriceInTinybars(any(), any()))
                .willReturn(DEFAULT_GAS_PRICE);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        transferPrecompile
                .when(() -> decodeTransferToken(eq(input), any()))
                .thenReturn(CRYPTO_TRANSFER_EMPTY_WRAPPER);

        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        long result = subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then
        assertEquals(EXPECTED_GAS_PRICE, result);
    }

    @Test
    void decodeCryptoTransferPositiveFungibleAmountAndNftTransfer() {
        transferPrecompile
                .when(
                        () ->
                                decodeCryptoTransfer(
                                        POSITIVE_FUNGIBLE_AMOUNT_AND_NFT_TRANSFER_CRYPTO_TRANSFER_INPUT,
                                        identity()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeCryptoTransfer(
                        POSITIVE_FUNGIBLE_AMOUNT_AND_NFT_TRANSFER_CRYPTO_TRANSFER_INPUT,
                        identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();
        final var nftExchanges = decodedInput.tokenTransferWrappers().get(0).nftExchanges();

        assertNotNull(fungibleTransfers);
        assertNotNull(nftExchanges);
        assertEquals(1, fungibleTransfers.size());
        assertEquals(1, nftExchanges.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        assertTrue(fungibleTransfers.get(0).receiver().getAccountNum() > 0);
        assertEquals(43, fungibleTransfers.get(0).receiverAdjustment().getAmount());
        assertTrue(nftExchanges.get(0).getTokenType().getTokenNum() > 0);
        assertTrue(nftExchanges.get(0).asGrpc().getReceiverAccountID().getAccountNum() > 0);
        assertTrue(nftExchanges.get(0).asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertEquals(72, nftExchanges.get(0).asGrpc().getSerialNumber());
    }

    @Test
    void decodeCryptoTransferNegativeFungibleAmount() {
        transferPrecompile
                .when(
                        () ->
                                decodeCryptoTransfer(
                                        NEGATIVE_FUNGIBLE_AMOUNT_CRYPTO_TRANSFER_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeCryptoTransfer(NEGATIVE_FUNGIBLE_AMOUNT_CRYPTO_TRANSFER_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        assertNotNull(fungibleTransfers);
        assertEquals(1, fungibleTransfers.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        assertTrue(fungibleTransfers.get(0).sender().getAccountNum() > 0);
        assertEquals(50, fungibleTransfers.get(0).amount());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferTokenInput() {
        transferPrecompile
                .when(() -> decodeTransferToken(TRANSFER_TOKEN_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput = decodeTransferToken(TRANSFER_TOKEN_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfer =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers().get(0);

        assertTrue(fungibleTransfer.sender().getAccountNum() > 0);
        assertTrue(fungibleTransfer.receiver().getAccountNum() > 0);
        assertTrue(fungibleTransfer.getDenomination().getTokenNum() > 0);
        assertEquals(20, fungibleTransfer.amount());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferTokensPositiveAmounts() {
        transferPrecompile
                .when(
                        () ->
                                decodeTransferTokens(
                                        POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeTransferTokens(POSITIVE_AMOUNTS_TRANSFER_TOKENS_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        assertEquals(2, fungibleTransfers.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        assertTrue(fungibleTransfers.get(1).getDenomination().getTokenNum() > 0);
        assertNull(fungibleTransfers.get(0).sender());
        assertNull(fungibleTransfers.get(1).sender());
        assertTrue(fungibleTransfers.get(0).receiver().getAccountNum() > 0);
        assertTrue(fungibleTransfers.get(1).receiver().getAccountNum() > 0);
        assertEquals(10, fungibleTransfers.get(0).amount());
        assertEquals(20, fungibleTransfers.get(1).amount());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferTokensPositiveNegativeAmount() {
        transferPrecompile
                .when(
                        () ->
                                decodeTransferTokens(
                                        POSITIVE_NEGATIVE_AMOUNT_TRANSFER_TOKENS_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput =
                decodeTransferTokens(POSITIVE_NEGATIVE_AMOUNT_TRANSFER_TOKENS_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var fungibleTransfers =
                decodedInput.tokenTransferWrappers().get(0).fungibleTransfers();

        assertEquals(2, fungibleTransfers.size());
        assertTrue(fungibleTransfers.get(0).getDenomination().getTokenNum() > 0);
        assertTrue(fungibleTransfers.get(1).getDenomination().getTokenNum() > 0);
        assertNull(fungibleTransfers.get(0).sender());
        assertNull(fungibleTransfers.get(1).receiver());
        assertTrue(fungibleTransfers.get(0).receiver().getAccountNum() > 0);
        assertTrue(fungibleTransfers.get(1).sender().getAccountNum() > 0);
        assertEquals(20, fungibleTransfers.get(0).amount());
        assertEquals(20, fungibleTransfers.get(1).amount());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferNFTInput() {
        transferPrecompile
                .when(() -> decodeTransferNFT(TRANSFER_NFT_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput = decodeTransferNFT(TRANSFER_NFT_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var nonFungibleTransfer =
                decodedInput.tokenTransferWrappers().get(0).nftExchanges().get(0);

        assertTrue(nonFungibleTransfer.asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfer.asGrpc().getReceiverAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfer.getTokenType().getTokenNum() > 0);
        assertEquals(101, nonFungibleTransfer.asGrpc().getSerialNumber());
        assertEquals(0, hbarTransfers.size());
    }

    @Test
    void decodeTransferNFTsInput() {
        transferPrecompile
                .when(() -> decodeTransferNFTs(TRANSFER_NFTS_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput = decodeTransferNFTs(TRANSFER_NFTS_INPUT, identity());
        final var hbarTransfers = decodedInput.transferWrapper().hbarTransfers();
        final var nonFungibleTransfers = decodedInput.tokenTransferWrappers().get(0).nftExchanges();

        assertEquals(2, nonFungibleTransfers.size());
        assertTrue(nonFungibleTransfers.get(0).asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfers.get(1).asGrpc().getSenderAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfers.get(0).asGrpc().getReceiverAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfers.get(1).asGrpc().getReceiverAccountID().getAccountNum() > 0);
        assertTrue(nonFungibleTransfers.get(0).getTokenType().getTokenNum() > 0);
        assertTrue(nonFungibleTransfers.get(1).getTokenType().getTokenNum() > 0);
        assertEquals(123, nonFungibleTransfers.get(0).asGrpc().getSerialNumber());
        assertEquals(234, nonFungibleTransfers.get(1).asGrpc().getSerialNumber());
        assertEquals(0, hbarTransfers.size());
    }

    private void givenFrameContext() {
        given(parentFrame.getContractAddress()).willReturn(parentContractAddress);
        given(parentFrame.getRecipientAddress()).willReturn(parentContractAddress);
        given(parentFrame.getSenderAddress()).willReturn(parentContractAddress);
        given(frame.getContractAddress()).willReturn(contractAddr);
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getMessageFrameStack()).willReturn(frameDeque);
        given(frame.getMessageFrameStack().iterator()).willReturn(dequeIterator);
        given(frame.getMessageFrameStack().iterator().hasNext()).willReturn(true);
        given(frame.getMessageFrameStack().iterator().next()).willReturn(parentFrame);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(sideEffects)).willReturn(wrappedLedgers);
    }

    private void givenMinimalFrameContext() {
        given(frame.getContractAddress()).willReturn(contractAddr);
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
