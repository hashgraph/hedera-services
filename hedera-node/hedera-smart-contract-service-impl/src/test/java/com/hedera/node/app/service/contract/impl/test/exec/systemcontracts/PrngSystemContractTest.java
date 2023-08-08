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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts;

import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INVALID_OPERATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.PrngSystemContract;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.precompile.PrecompiledContract.PrecompileContractResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrngSystemContractTest {

    static final String PSEUDORANDOM_SEED_GENERATOR_SELECTOR = "0xd83bf9a1";

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private MessageFrame messageFrame;

    @Mock
    BlockValues blockValues;

    @Mock
    ContractCallRecordBuilder contractCallRecordBuilder;

    @Mock
    ProxyWorldUpdater proxyWorldUpdater;

    private PrngSystemContract subject;

    @BeforeEach
    void setUp() {
        subject = new PrngSystemContract(gasCalculator);
    }

    @Test
    void computePrecompileStaticSuccessTest() {
        var input = Bytes.fromHexString(PSEUDORANDOM_SEED_GENERATOR_SELECTOR);
        var expectedRandomNumber =
                Bytes.fromHexString("0x1234567890123456789012345678901234567890123456789012345678901234");
        var expectedContractResult = PrecompiledContract.PrecompileContractResult.success(expectedRandomNumber);

        // given:
        givenCommonBlockValues();
        given(messageFrame.isStatic()).willReturn(true);
        given(messageFrame.getWorldUpdater()).willReturn(proxyWorldUpdater);
        given(proxyWorldUpdater.entropy()).willReturn(expectedRandomNumber);

        // when:
        var actual = subject.computePrecompile(input, messageFrame);

        // then:
        assertEqualContractResult(expectedContractResult, actual);
    }

    @Test
    void computePrecompileMutableSuccessTest() {
        var input = Bytes.fromHexString(PSEUDORANDOM_SEED_GENERATOR_SELECTOR);
        var expectedRandomNumber =
                Bytes.fromHexString("0x1234567890123456789012345678901234567890123456789012345678901234");
        var expectedContractResult = PrecompiledContract.PrecompileContractResult.success(expectedRandomNumber);

        // given:
        givenCommon();
        given(messageFrame.isStatic()).willReturn(false);
        given(messageFrame.getWorldUpdater()).willReturn(proxyWorldUpdater);
        given(proxyWorldUpdater.entropy()).willReturn(expectedRandomNumber);

        // when:
        var actual = subject.computePrecompile(input, messageFrame);

        // then:
        assertEqualContractResult(expectedContractResult, actual);
    }

    @Test
    void computePrecompileFailedTest() {
        var input = Bytes.fromHexString(PSEUDORANDOM_SEED_GENERATOR_SELECTOR);
        var expectedContractResult =
                PrecompiledContract.PrecompileContractResult.halt(Bytes.EMPTY, Optional.of(INVALID_OPERATION));

        // given:
        givenCommon();

        // when:
        var actual = subject.computePrecompile(input, messageFrame);

        // then:
        assertEqualContractResult(expectedContractResult, actual);
    }

    public void givenCommonBlockValues() {
        given(messageFrame.getBlockValues()).willReturn(blockValues);
        given(messageFrame.getBlockValues().getTimestamp()).willReturn(0L);
    }

    private void givenCommon() {
        givenCommonBlockValues();
        // TODO: uncomment out the following once there is a way to create child records
        // given(contractCallRecordBuilder.contractID(any())).willReturn(contractCallRecordBuilder);
        // given(contractCallRecordBuilder.status(any())).willReturn(contractCallRecordBuilder);
    }

    private void assertEqualContractResult(PrecompileContractResult expected, PrecompileContractResult actual) {
        assertEquals(expected.getState(), actual.getState());
        assertEquals(expected.getOutput(), actual.getOutput());
        assertEquals(expected.getHaltReason(), actual.getHaltReason());
    }
}
