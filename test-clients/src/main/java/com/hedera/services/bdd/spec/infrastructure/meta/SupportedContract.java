package com.hedera.services.bdd.spec.infrastructure.meta;

/*-
 * ‌
 * Hedera Services Test Clients
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

import java.math.BigInteger;
import java.util.List;

public enum SupportedContract {
	SIMPLE_STORAGE(ContractResources.SIMPLE_STORAGE_BYTECODE_PATH,
			List.of(
					new ContractCallDetails(
							ContractResources.SIMPLE_STORAGE_SETTER_ABI,
							new Object[] { BigInteger.valueOf(1) })
			),
			List.of(
					new ContractCallDetails(ContractResources.SIMPLE_STORAGE_GETTER_ABI, new Object[] {})
			)
	);

	private final String pathToBytecode;
	private final List<ContractCallDetails> callDetails;
	private final List<ContractCallDetails> localCallDetails;

	SupportedContract(String pathToBytecode,
			List<ContractCallDetails> callDetails,
			List<ContractCallDetails> localCallDetails
	) {
		this.pathToBytecode = pathToBytecode;
		this.callDetails = callDetails;
		this.localCallDetails = localCallDetails;
	}

	public List<ContractCallDetails> getCallDetails() {
		return callDetails;
	}

	public List<ContractCallDetails> getLocalCallDetails() {
		return localCallDetails;
	}

	public String getPathToBytecode() {
		return pathToBytecode;
	}
}
