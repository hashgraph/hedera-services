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

import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_ERC_NAME;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_INFO;
import static com.hedera.node.app.service.mono.contracts.execution.HederaMessageCallProcessor.INVALID_TRANSFER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ASSOCIATE_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_ASSOCIATE_TOKENS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_BURN_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_BURN_TOKEN_V2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_V3;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V3;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V3;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V3;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_CRYPTO_TRANSFER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_CRYPTO_TRANSFER_V2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_DISSOCIATE_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_DISSOCIATE_TOKENS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_CUSTOM_FEES;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_EXPIRY_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_MINT_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_MINT_TOKEN_V2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_PAUSE_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_FROM;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_FROM_NFT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_NFT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_NFTS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_TRANSFER_TOKENS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_UNPAUSE_TOKEN;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_UPDATE_TOKEN_EXPIRY_INFO_V2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE_V2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_NFT;
import static com.hedera.node.app.service.mono.store.contracts.precompile.ERC20PrecompilesTest.CRYPTO_TRANSFER_TOKEN_FROM_NFT_WRAPPER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.ERC20PrecompilesTest.CRYPTO_TRANSFER_TOKEN_FROM_WRAPPER;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.associateOp;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.createTokenCreateWrapperWithKeys;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.customFeesWrapper;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.dissociateToken;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungible;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungibleMint;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungibleMintAmountOversize;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungiblePause;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungibleWipe;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.getTokenExpiryInfoWrapper;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.multiDissociateOp;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.nonFungibleBurn;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.nonFungiblePause;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.nonFungibleUnpause;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.nonFungibleWipe;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokenUpdateExpiryInfoWrapper;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenCreatePrecompile.decodeFungibleCreateV2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenCreatePrecompile.decodeFungibleCreateV3;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenCreatePrecompile.decodeFungibleCreateWithFeesV2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenCreatePrecompile.decodeFungibleCreateWithFeesV3;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenCreatePrecompile.decodeNonFungibleCreateV2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenCreatePrecompile.decodeNonFungibleCreateV3;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenCreatePrecompile.decodeNonFungibleCreateWithFeesV2;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenCreatePrecompile.decodeNonFungibleCreateWithFeesV3;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.REVERT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmTokenInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenInfoWrapper;
import com.hedera.node.app.service.evm.store.contracts.precompile.impl.EvmTokenInfoPrecompile;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.RedirectViewExecutor;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewExecutor;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.node.app.service.mono.config.NetworkInfo;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.expiry.ExpiringCreations;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.TokenCreateWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.*;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.AssociatePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.BurnPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.DissociatePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.ERCTransferPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.GetTokenExpiryInfoPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.MintPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.MultiAssociatePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.MultiDissociatePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.PausePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenCreatePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenGetCustomFeesPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenInfoPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TransferPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.UnpausePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.UpdateTokenExpiryInfoPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.WipeFungiblePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.WipeNonFungiblePrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HTSPrecompiledContractTest {
    @Mock
    private GlobalDynamicProperties dynamicProperties;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private BlockValues blockValues;

    @Mock
    private MessageFrame messageFrame;

    @Mock
    private TxnAwareEvmSigsVerifier sigsVerifier;

    @Mock
    private RecordsHistorian recordsHistorian;

    @Mock
    private EncodingFacade encoder;

    @Mock
    private EvmEncodingFacade evmEncoder;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private ExpiringCreations creator;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private StateView stateView;

    @Mock
    private TokenAccessor tokenAccessor;

    @Mock
    private HederaStackedWorldStateUpdater worldUpdater;

    @Mock
    private WorldLedgers wrappedLedgers;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock
    private TransactionBody.Builder mockSynthBodyBuilder;

    @Mock
    private FeeObject mockFeeObject;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private ExchangeRate exchangeRate;

    @Mock
    private InfrastructureFactory infrastructureFactory;

    @Mock
    private EvmInfrastructureFactory evmInfrastructureFactory;

    @Mock
    private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts;

    @Mock
    private TokenInfoWrapper<byte[]> tokenInfoWrapper;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private NetworkInfo networkInfo;

    private HTSPrecompiledContract subject;
    private PrecompilePricingUtils precompilePricingUtils;
    private MockedStatic<TokenInfoPrecompile> tokenInfoPrecompile;
    private MockedStatic<MintPrecompile> mintPrecompile;
    private MockedStatic<AssociatePrecompile> associatePrecompile;
    private MockedStatic<MultiAssociatePrecompile> multiAssociatePrecompile;
    private MockedStatic<DissociatePrecompile> dissociatePrecompile;
    private MockedStatic<MultiDissociatePrecompile> multiDissociatePrecompile;
    private MockedStatic<TokenCreatePrecompile> tokenCreatePrecompile;
    private MockedStatic<PausePrecompile> pausePrecompile;
    private MockedStatic<UnpausePrecompile> unpausePrecompile;
    private MockedStatic<WipeFungiblePrecompile> wipeFungiblePrecompile;
    private MockedStatic<WipeNonFungiblePrecompile> wipeNonFungiblePrecompile;
    private MockedStatic<TokenGetCustomFeesPrecompile> tokenGetCustomFeesPrecompile;
    private MockedStatic<GetTokenExpiryInfoPrecompile> getTokenExpiryInfoPrecompile;
    private MockedStatic<UpdateTokenExpiryInfoPrecompile> updateTokenExpiryInfoPrecompile;
    private MockedStatic<ERCTransferPrecompile> ercTransferPrecompile;
    private MockedStatic<BurnPrecompile> burnPrecompile;
    private MockedStatic<BalanceOfPrecompile> balanceOfPrecompile;

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    private static final long viewTimestamp = 10L;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;

    public static final Id fungibleId = Id.fromGrpcToken(fungible);
    public static final Address fungibleTokenAddress = fungibleId.asEvmAddress();

    @BeforeEach
    void setUp() throws IOException {
        precompilePricingUtils = new PrecompilePricingUtils(
                assetLoader, exchange, () -> feeCalculator, resourceCosts, stateView, accessorFactory);
        evmHTSPrecompiledContract = new EvmHTSPrecompiledContract(evmInfrastructureFactory);
        subject = new HTSPrecompiledContract(
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
        tokenInfoPrecompile = Mockito.mockStatic(TokenInfoPrecompile.class);
        mintPrecompile = Mockito.mockStatic(MintPrecompile.class);
        associatePrecompile = Mockito.mockStatic(AssociatePrecompile.class);
        multiAssociatePrecompile = Mockito.mockStatic(MultiAssociatePrecompile.class);
        dissociatePrecompile = Mockito.mockStatic(DissociatePrecompile.class);
        multiDissociatePrecompile = Mockito.mockStatic(MultiDissociatePrecompile.class);
        tokenCreatePrecompile = Mockito.mockStatic(TokenCreatePrecompile.class);
        pausePrecompile = Mockito.mockStatic(PausePrecompile.class);
        unpausePrecompile = Mockito.mockStatic(UnpausePrecompile.class);
        wipeFungiblePrecompile = Mockito.mockStatic(WipeFungiblePrecompile.class);
        wipeNonFungiblePrecompile = Mockito.mockStatic(WipeNonFungiblePrecompile.class);
        tokenGetCustomFeesPrecompile = Mockito.mockStatic(TokenGetCustomFeesPrecompile.class);
        getTokenExpiryInfoPrecompile = Mockito.mockStatic(GetTokenExpiryInfoPrecompile.class);
        updateTokenExpiryInfoPrecompile = Mockito.mockStatic(UpdateTokenExpiryInfoPrecompile.class);
        ercTransferPrecompile = Mockito.mockStatic(ERCTransferPrecompile.class);
        burnPrecompile = Mockito.mockStatic(BurnPrecompile.class);
        balanceOfPrecompile = Mockito.mockStatic(BalanceOfPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        tokenInfoPrecompile.close();
        mintPrecompile.close();
        associatePrecompile.close();
        multiAssociatePrecompile.close();
        dissociatePrecompile.close();
        multiDissociatePrecompile.close();
        tokenCreatePrecompile.close();
        pausePrecompile.close();
        unpausePrecompile.close();
        wipeFungiblePrecompile.close();
        wipeNonFungiblePrecompile.close();
        tokenGetCustomFeesPrecompile.close();
        getTokenExpiryInfoPrecompile.close();
        updateTokenExpiryInfoPrecompile.close();
        ercTransferPrecompile.close();
        burnPrecompile.close();
        balanceOfPrecompile.close();
    }

    private ByteString fromString(final String value) {
        return ByteString.copyFrom(Bytes.fromHexString(value).toArray());
    }

    @Test
    void gasRequirementReturnsCorrectValueForInvalidInput() {
        final Bytes input = Bytes.of(4, 3, 2, 1);
        // when
        final var gas = subject.gasRequirement(input);

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
        final Bytes input = prerequisitesForRedirect(ABI_ID_ERC_NAME);
        given(messageFrame.isStatic()).willReturn(true);
        given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
        given(worldUpdater.isInTransaction()).willReturn(false);

        final var redirectViewExecutor = new RedirectViewExecutor(
                input, messageFrame, evmEncoder, precompilePricingUtils::computeViewFunctionGas, tokenAccessor);
        given(evmInfrastructureFactory.newRedirectExecutor(any(), any(), any(), any()))
                .willReturn(redirectViewExecutor);
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(feeCalculator.estimatedGasPriceInTinybars(
                        HederaFunctionality.ContractCall,
                        Timestamp.newBuilder().setSeconds(viewTimestamp).build()))
                .willReturn(1L);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));

        final var name = "name";
        given(tokenAccessor.nameOf(any())).willReturn(name);
        given(evmEncoder.encodeName(name)).willReturn(Bytes.of(1));

        final var result = subject.computeCosted(input, messageFrame);

        verify(messageFrame, never()).setRevertReason(any());
        assertEquals(Bytes.of(1), result.getValue());
    }

    @Test
    void computeCostedWorksForView() {
        EvmTokenInfo evmTokenInfo = new EvmTokenInfo(
                fromString("0x03").toByteArray(),
                1,
                false,
                "FT",
                "NAME",
                "MEMO",
                Address.wrap(Bytes.fromHexString("0x00000000000000000000000000000000000005cc")),
                1L,
                1000L,
                0,
                0L);

        try (MockedStatic<EvmTokenInfoPrecompile> utilities = Mockito.mockStatic(EvmTokenInfoPrecompile.class)) {

            final Bytes input = prerequisites(ABI_ID_GET_TOKEN_INFO);
            utilities
                    .when(() -> EvmTokenInfoPrecompile.decodeGetTokenInfo(input))
                    .thenReturn(tokenInfoWrapper);
            given(tokenInfoWrapper.token()).willReturn(fungibleTokenAddr.toArrayUnsafe());
            given(messageFrame.isStatic()).willReturn(true);
            given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
            given(worldUpdater.isInTransaction()).willReturn(false);
            given(worldUpdater.trackingLedgers()).willReturn(wrappedLedgers);
            final var viewExecutor = new ViewExecutor(
                    input, messageFrame, evmEncoder, precompilePricingUtils::computeViewFunctionGas, tokenAccessor);
            given(evmInfrastructureFactory.newViewExecutor(any(), any(), any(), any()))
                    .willReturn(viewExecutor);
            given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                    .willReturn(mockFeeObject);
            given(feeCalculator.estimatedGasPriceInTinybars(
                            HederaFunctionality.ContractCall,
                            Timestamp.newBuilder().setSeconds(viewTimestamp).build()))
                    .willReturn(1L);
            given(mockFeeObject.getNodeFee()).willReturn(1L);
            given(mockFeeObject.getNetworkFee()).willReturn(1L);
            given(mockFeeObject.getServiceFee()).willReturn(1L);

            given(stateView.getNetworkInfo()).willReturn(networkInfo);
            given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));
            given(tokenAccessor.evmInfoForToken(any())).willReturn(Optional.of(evmTokenInfo));
            final var encodedResult = Bytes.fromHexString(
                    "0x00000000000000000000000000000000000000000000000000000000000000160000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000012000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000360000000000000000000000000000000000000000000000000000000000000038000000000000000000000000000000000000000000000000000000000000003a000000000000000000000000000000000000000000000000000000000000003c0000000000000000000000000000000000000000000000000000000000000016000000000000000000000000000000000000000000000000000000000000001a000000000000000000000000000000000000000000000000000000000000005cc00000000000000000000000000000000000000000000000000000000000001e0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000003e80000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000022000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000044e414d45000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002465400000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000044d454d4f00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000043078303300000000000000000000000000000000000000000000000000000000");
            given(evmEncoder.encodeGetTokenInfo(any())).willReturn(encodedResult);

            final var result = subject.computeCosted(input, messageFrame);

            verify(messageFrame, never()).setRevertReason(any());
            assertEquals(encodedResult, result.getValue());
        }
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
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof TransferPrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForCryptoTransferV2() {
        // given
        givenFrameContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CRYPTO_TRANSFER_V2));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        given(dynamicProperties.isAtomicCryptoTransferEnabled()).willReturn(true);
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof TransferPrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForTransferTokens() {
        // given
        givenFrameContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKENS));
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
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_TOKEN));
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
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFTS));
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
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFT));
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
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_MINT_TOKEN));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof MintPrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForMintTokenV2() {
        // given
        givenFrameContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_MINT_TOKEN_V2));
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
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_BURN_TOKEN));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof BurnPrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForBurnTokenV2() {
        // given
        givenFrameContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_BURN_TOKEN_V2));
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
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_ASSOCIATE_TOKENS));
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
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_ASSOCIATE_TOKEN));
        associatePrecompile
                .when(() -> AssociatePrecompile.decodeAssociation(any(), any()))
                .thenReturn(associateOp);
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
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_DISSOCIATE_TOKENS));
        multiDissociatePrecompile
                .when(() -> MultiDissociatePrecompile.decodeMultipleDissociations(any(), any()))
                .thenReturn(multiDissociateOp);
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
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN));
        tokenCreatePrecompile
                .when(() -> TokenCreatePrecompile.decodeFungibleCreate(any(), any()))
                .thenReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
    }

    @Test
    void computeCallsCorrectImplementationForCreateNonFungibleToken() {
        // given
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_NON_FUNGIBLE_TOKEN));
        tokenCreatePrecompile
                .when(() -> TokenCreatePrecompile.decodeNonFungibleCreate(any(), any()))
                .thenReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
    }

    @Test
    void replacesInheritedPropertiesOnCreateNonFungibleToken() {
        // given
        final var autoRenewId = EntityId.fromIdentityCode(10);
        final var tokenCreateWrapper = mock(TokenCreateWrapper.class);
        given(tokenCreateWrapper.hasAutoRenewAccount()).willReturn(false);
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_NON_FUNGIBLE_TOKEN));
        tokenCreatePrecompile
                .when(() -> TokenCreatePrecompile.decodeNonFungibleCreate(any(), any()))
                .thenReturn(tokenCreateWrapper);
        given(wrappedLedgers.accounts()).willReturn(accounts);
        given(accounts.get(any(), eq(AccountProperty.AUTO_RENEW_ACCOUNT_ID))).willReturn(autoRenewId);
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
    void computeCallsCorrectImplementationForCreateFungibleTokenWithFeesV2() {
        // given
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V2));
        tokenCreatePrecompile
                .when(() -> decodeFungibleCreateWithFeesV2(any(), any()))
                .thenReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
    }

    @Test
    void computeCallsCorrectImplementationForCreateFungibleTokenWithFeesV3() {
        // given
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V3));
        tokenCreatePrecompile
                .when(() -> decodeFungibleCreateWithFeesV3(any(), any()))
                .thenReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
    }

    @Test
    void computeCallsCorrectImplementationForCreateNonFungibleTokenWithFeesV2() {
        // given
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V2));
        tokenCreatePrecompile
                .when(() -> decodeNonFungibleCreateWithFeesV2(any(), any()))
                .thenReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
    }

    @Test
    void computeCallsCorrectImplementationForCreateNonFungibleTokenWithFeesV3() {
        // given
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V3));
        tokenCreatePrecompile
                .when(() -> decodeNonFungibleCreateWithFeesV3(any(), any()))
                .thenReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
    }

    @Test
    void computeCallsCorrectImplementationForCreateFungibleTokenV2() {
        // given
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN_V2));
        tokenCreatePrecompile
                .when(() -> decodeFungibleCreateV2(any(), any()))
                .thenReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
    }

    @Test
    void computeCallsCorrectImplementationForCreateFungibleTokenV3() {
        // given
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN_V3));
        tokenCreatePrecompile
                .when(() -> decodeFungibleCreateV3(any(), any()))
                .thenReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
    }

    @Test
    void computeCallsCorrectImplementationForCreateNonFungibleTokenV2() {
        // given
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V2));
        tokenCreatePrecompile
                .when(() -> decodeNonFungibleCreateV2(any(), any()))
                .thenReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
    }

    @Test
    void computeCallsCorrectImplementationForCreateNonFungibleTokenV3() {
        // given
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V3));
        tokenCreatePrecompile
                .when(() -> decodeNonFungibleCreateV3(any(), any()))
                .thenReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
    }

    @Test
    void computeCallsCorrectImplementationForCreateFungibleTokenWithFees() {
        // given
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES));
        tokenCreatePrecompile
                .when(() -> TokenCreatePrecompile.decodeFungibleCreateWithFees(any(), any()))
                .thenReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
    }

    @Test
    void computeCallsCorrectImplementationForCreateNonFungibleTokenWithFees() {
        // given
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES));
        tokenCreatePrecompile
                .when(() -> TokenCreatePrecompile.decodeNonFungibleCreateWithFees(any(), any()))
                .thenReturn(createTokenCreateWrapperWithKeys(Collections.emptyList()));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(input);
    }

    private void prepareAndAssertCorrectInstantiationOfTokenCreatePrecompile(final Bytes input) {
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
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_DISSOCIATE_TOKEN));
        dissociatePrecompile
                .when(() -> DissociatePrecompile.decodeDissociate(any(), any()))
                .thenReturn(dissociateToken);
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
        final Bytes input = Bytes.of(0, 0, 0, 0);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.computePrecompile(input, messageFrame);

        // then
        assertNull(result.getOutput());
    }

    @Test
    void computeReturnsNullForEmptyTransactionBody() {
        // given
        givenFrameContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_MINT_TOKEN));
        mintPrecompile.when(() -> MintPrecompile.decodeMint(any())).thenReturn(fungibleMintAmountOversize);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        final var result = subject.computePrecompile(input, messageFrame);
        assertNull(result.getOutput());
    }

    @Test
    void computeReturnsNullForBurnEmptyTransactionBody() {
        // given
        givenFrameContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_BURN_TOKEN_V2));
        burnPrecompile.when(() -> BurnPrecompile.decodeBurnV2(any())).thenReturn(nonFungibleBurn);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        final var result = subject.computePrecompile(input, messageFrame);
        assertNull(result.getOutput());
    }

    @Test
    void computeReturnsNullForTokenCreateWhenNotEnabled() {
        // given
        givenFrameContext();
        given(dynamicProperties.isHTSPrecompileCreateEnabled()).willReturn(false);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_CREATE_FUNGIBLE_TOKEN));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.computePrecompile(input, messageFrame);

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
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_MINT_TOKEN));
        mintPrecompile.when(() -> MintPrecompile.decodeMint(any())).thenReturn(fungibleMint);
        given(messageFrame.getRemainingGas()).willReturn(0L);
        given(syntheticTxnFactory.createMint(fungibleMint)).willReturn(mockSynthBodyBuilder);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, HTSTestsUtil.timestamp))
                .willReturn(1L);
        given(feeCalculator.computeFee(any(), any(), any(), any())).willReturn(mockFeeObject);
        given(mockSynthBodyBuilder.build())
                .willReturn(TransactionBody.newBuilder().build());
        given(mockSynthBodyBuilder.setTransactionID(any(TransactionID.class))).willReturn(mockSynthBodyBuilder);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);

        // then
        assertThrows(InvalidTransactionException.class, () -> subject.computeInternal(messageFrame));
    }

    @Test
    void computeCallsCorrectImplementationForPauseFungibleToken() {
        // given
        givenFrameContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_PAUSE_TOKEN));
        pausePrecompile.when(() -> PausePrecompile.decodePause(any())).thenReturn(fungiblePause);
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
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_PAUSE_TOKEN));
        pausePrecompile.when(() -> PausePrecompile.decodePause(any())).thenReturn(nonFungiblePause);
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
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_UNPAUSE_TOKEN));
        unpausePrecompile.when(() -> UnpausePrecompile.decodeUnpause(any())).thenReturn(HTSTestsUtil.fungibleUnpause);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof UnpausePrecompile);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void computeCallsCorrectImplementationForExplicitRedirectTokenCall(final boolean tokenExists) {
        // given
        givenFrameContext();
        final Bytes input = Bytes.fromHexString(
                // explicit redirectForToken input (normal encoding)
                "0x618dc65e000000000000000000000000000000000000000000000000000000000000043c0000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000002470a08231000000000000000000000000000000000000000000000000000000000000043b00000000000000000000000000000000000000000000000000000000");
        balanceOfPrecompile
                // the input passed in the actual decoding method should be in redirect form (packed
                // encoding)
                .when(() -> BalanceOfPrecompile.decodeBalanceOf(
                        eq(Bytes.fromHexString(
                                "0x70a08231000000000000000000000000000000000000000000000000000000000000043b")),
                        any()))
                .thenReturn(HTSTestsUtil.balanceOfWrapper);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        final var tokensLedger = mock(TransactionalLedger.class);
        given(wrappedLedgers.tokens()).willReturn(tokensLedger);
        given(tokensLedger.exists(any(TokenID.class))).willReturn(tokenExists);

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof RedirectPrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForUnpauseNonFungibleToken() {
        // given
        givenFrameContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_UNPAUSE_TOKEN));
        unpausePrecompile.when(() -> UnpausePrecompile.decodeUnpause(any())).thenReturn(nonFungibleUnpause);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof UnpausePrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForHapiTransferFromForFungibleToken() {
        // given
        givenFrameContext();
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_FROM));
        ercTransferPrecompile
                .when(() -> ERCTransferPrecompile.decodeERCTransferFrom(
                        any(), any(), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_TOKEN_FROM_WRAPPER);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof ERCTransferPrecompile);
    }

    @Test
    void computeThrowsWhenTryingToCallFungibleHapiTransferFromWhenNotEnabled() {
        // given
        givenFrameContext();
        given(dynamicProperties.areAllowancesEnabled()).willReturn(false);
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_FROM));
        ercTransferPrecompile
                .when(() -> ERCTransferPrecompile.decodeERCTransferFrom(
                        any(), any(), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_TOKEN_FROM_WRAPPER);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);

        // then
        assertThrows(InvalidTransactionException.class, () -> subject.prepareComputation(input, a -> a));
    }

    @Test
    void computeCallsCorrectImplementationForHapiTransferFromForNFTToken() {
        // given
        givenFrameContext();
        given(dynamicProperties.areAllowancesEnabled()).willReturn(true);
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_FROM_NFT));
        ercTransferPrecompile
                .when(() -> ERCTransferPrecompile.decodeERCTransferFrom(
                        any(), any(), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_TOKEN_FROM_NFT_WRAPPER);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof ERCTransferPrecompile);
    }

    @Test
    void computeThrowsWhenTryingToCallHapiTransferFromNFTWhenNotEnabled() {
        // given
        givenFrameContext();
        given(dynamicProperties.areAllowancesEnabled()).willReturn(false);
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_FROM_NFT));
        ercTransferPrecompile
                .when(() -> ERCTransferPrecompile.decodeERCTransferFrom(
                        any(), any(), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(CRYPTO_TRANSFER_TOKEN_FROM_NFT_WRAPPER);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);

        // then
        assertThrows(InvalidTransactionException.class, () -> subject.prepareComputation(input, a -> a));
    }

    @Test
    void defaultHandleHbarsThrows() {
        // given
        givenFrameContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_TRANSFER_NFTS));
        given(messageFrame.getValue()).willReturn(Wei.of(1));
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        final var precompile = subject.getPrecompile();
        assertThrows(InvalidTransactionException.class, () -> precompile.handleSentHbars(messageFrame));

        verify(messageFrame).setRevertReason(INVALID_TRANSFER);
        verify(messageFrame).setState(REVERT);
    }

    @Test
    void computeCallsCorrectImplementationForWipeFungibleToken() {
        // given
        givenFrameContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE));
        wipeFungiblePrecompile
                .when(() -> WipeFungiblePrecompile.decodeWipe(any(), any()))
                .thenReturn(fungibleWipe);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof WipeFungiblePrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForWipeFungibleTokenV2() {
        // given
        givenFrameContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE_V2));
        wipeFungiblePrecompile
                .when(() -> WipeFungiblePrecompile.decodeWipeV2(any(), any()))
                .thenReturn(fungibleWipe);
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
        final Bytes input = Bytes.of(Integers.toBytes(ABI_WIPE_TOKEN_ACCOUNT_NFT));
        wipeNonFungiblePrecompile
                .when(() -> WipeNonFungiblePrecompile.decodeWipeNFT(any(), any()))
                .thenReturn(nonFungibleWipe);
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
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_CUSTOM_FEES));
        tokenGetCustomFeesPrecompile
                .when(() -> TokenGetCustomFeesPrecompile.decodeTokenGetCustomFees(any()))
                .thenReturn(customFeesWrapper);
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
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_EXPIRY_INFO));
        getTokenExpiryInfoPrecompile
                .when(() -> GetTokenExpiryInfoPrecompile.decodeGetTokenExpiryInfo(any()))
                .thenReturn(getTokenExpiryInfoWrapper);
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
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_UPDATE_TOKEN_EXPIRY_INFO));
        updateTokenExpiryInfoPrecompile
                .when(() -> UpdateTokenExpiryInfoPrecompile.decodeUpdateTokenExpiryInfo(any(), any()))
                .thenReturn(tokenUpdateExpiryInfoWrapper);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        // when
        subject.prepareFields(messageFrame);
        subject.prepareComputation(input, a -> a);

        // then
        assertTrue(subject.getPrecompile() instanceof UpdateTokenExpiryInfoPrecompile);
    }

    @Test
    void computeCallsCorrectImplementationForUpdateExpiryInfoV2() {
        // given
        givenFrameContext();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_UPDATE_TOKEN_EXPIRY_INFO_V2));
        updateTokenExpiryInfoPrecompile
                .when(() -> UpdateTokenExpiryInfoPrecompile.decodeUpdateTokenExpiryInfoV2(any(), any()))
                .thenReturn(tokenUpdateExpiryInfoWrapper);
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
