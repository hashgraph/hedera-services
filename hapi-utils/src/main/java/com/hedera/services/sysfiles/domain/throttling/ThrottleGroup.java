package com.hedera.services.sysfiles.domain.throttling;

/*-
 * ‌
 * Hedera Services API Utilities
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

import com.hederahashgraph.api.proto.java.HederaFunctionality;

import java.util.ArrayList;
import java.util.List;

public class ThrottleGroup {
	int opsPerSec;
	long milliOpsPerSec;
	List<HederaFunctionality> operations = new ArrayList<>();

	public void setMilliOpsPerSec(long milliOpsPerSec) {
		this.milliOpsPerSec = milliOpsPerSec;
	}

	public int getOpsPerSec() {
		return opsPerSec;
	}

	public void setOpsPerSec(int opsPerSec) {
		this.opsPerSec = opsPerSec;
	}

	public List<HederaFunctionality> getOperations() {
		return operations;
	}

	public void setOperations(List<HederaFunctionality> operations) {
		this.operations = operations;
	}

	public static ThrottleGroup fromProto(com.hederahashgraph.api.proto.java.ThrottleGroup group) {
		var pojo = new ThrottleGroup();
		pojo.setMilliOpsPerSec(group.getMilliOpsPerSec());
		pojo.setOpsPerSec((int)(group.getMilliOpsPerSec() / 1_000));
		pojo.operations.addAll(group.getOperationsList());
		return pojo;
	}

	public com.hederahashgraph.api.proto.java.ThrottleGroup toProto() {
		return com.hederahashgraph.api.proto.java.ThrottleGroup.newBuilder()
				.setMilliOpsPerSec(impliedMilliOpsPerSec())
				.addAllOperations(operations)
				.build();
	}

	public long getMilliOpsPerSec() {
		return milliOpsPerSec;
	}

	public long impliedMilliOpsPerSec() {
		return milliOpsPerSec > 0 ? milliOpsPerSec : 1_000 * opsPerSec;
	}
}
