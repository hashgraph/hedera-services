package com.hedera.services.contracts.operation;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.contracts.sources.SoliditySigsVerifier;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.contracts.HederaWorldUpdater;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Utility methods used by Hedera adapted {@link org.hyperledger.besu.evm.operation.Operation}
 */
public final class HederaOperationUtil {

	private HederaOperationUtil() {
		throw new UnsupportedOperationException("Utility Class");
	}

	/**
	 * Returns the expiry to be used for a new contract. Climbs the {@link MessageFrame} and searches for the parent
	 * {@link com.hedera.services.store.contracts.HederaWorldState.WorldStateAccount}. The expiry to be used is
	 * the expiry of the first account found in the stack
	 *
	 * @param frame
	 * 		Current message frame
	 * @return Expiry to be used for new contracts
	 */
	public static long computeExpiryForNewContract(MessageFrame frame) {
		long expiry = 0;
		HederaWorldState.WorldStateAccount hederaAccount;
		Iterator<MessageFrame> framesIterator = frame.getMessageFrameStack().iterator();
		MessageFrame messageFrame;
		while (framesIterator.hasNext()) {
			messageFrame = framesIterator.next();
			/* if this is the initial frame from the deque, check context vars first */
			if (!framesIterator.hasNext()) {
				OptionalLong expiryOptional = messageFrame.getContextVariable("expiry");
				if (expiryOptional.isPresent()) {
					expiry = expiryOptional.getAsLong();
					break;
				}
			}
			/* check if this messageFrame's sender account can be retrieved from state */
			hederaAccount = ((HederaWorldUpdater) messageFrame.getWorldUpdater()).getHederaAccount(
					frame.getSenderAddress());
			if (hederaAccount != null) {
				expiry = hederaAccount.getExpiry();
				break;
			}
		}
		return expiry;
	}

	/**
	 * An extracted address check and execution of extended Hedera Operations.
	 * Halts the execution of the EVM transaction with {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if
	 * the account does not exist, or it is deleted.
	 *
	 * @param frame
	 * 		The current message frame
	 * @param supplierAddressBytes
	 * 		Supplier for the address bytes
	 * @param supplierHaltGasCost
	 * 		Supplier for the gas cost
	 * @param supplierExecution
	 * 		Supplier with the execution
	 * @param addressValidator
	 * 		Address validator predicate
	 * @return The operation result of the execution
	 */
	public static Operation.OperationResult addressCheckExecution(
			MessageFrame frame,
			Supplier<Bytes> supplierAddressBytes,
			Supplier<Gas> supplierHaltGasCost,
			Supplier<Operation.OperationResult> supplierExecution,
			BiPredicate<Address, MessageFrame> addressValidator) {
		try {
			final var address = Words.toAddress(supplierAddressBytes.get());
			if (Boolean.FALSE.equals(addressValidator.test(address, frame))) {
				return new Operation.OperationResult(
						Optional.of(supplierHaltGasCost.get()),
						Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS));
			}

			return supplierExecution.get();
		} catch (final FixedStack.UnderflowException ufe) {
			return new Operation.OperationResult(
					Optional.of(supplierHaltGasCost.get()),
					Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS));
		}
	}

	/**
	 * An extracted address and signature check, including a further execution of {@link HederaCallOperation} and {@link
	 * HederaCallCodeOperation}
	 * Performs an existence check on the {@link Address} to be called
	 * Halts the execution of the EVM transaction with {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if
	 * the account does not exist or it is deleted.
	 * <p>
	 * If the target {@link Address} has {@link com.hedera.services.state.merkle.MerkleAccount#isReceiverSigRequired()}
	 * set to true, verification of the
	 * provided signature is performed. If the signature is not
	 * active, the execution is halted with {@link HederaExceptionalHaltReason#INVALID_SIGNATURE}.
	 *
	 * @param sigsVerifier
	 * 		The signature
	 * @param frame
	 * 		The current message frame
	 * @param address
	 * 		The target address
	 * @param supplierHaltGasCost
	 * 		Supplier for the gas cost
	 * @param supplierExecution
	 * 		Supplier with the execution
	 * @param addressValidator
	 * 		Address validator predicate
	 * @param precompiledContractMap
	 * @return The operation result of the execution
	 */
	public static Operation.OperationResult addressSignatureCheckExecution(
			final SoliditySigsVerifier sigsVerifier,
			final MessageFrame frame,
			final Address address,
			final Supplier<Gas> supplierHaltGasCost,
			final Supplier<Operation.OperationResult> supplierExecution,
			final BiPredicate<Address, MessageFrame> addressValidator,
			final Map<String, PrecompiledContract> precompiledContractMap
	) {
		// The Precompiled contracts verify their signatures themselves
		if (precompiledContractMap.containsKey(address.toShortHexString())) {
			return supplierExecution.get();
		}

		final var account = frame.getWorldUpdater().get(address);
		if (Boolean.FALSE.equals(addressValidator.test(address, frame))) {
			return new Operation.OperationResult(
					Optional.of(supplierHaltGasCost.get()),
					Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS));
		}
		boolean isDelegateCall = !frame.getContractAddress().equals(frame.getRecipientAddress());
		boolean sigReqIsMet;
		// if this is a delegate call activeContract should be the recipient address
		// otherwise it should be the contract address
		if (isDelegateCall) {
			sigReqIsMet = sigsVerifier.hasActiveKeyOrNoReceiverSigReq(account.getAddress(),
					frame.getRecipientAddress(), frame.getContractAddress(), frame.getRecipientAddress());
		} else {
			sigReqIsMet = sigsVerifier.hasActiveKeyOrNoReceiverSigReq(account.getAddress(),
					frame.getRecipientAddress(), frame.getContractAddress(), frame.getContractAddress());
		}
		if (!sigReqIsMet) {
			return new Operation.OperationResult(
					Optional.of(supplierHaltGasCost.get()), Optional.of(HederaExceptionalHaltReason.INVALID_SIGNATURE)
			);
		}

		return supplierExecution.get();
	}
}
