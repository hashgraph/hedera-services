/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.defaultKycStatusWrapper;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.GetTokenDefaultKycStatus.decodeTokenDefaultKycStatus;
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
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.expiry.ExpiringCreations;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.GetTokenDefaultKycStatus;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.io.IOException;
import java.util.Optional;
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
class GetTokenDefaultKycStatusTest {
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame frame;
    @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private EncodingFacade encoder;
    @Mock private EvmEncodingFacade evmEncoder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private ExpiringCreations creator;
    @Mock private SideEffectsTracker sideEffects;
    @Mock private FeeCalculator feeCalculator;
    @Mock private StateView stateView;
    @Mock private HederaStackedWorldStateUpdater worldUpdater;
    @Mock private WorldLedgers wrappedLedgers;
    @Mock private UsagePricesProvider resourceCosts;
    @Mock private HbarCentExchange exchange;
    @Mock private TransactionBody.Builder mockSynthBodyBuilder;
    @Mock private InfrastructureFactory infrastructureFactory;
    @Mock private AccessorFactory accessorFactory;

    @Mock private AssetsLoader assetLoader;
    public static final Bytes GET_TOKEN_DEFAULT_KYC_STATUS_INPUT =
            Bytes.fromHexString(
                    "0x335e04c10000000000000000000000000000000000000000000000000000000000000404");

    private HTSPrecompiledContract subject;
    private MockedStatic<GetTokenDefaultKycStatus> getTokenDefaultKycStatus;

    @BeforeEach
    void setUp() throws IOException {
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
        getTokenDefaultKycStatus = Mockito.mockStatic(GetTokenDefaultKycStatus.class);
    }

    @AfterEach
    void closeMocks() {
        getTokenDefaultKycStatus.close();
    }

    @Test
    void getTokenDefaultKycStatus() {
        final var output =
                "0x000000000000000000000000000000000000000000000000000000000000"
                        + "00160000000000000000000000000000000000000000000000000000000000000001";

        final var successOutput =
                Bytes.fromHexString(
                        "0x000000000000000000000000000000000000000000000000000000000000001600000000000"
                            + "00000000000000000000000000000000000000000000000000001");

        givenMinimalFrameContext();
        givenLedgers();
        givenMinimalContextForSuccessfulCall();
        final Bytes pretendArguments =
                Bytes.of(Integers.toBytes(ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS));

        given(syntheticTxnFactory.createTransactionCall(1L, pretendArguments))
                .willReturn(mockSynthBodyBuilder);
        getTokenDefaultKycStatus
                .when(() -> decodeTokenDefaultKycStatus(any()))
                .thenReturn(defaultKycStatusWrapper);
        given(evmEncoder.encodeGetTokenDefaultKycStatus(true)).willReturn(successResult);
        given(infrastructureFactory.newSideEffects()).willReturn(sideEffects);
        given(wrappedLedgers.defaultKycStatus((any()))).willReturn(Boolean.TRUE);
        given(evmEncoder.encodeGetTokenDefaultKycStatus(true))
                .willReturn(Bytes.fromHexString(output));
        given(frame.getValue()).willReturn(Wei.ZERO);

        // when
        subject.prepareFields(frame);
        subject.prepareComputation(pretendArguments, a -> a);
        final var result = subject.computeInternal(frame);

        // then
        assertEquals(successOutput, result);
    }

    @Test
    void decodeGetTokenDefaultKycStatusInput() {
        getTokenDefaultKycStatus
                .when(() -> decodeTokenDefaultKycStatus(GET_TOKEN_DEFAULT_KYC_STATUS_INPUT))
                .thenCallRealMethod();
        final var decodedInput = decodeTokenDefaultKycStatus(GET_TOKEN_DEFAULT_KYC_STATUS_INPUT);

        assertTrue(decodedInput.token().getTokenNum() > 0);
    }

    private void givenMinimalContextForSuccessfulCall() {
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
    }

    private void givenMinimalFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
    }

    private void givenLedgers() {
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
    }
}
