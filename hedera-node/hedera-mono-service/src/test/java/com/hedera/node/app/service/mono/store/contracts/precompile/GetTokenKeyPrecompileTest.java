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

import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.fungible;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.invalidTokenIdResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.successResult;
import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.GetTokenKeyPrecompile.decodeGetTokenKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.legacy.core.jproto.JContractIDKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JDelegatableContractIDKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.expiry.ExpiringCreations;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.GetTokenKeyWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.impl.GetTokenKeyPrecompile;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenID;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
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
class GetTokenKeyPrecompileTest {
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame frame;
    @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private EncodingFacade encoder;
    @Mock private EvmEncodingFacade evmEncoder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private ExpiringCreations creator;
    @Mock private FeeCalculator feeCalculator;
    @Mock private StateView stateView;
    @Mock private HederaStackedWorldStateUpdater worldUpdater;
    @Mock private WorldLedgers wrappedLedgers;
    @Mock private UsagePricesProvider resourceCosts;
    @Mock private HbarCentExchange exchange;
    @Mock private InfrastructureFactory infrastructureFactory;
    @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
    @Mock private AssetsLoader assetLoader;
    @Mock private JKey key;
    @Mock private JContractIDKey jContractIDKey;
    @Mock private JDelegatableContractIDKey jDelegatableContractIDKey;
    @Mock private AccessorFactory accessorFactory;

    private static final Bytes GET_TOKEN_KEY_INPUT =
            Bytes.fromHexString(
                    "0x3c4dd32e00000000000000000000000000000000000000000000000000000000000010650000000000000000000000000000000000000000000000000000000000000001");
    private HTSPrecompiledContract subject;
    private MockedStatic<GetTokenKeyPrecompile> getTokenKeyPrecompile;
    private GetTokenKeyWrapper wrapper = new GetTokenKeyWrapper(fungible, 1L);
    private final byte[] ed25519Key =
            new byte[] {
                -98, 65, 115, 52, -46, -22, 107, -28, 89, 98, 64, 96, -29, -17, -36, 27, 69, -102,
                -120, 75, -58, -87, -62, 50, 52, -102, -13, 94, -112, 96, -19, 98
            };

    @BeforeEach
    void setUp() throws IOException {
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
        canonicalPrices.put(
                HederaFunctionality.TokenGetInfo, Map.of(SubType.DEFAULT, BigDecimal.valueOf(0)));
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
                        infrastructureFactory);
        getTokenKeyPrecompile = Mockito.mockStatic(GetTokenKeyPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        getTokenKeyPrecompile.close();
    }

    @Test
    void successfulCallForGetFungibleTokenKey() {
        // given
        final var input =
                Bytes.fromHexString(
                        "0x3c4dd32e00000000000000000000000000000000000000000000000000000000000010650000000000000000000000000000000000000000000000000000000000000001");
        givenMinimalFrameContext();
        givenMinimalContextForCall();
        given(tokens.get(fungible, TokenProperty.ADMIN_KEY)).willReturn(key);
        given(key.getECDSASecp256k1Key()).willReturn(new byte[0]);
        given(key.getEd25519()).willReturn(ed25519Key);
        given(wrappedLedgers.tokens()).willReturn(tokens);
        getTokenKeyPrecompile.when(() -> decodeGetTokenKey(input)).thenReturn(wrapper);
        given(encoder.encodeGetTokenKey(any())).willReturn(successResult);
        given(tokens.exists(any())).willReturn(true);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(successResult, result);
    }

    @Test
    void successfulCallForGetFungibleTokenKeyWithDelegateContractKey() {
        // given
        final var input =
                Bytes.fromHexString(
                        "0x3c4dd32e00000000000000000000000000000000000000000000000000000000000010650000000000000000000000000000000000000000000000000000000000000001");
        givenMinimalFrameContext();
        givenMinimalContextForCall();
        givenJKeyContractAndDelegateContext();
        wrapper = new GetTokenKeyWrapper(fungible, 16L);
        given(tokens.get(fungible, TokenProperty.SUPPLY_KEY)).willReturn(key);
        given(key.getECDSASecp256k1Key()).willReturn(new byte[0]);
        given(key.getEd25519()).willReturn(ed25519Key);
        given(wrappedLedgers.tokens()).willReturn(tokens);
        getTokenKeyPrecompile.when(() -> decodeGetTokenKey(input)).thenReturn(wrapper);
        given(encoder.encodeGetTokenKey(any())).willReturn(successResult);
        given(tokens.exists(any())).willReturn(true);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(successResult, result);
    }

