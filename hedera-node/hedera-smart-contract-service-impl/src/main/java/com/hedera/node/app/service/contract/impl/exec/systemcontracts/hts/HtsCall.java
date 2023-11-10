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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompiledContract.PrecompileContractResult;

/**
 * Encapsulates a call to the HTS system contract.
 */
public interface HtsCall {
    /**
     * Encapsulates the result of a call to the HTS system contract. There are two elements,
     * <ol>
     *     <li>The "full result" of the call, including both its EVM-standard {@link PrecompileContractResult}
     *     and gas requirement (which is often difficult to compute without executing the call).</li>
     *     <li>Any additional cost <i>beyond</i> the gas requirement.</li>
     * </ol>
     *
     * @param fullResult the full result of the call
     * @param nonGasCost any additional cost beyond the gas requirement
     */
    record PricedResult(HederaSystemContract.FullResult fullResult, long nonGasCost) {
        public static PricedResult gasOnly(HederaSystemContract.FullResult result) {
            return new PricedResult(result, 0L);
        }
    }

    /**
     * Executes the call, returning the {@link PrecompileContractResult}, the gas requirement, and any
     * non-gas cost that must be sent as value with the call.
     *
     * @return the result, the gas requirement, and any non-gas cost
     */
    @NonNull
    PricedResult execute();

    @NonNull
    default PricedResult execute(MessageFrame frame) {
        return execute();
    }
}
