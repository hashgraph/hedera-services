package com.hedera.services.store.contracts.precompile;

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

import com.hedera.services.store.contracts.WorldLedgers;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.log.Log;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PrecompileMessage {

	private final WorldLedgers ledgers;
	private final Address senderAddress;
	private State state;
	private Optional<Bytes> revertReason;
	private final List<Log> logs;
	private Bytes htsOutputResult;
	private long gasRequired;
	private final Wei value;
	private final Instant consensusTime;
	private long gasRemaining;
	private final Bytes inputData;
	private final TokenID tokenID;
	private Optional<ExceptionalHaltReason> exceptionalHaltReason;


	public static PrecompileMessage.Builder builder() {
		return new PrecompileMessage.Builder();
	}

	private PrecompileMessage(WorldLedgers ledgers, Address senderAddress, Wei value, Instant consensusTime,
							  Long gasRemaining, Bytes inputData, TokenID tokenID) {
		state = State.NOT_STARTED;
		revertReason = Optional.empty();
		logs = new ArrayList<>();
		this.ledgers = ledgers;
		this.senderAddress = senderAddress;
		this.value = value;
		this.consensusTime = consensusTime;
		this.gasRemaining = gasRemaining;
		this.inputData = inputData;
		this.tokenID = tokenID;
		exceptionalHaltReason = Optional.empty();
	}

	public Wei getValue() {
		return value;
	}

	public Address getSenderAddress() {
		return senderAddress;
	}

	public Bytes getHtsOutputResult() {
		return htsOutputResult;
	}

	public long getGasRequired() {
		return gasRequired;
	}

	public long getConsensusTime() {
		return consensusTime.getEpochSecond();
	}

	public Bytes getInputData() {
		return inputData;
	}

	public TokenID getTokenID() {
		return tokenID;
	}

	public WorldLedgers getLedgers() {
		return ledgers;
	}

	public long getGasRemaining() {
		return gasRemaining;
	}

	public State getState() {
		return state;
	}

	public Optional<Bytes> getRevertReason() {
		return revertReason;
	}

	public List<Log> getLogs() {
		return this.logs;
	}

	public Optional<ExceptionalHaltReason> getExceptionalHaltReason() {
		return exceptionalHaltReason;
	}

	public void setRevertReason(Bytes revertReason) {
		this.revertReason = Optional.ofNullable(revertReason);
	}

	public void setState(State state) {
		this.state = state;
	}

	public void setGasRequired(long gasRequired) {
		this.gasRequired = gasRequired;
	}

	public void setHtsOutputResult(Bytes htsOutputResult) {
		this.htsOutputResult = htsOutputResult;
	}

	public void setExceptionalHaltReason(Optional<ExceptionalHaltReason> exceptionalHaltReason) {
		this.exceptionalHaltReason = exceptionalHaltReason;
	}

	public void addLog(Log log) {
		logs.add(log);
	}

	public void decrementRemainingGas(long amount) {
		this.gasRemaining -= amount;
	}

	public byte[] unaliased(final byte[] evmAddress) {
		final var addressOrAlias = Address.wrap(Bytes.wrap(evmAddress));
		if (!addressOrAlias.equals(ledgers.canonicalAddress(addressOrAlias))) {
			return new byte[20];
		}
		return ledgers.aliases().resolveForEvm(addressOrAlias).toArrayUnsafe();
	}

	public static class Builder {
		private WorldLedgers ledgers;
		private Address senderAddress;
		private Wei value;
		private Instant consensusTime;
		private Long gasRemaining;
		private Bytes inputData;
		private TokenID tokenID;

		private Builder() {
		}

		public Builder setLedgers(WorldLedgers ledgers) {
			this.ledgers = ledgers;
			return this;
		}

		public Builder setSenderAddress(Address senderAddress) {
			this.senderAddress = senderAddress;
			return this;
		}

		public Builder setValue(Wei value) {
			this.value = value;
			return this;
		}

		public Builder setConsensusTime(Instant consensusTime) {
			this.consensusTime = consensusTime;
			return this;
		}

		public Builder setGasRemaining(Long gasRemaining) {
			this.gasRemaining = gasRemaining;
			return this;
		}

		public Builder setInputData(Bytes inputData) {
			this.inputData = inputData;
			return this;
		}

		public Builder setTokenID(TokenID tokenID) {
			this.tokenID = tokenID;
			return this;
		}


		public PrecompileMessage build() {
			return new PrecompileMessage(this.ledgers, this.senderAddress, this.value, this.consensusTime,
					this.gasRemaining, this.inputData, this.tokenID);
		}
	}

	public enum State {
		NOT_STARTED,
		CODE_EXECUTING,
		CODE_SUCCESS,
		CODE_SUSPENDED,
		EXCEPTIONAL_HALT,
		REVERT,
		COMPLETED_FAILED,
		COMPLETED_SUCCESS;

		State() {
		}
	}
}
