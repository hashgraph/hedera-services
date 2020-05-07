package com.hedera.services.bdd.suites.utils.validation.domain;

/*-
 * ‌
 * Hedera Services Test Clients
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

public class Scenarios {
	FileScenario file;
	CryptoScenario crypto;
	ContractScenario contract;
	ConsensusScenario consensus;

	public CryptoScenario getCrypto() {
		return crypto;
	}

	public void setCrypto(CryptoScenario crypto) {
		this.crypto = crypto;
	}

	public FileScenario getFile() {
		return file;
	}

	public void setFile(FileScenario file) {
		this.file = file;
	}

	public ContractScenario getContract() {
		return contract;
	}

	public void setContract(ContractScenario contract) {
		this.contract = contract;
	}

	public ConsensusScenario getConsensus() {
		return consensus;
	}

	public void setConsensus(ConsensusScenario consensus) {
		this.consensus = consensus;
	}
}
