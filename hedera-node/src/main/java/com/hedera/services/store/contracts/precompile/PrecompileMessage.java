package com.hedera.services.store.contracts.precompile;

import com.hedera.services.store.contracts.WorldLedgers;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;

public class PrecompileMessage {

	private WorldLedgers ledgers;
	private Address senderAddress;
	private State state;
	private Bytes htsOutputResult;
	private long gasRequired;
	private Wei value;
	private long consensusTime;
	private long gasAvailable;
	private Bytes inputData;

	public static PrecompileMessage.Builder builder() {
		return new PrecompileMessage.Builder();
	}

	private PrecompileMessage(WorldLedgers ledgers, Address senderAddress, Wei value, long consensusTime,
							  long gasAvailable, Bytes inputData) {
		this.state = State.NOT_STARTED;
		this.ledgers = ledgers;
		this.senderAddress = senderAddress;
		this.value = value;
		this.consensusTime = consensusTime;
		this.gasAvailable = gasAvailable;
		this.inputData = inputData;
	}

	public State getState() {
		return state;
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
		return consensusTime;
	}

	public Bytes getInputData() {
		return inputData;
	}

	public WorldLedgers getLedgers() {
		return ledgers;
	}

	public long getRemainingGas() {
		return gasAvailable;
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
		private State state;
		private Wei value;
		private long consensusTime;
		private long gasAvailable;
		private Bytes inputData;

		public Builder() {
		}

		public Builder setLedgers(WorldLedgers ledgers) {
			this.ledgers = ledgers;
			return this;
		}

		public Builder setSenderAddress(Address senderAddress) {
			this.senderAddress = senderAddress;
			return this;
		}

		public Builder setState(State state) {
			this.state = state;
			return this;
		}

		public Builder setValue(Wei value) {
			this.value = value;
			return this;
		}

		public Builder setConsensusTime(long consensusTime) {
			this.consensusTime = consensusTime;
			return this;
		}

		public Builder setGasAvailable(long gasAvailable) {
			this.gasAvailable = gasAvailable;
			return this;
		}

		public Builder setInputData(Bytes inputData) {
			this.inputData = inputData;
			return this;
		}

		public PrecompileMessage build() {
			return new PrecompileMessage(this.ledgers, this.senderAddress, this.value, this.consensusTime,
					this.gasAvailable, this.inputData);
		}
	}

	public static enum State {
		NOT_STARTED,
		CODE_EXECUTING,
		CODE_SUCCESS,
		CODE_SUSPENDED,
		EXCEPTIONAL_HALT,
		REVERT,
		COMPLETED_FAILED,
		COMPLETED_SUCCESS;

		private State() {
		}
	}
}
