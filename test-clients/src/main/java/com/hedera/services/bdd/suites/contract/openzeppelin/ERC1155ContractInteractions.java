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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;

public class ERC1155ContractInteractions extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ERC1155ContractInteractions.class);
	private static final String ACCOUNT1 = "acc1";
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
		return defaultHapiSpec("ERC-1155")
				.given(
						cryptoCreate(ACCOUNT1),
						uploadInitCode(CONTRACT)
				)
				.when()
				.then(
						contractCreate(CONTRACT).via("contractCreate").payingWith(DEFAULT_CONTRACT_SENDER),
						getTxnRecord("contractCreate").logged(),
						getAccountBalance(DEFAULT_CONTRACT_SENDER).logged(),
						getAccountInfo(ACCOUNT1).savingSnapshot(ACCOUNT1 + "Info"),
						getAccountInfo(DEFAULT_CONTRACT_SENDER).savingSnapshot(DEFAULT_CONTRACT_SENDER + "Info"),
						withOpContext((spec, log) -> {
							final var accountOneAddress = asAddress(spec.registry().getAccountInfo(ACCOUNT1 + "Info").getContractAccountID());
							final var senderAddress = asAddress(spec.registry().getAccountInfo(DEFAULT_CONTRACT_SENDER + "Info").getContractAccountID());

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
									BigInteger.valueOf(0), BigInteger.valueOf(10), senderAddress
							)
									.via("contractMintCall")
									.payingWith(DEFAULT_CONTRACT_SENDER)
									.hasKnownStatus(ResponseCodeEnum.SUCCESS);
							ops.add(mintCall);

							/* transfer from - account to account */
							final var transferCall = contractCall(CONTRACT, "safeTransferFrom",
									senderAddress, accountOneAddress,
									BigInteger.valueOf(0), // token id
									BigInteger.valueOf(1), // amount
									"0x0".getBytes()
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
