package com.hedera.services.contracts.execution;

import com.hedera.services.state.enums.ContractActionType;
import com.hedera.services.state.submerkle.SolidityAction;
import java.util.ArrayList;
import java.util.List;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * A fake implementation of HederaOperationTracer for unit tests.
 * This is needed since EvmProcessor's tests need a tracer that
 * executes the operations in traceExecution(frame, operation), otherwise
 * the tests hang.
 */
public class FakeHederaTracer implements HederaOperationTracer {

  private final List<SolidityAction> actions = new ArrayList<>();
  private boolean hasBeenReset;

  @Override
  public void traceExecution(MessageFrame frame, ExecuteOperation executeOperation) {
    executeOperation.execute();
  }

  @Override
  public void reset(boolean isActionTracingEnabled) {
    hasBeenReset = true;
  }

  public boolean hasBeenReset() {
    return hasBeenReset;
  }

  @Override
  public List<SolidityAction> getFinalizedActions() {
    return actions;
  }

  public void addAction(final SolidityAction action) {
    this.actions.add(action);
  }

  @Override
  public void tracePrecompileResult(MessageFrame frame, ContractActionType type) {}
}
