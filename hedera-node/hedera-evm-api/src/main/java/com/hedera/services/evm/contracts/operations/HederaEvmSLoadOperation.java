package com.hedera.services.evm.contracts.operations;

import com.hedera.services.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.operation.AbstractOperation;

import java.util.Optional;
import java.util.OptionalLong;

public class HederaEvmSLoadOperation extends AbstractOperation {
	protected final OptionalLong warmCost;
	protected final OptionalLong coldCost;

	protected final OperationResult warmSuccess;
	protected final OperationResult coldSuccess;

	protected HederaEvmSLoadOperation(final GasCalculator gasCalculator) {
		super(0x54, "SLOAD", 1, 1, 1, gasCalculator);
		final long baseCost = gasCalculator.getSloadOperationGasCost();
		warmCost = OptionalLong.of(baseCost + gasCalculator.getWarmStorageReadCost());
		coldCost = OptionalLong.of(baseCost + gasCalculator.getColdSloadCost());

		warmSuccess = new OperationResult(warmCost, Optional.empty());
		coldSuccess = new OperationResult(coldCost, Optional.empty());
	}

	@Override
	public OperationResult execute(MessageFrame frame, EVM evm) {
		try {
			final var addressOrAlias = frame.getRecipientAddress();
			final var worldUpdater = (AbstractLedgerEvmWorldUpdater) frame.getWorldUpdater();
			final Account account = worldUpdater.get(addressOrAlias);
			final Address address = account.getAddress();
			final Bytes32 key = UInt256.fromBytes(frame.popStackItem());
			final boolean slotIsWarm = frame.warmUpStorage(address, key);
			final OptionalLong optionalCost = slotIsWarm ? warmCost : coldCost;
			if (frame.getRemainingGas() < optionalCost.orElse(0L)) {
				return new OperationResult(
						optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
			} else {
				UInt256 storageValue = account.getStorageValue(UInt256.fromBytes(key));

				frame.pushStackItem(storageValue);
				return slotIsWarm ? warmSuccess : coldSuccess;
			}
		} catch (final FixedStack.UnderflowException ufe) {
			return new OperationResult(
					warmCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS));
		} catch (final FixedStack.OverflowException ofe) {
			return new OperationResult(
					warmCost, Optional.of(ExceptionalHaltReason.TOO_MANY_STACK_ITEMS));
		}
	}
}
