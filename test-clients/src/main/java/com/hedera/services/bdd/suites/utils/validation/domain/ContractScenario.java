package com.hedera.services.bdd.suites.utils.validation.domain;

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

public class ContractScenario {
	public static String NOVEL_CONTRACT_NAME = "novelContract";
	public static String PERSISTENT_CONTRACT_NAME = "persistentContract";
	public static String DEFAULT_CONTRACT_RESOURCE = "contract/contracts/Multipurpose/Multipurpose.sol";
	public static String DEFAULT_BYTECODE_RESOURCE = "contract/contracts/Multipurpose/Multipurpose.bin";
	public static int DEFAULT_LUCKY_NUMBER = 42;

	PersistentContract persistent;

	public PersistentContract getPersistent() {
		return persistent;
	}

	public void setPersistent(PersistentContract persistent) {
		this.persistent = persistent;
	}
}
