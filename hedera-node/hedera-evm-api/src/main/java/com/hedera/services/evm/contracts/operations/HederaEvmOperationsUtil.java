package com.hedera.services.evm.contracts.operations;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.Operation;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public interface HederaEvmOperationsUtil {

	/**
	 * An extracted address check and execution of extended Hedera Operations. Halts the execution
	 * of the EVM transaction with {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if
	 * the account does not exist, or it is deleted.
	 *
	 * @param frame The current message frame
	 * @param supplierAddressBytes Supplier for the address bytes
	 * @param supplierHaltGasCost Supplier for the gas cost
	 * @param supplierExecution Supplier with the execution
	 * @param addressValidator Address validator predicate
	 * @return The operation result of the execution
	 */
	 static Operation.OperationResult addressCheckExecution(
			MessageFrame frame,
			Supplier<Bytes> supplierAddressBytes,
			LongSupplier supplierHaltGasCost,
			Supplier<Operation.OperationResult> supplierExecution,
			BiPredicate<Address, MessageFrame> addressValidator) {
		try {
			final var address = Words.toAddress(supplierAddressBytes.get());
			if (Boolean.FALSE.equals(addressValidator.test(address, frame))) {
				return new Operation.OperationResult(
						OptionalLong.of(supplierHaltGasCost.getAsLong()),
						Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS));
			}

			return supplierExecution.get();
		} catch (final FixedStack.UnderflowException ufe) {
			return new Operation.OperationResult(
					OptionalLong.of(supplierHaltGasCost.getAsLong()),
					Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS));
		}
	}


}
