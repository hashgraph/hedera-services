package com.hedera.services.txns.contract.process;

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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.fluent.SimpleBlockHeader;

import java.util.Optional;

public class HederaBlockHeader extends SimpleBlockHeader {

	protected final long gasLimit;
	protected final long timestamp;
	protected final Address coinbase;

	public HederaBlockHeader(Address coinbase, long gasLimit, long timestamp) {
		this.coinbase = coinbase;
		this.gasLimit = gasLimit;
		this.timestamp = timestamp;
	}

	@Override
	public Address getCoinbase() {
		return coinbase;
	}

	@Override
	public Hash getBlockHash() {
		return Hash.EMPTY;
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
	public Optional<Long> getBaseFee() { return Optional.of(0L); }
}
