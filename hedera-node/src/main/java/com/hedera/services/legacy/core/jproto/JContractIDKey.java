package com.hedera.services.legacy.core.jproto;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
 *
 * @author hua Created on 2019-01-07
 */
public class JContractIDKey extends JKey {

	private static final long serialVersionUID = 1L;
	private long shardNum = 0; // the shard number (nonnegative)
	private long realmNum = 0; // the realm number (nonnegative)
	private long contractNum = 0; // a nonnegative number unique within its realm

	public JContractIDKey(ContractID contractID) {
		super();
		this.shardNum = contractID.getShardNum();
		this.realmNum = contractID.getRealmNum();
		this.contractNum = contractID.getContractNum();
	}

	public JContractIDKey getContractIDKey() {
		return this;
	}

	public boolean hasContractID() {
		return true;
	}

	public ContractID getContractID() {
		return ContractID.newBuilder().setShardNum(shardNum).setRealmNum(realmNum)
				.setContractNum(contractNum).build();
	}

	public JContractIDKey(long shardNum, long realmNum, long contractNum) {
		super();
		this.shardNum = shardNum;
		this.realmNum = realmNum;
		this.contractNum = contractNum;
	}

	public long getShardNum() {
		return shardNum;
	}

	public long getRealmNum() {
		return realmNum;
	}

	public long getContractNum() {
		return contractNum;
	}

	@Override
	public String toString() {
		return "<JContractID: " + shardNum + "." + realmNum + "." + contractNum + ">";
	}

	@Override
	public boolean isEmpty() {
		return (0 == contractNum);
	}

	@Override
	public boolean isValid() {
		return !isEmpty();
	}
}
