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

package com.hedera.node.app.service.contract.impl.exec.processors;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.contractvalidation.ContractValidationRule;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;

public class CustomContractCreationProcessor extends ContractCreationProcessor {
    public CustomContractCreationProcessor(
            @NonNull final EVM evm,
            @NonNull final GasCalculator gasCalculator,
            final boolean requireCodeDepositToSucceed,
            @NonNull final List<ContractValidationRule> contractValidationRules,
            final long initialContractNonce) {
        super(gasCalculator, evm, requireCodeDepositToSucceed, contractValidationRules, initialContractNonce);
    }
}