    @Test
    void successfulCallForGetFungibleTokenKeyWithFreezeKey() {
        // given
        final var input =
                Bytes.fromHexString(
                        "0x3c4dd32e00000000000000000000000000000000000000000000000000000000000010650000000000000000000000000000000000000000000000000000000000000001");
        givenMinimalFrameContext();
        givenMinimalContextForCall();
        wrapper = new GetTokenKeyWrapper(fungible, 4L);
        given(tokens.get(fungible, TokenProperty.FREEZE_KEY)).willReturn(key);
        given(key.getECDSASecp256k1Key()).willReturn(new byte[0]);
        given(key.getEd25519()).willReturn(ed25519Key);
        given(wrappedLedgers.tokens()).willReturn(tokens);
        getTokenKeyPrecompile.when(() -> decodeGetTokenKey(input)).thenReturn(wrapper);
        given(encoder.encodeGetTokenKey(any())).willReturn(successResult);
        given(tokens.exists(any())).willReturn(true);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(successResult, result);
    }

    @Test
    void successfulCallForGetFungibleTokenKeyWithWipeKey() {
        // given
        final var input =
                Bytes.fromHexString(
                        "0x3c4dd32e00000000000000000000000000000000000000000000000000000000000010650000000000000000000000000000000000000000000000000000000000000001");
        givenMinimalFrameContext();
        givenMinimalContextForCall();
        wrapper = new GetTokenKeyWrapper(fungible, 8L);
        given(tokens.get(fungible, TokenProperty.WIPE_KEY)).willReturn(key);
        given(key.getECDSASecp256k1Key()).willReturn(new byte[0]);
        given(key.getEd25519()).willReturn(ed25519Key);
        given(wrappedLedgers.tokens()).willReturn(tokens);
        getTokenKeyPrecompile.when(() -> decodeGetTokenKey(input)).thenReturn(wrapper);
        given(encoder.encodeGetTokenKey(any())).willReturn(successResult);
        given(tokens.exists(any())).willReturn(true);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(successResult, result);
    }

    @Test
    void successfulCallForGetFungibleTokenKeyWithPauseKey() {
        // given
        final var input =
                Bytes.fromHexString(
                        "0x3c4dd32e00000000000000000000000000000000000000000000000000000000000010650000000000000000000000000000000000000000000000000000000000000001");
        givenMinimalFrameContext();
        givenMinimalContextForCall();
        wrapper = new GetTokenKeyWrapper(fungible, 64L);
        given(tokens.get(fungible, TokenProperty.PAUSE_KEY)).willReturn(key);
        given(key.getECDSASecp256k1Key()).willReturn(new byte[0]);
        given(key.getEd25519()).willReturn(ed25519Key);
        given(wrappedLedgers.tokens()).willReturn(tokens);
        getTokenKeyPrecompile.when(() -> decodeGetTokenKey(input)).thenReturn(wrapper);
        given(encoder.encodeGetTokenKey(any())).willReturn(successResult);
        given(tokens.exists(any())).willReturn(true);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(successResult, result);
    }

