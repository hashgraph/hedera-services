package com.hedera.services.evm.implementation.contracts.execution.traceability;

import java.util.ArrayList;
import java.util.List;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.tracing.OperationTracer;

public class HederaEvmTracer implements OperationTracer {

  public HederaEvmTracer(final boolean areActionSidecarsEnabled) {
  }

  public void init(final MessageFrame initialFrame) {
  }

  @Override
  public void traceExecution(MessageFrame currentFrame, ExecuteOperation executeOperation) {
    executeOperation.execute();
  }

  public List<SolidityAction> getActions() {
    return new ArrayList<SolidityAction>();
  }
}
