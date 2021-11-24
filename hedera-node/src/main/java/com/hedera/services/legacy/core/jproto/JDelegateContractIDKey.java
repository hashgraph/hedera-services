package com.hedera.services.legacy.core.jproto;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.ContractID;

/**
 * Maps to proto Key of type contractID.
 */
public class JDelegateContractIDKey extends JContractIDKey {
	public JDelegateContractIDKey(final ContractID contractID) {
		super(contractID);
	}

	public JDelegateContractIDKey(final long shardNum, final long realmNum, final long contractNum) {
		super(shardNum, realmNum, contractNum);
	}

	@Override
	public JDelegateContractIDKey getDelegateContractIDKey() {
		return this;
	}

	@Override
	public boolean hasDelegateContractID() {
		return true;
	}

	@Override
	public boolean hasContractID() {
		return false;
	}

	@Override
	public JContractIDKey getContractIDKey() {
		return null;
	}

	@Override
	public String toString() {
		return "<JDelegateContractID: " + getShardNum() + "." + getRealmNum() + "." + getContractNum() + ">";
	}
}
