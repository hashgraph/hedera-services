package com.hedera.services.store.contracts.precompile;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;

public interface InfoProvider {

	Wei getValue();

	MessageFrame getMessageFrame();
	long getRemainingGas();

	boolean isDirectTokenCall();

	long getTimestamp();

	Bytes getInputData();

	void setState(MessageFrame.State state);

	void setRevertReason(Bytes revertReason);

	boolean validateKey(final Address target,
						final HTSPrecompiledContract.ContractActivationTest activationTest);


}
