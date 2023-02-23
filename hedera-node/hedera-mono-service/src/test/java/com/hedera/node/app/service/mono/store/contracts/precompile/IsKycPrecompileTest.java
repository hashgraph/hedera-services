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

package com.hedera.node.app.service.mono.store.contracts.precompile;

import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_IS_KYC;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.accountAddr;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungibleTokenAddr;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.grantRevokeKycWrapper;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.IsKycPrecompile.decodeIsKyc;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.FeeResourcesLoaderImpl;
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
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.IsKycPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.io.IOException;
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
class IsKycPrecompileTest {
    @Mock
    private GlobalDynamicProperties dynamicProperties;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private MessageFrame frame;

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
    private SideEffectsTracker sideEffects;

    @Mock
    private FeeObject mockFeeObject;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private StateView stateView;

    @Mock
    private ContractAliases aliases;

    @Mock
    private HederaStackedWorldStateUpdater worldUpdater;

    @Mock
    private WorldLedgers wrappedLedgers;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private TransactionBody.Builder mockSynthBodyBuilder;

    @Mock
    private InfrastructureFactory infrastructureFactory;

    @Mock
    private TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts;

    @Mock
    private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;

    @Mock
    private TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nfts;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, HederaTokenRel> tokenRels;

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    @Mock
    private FeeResourcesLoaderImpl feeResourcesLoader;

    public static final Bytes IS_KYC = Bytes.fromHexString(
            "0xf2c31ff400000000000000000000000000000000000000000000000000000000000004b200000000000000000000000000000000000000000000000000000000000004b0");
    private HTSPrecompiledContract subject;
    private MockedStatic<IsKycPrecompile> isKycPrecompile;

    @BeforeEach
    void setUp() throws IOException {
        final PrecompilePricingUtils precompilePricingUtils = new PrecompilePricingUtils(
                assetLoader, () -> feeCalculator, stateView, accessorFactory, feeResourcesLoader);
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
        isKycPrecompile = Mockito.mockStatic(IsKycPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        isKycPrecompile.close();
    }

    @Test
    void IsKyc() {
        // given
        final var output = "0x000000000000000000000000000000000000000000000000000000000000"
                + "00160000000000000000000000000000000000000000000000000000000000000001";
        final var successOutput =
                Bytes.fromHexString("0x000000000000000000000000000000000000000000000000000000000000001600000000000"
                        + "00000000000000000000000000000000000000000000000000001");
        final Bytes pretendArguments =
                Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_IS_KYC)), fungibleTokenAddr, accountAddr);
        givenMinimalFrameContext();
        givenMinimalFeesContext();
        givenLedgers();
        given(wrappedLedgers.isKyc(any(), any())).willReturn(true);
        givenMinimalContextForSuccessfulCall();
        final Bytes input = Bytes.of(Integers.toBytes(ABI_ID_IS_KYC));
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        isKycPrecompile.when(() -> decodeIsKyc(any(), any())).thenReturn(grantRevokeKycWrapper);
        given(evmEncoder.encodeIsKyc(true)).willReturn(successResult);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(tokenRels.get(any(), any())).willReturn(Boolean.TRUE);
        given(evmEncoder.encodeIsKyc(true)).willReturn(Bytes.fromHexString(output));
        given(frame.getValue()).willReturn(Wei.ZERO);

        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.computeInternal(frame);

        // then
        assertEquals(successOutput, result);
    }

    @Test
    void decodeIsKycInput() {
        isKycPrecompile.when(() -> decodeIsKyc(IS_KYC, identity())).thenCallRealMethod();
        final var decodedInput = decodeIsKyc(IS_KYC, identity());

        assertTrue(decodedInput.token().getTokenNum() > 0);
        assertTrue(decodedInput.account().getAccountNum() > 0);
    }

    private void givenMinimalFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
    }

    private void givenMinimalContextForSuccessfulCall() {
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.aliases()).willReturn(aliases);
        given(aliases.resolveForEvm(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
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

    private void givenMinimalFeesContext() {
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
    }
}
