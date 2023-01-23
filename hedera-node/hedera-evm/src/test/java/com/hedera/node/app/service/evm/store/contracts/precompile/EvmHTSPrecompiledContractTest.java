/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.evm.store.contracts.precompile;

import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_ERC_NAME;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_IS_FROZEN;
import static com.hedera.node.app.service.evm.store.contracts.precompile.AbiConstants.ABI_ID_REDIRECT_FOR_TOKEN;
import static com.hedera.node.app.service.evm.store.contracts.precompile.proxy.RedirectViewExecutorTest.fungibleTokenAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.esaulpaugh.headlong.util.Integers;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.RedirectViewExecutor;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewExecutor;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.ViewGasCalculator;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmHTSPrecompiledContractTest {

    @Mock private MessageFrame messageFrame;
    @Mock private EvmHTSPrecompiledContract evmHTSPrecompiledContract;
    @Mock private EvmInfrastructureFactory evmInfrastructureFactory;
    @Mock private RedirectViewExecutor redirectViewExecutor;
    @Mock private ViewExecutor viewExecutor;
    @Mock private ViewGasCalculator viewGasCalculator;
    @Mock private TokenAccessor tokenAccessor;

    @BeforeEach
    void setUp() {
        evmHTSPrecompiledContract = new EvmHTSPrecompiledContract(evmInfrastructureFactory);
    }

    @Test
    void computeCostedFrameNotStatic() {
        final Bytes input = prerequisitesForRedirect(ABI_ID_ERC_NAME);
        given(messageFrame.isStatic()).willReturn(false);
        final var result =
                evmHTSPrecompiledContract.computeCosted(
                        input, messageFrame, viewGasCalculator, tokenAccessor);

        assertEquals(Bytes.EMPTY, result.getValue());
        assertEquals("EvmHTS", evmHTSPrecompiledContract.getName());
        assertEquals(0, evmHTSPrecompiledContract.gasRequirement(input));
    }

    @Test
    void computeCostedWrongInput() {
        final Bytes input = prerequisites(0x3c4dd32c);
        given(messageFrame.isStatic()).willReturn(true);
        final var result =
                evmHTSPrecompiledContract.computeCosted(
                        input, messageFrame, viewGasCalculator, tokenAccessor);

        assertEquals(Bytes.EMPTY, result.getValue());
        assertEquals("EvmHTS", evmHTSPrecompiledContract.getName());
        assertEquals(0, evmHTSPrecompiledContract.gasRequirement(input));
    }

    @Test
    void computeCostedWorksForRedirectView() {
        final Bytes input = prerequisitesForRedirect(ABI_ID_ERC_NAME);
        given(messageFrame.isStatic()).willReturn(true);

        given(evmInfrastructureFactory.newRedirectExecutor(any(), any(), any(), any()))
                .willReturn(redirectViewExecutor);
        given(redirectViewExecutor.computeCosted()).willReturn(Pair.of(1L, Bytes.of(1)));

        final var result =
                evmHTSPrecompiledContract.computeCosted(
                        input, messageFrame, viewGasCalculator, tokenAccessor);

        verify(messageFrame, never()).setRevertReason(any());
        assertEquals(Bytes.of(1), result.getValue());
    }

    @Test
    void computeCostedWorksForView() {
        final Bytes input = prerequisites(ABI_ID_IS_FROZEN);
        given(messageFrame.isStatic()).willReturn(true);

        given(evmInfrastructureFactory.newViewExecutor(any(), any(), any(), any()))
                .willReturn(viewExecutor);
        given(viewExecutor.computeCosted()).willReturn(Pair.of(1L, Bytes.of(1)));

        final var result =
                evmHTSPrecompiledContract.computeCosted(
                        input, messageFrame, viewGasCalculator, tokenAccessor);

        verify(messageFrame, never()).setRevertReason(any());
        assertEquals(Bytes.of(1), result.getValue());
    }

    Bytes prerequisites(final int descriptor) {
        return Bytes.concatenate(Bytes.of(Integers.toBytes(descriptor)), fungibleTokenAddress);
    }

    Bytes prerequisitesForRedirect(final int descriptor) {
        return Bytes.concatenate(
                Bytes.of(Integers.toBytes(ABI_ID_REDIRECT_FOR_TOKEN)),
                fungibleTokenAddress,
                Bytes.of(Integers.toBytes(descriptor)));
    }
}
