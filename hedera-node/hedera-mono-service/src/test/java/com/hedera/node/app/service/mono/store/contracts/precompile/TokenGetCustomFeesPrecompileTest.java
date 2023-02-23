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

import static com.hedera.node.app.service.mono.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_CUSTOM_FEES;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.invalidTokenIdResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.payer;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokenMerkleAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokenMerkleId;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenGetCustomFeesPrecompile.decodeTokenGetCustomFees;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.TokenGetCustomFeesWrapper;
import com.hedera.node.app.service.mono.config.NetworkInfo;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.FeeResourcesLoaderImpl;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.expiry.ExpiringCreations;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.TokenGetCustomFeesPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
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
class TokenGetCustomFeesPrecompileTest {

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
    private SideEffectsTracker sideEffects;

    @Mock
    private TransactionBody.Builder mockSynthBodyBuilder;

    @Mock
    private ExpirableTxnRecord.Builder mockRecordBuilder;

    @Mock
    private SyntheticTxnFactory syntheticTxnFactory;

    @Mock
    private HederaStackedWorldStateUpdater worldUpdater;

    @Mock
    private WorldLedgers wrappedLedgers;

    @Mock
    private ExpiringCreations creator;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private StateView stateView;

    @Mock
    private UsagePricesProvider resourceCosts;

    @Mock
    private InfrastructureFactory infrastructureFactory;

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private HbarCentExchange exchange;

    @Mock
    private FeeObject mockFeeObject;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    @Mock
    private FeeResourcesLoaderImpl feeResourcesLoader;

    private static final Bytes GET_FUNGIBLE_TOKEN_CUSTOM_FEES_INPUT =
            Bytes.fromHexString("0xae7611a000000000000000000000000000000000000000000000000000000000000003ee");

    private static final Bytes GET_NON_FUNGIBLE_TOKEN_CUSTOM_FEES_INPUT =
            Bytes.fromHexString("0xae7611a000000000000000000000000000000000000000000000000000000000000003f6");
    private HTSPrecompiledContract subject;
    private MockedStatic<EntityIdUtils> entityIdUtils;
    private MockedStatic<TokenGetCustomFeesPrecompile> tokenGetCustomFeesPrecompile;

    @BeforeEach
    void setUp() {
        final PrecompilePricingUtils precompilePricingUtils = new PrecompilePricingUtils(
                assetLoader, () -> feeCalculator, stateView, accessorFactory, feeResourcesLoader);

        entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
        entityIdUtils.when(() -> EntityIdUtils.asTypedEvmAddress(tokenMerkleId)).thenReturn(tokenMerkleAddress);

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

        tokenGetCustomFeesPrecompile = Mockito.mockStatic(TokenGetCustomFeesPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        entityIdUtils.close();
        tokenGetCustomFeesPrecompile.close();
    }

    @Test
    void getTokenCustomFeesWorks() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenCustomFeesWrapper = new TokenGetCustomFeesWrapper<>(tokenMerkleId);
        final var fractionalFee = customFees();
        final Bytes pretendArguments =
                Bytes.concatenate(Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_CUSTOM_FEES)), tokenMerkleAddress);
        tokenGetCustomFeesPrecompile
                .when(() -> decodeTokenGetCustomFees(pretendArguments))
                .thenReturn(tokenCustomFeesWrapper);

        given(wrappedLedgers.infoForTokenCustomFees(tokenMerkleId)).willReturn(Optional.of(fractionalFee));
        given(evmEncoder.encodeTokenGetCustomFees(fractionalFee)).willReturn(successResult);

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(successResult, result);
        // and:
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getTokenCustomFeesMissingTokenIdFails() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var tokenCustomFeesWrapper = new TokenGetCustomFeesWrapper<>(tokenMerkleId);
        final Bytes pretendArguments = Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_CUSTOM_FEES)),
                EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        tokenGetCustomFeesPrecompile
                .when(() -> decodeTokenGetCustomFees(pretendArguments))
                .thenReturn(tokenCustomFeesWrapper);

        givenMinimalContextForInvalidTokenIdCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(invalidTokenIdResult, result);
        // and:
        verify(worldUpdater).manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void decodeGetFungibleTokenCustomFeesInput() {
        final var address = "0x00000000000000000000000000000000000003ee";
        tokenGetCustomFeesPrecompile
                .when(() -> decodeTokenGetCustomFees(GET_FUNGIBLE_TOKEN_CUSTOM_FEES_INPUT))
                .thenCallRealMethod();
        entityIdUtils
                .when(() -> EntityIdUtils.tokenIdFromEvmAddress(
                        Address.fromHexString(address).toArray()))
                .thenCallRealMethod();
        final var decodedInput = decodeTokenGetCustomFees(GET_FUNGIBLE_TOKEN_CUSTOM_FEES_INPUT);

        assertTrue(decodedInput.token().getTokenNum() > 0);
    }

    @Test
    void decodeGetNonFungibleTokenCustomFeesInput() {
        final var address = "0x00000000000000000000000000000000000003f6";
        tokenGetCustomFeesPrecompile
                .when(() -> decodeTokenGetCustomFees(GET_NON_FUNGIBLE_TOKEN_CUSTOM_FEES_INPUT))
                .thenCallRealMethod();
        entityIdUtils
                .when(() -> EntityIdUtils.tokenIdFromEvmAddress(
                        Address.fromHexString(address).toArray()))
                .thenCallRealMethod();
        final var decodedInput = decodeTokenGetCustomFees(GET_NON_FUNGIBLE_TOKEN_CUSTOM_FEES_INPUT);

        assertTrue(decodedInput.token().getTokenNum() > 0);
    }

    private void givenMinimalFrameContext() {
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getRemainingGas()).willReturn(100_000L);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(frame.getSenderAddress()).willReturn(senderAddress);
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
    }

    private void givenMinimalContextForSuccessfulCall(final Bytes pretendArguments) {
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createSuccessfulSyntheticRecord(Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
    }

    private void givenMinimalContextForInvalidTokenIdCall(final Bytes pretendArguments) {
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments)).willReturn(mockSynthBodyBuilder);
        given(creator.createUnsuccessfulSyntheticRecord(ResponseCodeEnum.INVALID_TOKEN_ID))
                .willReturn(mockRecordBuilder);
    }

    private void givenReadOnlyFeeSchedule() {
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any())).willReturn(mockFeeObject);
        given(feeCalculator.estimatedGasPriceInTinybars(HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
    }

    private CustomFee getFractionalFee() {
        final long denominator = 2L;
        final long numerator = 1L;
        final var fraction = Fraction.newBuilder()
                .setNumerator(numerator)
                .setDenominator(denominator)
                .build();
        final long maximumAmount = 400_000_000L;
        final long minimumAmount = 10_000L;
        final var fractionalFee = FractionalFee.newBuilder()
                .setFractionalAmount(fraction)
                .setMinimumAmount(minimumAmount)
                .setMaximumAmount(maximumAmount)
                .build();
        return CustomFee.newBuilder()
                .setFractionalFee(fractionalFee)
                .setFeeCollectorAccountId(payer)
                .build();
    }

    private List<com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee> customFees() {
        com.hedera.node.app.service.evm.store.contracts.precompile.codec.FractionalFee fractionalFee =
                new com.hedera.node.app.service.evm.store.contracts.precompile.codec.FractionalFee(
                        1L, 2L, 10_000L, 400_000_000L, false, EntityIdUtils.asTypedEvmAddress(payer));

        com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee customFee1 =
                new com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee();
        customFee1.setFractionalFee(fractionalFee);

        return List.of(customFee1);
    }
}
