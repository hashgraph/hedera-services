/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.contracts.operation;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.CallCodeOperation;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;

/**
 * Hedera adapted version of the {@link CallCodeOperation}.
 *
 * <p>Performs an existence check on the {@link Address} to be called Halts the execution of the EVM
 * transaction with {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if the account does
 * not exist or it is deleted.
 *
 * <p>If the target {@link Address} has {@link MerkleAccount#isReceiverSigRequired()} set to true,
 * verification of the provided signature is performed. If the signature is not active, the
 * execution is halted with {@link HederaExceptionalHaltReason#INVALID_SIGNATURE}.
 */
public class HederaCallCodeOperation extends CallCodeOperation {
    private final EvmSigsVerifier sigsVerifier;
    private final BiPredicate<Address, MessageFrame> addressValidator;
    private final Predicate<Address> precompileDetector;

    public HederaCallCodeOperation(
            final EvmSigsVerifier sigsVerifier,
            final GasCalculator gasCalculator,
            final BiPredicate<Address, MessageFrame> addressValidator,
            final Predicate<Address> precompileDetector) {
        super(gasCalculator);
        this.sigsVerifier = sigsVerifier;
        this.addressValidator = addressValidator;
        this.precompileDetector = precompileDetector;
    }

    @Override
    public OperationResult execute(final MessageFrame frame, final EVM evm) {
        return HederaOperationUtil.addressSignatureCheckExecution(
                sigsVerifier,
                frame,
                to(frame),
                () -> cost(frame),
                () -> super.execute(frame, evm),
                addressValidator,
                precompileDetector,
                () -> isStatic(frame));
    }
}
