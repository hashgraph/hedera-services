package com.hedera.services.contracts.gascalculator;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.txns.contract.helpers.StorageExpiry;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.hedera.services.contracts.execution.CreateEvmTxProcessor.EXPIRY_ORACLE_CONTEXT_KEY;
import static com.hedera.services.contracts.execution.CreateEvmTxProcessor.SBH_CONTEXT_KEY;
import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

/**
 * Computes the EVM gas cost of using storage in a given {@link MessageFrame} based on the relative
 * Hedera prices of the {@code sbh} and {@code gas} resources in that frame.
 *
 * Recall that,
 * <ul>
 *     <li>Using one {@code sbh} resource means storing one byte in the off-heap Hedera world state
 *     for one hour.</li>
 *     <li>Using one {@code gas} resource means consuming one unit of EVM gas.</li>
 * </ul>
 *
 * Since we want the price of storing data in the off-heap Hedera state to be consistent between the
 * smart contract service and, e.g., the file service, we need to define the EVM gas cost of a storage
 * operation based the <i>relative prices</i> of the {@code sbh} and {@code gas} resources. (That is,
 * if the Hedera {@code sbh} price is high relative to the Hedera {@code gas} price, we need EVM opcodes
 * that use storage to have a high gas cost. On the other hand, if the {@code sbh} price is low relative
 * to the {@code gas} price, EVM operations that use storage should have a low gas cost.)
 *
 * <p><i>Note:</i> We get the Hedera resource prices from the {@link MessageFrame} as follows:
 * <ul>
 *     <li>The Hedera {@code gas} price is exactly {@link MessageFrame#getGasPrice()}---but
 *     in tinybar instead of Wei, of course.</li>
 *     <li>The Hedera {@code sbh} price (also in tinybar) is stored in the bottom stack frame's context
 *     under the {@link com.hedera.services.contracts.execution.CreateEvmTxProcessor#SBH_CONTEXT_KEY}.</li>
 * </ul>
 */
@Singleton
public class StorageGasCalculator {
	private static final int SECONDS_PER_HOUR = 3600;

	@Inject
	public StorageGasCalculator() {
		// Dagger2
	}

	/**
	 * Computes the gas cost of the storage consumed by the {@code CONTRACT_CREATION} represented by
	 * the given frame, using the given {@code GasCalculator}.
	 *
	 * @param frame the creation frame
	 * @param gasCalculator the gas calculator
	 * @return the gas required for the storage and memory usage of this contract creaiton
	 */
	public long creationGasCost(final MessageFrame frame, final GasCalculator gasCalculator) {
		return gasCostOfStorageIn(frame) + memoryExpansionGasCost(frame, gasCalculator);
	}

	public long gasCostOfStorageIn(final MessageFrame frame) {
		final var baseFrame = base(frame);
		final var expectedLifetimeSecs = effStorageLifetime(frame, baseFrame);
		final long sbhPrice = baseFrame.getContextVariable(SBH_CONTEXT_KEY);
		final var storagePrice = (expectedLifetimeSecs * sbhPrice) / SECONDS_PER_HOUR;
		final var gasPrice = frame.getGasPrice().toLong();
		return storagePrice / gasPrice;
	}

	private static long effStorageLifetime(final MessageFrame frame, final MessageFrame baseFrame) {
		final StorageExpiry.Oracle expiryOracle = baseFrame.getContextVariable(EXPIRY_ORACLE_CONTEXT_KEY);
		final var now = frame.getBlockValues().getTimestamp();
		final var expectedLifetimeSecs = expiryOracle.storageExpiryIn(frame) - now;
		return Math.max(0, expectedLifetimeSecs);
	}

	private static long memoryExpansionGasCost(final MessageFrame frame, final GasCalculator gasCalculator) {
		final var initCodeOffset = clampedToLong(frame.getStackItem(1));
		final var initCodeLength = clampedToLong(frame.getStackItem(2));
		return gasCalculator.memoryExpansionGasCost(frame, initCodeOffset, initCodeLength);
	}

	private static MessageFrame base(final MessageFrame frame) {
		return frame.getMessageFrameStack().getLast();
	}
}