    @Test
    void successfulCallForGetFungibleTokenKeyWithFeeScheduleKey() {
        // given
        final var input =
                Bytes.fromHexString(
                        "0x3c4dd32e00000000000000000000000000000000000000000000000000000000000010650000000000000000000000000000000000000000000000000000000000000001");
        givenMinimalFrameContext();
        givenMinimalContextForCall();
        wrapper = new GetTokenKeyWrapper(fungible, 32L);
        given(tokens.get(fungible, TokenProperty.FEE_SCHEDULE_KEY)).willReturn(key);
        given(key.getECDSASecp256k1Key()).willReturn(new byte[0]);
        given(key.getEd25519()).willReturn(ed25519Key);
        given(wrappedLedgers.tokens()).willReturn(tokens);
        getTokenKeyPrecompile.when(() -> decodeGetTokenKey(input)).thenReturn(wrapper);
        given(encoder.encodeGetTokenKey(any())).willReturn(successResult);
        given(tokens.exists(any())).willReturn(true);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(successResult, result);
    }

    @Test
    void successfulCallForGetFungibleTokenKeyWithKycKey() {
        // given
        final var input =
                Bytes.fromHexString(
                        "0x3c4dd32e00000000000000000000000000000000000000000000000000000000000010650000000000000000000000000000000000000000000000000000000000000001");
        givenMinimalFrameContext();
        givenMinimalContextForCall();
        wrapper = new GetTokenKeyWrapper(fungible, 2L);
        given(tokens.get(fungible, TokenProperty.KYC_KEY)).willReturn(key);
        given(key.getECDSASecp256k1Key()).willReturn(new byte[0]);
        given(key.getEd25519()).willReturn(ed25519Key);
        given(wrappedLedgers.tokens()).willReturn(tokens);
        getTokenKeyPrecompile.when(() -> decodeGetTokenKey(input)).thenReturn(wrapper);
        given(encoder.encodeGetTokenKey(any())).willReturn(successResult);
        given(tokens.exists(any())).willReturn(true);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(successResult, result);
    }

    @Test
    void callForGetFungibleTokenKeyWithInvalidKeyFails() {
        // given
        final var input =
                Bytes.fromHexString(
                        "0x3c4dd32e00000000000000000000000000000000000000000000000000000000000010650000000000000000000000000000000000000000000000000000000000000001");
        givenMinimalFrameContext();
        givenMinimalContextForCall();
        wrapper = new GetTokenKeyWrapper(fungible, 200L);
        given(wrappedLedgers.tokens()).willReturn(tokens);
        getTokenKeyPrecompile.when(() -> decodeGetTokenKey(input)).thenReturn(wrapper);
        given(tokens.exists(any())).willReturn(true);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(HTSTestsUtil.failResult, result);
    }

    @Test
    void getTokenKeyCallForInvalidTokenIds() {
        // given
        final var input =
                Bytes.fromHexString(
                        "0x3c4dd32e00000000000000000000000000000000000000000000000000000000000010650000000000000000000000000000000000000000000000000000000000000001");
        givenMinimalFrameContext();
        givenMinimalContextForCall();
        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(tokens.exists(any())).willReturn(false);
        getTokenKeyPrecompile.when(() -> decodeGetTokenKey(input)).thenReturn(wrapper);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(invalidTokenIdResult, result);
    }

    @Test
    void decodeFungibleTokenGetKey() {
        getTokenKeyPrecompile
                .when(() -> decodeGetTokenKey(GET_TOKEN_KEY_INPUT))
                .thenCallRealMethod();
        final var decodedInput = decodeGetTokenKey(GET_TOKEN_KEY_INPUT);
        assertTrue(decodedInput.tokenID().getTokenNum() > 0);
        assertEquals(1L, decodedInput.keyType());
        assertEquals(TokenProperty.ADMIN_KEY, decodedInput.tokenKeyType());
    }

    private void givenMinimalFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
    }

    private void givenMinimalContextForCall() {
        final Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
    }

    private void givenJKeyContractAndDelegateContext() {
        given(key.hasContractID()).willReturn(true);
        given(key.getContractIDKey()).willReturn(jContractIDKey);
        given(jContractIDKey.getContractID()).willReturn(ContractID.getDefaultInstance());
        given(key.hasDelegatableContractId()).willReturn(true);
        given(key.getDelegatableContractIdKey()).willReturn(jDelegatableContractIDKey);
        given(jDelegatableContractIDKey.getContractID())
                .willReturn(ContractID.getDefaultInstance());
    }
}
