package com.hedera.services.bdd.suites.contract.openzeppelin;

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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;

public class ERC1155ContractInteractions extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ERC1155ContractInteractions.class);
	private static final String CONTRACT_NAME = "ERC-1155";
	private static final String OPERATIONS_PAYER = "payer";
	private static final String ACCOUNT1 = "acc1";
	private static final String CONTRACT_FILE_NAME = "contractFileName";
	public static void main(String... args) {
		new ERC1155ContractInteractions().runSuiteSync();
	}
	
	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				erc1155()
		);
	}

	private HapiApiSpec erc1155() {
		final var PAYER_KEY = "payerKey";
		final var FILE_KEY_LIST = "fileKeyList";
		return defaultHapiSpec(CONTRACT_NAME)
				.given(
						newKeyNamed(PAYER_KEY),
						newKeyListNamed(FILE_KEY_LIST, List.of(PAYER_KEY)),
						cryptoCreate(ACCOUNT1),
						cryptoCreate(OPERATIONS_PAYER).balance(ONE_MILLION_HBARS).key(PAYER_KEY),
						fileCreate(CONTRACT_FILE_NAME).payingWith(OPERATIONS_PAYER).key(FILE_KEY_LIST),
						updateLargeFile(OPERATIONS_PAYER, CONTRACT_FILE_NAME, extractByteCode(ContractResources.ERC_1155_BYTECODE_PATH))
				)
				.when()
				.then(
						contractCreate(CONTRACT_NAME).bytecode(CONTRACT_FILE_NAME).via("contractCreate").payingWith(OPERATIONS_PAYER),
						getTxnRecord("contractCreate").logged(), // 121618 gas
						getAccountBalance(OPERATIONS_PAYER).logged(), // started with 1M hbars
						getAccountInfo(ACCOUNT1).savingSnapshot(ACCOUNT1 + "Info"),
						getAccountInfo(OPERATIONS_PAYER).savingSnapshot(OPERATIONS_PAYER + "Info"),
						withOpContext((spec, log) -> {
							var accountOneAddress = spec.registry().getAccountInfo(ACCOUNT1 + "Info").getContractAccountID();
							var operationsPayerAddress = spec.registry().getAccountInfo(OPERATIONS_PAYER + "Info").getContractAccountID();
							var ops = new ArrayList<HapiSpecOperation>();

							/* approve for other accounts */
							var approveCall = contractCall(CONTRACT_NAME, ContractResources.ERC_1155_ABI_APPROVE, accountOneAddress, true)
									.via("acc1ApproveCall").payingWith(OPERATIONS_PAYER) 
									.hasKnownStatus(ResponseCodeEnum.SUCCESS);
							ops.add(approveCall);
							
							/* mint to the contract owner */
							var mintCall = contractCall(CONTRACT_NAME, ContractResources.ERC_1155_ABI_MINT, 0, 10, operationsPayerAddress)
									.via("contractMintCall")
									.payingWith(OPERATIONS_PAYER)
									.hasKnownStatus(ResponseCodeEnum.SUCCESS);
							ops.add(mintCall);

							/* transfer from - account to account */
							var transferCall = contractCall(
									CONTRACT_NAME,
									ContractResources.ERC_1155_ABI_SAFE_TRANSFER_FROM,
									operationsPayerAddress,
									accountOneAddress,
									0, // token id 
									1, // amount 
									"0x0"
							).via("contractTransferFromCall").payingWith(ACCOUNT1)
									.hasKnownStatus(ResponseCodeEnum.SUCCESS);
							ops.add(transferCall);
							allRunFor(spec, ops);
						}),
						getTxnRecord("contractMintCall").logged(),
						getTxnRecord("acc1ApproveCall").logged(),
						getTxnRecord("contractTransferFromCall").logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
