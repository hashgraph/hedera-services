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

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;

public record EVMInfoProvider(MessageFrame messageFrame) implements InfoProvider {

	@Override
	public Wei getValue() {
		return messageFrame.getValue();
	}

	@Override
	public long getRemainingGas() {
		return messageFrame.getRemainingGas();
	}

	@Override
	public boolean isDirectTokenCall() {
		return false;
	}

	@Override
	public long getTimestamp() {
		return messageFrame.getBlockValues().getTimestamp();
	}

	@Override
	public Address getSenderAddress() {
		return messageFrame.getSenderAddress();
	}

	@Override
	public Bytes getInputData() {
		return messageFrame.getInputData();
	}

	@Override
	public void setState(MessageFrame.State state) {
		messageFrame.setState(state);
	}

	@Override
	public void setRevertReason(Bytes revertReason) {
		messageFrame.setRevertReason(revertReason);
	}

	@Override
	public void addLog(Log log) {
		messageFrame.addLog(log);
	}


}
