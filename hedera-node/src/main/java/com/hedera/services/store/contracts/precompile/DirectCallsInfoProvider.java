package com.hedera.services.store.contracts.precompile;

import com.hedera.services.store.contracts.WorldLedgers;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class DirectCallsInfoProvider implements InfoProvider{
	private PrecompileMessage precompileMessage;
	private WorldLedgers ledgers;

	public DirectCallsInfoProvider(PrecompileMessage precompileMessage, WorldLedgers ledgers) {
		this.precompileMessage = precompileMessage;
		this.ledgers = ledgers;
	}
	@Override
	public Wei getValue() {
		return precompileMessage.getValue();
	}

	@Override
	public MessageFrame getMessageFrame() {
		return null;
	}

	@Override
	public long getRemainingGas() {
		return precompileMessage.getGasRemaining();
	}
	@Override
	public boolean isDirectTokenCall() {
		return true;
	}
	@Override
	public long getTimestamp() {
		return precompileMessage.getConsensusTime();
	}
	@Override
	public Bytes getInputData() {
		return precompileMessage.getInputData();
	}
	@Override
	public void setState(MessageFrame.State state) {
		precompileMessage.setState(PrecompileMessage.State.valueOf(state.name()));
	}
	@Override
	public void setRevertReason(Bytes revertReason) {
		precompileMessage.setRevertReason(revertReason);
	}
	@Override
	public boolean validateKey(final Address target, final HTSPrecompiledContract.ContractActivationTest activationTest) {
		return activationTest.apply(false, target, precompileMessage.getSenderAddress(), ledgers);
	}
}
