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
import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_EXPIRY_INFO;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.TEST_CONSENSUS_TIME;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.invalidTokenIdResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.senderAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.senderId;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.timestamp;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokenMerkleAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.tokenMerkleId;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.GetTokenExpiryInfoPrecompile.decodeGetTokenExpiryInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.evm.store.contracts.precompile.EvmHTSPrecompiledContract;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmTokenInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenExpiryInfoWrapper;
import com.hedera.node.app.service.mono.config.NetworkInfo;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.expiry.ExpiringCreations;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.GetTokenExpiryInfoPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Collections;
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
class GetTokenExpiryInfoPrecompileTest {

    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame frame;
    @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private EncodingFacade encoder;
    @Mock private EvmEncodingFacade evmEncoder;
    @Mock private SideEffectsTracker sideEffects;
    @Mock private TransactionBody.Builder mockSynthBodyBuilder;
    @Mock private ExpirableTxnRecord.Builder mockRecordBuilder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private HederaStackedWorldStateUpdater worldUpdater;
    @Mock private WorldLedgers wrappedLedgers;
    @Mock private ExpiringCreations creator;
    @Mock private FeeCalculator feeCalculator;
    @Mock private StateView stateView;
    @Mock private UsagePricesProvider resourceCosts;
    @Mock private InfrastructureFactory infrastructureFactory;
    @Mock private AssetsLoader assetLoader;
    @Mock private HbarCentExchange exchange;
    @Mock private FeeObject mockFeeObject;
    @Mock private AccessorFactory accessorFactory;
    @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;
    @Mock private NetworkInfo networkInfo;
    @Mock private EvmHTSPrecompiledContract evmHTSPrecompiledContract;

    public static final Bytes GET_EXPIRY_INFO_FOR_TOKEN_INPUT =
            Bytes.fromHexString(
                    "0xd614cdb800000000000000000000000000000000000000000000000000000000000008c1");
    private HTSPrecompiledContract subject;
    private MockedStatic<EntityIdUtils> entityIdUtils;
    private MockedStatic<GetTokenExpiryInfoPrecompile> getTokenExpiryInfoPrecompile;
    private final EntityId treasury = senderId;

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

        entityIdUtils = Mockito.mockStatic(EntityIdUtils.class);
        entityIdUtils
                .when(() -> EntityIdUtils.asTypedEvmAddress(tokenMerkleId))
                .thenReturn(tokenMerkleAddress);

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

        getTokenExpiryInfoPrecompile = Mockito.mockStatic(GetTokenExpiryInfoPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        entityIdUtils.close();
        getTokenExpiryInfoPrecompile.close();
    }

    @Test
    void getTokenExpiryInfoWorks() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var getTokenExpiryInfoWrapper = new GetTokenExpiryInfoWrapper<>(tokenMerkleId);
        final var evmTokenInfo =
                new EvmTokenInfo(
                        Bytes.fromHexString("0x03").toArray(),
                        1,
                        false,
                        "FT",
                        "NAME",
                        "MEMO",
                        Address.wrap(
                                Bytes.fromHexString("0x00000000000000000000000000000000000005cc")),
                        1L,
                        1000L,
                        0,
                        0L);

        final Bytes pretendArguments =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_EXPIRY_INFO)),
                        EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        getTokenExpiryInfoPrecompile
                .when(() -> decodeGetTokenExpiryInfo(pretendArguments))
                .thenReturn(getTokenExpiryInfoWrapper);

        given(evmEncoder.encodeGetTokenExpiryInfo(any())).willReturn(successResult);
        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));
        given(wrappedLedgers.evmInfoForToken(tokenMerkleId, networkInfo.ledgerId()))
                .willReturn(Optional.of(evmTokenInfo));

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
        verify(worldUpdater)
                .manageInProgressRecord(recordsHistorian, mockRecordBuilder, mockSynthBodyBuilder);
    }

    @Test
    void getTokenExpiryInfoFails() {
        givenMinimalFrameContext();
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        final var getTokenExpiryInfoWrapper = new GetTokenExpiryInfoWrapper<>(tokenMerkleId);
        final Bytes pretendArguments =
                Bytes.concatenate(
                        Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_EXPIRY_INFO)),
                        EntityIdUtils.asTypedEvmAddress(tokenMerkleId));
        getTokenExpiryInfoPrecompile
                .when(() -> decodeGetTokenExpiryInfo(pretendArguments))
                .thenReturn(getTokenExpiryInfoWrapper);

        given(stateView.getNetworkInfo()).willReturn(networkInfo);
        given(networkInfo.ledgerId()).willReturn(ByteString.copyFromUtf8("0xff"));
        given(wrappedLedgers.evmInfoForToken(tokenMerkleId, networkInfo.ledgerId()))
                .willReturn(Optional.empty());

        givenMinimalContextForSuccessfulCall(pretendArguments);
        givenReadOnlyFeeSchedule();

        // when:
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        subject.getPrecompile().getGasRequirement(TEST_CONSENSUS_TIME);
        final var result = subject.computeInternal(frame);

        // then:
        assertEquals(invalidTokenIdResult, result);
    }

    @Test
    void decodeGetExpiryInfoForTokenInput() {
        final var address = "0x00000000000000000000000000000000000008c1";
        getTokenExpiryInfoPrecompile
                .when(() -> decodeGetTokenExpiryInfo(GET_EXPIRY_INFO_FOR_TOKEN_INPUT))
                .thenCallRealMethod();
        entityIdUtils
                .when(
                        () ->
                                EntityIdUtils.tokenIdFromEvmAddress(
                                        Address.fromHexString(address).toArray()))
                .thenCallRealMethod();
        final var decodedInput = decodeGetTokenExpiryInfo(GET_EXPIRY_INFO_FOR_TOKEN_INPUT);

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
        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        given(
                        creator.createSuccessfulSyntheticRecord(
                                Collections.emptyList(), sideEffects, EMPTY_MEMO))
                .willReturn(mockRecordBuilder);
    }

    private void givenReadOnlyFeeSchedule() {
        given(feeCalculator.estimatePayment(any(), any(), any(), any(), any()))
                .willReturn(mockFeeObject);
        given(
                        feeCalculator.estimatedGasPriceInTinybars(
                                HederaFunctionality.ContractCall, timestamp))
                .willReturn(1L);
        given(mockFeeObject.getNodeFee()).willReturn(1L);
        given(mockFeeObject.getNetworkFee()).willReturn(1L);
        given(mockFeeObject.getServiceFee()).willReturn(1L);
    }
}
