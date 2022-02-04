package com.hedera.services.contracts.execution;

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

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.ContractStateChange;
import com.hederahashgraph.api.proto.java.StorageChange;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Model object holding all the necessary data to build and externalise the result of a single EVM transaction
 */
public class TransactionProcessingResult {

	/**
	 * The status of the transaction after being processed.
	 */
	public enum Status {

		/**
		 * The transaction was successfully processed.
		 */
		SUCCESSFUL,

		/**
		 * The transaction failed to be completely processed.
		 */
		FAILED
	}

	private final Bytes output;
	private final long gasUsed;
	private final long sbhRefund;
	private final long gasPrice;
	private final Status status;
	private final List<Log> logs;
	private final Optional<Address> recipient;
	private final Optional<Bytes> revertReason;
	private final Optional<ExceptionalHaltReason> haltReason;

	private List<ContractID> createdContracts = new ArrayList<>();
	private final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges;

	public static TransactionProcessingResult failed(
			final long gasUsed,
			final long sbhRefund,
			final long gasPrice,
			final Optional<Bytes> revertReason,
			final Optional<ExceptionalHaltReason> haltReason,
			final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges) {
		return new TransactionProcessingResult(
				Status.FAILED,
				new ArrayList<>(),
				gasUsed,
				sbhRefund,
				gasPrice,
				Bytes.EMPTY,
				Optional.empty(),
				revertReason,
				haltReason,
				stateChanges);
	}

	public static TransactionProcessingResult successful(
			final List<Log> logs,
			final long gasUsed,
			final long sbhRefund,
			final long gasPrice,
			final Bytes output,
			final Address recipient,
			final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges) {
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
				stateChanges);
	}

	public TransactionProcessingResult(
			final Status status,
			final List<Log> logs,
			final long gasUsed,
			final long sbhRefund,
			final long gasPrice,
			final Bytes output,
			final Optional<Address> recipient,
			final Optional<Bytes> revertReason,
			final Optional<ExceptionalHaltReason> haltReason,
			final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> stateChanges) {
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
	}

	/**
	 * Adds a list of created contracts to be externalised as part of the
	 * {@link com.hedera.services.state.submerkle.ExpirableTxnRecord}
	 *
	 * @param createdContracts
	 * 		the list of contractIDs created
	 */
	public void setCreatedContracts(List<ContractID> createdContracts) {
		this.createdContracts = createdContracts;
	}

	/**
	 * Returns whether or not the transaction was successfully processed.
	 *
	 * @return {@code true} if the transaction was successfully processed; otherwise {@code false}
	 */
	public boolean isSuccessful() {
		return status == Status.SUCCESSFUL;
	}

	public long getGasPrice() {
		return gasPrice;
	}

	public long getGasUsed() {
		return gasUsed;
	}

	public long getSbhRefund() {
		return sbhRefund;
	}

	/**
	 * Returns the exceptional halt reason
	 *
	 * @return the halt reason
	 */
	public Optional<ExceptionalHaltReason> getHaltReason() {
		return haltReason;
	}

	public Optional<Bytes> getRevertReason() {
		return revertReason;
	}

	/**
	 * Converts the {@link TransactionProcessingResult} into {@link ContractFunctionResult} GRPC model
	 *
	 * @return the {@link ContractFunctionResult} model to externalise
	 */
	public ContractFunctionResult toGrpc() {
		return toBaseGrpc().build();
	}

	public ContractFunctionResult toCreationGrpc(final byte[] newEvmAddress) {
		return toBaseGrpc().setEvmAddress(BytesValue.newBuilder().setValue(ByteString.copyFrom(newEvmAddress))).build();
	}

	private ContractFunctionResult.Builder toBaseGrpc() {
		final var contractResultBuilder = ContractFunctionResult.newBuilder()
				.setGasUsed(gasUsed);
		contractResultBuilder.setContractCallResult(ByteString.copyFrom(output.toArray()));
		recipient.ifPresent(address -> contractResultBuilder.setContractID(
				EntityIdUtils.contractIdFromEvmAddress(address.toArray())));
		// Set Revert reason as error message if present, otherwise set halt reason (if present)
		if (revertReason.isPresent()) {
			contractResultBuilder.setErrorMessage(revertReason.toString());
		} else {
			haltReason.ifPresent(reason -> contractResultBuilder.setErrorMessage(reason.toString()));
		}

		/* Calculate and populate bloom */
		final var bloom = LogsBloomFilter.builder().insertLogs(logs).build();
		contractResultBuilder.setBloom(ByteString.copyFrom(bloom.toArray()));

		/* Populate Logs */
		final ArrayList<ContractLoginfo> logInfo = buildLogInfo();
		contractResultBuilder.addAllLogInfo(logInfo);

		/* Populate Created Contract IDs */
		contractResultBuilder.addAllCreatedContractIDs(createdContracts);

		/* Populate stateChanges */
		stateChanges.forEach((address, states) -> {
			ContractStateChange.Builder contractChanges = ContractStateChange.newBuilder().setContractID(
					EntityIdUtils.contractParsedFromSolidityAddress(address.toArray()));
			states.forEach((slot, changePair) -> {
				StorageChange.Builder stateChange = StorageChange.newBuilder()
						.setSlot(ByteString.copyFrom(slot.toArrayUnsafe()))
						.setValueRead(ByteString.copyFrom(changePair.getLeft().toArrayUnsafe()));
				Bytes changePairRight = changePair.getRight();
				if (changePairRight == null) {
					stateChange.setReadOnly(true);
				} else {
					stateChange.setValueWritten(ByteString.copyFrom(changePairRight.toArrayUnsafe()));
				}
				contractChanges.addStorageChanges(stateChange.build());
			});
			contractResultBuilder.addStateChanges(contractChanges.build());
		});

		return contractResultBuilder.build();
	}

	@NotNull
	private ArrayList<ContractLoginfo> buildLogInfo() {
		final var logInfo = new ArrayList<ContractLoginfo>();
		logs.forEach(log -> {
			var logBuilder = ContractLoginfo.newBuilder()
					.setContractID(EntityIdUtils.contractIdFromEvmAddress(log.getLogger().toArray()))
					.setData(ByteString.copyFrom(log.getData().toArray()))
					.setBloom(ByteString.copyFrom(LogsBloomFilter.builder().insertLog(log).build().toArray()));
			final var topics = new ArrayList<ByteString>();
			log.getTopics().forEach(topic -> topics.add(ByteString.copyFrom(topic.toArray())));
			logBuilder.addAllTopic(topics);
			logInfo.add(logBuilder.build());
		});
		return logInfo;
	}
}
