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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

public class ERC1155ContractInteractions extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ERC1155ContractInteractions.class);
//	private static final String OPERATIONS_PAYER = "payer";
//	private static final String ACCOUNT1 = "acc1";
	private static final String CONTRACT = "GameItems";

	public static void main(String... args) {
		new ERC1155ContractInteractions().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				erc1155()
		);
	}

	private HapiApiSpec erc1155() {
//		final var PAYER_KEY = "payerKey";
//		final var FILE_KEY_LIST = "fileKeyList";

		return defaultHapiSpec("ERC-1155")
				.given(
//						newKeyNamed(PAYER_KEY),
//						newKeyListNamed(FILE_KEY_LIST, List.of(PAYER_KEY)),
//						cryptoCreate(ACCOUNT1),
//						cryptoCreate(OPERATIONS_PAYER).balance(ONE_MILLION_HBARS).key(PAYER_KEY),
						uploadInitCode(CONTRACT)
				)
				.when()
				.then(
						contractCreate(CONTRACT).via("contractCreate").payingWith(DEFAULT_CONTRACT_SENDER),
						getTxnRecord("contractCreate").logged(), // 121618 gas
						getAccountBalance(DEFAULT_CONTRACT_SENDER).logged(), // started with 1M hbars
						getAccountInfo(DEFAULT_CONTRACT_RECEIVER).savingSnapshot(DEFAULT_CONTRACT_RECEIVER + "Info"),
						getAccountInfo(DEFAULT_CONTRACT_SENDER).savingSnapshot(DEFAULT_CONTRACT_SENDER + "Info"),
						withOpContext((spec, log) -> {
							final var accountOneAddress = spec.registry().getAccountInfo(DEFAULT_CONTRACT_RECEIVER + "Info").getContractAccountID();
							final var operationsPayerAddress = spec.registry().getAccountInfo(DEFAULT_CONTRACT_SENDER + "Info").getContractAccountID();

							final var ops = new ArrayList<HapiSpecOperation>();

							/* approve for other accounts */
							final var approveCall = contractCall(CONTRACT, "setApprovalForAll",
									accountOneAddress, true
							)
									.via("acc1ApproveCall")
									.payingWith(DEFAULT_CONTRACT_SENDER)
									.hasKnownStatus(ResponseCodeEnum.SUCCESS);
							ops.add(approveCall);

							/* mint to the contract owner */
							final var mintCall = contractCall(CONTRACT, "mintToken",
									0, 10, operationsPayerAddress
							)
									.via("contractMintCall")
									.payingWith(DEFAULT_CONTRACT_SENDER)
									.hasKnownStatus(ResponseCodeEnum.SUCCESS);
							ops.add(mintCall);

							/* transfer from - account to account */
							final var transferCall = contractCall(CONTRACT, "safeTransferFrom",
									operationsPayerAddress, accountOneAddress,
									0, // token id 
									1, // amount 
									"0x0"
							).via("contractTransferFromCall").payingWith(DEFAULT_CONTRACT_RECEIVER)
									.hasKnownStatus(ResponseCodeEnum.SUCCESS);
							ops.add(transferCall);
							allRunFor(spec, ops);
						})
//						getTxnRecord("contractMintCall").logged(),
//						getTxnRecord("acc1ApproveCall").logged()
//						getTxnRecord("contractTransferFromCall").logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
