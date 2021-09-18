/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hedera.services.txns.contract.process;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.log.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TransactionProcessingResult {

	/** The status of the transaction after being processed. */
	public enum Status {

		/** The transaction was invalid for processing. */
		INVALID,

		/** The transaction was successfully processed. */
		SUCCESSFUL,

		/** The transaction failed to be completely processed. */
		FAILED
	}

	private final Status status;

	private final long estimateGasUsedByTransaction;

	private final long gasRemaining;

	private final List<Log> logs;

	private final Bytes output;

	private final Optional<Bytes> revertReason;

	private final String invalidReason;

	public static TransactionProcessingResult invalid(String invalidReason) {
		return new TransactionProcessingResult(
				Status.INVALID,
				new ArrayList<>(),
				-1L,
				-1L,
				Bytes.EMPTY,
				Optional.empty(),
				invalidReason);
	}

	public static TransactionProcessingResult failed(
			final long gasUsedByTransaction,
			final long gasRemaining,
			final Optional<Bytes> revertReason) {
		return new TransactionProcessingResult(
				Status.FAILED,
				new ArrayList<>(),
				gasUsedByTransaction,
				gasRemaining,
				Bytes.EMPTY,
				revertReason,
				null);
	}

	public static TransactionProcessingResult successful(
			final List<Log> logs,
			final long gasUsedByTransaction,
			final long gasRemaining,
			final Bytes output) {
		return new TransactionProcessingResult(
				Status.SUCCESSFUL,
				logs,
				gasUsedByTransaction,
				gasRemaining,
				output,
				Optional.empty(), null);
	}

	public TransactionProcessingResult(
			final Status status,
			final List<Log> logs,
			final long estimateGasUsedByTransaction,
			final long gasRemaining,
			final Bytes output,
			final Optional<Bytes> revertReason,
			final String invalidReason) {
		this.status = status;
		this.logs = logs;
		this.estimateGasUsedByTransaction = estimateGasUsedByTransaction;
		this.gasRemaining = gasRemaining;
		this.output = output;
		this.revertReason = revertReason;
		this.invalidReason = invalidReason == null ? "" : invalidReason;
	}

	/**
	 * Return the logs produced by the transaction.
	 *
	 * <p>This is only valid when {@code TransactionProcessor#isSuccessful} returns {@code true}.
	 *
	 * @return the logs produced by the transaction
	 */
	public List<Log> getLogs() {
		return logs;
	}

	/**
	 * Returns the gas remaining after the transaction was processed.
	 *
	 * <p>This is only valid when {@code TransactionProcessor#isSuccessful} returns {@code true}.
	 *
	 * @return the gas remaining after the transaction was processed
	 */
	public long getGasRemaining() {
		return gasRemaining;
	}

	/**
	 * Returns the estimate gas used by the transaction Difference between the gas limit and the
	 * remaining gas
	 *
	 * @return the estimate gas used
	 */
	public long getEstimateGasUsedByTransaction() {
		return estimateGasUsedByTransaction;
	}

	/**
	 * Returns the status of the transaction after being processed.
	 *
	 * @return the status of the transaction after being processed
	 */
	public Status getStatus() {
		return status;
	}

	public Bytes getOutput() {
		return output;
	}

	/**
	 * Returns whether or not the transaction was invalid.
	 *
	 * @return {@code true} if the transaction was invalid; otherwise {@code false}
	 */
	public boolean isInvalid() {
		return getStatus() == Status.INVALID;
	}

	/**
	 * Returns whether or not the transaction was successfully processed.
	 *
	 * @return {@code true} if the transaction was successfully processed; otherwise {@code false}
	 */
	public boolean isSuccessful() {
		return getStatus() == Status.SUCCESSFUL;
	}

	/**
	 * Returns the reason why a transaction was reverted (if applicable).
	 *
	 * @return the revert reason.
	 */
	public Optional<Bytes> getRevertReason() {
		return revertReason;
	}

	/**
	 * Returns the reason why a transaction was invalid.
	 *
	 * @return the invalid reason.
	 */
	public String getInvalidReason() {
		return invalidReason;
	}
}
