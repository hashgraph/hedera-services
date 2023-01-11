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
package com.hedera.node.app.service.mono.contracts.execution;

import static com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason.FAILURE_DURING_LAZY_ACCOUNT_CREATE;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;

import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.service.evm.contracts.execution.HederaEvmMessageCallProcessor;
import com.hedera.node.app.service.mono.contracts.execution.traceability.ContractActionType;
import com.hedera.node.app.service.mono.contracts.execution.traceability.HederaOperationTracer;
import com.hedera.node.app.service.mono.ledger.BalanceChange;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.store.contracts.precompile.HTSPrecompiledContract;
import com.hedera.node.app.service.mono.store.contracts.precompile.InfrastructureFactory;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.tracing.OperationTracer;

public class HederaMessageCallProcessor extends HederaEvmMessageCallProcessor {
    private static final String INVALID_TRANSFER_MSG = "Transfer of Value to Hedera Precompile";
    public static final Bytes INVALID_TRANSFER =
            Bytes.of(INVALID_TRANSFER_MSG.getBytes(StandardCharsets.UTF_8));

    private final Predicate<Address> isNativePrecompileCheck;
    private InfrastructureFactory infrastructureFactory;

    public HederaMessageCallProcessor(
            final EVM evm,
            final PrecompileContractRegistry precompiles,
            final Map<String, PrecompiledContract> hederaPrecompileList) {
        super(evm, precompiles, hederaPrecompileList);
        isNativePrecompileCheck = addr -> precompiles.get(addr) != null;
    }

    public HederaMessageCallProcessor(
            final EVM evm,
            final PrecompileContractRegistry precompiles,
            final Map<String, PrecompiledContract> hederaPrecompileList,
            final InfrastructureFactory infrastructureFactory) {
        this(evm, precompiles, hederaPrecompileList);
        this.infrastructureFactory = infrastructureFactory;
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

    // can be reached only for a top level call with EVM_VERSION >= 0.32;
    // a top-level call to a non-existing recipient would have been rejected
    // immediately
    // in {@code ContractCallTransitionLogic.doStateTransitionOperation()} if
    // EVM_VERSION < 0.32
    // and nested calls to non-existing recipients are currently rejected in all
    // versions (see {@code HederaOperationUtil})
    @Override
    protected void executeLazyCreate(
            final MessageFrame frame, final OperationTracer operationTracer) {
        final var updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
        final var syntheticBalanceChange = constructSyntheticLazyCreateBalanceChangeFrom(frame);
        final var autoCreationLogic = infrastructureFactory.newAutoCreationLogicScopedTo(updater);

        final var lazyCreateResult =
                autoCreationLogic.create(
                        syntheticBalanceChange,
                        updater.trackingAccounts(),
                        List.of(syntheticBalanceChange));
        if (lazyCreateResult.getLeft() != ResponseCodeEnum.OK) {
            haltFrameAndTraceCreationResult(
                    frame, operationTracer, FAILURE_DURING_LAZY_ACCOUNT_CREATE);
        } else {
            final var creationFeeInTinybars = lazyCreateResult.getRight();
            final var creationFeeInGas = creationFeeInTinybars / frame.getGasPrice().toLong();
            if (frame.getRemainingGas() < creationFeeInGas) {
                // ledgers won't be committed on unsuccessful frame and StackedContractAliases
                // will revert any new aliases
                haltFrameAndTraceCreationResult(frame, operationTracer, INSUFFICIENT_GAS);
            } else {
                frame.decrementRemainingGas(creationFeeInGas);
                // track auto-creation preceding child record
                final var recordSubmissions =
                        infrastructureFactory.newRecordSubmissionsScopedTo(updater);
                autoCreationLogic.submitRecords(recordSubmissions);
                // track the lazy account so it is accessible to the EVM
                updater.trackLazilyCreatedAccount(
                        EntityIdUtils.asTypedEvmAddress(syntheticBalanceChange.accountId()));
            }
        }
    }

    @NonNull
    private BalanceChange constructSyntheticLazyCreateBalanceChangeFrom(final MessageFrame frame) {
        return BalanceChange.changingHbar(
                AccountAmount.newBuilder()
                        .setAccountID(
                                AccountID.newBuilder()
                                        .setAlias(
                                                ByteStringUtils.wrapUnsafely(
                                                        frame.getRecipientAddress()
                                                                .toArrayUnsafe()))
                                        .build())
                        .build(),
                null);
    }

    private void haltFrameAndTraceCreationResult(
            final MessageFrame frame,
            final OperationTracer operationTracer,
            final ExceptionalHaltReason haltReason) {
        frame.decrementRemainingGas(frame.getRemainingGas());
        frame.setState(EXCEPTIONAL_HALT);
        operationTracer.traceAccountCreationResult(frame, Optional.of(haltReason));
    }
}
