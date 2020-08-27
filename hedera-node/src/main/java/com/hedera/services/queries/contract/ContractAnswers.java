package com.hedera.services.queries.contract;

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

public class ContractAnswers {
	private final GetBytecodeAnswer getBytecode;
	private final GetContractInfoAnswer getContractInfo;

	public ContractAnswers(
			GetBytecodeAnswer getBytecode,
			GetContractInfoAnswer getContractInfo
	) {
		this.getBytecode = getBytecode;
		this.getContractInfo = getContractInfo;
	}

	public GetContractInfoAnswer getContractInfo() {
		return getContractInfo;
	}

	public GetBytecodeAnswer getBytecode() {
		return getBytecode;
	}
}
