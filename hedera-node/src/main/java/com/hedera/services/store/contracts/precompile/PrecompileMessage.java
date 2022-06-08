package com.hedera.services.store.contracts.precompile;

import com.hedera.services.store.contracts.WorldLedgers;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class PrecompileMessage {

	private WorldLedgers ledgers;
	private Address senderAddress;
	private State state;
	private Bytes htsOutputResult;
	private long gasRequired;

	public PrecompileMessage(WorldLedgers ledgers, Address senderAddress) {
		this.ledgers = ledgers;
		this.senderAddress = senderAddress;
		this.state = State.NOT_STARTED;
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

	public Address getSenderAddress() {
		return senderAddress;
	}

	public Bytes getHtsOutputResult() {
		return htsOutputResult;
	}

	public long getGasRequired() {
		return gasRequired;
	}

	public WorldLedgers getLedgers() {
		return ledgers;
	}

	public byte[] unaliased(final byte[] evmAddress) {
		final var addressOrAlias = Address.wrap(Bytes.wrap(evmAddress));
		if (!addressOrAlias.equals(ledgers.canonicalAddress(addressOrAlias))) {
			return new byte[20];
		}
		return ledgers.aliases().resolveForEvm(addressOrAlias).toArrayUnsafe();
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
