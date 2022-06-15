package com.hedera.services.store.contracts.precompile;

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

import com.hedera.services.store.contracts.WorldLedgers;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;

public record DirectCallsInfoProvider(PrecompileMessage precompileMessage,
									  WorldLedgers ledgers) implements InfoProvider {
	@Override
	public Wei getValue() {
		return precompileMessage.getValue();
	}

	@Override
	public MessageFrame messageFrame() {
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
