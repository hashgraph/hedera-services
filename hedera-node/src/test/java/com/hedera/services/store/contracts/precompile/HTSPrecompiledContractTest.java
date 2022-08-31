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

import static com.hedera.services.contracts.execution.HederaMessageCallProcessor.INVALID_TRANSFER;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ASSOCIATE_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ASSOCIATE_TOKENS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_BURN_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_CRYPTO_TRANSFER;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_DISSOCIATE_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_DISSOCIATE_TOKENS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ERC_NAME;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_CUSTOM_FEES;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_EXPIRY_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_MINT_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_PAUSE_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_NFT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_NFTS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_TOKENS;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_UNPAUSE_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_NFT;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.associateOp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.createTokenCreateWrapperWithKeys;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.customFeesWrapper;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.dissociateToken;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungible;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleBurn;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleMint;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleMintAmountOversize;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungiblePause;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleUnpause;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleWipe;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.getTokenExpiryInfoWrapper;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.multiDissociateOp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nonFungiblePause;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nonFungibleUnpause;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.nonFungibleWipe;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.tokenUpdateExpiryInfoWrapper;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.REVERT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.pricing.AssetsLoader;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.TokenCreateWrapper;
import com.hedera.services.store.contracts.precompile.codec.TokenInfoWrapper;
import com.hedera.services.store.contracts.precompile.impl.AssociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.BurnPrecompile;
import com.hedera.services.store.contracts.precompile.impl.DissociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.GetTokenExpiryInfoPrecompile;
import com.hedera.services.store.contracts.precompile.impl.MintPrecompile;
import com.hedera.services.store.contracts.precompile.impl.MultiAssociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.MultiDissociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.PausePrecompile;
import com.hedera.services.store.contracts.precompile.impl.TokenCreatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.TokenGetCustomFeesPrecompile;
import com.hedera.services.store.contracts.precompile.impl.TransferPrecompile;
import com.hedera.services.store.contracts.precompile.impl.UnpausePrecompile;
import com.hedera.services.store.contracts.precompile.impl.UpdateTokenExpiryInfoPrecompile;
import com.hedera.services.store.contracts.precompile.impl.WipeFungiblePrecompile;
import com.hedera.services.store.contracts.precompile.impl.WipeNonFungiblePrecompile;
import com.hedera.services.store.contracts.precompile.proxy.RedirectViewExecutor;
import com.hedera.services.store.contracts.precompile.proxy.ViewExecutor;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeObject;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HTSPrecompiledContractTest {
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private BlockValues blockValues;
    @Mock private MessageFrame messageFrame;
    @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private DecodingFacade decoder;
    @Mock private EncodingFacade encoder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private ExpiringCreations creator;
    @Mock private ImpliedTransfersMarshal impliedTransfers;
    @Mock private FeeCalculator feeCalculator;
    @Mock private StateView stateView;

    @Mock private HederaStackedWorldStateUpdater worldUpdater;
    @Mock private WorldLedgers wrappedLedgers;
    @Mock private UsagePricesProvider resourceCosts;
    @Mock private TransactionBody.Builder mockSynthBodyBuilder;
    @Mock private FeeObject mockFeeObject;
    @Mock private HbarCentExchange exchange;
    @Mock private ExchangeRate exchangeRate;
    @Mock private InfrastructureFactory infrastructureFactory;
    @Mock private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts;
    @Mock private TokenInfoWrapper tokenInfoWrapper;
    @Mock private AccessorFactory accessorFactory;

    private HTSPrecompiledContract subject;
    private PrecompilePricingUtils precompilePricingUtils;
    @Mock private AssetsLoader assetLoader;

    private static final long viewTimestamp = 10L;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private TokenInfo tokenInfo;

    public static final Id fungibleId = Id.fromGrpcToken(fungible);
    public static final Address fungibleTokenAddress = fungibleId.asEvmAddress();

    @BeforeEach
    void setUp() throws IOException {
        tokenInfo =
                TokenInfo.newBuilder()
                        .setLedgerId(fromString("0x03"))
                        .setSupplyTypeValue(1)
                        .setTokenId(fungible)
                        .setDeleted(false)
                        .setSymbol("FT")
                        .setName("NAME")
                        .setMemo("MEMO")
                        .setTreasury(
                                EntityIdUtils.accountIdFromEvmAddress(
                                        Address.wrap(
                                                Bytes.fromHexString(
                                                        "0x00000000000000000000000000000000000005cc"))))
                        .setTotalSupply(1L)
                        .setMaxSupply(1000L)
                        .build();

        precompilePricingUtils =
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
                        decoder,
                        encoder,
                        syntheticTxnFactory,
                        creator,
                        impliedTransfers,
                        () -> feeCalculator,
                        stateView,
                        precompilePricingUtils,
                        infrastructureFactory);
    }

    private ByteString fromString(final String value) {
        return ByteString.copyFrom(Bytes.fromHexString(value).toArray());
    }

    @Test
    void gasRequirementReturnsCorrectValueForInvalidInput() {
        Bytes input = Bytes.of(4, 3, 2, 1);
        // when
        var gas = subject.gasRequirement(input);

        // then
        assertEquals(0L, gas);
    }

    @Test
    void computeCostedRevertsTheFrameIfTheFrameIsStatic() {
        given(messageFrame.isStatic()).willReturn(true);

        final var result = subject.computeCosted(Bytes.of(1, 2, 3, 4), messageFrame);

        verify(messageFrame).setRevertReason(Bytes.of("HTS precompiles are not static".getBytes()));
        assertNull(result.getValue());
    }

    @Test
    void computeCostedWorksForRedirectView() {
        given(worldUpdater.trackingLedgers()).willReturn(wrappedLedgers);
        given(wrappedLedgers.typeOf(fungible)).willReturn(TokenType.FUNGIBLE_COMMON);
        Bytes input = prerequisitesForRedirect(ABI_ID_ERC_NAME);
        given(messageFrame.isStatic()).willReturn(true);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.isInTransaction()).willReturn(false);

        final var redirectViewExecutor =
                new RedirectViewExecutor(
                        input,
                        messageFrame,
                        encoder,
                        decoder,
                        precompilePricingUtils::computeViewFunctionGas);
        given(infrastructureFactory.newRedirectExecutor(any(), any(), any()))
                .willReturn(redirectViewExecutor);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall,
                                Timestamp.newBuilder().setSeconds(viewTimestamp).build()))
                .willReturn(1L);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        final var name = "name";
        given(wrappedLedgers.nameOf(fungible)).willReturn(name);
        given(encoder.encodeName(name)).willReturn(Bytes.of(1));

        final var result = subject.computeCosted(input, messageFrame);

        verify(messageFrame, never()).setRevertReason(any());
        assertEquals(Bytes.of(1), result.getValue());
    }

    @Test
    void computeCostedWorksForView() {
        Bytes input = prerequisites(ABI_ID_GET_TOKEN_INFO);
        given(decoder.decodeGetTokenInfo(input)).willReturn(tokenInfoWrapper);
        given(tokenInfoWrapper.tokenID()).willReturn(fungible);
        given(messageFrame.isStatic()).willReturn(true);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.isInTransaction()).willReturn(false);

        final var viewExecutor =
                new ViewExecutor(
                        input,
                        messageFrame,
                        encoder,
                        decoder,
                        precompilePricingUtils::computeViewFunctionGas,
                        stateView,
                        wrappedLedgers);
        given(infrastructureFactory.newViewExecutor(any(), any(), any(), any(), any()))
                .willReturn(viewExecutor);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall,
                                Timestamp.newBuilder().setSeconds(viewTimestamp).build()))
                .willReturn(1L);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);

        given(stateView.infoForToken(fungible)).willReturn(Optional.of(tokenInfo));
        final var encodedResult =
                Bytes.fromHexString(
                        "0x00000000000000000000000000000000000000000000000000000000000000160000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000012000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000360000000000000000000000000000000000000000000000000000000000000038000000000000000000000000000000000000000000000000000000000000003a000000000000000000000000000000000000000000000000000000000000003c0000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000001a000000000000000000000000000000000000000000000000000000000000005cc00000000000000000000000000000000000000000000000000000000000001e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000003e80000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000022000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000044e414d45000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002465400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000044d454d4f00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000043078303300000000000000000000000000000000000000000000000000000000");
        given(encoder.encodeGetTokenInfo(any())).willReturn(encodedResult);

        final var result = subject.computeCosted(input, messageFrame);

        verify(messageFrame, never()).setRevertReason(any());
        assertEquals(encodedResult, result.getValue());
    }

    Bytes prerequisitesForRedirect(final int descriptor) {
        given(messageFrame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(viewTimestamp);
        return Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                fungibleTokenAddress,
                Bytes.of(Integers.toBytes(descriptor)));
    }

    Bytes prerequisites(final int descriptor) {
        given(messageFrame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(viewTimestamp);
        return Bytes.concatenate(Bytes.of(Integers.toBytes(descriptor)), fungibleTokenAddress);
    }

    @Test
    void computeCallsCorrectImplementationForCryptoTransfer() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof TransferPrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForTransferTokens() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKENS));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof TransferPrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForTransferToken() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKEN));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof TransferPrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForTransferNfts() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFTS));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof TransferPrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForTransferNft() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFT));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof TransferPrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForMintToken() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_MINT_TOKEN));
        given(decoder.decodeMint(any())).willReturn(fungibleMint);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof MintPrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForBurnToken() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_BURN_TOKEN));
        given(decoder.decodeBurn(any())).willReturn(fungibleBurn);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof BurnPrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForAssociateTokens() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_ASSOCIATE_TOKENS));
        final var builder = TokenAssociateTransactionBody.newBuilder();
        builder.setAccount(multiDissociateOp.accountId());
        builder.addAllTokens(multiDissociateOp.tokenIds());
        given(syntheticTxnFactory.createAssociate(any()))
                .willReturn(TransactionBody.newBuilder().setTokenAssociate(builder));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof MultiAssociatePrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForAssociateToken() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_ASSOCIATE_TOKEN));
        given(decoder.decodeAssociation(any(), any())).willReturn(associateOp);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof AssociatePrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForDissociateTokens() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_DISSOCIATE_TOKENS));
        given(decoder.decodeMultipleDissociations(any(), any())).willReturn(multiDissociateOp);
        final var builder = TokenDissociateTransactionBody.newBuilder();
        builder.setAccount(multiDissociateOp.accountId());
        builder.addAllTokens(multiDissociateOp.tokenIds());
        given(syntheticTxnFactory.createDissociate(any()))
                .willReturn(TransactionBody.newBuilder().setTokenDissociate(builder));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof MultiDissociatePrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForCreateFungibleToken() {
        // given
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN));
        given(decoder.decodeFungibleCreate(any(), any()))
                .willReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
    }

    @Test
    void computeCallsCorrectImplementationForCreateNonFungibleToken() {
        // given
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_NON_FUNGIBLE_TOKEN));
        given(decoder.decodeNonFungibleCreate(any(), any()))
                .willReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
    }

    @Test
    void replacesInheritedPropertiesOnCreateNonFungibleToken() {
        // given
        final var parentId = EntityIdUtils.accountIdFromEvmAddress(contractAddress);

        final var autoRenewId = EntityId.fromIdentityCode(10);
        final var tokenCreateWrapper = mock(TokenCreateWrapper.class);
        given(tokenCreateWrapper.hasAutoRenewAccount()).willReturn(false);
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_NON_FUNGIBLE_TOKEN));
        given(decoder.decodeNonFungibleCreate(any(), any())).willReturn(tokenCreateWrapper);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(accounts.get(any(), eq(AccountProperty.AUTO_RENEW_ACCOUNT_ID)))
                .willReturn(autoRenewId);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        givenFrameContext();
        given(dynamicProperties.isHTSPrecompileCreateEnabled()).willReturn(true);
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        assertTrue(subject.getPrecompile() instanceof TokenCreatePrecompile);
        verify(tokenCreateWrapper).inheritAutoRenewAccount(autoRenewId);
    }

    @Test
    void computeCallsCorrectImplementationForCreateFungibleTokenWithFees() {
        // given
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES));
        given(decoder.decodeFungibleCreateWithFees(any(), any()))
                .willReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
    }

    @Test
    void computeCallsCorrectImplementationForCreateNonFungibleTokenWithFees() {
        // given
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES));
        given(decoder.decodeNonFungibleCreateWithFees(any(), any()))
                .willReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
    }

    private void prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(Bytes input) {
        // given
        givenFrameContext();
        given(dynamicProperties.isHTSPrecompileCreateEnabled()).willReturn(true);
        final var accounts = mock(TransactionalLedger.class);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        final var key = mock(JKey.class);
        given(accounts.get(any(), any())).willReturn(key);

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof TokenCreatePrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForDissociateToken() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_DISSOCIATE_TOKEN));
        given(decoder.decodeDissociate(any(), any())).willReturn(dissociateToken);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof DissociatePrecompile);
    }

    @Test
    void computeReturnsNullForWrongInput() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(0, 0, 0, 0);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);
        var result = subject.computePrecompile(input, messageFrame);

        // then
        assertNull(result.getOutput());
    }

    @Test
    void computeReturnsNullForEmptyTransactionBody() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_MINT_TOKEN));
        given(decoder.decodeMint(any())).willReturn(fungibleMintAmountOversize);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        var result = subject.computePrecompile(input, messageFrame);
        assertNull(result.getOutput());
    }

    @Test
    void computeReturnsNullForTokenCreateWhenNotEnabled() {
        // given
        givenFrameContext();
        given(dynamicProperties.isHTSPrecompileCreateEnabled()).willReturn(false);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);
        var result = subject.computePrecompile(input, messageFrame);

        // then
        assertNull(result.getOutput());
        assertNull(subject.getPrecompile());
    }

    @Test
    void prepareFieldsWithAliasedMessageSender() {
        givenFrameContext();
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        subject.prepareFields(messageFrame);

        verify(messageFrame, times(1)).getSenderAddress();
    }

    @Test
    void computeInternalThrowsExceptionForInsufficientGas() {
        // given
        givenFrameContext();
        givenPricingUtilsContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_MINT_TOKEN));
        given(decoder.decodeMint(any())).willReturn(fungibleMint);
        given(messageFrame.getRemainingGas()).willReturn(0L);
        given(syntheticTxnFactory.createMint(fungibleMint)).willReturn(mockSynthBodyBuilder);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockSynthBodyBuilder.build()).willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class)))
                .willReturn(mockSynthBodyBuilder);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then
        assertThrows(
                InvalidTransactionException.class, () -> subject.computeInternal(messageFrame));
    }

    @Test
    void computeCallsCorrectImplementationForPauseFungibleToken() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_PAUSE_TOKEN));
        given(decoder.decodePause(any())).willReturn(fungiblePause);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof PausePrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForPauseNonFungibleToken() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_PAUSE_TOKEN));
        given(decoder.decodePause(any())).willReturn(nonFungiblePause);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof PausePrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForUnpauseFungibleToken() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_UNPAUSE_TOKEN));
        given(decoder.decodeUnpause(any())).willReturn(fungibleUnpause);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof UnpausePrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForUnpauseNonFungibleToken() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_UNPAUSE_TOKEN));
        given(decoder.decodeUnpause(any())).willReturn(nonFungibleUnpause);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof UnpausePrecompile);
    }

    @Test
    void defaultHandleHbarsThrows() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFTS));
        given(messageFrame.getValue()).willReturn(Wei.of(1));
        given(syntheticTxnFactory.createCryptoTransfer(any()))
                .willReturn(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        final var precompile = subject.getPrecompile();
        assertThrows(
                InvalidTransactionException.class, () -> precompile.handleSentHbars(messageFrame));

        verify(messageFrame).setRevertReason(INVALID_TRANSFER);
        verify(messageFrame).setState(REVERT);
    }

    @Test
    void computeCallsCorrectImplementationForWipeFungibleToken() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE));
        given(decoder.decodeWipe(any(), any())).willReturn(fungibleWipe);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof WipeFungiblePrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForWipeNonFungibleToken() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_WIPE_TOKEN_ACCOUNT_NFT));
        given(decoder.decodeWipeNFT(any(), any())).willReturn(nonFungibleWipe);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof WipeNonFungiblePrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForGetTokenCustomFees() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_CUSTOM_FEES));
        given(decoder.decodeTokenGetCustomFees(any())).willReturn(customFeesWrapper);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof TokenGetCustomFeesPrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForGetExpiryInfoForToken() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_EXPIRY_INFO));
        given(decoder.decodeGetTokenExpiryInfo(any())).willReturn(getTokenExpiryInfoWrapper);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof GetTokenExpiryInfoPrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForUpdateExpiryInfoForToken() {
        // given
        givenFrameContext();
        Bytes input = Bytes.of(Integers.toBytes(ABI_ID_UPDATE_TOKEN_EXPIRY_INFO));
        given(decoder.decodeUpdateTokenExpiryInfo(any(), any()))
                .willReturn(tokenUpdateExpiryInfoWrapper);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof UpdateTokenExpiryInfoPrecompile);
    }

    private void givenFrameContext() {
        given(messageFrame.getSenderAddress()).willReturn(contractAddress);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
    }

    private void givenPricingUtilsContext() {
        given(exchange.rate(any())).willReturn(exchangeRate);
        given(exchangeRate.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRate.getHbarEquiv()).willReturn(HBAR_RATE);
    }
}
