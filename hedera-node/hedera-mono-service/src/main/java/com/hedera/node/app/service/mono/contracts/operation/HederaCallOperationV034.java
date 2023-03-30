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

import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.contracts.sources.EvmSigsVerifier;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import java.util.function.BiPredicate;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.CallOperation;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;

/**
 * Hedera adapted version of the {@link CallOperation} for version EVM v0.34
 *
 * <p>Performs an existence check on the {@link Address} to be called. If the account does not exist
 * or is deleted and value is being transferred, execution is allowed to attempt a lazy create.
 * However, if account does not exist and value is not being transferred, halts the execution of the
 * EVM transaction with {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS}.
 *
 * <p>If the target {@link Address} exists and has {@link MerkleAccount#isReceiverSigRequired()} set
 * to true, verification of the provided signature is performed. If the signature is not active, the
 * execution is halted with {@link HederaExceptionalHaltReason#INVALID_SIGNATURE}.
 */
public class HederaCallOperationV034 extends CallOperation {
    private final EvmSigsVerifier sigsVerifier;
    private final BiPredicate<Address, MessageFrame> addressValidator;
    private final PrecompileContractRegistry precompileContractRegistry;
    private final GlobalDynamicProperties globalDynamicProperties;

    public HederaCallOperationV034(
            final EvmSigsVerifier sigsVerifier,
            final GasCalculator gasCalculator,
            final BiPredicate<Address, MessageFrame> addressValidator,
            final PrecompileContractRegistry precompileContractRegistry,
            final GlobalDynamicProperties globalDynamicProperties) {
        super(gasCalculator);
        this.sigsVerifier = sigsVerifier;
        this.addressValidator = addressValidator;
        this.precompileContractRegistry = precompileContractRegistry;
        this.globalDynamicProperties = globalDynamicProperties;
    }

    @Override
    public OperationResult execute(final MessageFrame frame, final EVM evm) {
        if (globalDynamicProperties.isImplicitCreationEnabled() && isLazyCreateAttempt(frame)) {
            return super.execute(frame, evm);
        } else {
            return HederaOperationUtil.addressSignatureCheckExecution(
                    sigsVerifier,
                    frame,
                    to(frame),
                    () -> cost(frame),
                    () -> super.execute(frame, evm),
                    addressValidator,
                    precompileContractRegistry);
        }
    }

    private boolean isLazyCreateAttempt(final MessageFrame frame) {
        return !addressValidator.test(to(frame), frame)
                && !((HederaStackedWorldStateUpdater) frame.getWorldUpdater())
                        .aliases()
                        .isMirror(to(frame))
                && value(frame).greaterThan(Wei.ZERO);
    }
}
