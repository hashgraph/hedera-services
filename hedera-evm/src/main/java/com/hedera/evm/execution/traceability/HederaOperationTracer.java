package com.hedera.evm.execution.traceability;

import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/**
 * Hedera-specific EVM operation tracer interface with added functionality for contract actions
 * traceability.
 */
public interface HederaOperationTracer extends OperationTracer {

  /**
   * Perform initialization logic before EVM execution begins.
   *
   * @param initialFrame the initial frame associated with this EVM execution
   */
  void init(final MessageFrame initialFrame);

  /**
   * Trace the result from a precompile execution. Must be called after the result has been
   * reflected in the associated message frame.
   *
   * @param frame the frame associated with this precompile call
   * @param type the type of precompile called; expected values are {@code PRECOMPILE} and {@code
   *     SYSTEM}
   */
  void tracePrecompileResult(final MessageFrame frame, final ContractActionType type);
}
