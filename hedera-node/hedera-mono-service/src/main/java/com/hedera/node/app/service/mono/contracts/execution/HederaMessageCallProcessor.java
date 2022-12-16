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
package com.hedera.node.app.service.mono.contracts.execution;

import com.hedera.node.app.service.evm.contracts.execution.HederaEvmMessageCallProcessor;
import com.hedera.node.app.service.mono.contracts.execution.traceability.ContractActionType;
import com.hedera.node.app.service.mono.contracts.execution.traceability.HederaOperationTracer;
import com.hedera.node.app.service.mono.store.contracts.precompile.HTSPrecompiledContract;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.tracing.OperationTracer;

public class HederaMessageCallProcessor extends HederaEvmMessageCallProcessor {
    private static final String INVALID_TRANSFER_MSG = "Transfer of Value to Hedera Precompile";
    public static final Bytes INVALID_TRANSFER =
            Bytes.of(INVALID_TRANSFER_MSG.getBytes(StandardCharsets.UTF_8));

    private final Predicate<Address> isNativePrecompileCheck;

    public HederaMessageCallProcessor(
            final EVM evm,
            final PrecompileContractRegistry precompiles,
            final Map<String, PrecompiledContract> hederaPrecompileList) {
        super(evm, precompiles, hederaPrecompileList);
        isNativePrecompileCheck = addr -> precompiles.get(addr) != null;
    }

    @Override
    public void start(final MessageFrame frame, final OperationTracer operationTracer) {
        super.start(frame, operationTracer);

        // potential precompile execution will be done after super.start(),
        // so trace results here
        final var contractAddress = frame.getContractAddress();
        if (isNativePrecompileCheck.test(contractAddress)
                || hederaPrecompiles.containsKey(contractAddress)) {
            ((HederaOperationTracer) operationTracer)
                    .tracePrecompileResult(
                            frame,
                            hederaPrecompiles.containsKey(contractAddress)
                                    ? ContractActionType.SYSTEM
                                    : ContractActionType.PRECOMPILE);
        }
    }

    @Override
    protected void executeHederaPrecompile(
            final PrecompiledContract contract,
            final MessageFrame frame,
            final OperationTracer operationTracer) {
        if (contract instanceof HTSPrecompiledContract htsPrecompile) {
            final var costedResult = htsPrecompile.computeCosted(frame.getInputData(), frame);
            output = costedResult.getValue();
            gasRequirement = costedResult.getKey();
        }
        super.executeHederaPrecompile(contract, frame, operationTracer);
    }
}
