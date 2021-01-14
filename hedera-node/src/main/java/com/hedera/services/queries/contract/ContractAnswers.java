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
	private final GetBySolidityIdAnswer getBySolidityIdAnswer;
	private final ContractCallLocalAnswer contractCallLocal;
	private final GetContractRecordsAnswer getContractRecords;

	public ContractAnswers(
			GetBytecodeAnswer getBytecode,
			GetContractInfoAnswer getContractInfo,
			GetBySolidityIdAnswer getBySolidityIdAnswer,
			GetContractRecordsAnswer getContractRecords,
			ContractCallLocalAnswer contractCallLocal
	) {
		this.getBytecode = getBytecode;
		this.getContractRecords = getContractRecords;
		this.getContractInfo = getContractInfo;
		this.getBySolidityIdAnswer = getBySolidityIdAnswer;
		this.contractCallLocal = contractCallLocal;
	}

	public GetContractInfoAnswer getContractInfo() {
		return getContractInfo;
	}

	public GetBytecodeAnswer getBytecode() {
		return getBytecode;
	}

	public GetContractRecordsAnswer getContractRecords() {
		return getContractRecords;
	}

	public ContractCallLocalAnswer contractCallLocal() {
		return contractCallLocal;
	}

	public GetBySolidityIdAnswer getBySolidityId() {
		return getBySolidityIdAnswer;
	}
}
