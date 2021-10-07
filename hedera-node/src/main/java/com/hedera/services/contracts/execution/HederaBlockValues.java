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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.evm.frame.BlockValues;

import java.util.Optional;

public class HederaBlockValues implements BlockValues {

	protected final long gasLimit;
	protected final long timestamp;

	public HederaBlockValues(long gasLimit, long timestamp) {
		this.gasLimit = gasLimit;
		this.timestamp = timestamp;
	}

	@Override
	public long getGasLimit() {
		return gasLimit;
	}

	@Override
	public long getTimestamp() {
		return timestamp;
	}

	@Override
	public Optional<Long> getBaseFee() {
		return Optional.of(0L);
	}

	@Override
	public Bytes getDifficultyBytes() {
		return UInt256.ZERO;
	}

	@Override
	public long getNumber() {
		return timestamp;
	}
}
