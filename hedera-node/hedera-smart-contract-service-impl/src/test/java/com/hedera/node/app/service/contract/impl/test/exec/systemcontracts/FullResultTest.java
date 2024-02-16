/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FullResultTest {
    @Mock
    private ContractCallRecordBuilder recordBuilder;

    @Test
    void canRecordInsufficientGasWithBuilder() {
        when(recordBuilder.contractFunctionResult()).thenReturn(ContractFunctionResult.DEFAULT);
        final var result = new PrecompiledContract.PrecompileContractResult(
                Bytes.EMPTY, true, MessageFrame.State.CODE_SUCCESS, Optional.empty());
        final var subject = new FullResult(result, 123L, recordBuilder);
        subject.recordInsufficientGas();
        verify(recordBuilder).status(ResponseCodeEnum.INSUFFICIENT_GAS);
        verify(recordBuilder)
                .contractCallResult(ContractFunctionResult.newBuilder()
                        .contractCallResult(
                                tuweniToPbjBytes(Bytes.wrap(UInt256.valueOf(INSUFFICIENT_GAS.protoOrdinal()))))
                        .build());
    }

    @Test
    void insufficientGasIfNoopIfResultHasNoBuilder() {
        final var result = new PrecompiledContract.PrecompileContractResult(
                Bytes.EMPTY, true, MessageFrame.State.CODE_SUCCESS, Optional.empty());
        final var subject = new FullResult(result, 123L, null);
        assertDoesNotThrow(subject::recordInsufficientGas);
    }
}
