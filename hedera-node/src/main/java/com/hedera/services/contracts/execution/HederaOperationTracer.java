package com.hedera.services.contracts.execution;

import com.hedera.services.state.enums.ContractActionType;
import java.util.List;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.tracing.OperationTracer;

// Add javadocs
public interface HederaOperationTracer extends OperationTracer {

  void tracePrecompileResult(final MessageFrame frame, final ContractActionType type);

  List<SolidityAction> getFinalizedActions();

  void reset(boolean areActionSidecarsEnabled);

}
