package com.hedera.services.contracts.operation;

import static com.hedera.services.contracts.operation.HederaOperationUtil.cacheExistingValue;
import static com.hedera.services.stream.proto.SidecarType.CONTRACT_STATE_CHANGE;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.ILLEGAL_STATE_CHANGE;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.stream.proto.SidecarType;
import java.util.Optional;
import java.util.OptionalLong;
import javax.inject.Inject;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.AbstractOperation;

/**
 * Hedera adapted version of the {@link org.hyperledger.besu.evm.operation.SStoreOperation} to
 * support traceability (if enabled).
 */
public class HederaSStoreOperation extends AbstractOperation {
  static final OperationResult ILLEGAL_STATE_CHANGE_RESULT =
      new OperationResult(OptionalLong.empty(), Optional.of(ILLEGAL_STATE_CHANGE));

  private final long minumumGasRemaining;
  private final GlobalDynamicProperties dynamicProperties;
  private final OperationResult insufficientMinimumGasRemainingResult;

  @Inject
  public HederaSStoreOperation(
      final long minimumGasRemaining,
      final GasCalculator gasCalculator,
      final GlobalDynamicProperties dynamicProperties) {
    super(0x55, "SSTORE", 2, 0, 1, gasCalculator);
    this.dynamicProperties = dynamicProperties;
    this.minumumGasRemaining = minimumGasRemaining;
    insufficientMinimumGasRemainingResult =
        new OperationResult(OptionalLong.of(minumumGasRemaining), Optional.of(INSUFFICIENT_GAS));
  }

  @Override
  public OperationResult execute(final MessageFrame frame, final EVM evm) {
    final var key = UInt256.fromBytes(frame.popStackItem());
    final var value = UInt256.fromBytes(frame.popStackItem());
    final var address = frame.getRecipientAddress();
    final var account = frame.getWorldUpdater().getAccount(address).getMutable();
    if (account == null) {
      return ILLEGAL_STATE_CHANGE_RESULT;
    }

    final var slotIsWarm = frame.warmUpStorage(address, key);
    final var calculator = gasCalculator();
    final var calcGasCost =
        calculator.calculateStorageCost(account, key, value)
            + (slotIsWarm ? 0L : calculator.getColdSloadCost());
    final var gasCostWrapper = OptionalLong.of(calcGasCost);

    final var remainingGas = frame.getRemainingGas();
    if (frame.isStatic()) {
      return new OperationResult(gasCostWrapper, Optional.of(ILLEGAL_STATE_CHANGE));
    } else if (remainingGas < calcGasCost) {
      return new OperationResult(gasCostWrapper, Optional.of(INSUFFICIENT_GAS));
    } else if (remainingGas < minumumGasRemaining) {
      return insufficientMinimumGasRemainingResult;
    } else {
      if (dynamicProperties.enabledSidecars().contains(CONTRACT_STATE_CHANGE)) {
        cacheExistingValue(frame, address, key, account.getStorageValue(key));
      }
      frame.incrementGasRefund(calculator.calculateStorageRefundAmount(account, key, value));
      account.setStorageValue(key, value);
      frame.storageWasUpdated(key, value);
      return new OperationResult(gasCostWrapper, Optional.empty());
    }
  }

  @VisibleForTesting
  OperationResult getInsufficientMinimumGasRemainingResult() {
    return insufficientMinimumGasRemainingResult;
  }
}
