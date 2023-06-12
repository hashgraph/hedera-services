package com.hedera.node.app.service.contract.impl.exec.processors;

import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.util.Map;
import java.util.Optional;

import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.INVALID_VALUE_TRANSFER;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.isDelegateCall;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.transfersValue;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.PRECOMPILE_ERROR;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;

public class CustomMessageCallProcessor extends MessageCallProcessor {
    private final AddressChecks addressChecks;
    private final PrecompileContractRegistry precompiles;
    private final Map<Address, PrecompiledContract> hederaPrecompiles;

    public CustomMessageCallProcessor(
            @NonNull final EVM evm,
            @NonNull final PrecompileContractRegistry precompiles,
            @NonNull final AddressChecks addressChecks,
            @NonNull final Map<Address, PrecompiledContract> hederaPrecompiles) {
        super(evm, precompiles);
        this.precompiles = precompiles;
        this.addressChecks = addressChecks;
        this.hederaPrecompiles = hederaPrecompiles;
    }

    @Override
    public void start(
            @NonNull final MessageFrame frame,
            @NonNull final OperationTracer operationTracer) {
        final var codeAddress = frame.getContractAddress();
        if (hederaPrecompiles.containsKey(codeAddress)) {
            throw new UnsupportedOperationException("Hedera precompiles not yet supported");
        } else {
            if (addressChecks.isSystemAccount(codeAddress)) {
                if (precompiles.get(codeAddress) == null) {
                    doHalt(frame, PRECOMPILE_ERROR, operationTracer);
                } else if (transfersValue(frame)) {
                    doHalt(frame, INVALID_VALUE_TRANSFER, operationTracer);
                }
            } else {
                // Note the call operation would have already failed if this was a static frame
                if (transfersValue(frame)) {
                    if (addressChecks.isPresent(frame.getRecipientAddress(), frame)) {
                        final var proxyWorldUpdater = (ProxyWorldUpdater) frame.getWorldUpdater();
                        final var maybeReasonToHalt = proxyWorldUpdater.tryTransferFromContract(
                                frame.getSenderAddress(),
                                frame.getRecipientAddress(),
                                frame.getValue().toLong(),
                                isDelegateCall(frame));
                        maybeReasonToHalt.ifPresent(reason -> doHalt(frame, reason, operationTracer));
                    } else {
                        throw new AssertionError("Not implemented");
                    }
                }
            }
        }
    }

    private void doHalt(
            @NonNull final MessageFrame frame,
            @NonNull final ExceptionalHaltReason reason,
            @NonNull final OperationTracer operationTracer) {
        frame.setState(EXCEPTIONAL_HALT);
        frame.setExceptionalHaltReason(Optional.of(reason));
        operationTracer.tracePostExecution( frame, new Operation.OperationResult(frame.getRemainingGas(), reason));
    }
}
