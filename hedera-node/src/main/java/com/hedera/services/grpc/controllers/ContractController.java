package com.hedera.services.grpc.controllers;

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

public class ContractController {
	/* Transactions */
	public static final String CALL_CONTRACT_METRIC = "contractCallMethod";
	public static final String CREATE_CONTRACT_METRIC = "createContract";
	public static final String UPDATE_CONTRACT_METRIC = "updateContract";
	public static final String DELETE_CONTRACT_METRIC = "deleteContract";
	public static final String CONTRACT_SYSDEL_METRIC = "smartContractSystemDelete";
	public static final String CONTRACT_SYSUNDEL_METRIC = "smartContractSystemUndelete";
	/* Queries */
	public static final String GET_CONTRACT_INFO_METRIC = "getContractInfo";
	public static final String LOCALCALL_CONTRACT_METRIC = "contractCallLocalMethod";
	public static final String GET_CONTRACT_RECORDS_METRIC = "getTxRecordByContractID";
	public static final String GET_CONTRACT_BYTECODE_METRIC = "ContractGetBytecode";
	public static final String GET_SOLIDITY_ADDRESS_INFO_METRIC = "getBySolidityID";
}
