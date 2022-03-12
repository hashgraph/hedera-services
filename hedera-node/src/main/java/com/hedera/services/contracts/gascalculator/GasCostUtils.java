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
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import static com.hedera.services.contracts.execution.CreateEvmTxProcessor.EXPIRY_ORACLE_CONTEXT_KEY;
import static com.hedera.services.contracts.execution.CreateEvmTxProcessor.SBH_CONTEXT_KEY;
import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

public class GasCostUtils {
	private static final int SECONDS_PER_HOUR = 3600;
	public static final long KV_PAIR_BYTES = 64;

	public static Gas storageGasForNewSlot(final MessageFrame frame) {
		throw new AssertionError("Not implemented");
	}

	/**
	 * Returns the gas required for the memory and storage consumed by the {@code CONTRACT_CREATION}
	 * represented by the given frame, using the given {@code GasCalculator}.
	 *
	 * @param frame the creation frame
	 * @param gasCalculator the gas calculator
	 * @return the gas required for the storage and memory usage of this contract creaiton
	 */
	public static Gas storageAndMemoryGasForCreation(final MessageFrame frame, final GasCalculator gasCalculator) {
		return storageCostFor(frame, base(frame)).plus(memoryCostFor(frame, gasCalculator));
	}

	private static Gas storageCostFor(final MessageFrame frame, final MessageFrame baseFrame) {
		final var expectedLifetimeSecs = effStorageLifetime(frame, baseFrame);
		final long sbhTinybars = baseFrame.getContextVariable(SBH_CONTEXT_KEY);
		final var storageCostTinyBars = (expectedLifetimeSecs * sbhTinybars) / SECONDS_PER_HOUR;
		final var gasPrice = frame.getGasPrice().toLong();
		return Gas.of(storageCostTinyBars / gasPrice);
	}

	private static long effStorageLifetime(final MessageFrame frame, final MessageFrame baseFrame) {
		final StorageExpiry.Oracle expiryOracle = baseFrame.getContextVariable(EXPIRY_ORACLE_CONTEXT_KEY);
		final var now = frame.getBlockValues().getTimestamp();
		final var expectedLifetimeSecs = expiryOracle.storageExpiryIn(frame) - now;
		return Math.max(0, expectedLifetimeSecs);
	}

	private static Gas memoryCostFor(final MessageFrame frame, final GasCalculator gasCalculator) {
		final var initCodeOffset = clampedToLong(frame.getStackItem(1));
		final var initCodeLength = clampedToLong(frame.getStackItem(2));
		return gasCalculator.memoryExpansionGasCost(frame, initCodeOffset, initCodeLength);
	}

	private static MessageFrame base(final MessageFrame frame) {
		return frame.getMessageFrameStack().getLast();
	}

	/**
	 * Given a
	 * 
	 * @param numberOfBytes
	 * @param lifetimeSecs
	 * @param sbhPrice
	 * @param gasPrice
	 * @return
	 */
	public static long computeSbhGasEquivalent(
			final long numberOfBytes,
			final long lifetimeSecs,
			final long sbhPrice,
			final long gasPrice
	) {
		final var storageCost = (lifetimeSecs * sbhPrice) / SECONDS_PER_HOUR;
		return Math.round((double) storageCost / (double) gasPrice);
	}

	private GasCostUtils() {
		throw new UnsupportedOperationException("Utility Class");
	}
}
