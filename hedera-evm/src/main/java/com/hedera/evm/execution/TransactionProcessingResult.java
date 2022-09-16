package com.hedera.evm.execution;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.log.Log;

public class TransactionProcessingResult {
  /** The status of the transaction after being processed. */
  public enum Status {
    /** The transaction was successfully processed. */
    SUCCESSFUL,

    /** The transaction failed to be completely processed. */
    FAILED
  }

  public static TransactionProcessingResult failed(
      final long gasUsed,
      final long sbhRefund,
      final long gasPrice,
      final Optional<Bytes> revertReason,
      final Optional<ExceptionalHaltReason> haltReason,
      final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges,
      final List<SolidityAction> actions) {
    return new TransactionProcessingResult(
        Status.FAILED,
        Collections.emptyList(),
        gasUsed,
        sbhRefund,
        gasPrice,
        Bytes.EMPTY,
        Optional.empty(),
        revertReason,
        haltReason,
        stateChanges,
        actions);
  }

  public static TransactionProcessingResult successful(
      final List<Log> logs,
      final long gasUsed,
      final long sbhRefund,
      final long gasPrice,
      final Bytes output,
      final Address recipient,
      final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges,
      final List<SolidityAction> actions) {
    return new TransactionProcessingResult(
        Status.SUCCESSFUL,
        logs,
        gasUsed,
        sbhRefund,
        gasPrice,
        output,
        Optional.of(recipient),
        Optional.empty(),
        Optional.empty(),
        stateChanges,
        actions);
  }

  private TransactionProcessingResult(
      final Status status,
      final List<Log> logs,
      final long gasUsed,
      final long sbhRefund,
      final long gasPrice,
      final Bytes output,
      final Optional<Address> recipient,
      final Optional<Bytes> revertReason,
      final Optional<ExceptionalHaltReason> haltReason,
      final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges,
      final List<SolidityAction> actions) {
    this.logs = logs;
    this.output = output;
    this.status = status;
    this.gasUsed = gasUsed;
    this.sbhRefund = sbhRefund;
    this.gasPrice = gasPrice;
    this.recipient = recipient;
    this.haltReason = haltReason;
    this.revertReason = revertReason;
    this.stateChanges = stateChanges;
    this.actions = actions;
  }

}
