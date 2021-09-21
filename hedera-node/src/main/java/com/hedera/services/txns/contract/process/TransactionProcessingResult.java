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

import com.google.protobuf.ByteString;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogsBloomFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TransactionProcessingResult {

	/** The status of the transaction after being processed. */
	public enum Status {

		/** The transaction was successfully processed. */
		SUCCESSFUL,

		/** The transaction failed to be completely processed. */
		FAILED
	}


	private final Status status;

	private final long gasUsed;

	private final List<Log> logs;

	private final Optional<LogsBloomFilter> bloomFilter;

	private final Bytes output;

	private final Optional<Address> recipient;

	private final Optional<Bytes> revertReason;

	private final Optional<ExceptionalHaltReason> haltReason;

	public static TransactionProcessingResult failed(
			final long gasUsed,
			final Optional<Bytes> revertReason,
			final Optional<ExceptionalHaltReason> haltReason) {
		return new TransactionProcessingResult(
				Status.FAILED,
				new ArrayList<>(),
				Optional.empty(),
				gasUsed,
				Bytes.EMPTY,
				Optional.empty(),
				revertReason,
				haltReason);
	}

	public static TransactionProcessingResult successful(
			final List<Log> logs,
			final Optional<LogsBloomFilter> bloom,
			final long gasUsed,
			final Bytes output,
			final Address recipient) {
		return new TransactionProcessingResult(
				Status.SUCCESSFUL,
				logs,
				bloom,
				gasUsed,
				output,
				Optional.of(recipient),
				Optional.empty(),
				Optional.empty());
	}

	public TransactionProcessingResult(
			final Status status,
			final List<Log> logs,
			final Optional<LogsBloomFilter> bloom,
			final long gasUsed,
			final Bytes output,
			final Optional<Address> recipient,
			final Optional<Bytes> revertReason,
			final Optional<ExceptionalHaltReason> haltReason) {
		this.logs = logs;
		this.output = output;
		this.status = status;
		this.gasUsed = gasUsed;
		this.bloomFilter = bloom;
		this.recipient = recipient;
		this.haltReason = haltReason;
		this.revertReason = revertReason;
	}

	/**
	 * Returns whether or not the transaction was successfully processed.
	 *
	 * @return {@code true} if the transaction was successfully processed; otherwise {@code false}
	 */
	public boolean isSuccessful() {
		return status == Status.SUCCESSFUL;
	}

	/**
	 * Converts the {@link TransactionProcessingResult} into {@link ContractFunctionResult} GRPC model
	 * @return the {@link ContractFunctionResult} model to externalise
	 */
	public ContractFunctionResult toGrpc() {
		final var contractResultBuilder = ContractFunctionResult.newBuilder()
				.setGasUsed(gasUsed);
		contractResultBuilder.setContractCallResult(ByteString.copyFrom(output.toArray()));
		recipient.ifPresent(address -> contractResultBuilder.setContractID(EntityIdUtils.contractParsedFromSolidityAddress(address.toArray())));
		bloomFilter.ifPresent(filter -> contractResultBuilder.setBloom(ByteString.copyFrom(filter.toArray())));
		// Set Revert reason as error message if present, otherwise set halt reason (if present)
		if (revertReason.isPresent()) {
			contractResultBuilder.setErrorMessage(revertReason.toString());
		} else {
			haltReason.ifPresent(reason -> contractResultBuilder.setErrorMessage(reason.toString()));
		}

		//TODO populate logs and createdContractIDs
		return contractResultBuilder.build();
	}
}
