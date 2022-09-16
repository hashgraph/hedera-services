package com.hedera.evm.execution.traceability;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class HederaTracer {
  private final List<SolidityAction> allActions;
  private final Deque<SolidityAction> currentActionsStack;
  private final boolean areActionSidecarsEnabled;

  public HederaTracer(final boolean areActionSidecarsEnabled) {
    this.currentActionsStack = new ArrayDeque<>();
    this.allActions = new ArrayList<>();
    this.areActionSidecarsEnabled = areActionSidecarsEnabled;
  }

  public void init(final MessageFrame initialFrame) {
    if (areActionSidecarsEnabled) {
      // since this is the initial frame, call depth is always 0
      trackActionFor(initialFrame, 0);
    }
  }
}
