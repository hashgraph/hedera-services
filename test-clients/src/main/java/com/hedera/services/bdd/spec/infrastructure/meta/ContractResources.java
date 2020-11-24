package com.hedera.services.bdd.spec.infrastructure.meta;

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

public class ContractResources {
	public static final String SIMPLE_STORAGE_BYTECODE = "simpleStorage";
	public static final String PAYABLE_CONTRACT_BYTECODE = "PayReceivable";
	public static final String DELEGATING_CONTRACT_BYTECODE = "CreateTrivial";

	public static final String CREATE_CHILD_ABI = "{\"constant\":false,\"inputs\":[],\"name\":\"create\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String GET_CHILD_RESULT_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"getIndirect\",\"outputs\":[{\"name\":\"value\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String GET_CHILD_ADDRESS_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"getAddress\",\"outputs\":[{\"name\":\"retval\",\"type\":\"address\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}";
	public static final String SEND_FUNDS_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"receiver\",\"type\":\"address\"},{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"sendFunds\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"}";
	public static final String DEPOSIT_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"amount\",\"type\":\"uint256\"}],\"name\":\"deposit\",\"outputs\":[],\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";

	public static final String getPathTo(String bytecode) {
		return String.format("src/main/resource/contract/bytecodes/%s.bin", bytecode);
	}
}
