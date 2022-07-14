package com.hedera.services.contracts.execution;

import com.hedera.services.state.enums.ContractActionType;
import com.hedera.services.state.submerkle.SolidityAction;
import java.util.List;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.tracing.OperationTracer;

//TODO: javadocs
public interface HederaOperationTracer extends OperationTracer {

  void tracePrecompileResult(final MessageFrame frame, final ContractActionType type);

  List<SolidityAction> getFinalizedActions();

  void reset();

}
