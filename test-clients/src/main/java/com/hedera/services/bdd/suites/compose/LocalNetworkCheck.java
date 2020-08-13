package com.hedera.services.bdd.suites.compose;

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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractRecords;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.suites.utils.validation.domain.Network.SCENARIO_PAYER_NAME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;

public class LocalNetworkCheck extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(LocalNetworkCheck.class);

	private static String LUCKY_NO_LOOKUP_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"pick\"," +
			"\"outputs\":[{\"internalType\":\"uint32\",\"name\":\"\",\"type\":\"uint32\"}],\"payable\":false," +
			"\"stateMutability\":\"view\",\"type\":\"function\"}";

	public static void main(String... args) {
		new LocalNetworkCheck().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
//						balancesChangeOnTransfer(),
						zeroStakeBehavesAsExpected(),
				}
		);
	}

	/* Assumes that node 0.0.4 has been started with zero stake. */
	private HapiApiSpec zeroStakeBehavesAsExpected() {
		return customHapiSpec("zeroStakeBehavesAsExpected")
				.withProperties(Map.of(
						"nodes", "127.0.0.1:50213:0.0.3,127.0.0.1:50214:0.0.4,127.0.0.1:50215:0.0.5"
				)).given(
						cryptoCreate("sponsor"),
						cryptoCreate("beneficiary"),
						fileCreate("bytecode").fromResource("Multipurpose.bin"),
						contractCreate("multi").bytecode("bytecode"),
						contractCreate("impossible")
								.setNode("0.0.4")
								.bytecode("bytecode")
								.hasPrecheck(INVALID_NODE_ACCOUNT),
						contractUpdate("multi")
								.setNode("0.0.4")
								.newMemo("Oops!")
								.hasPrecheck(INVALID_NODE_ACCOUNT),
						contractDelete("multi")
								.setNode("0.0.4")
								.hasPrecheck(INVALID_NODE_ACCOUNT),
						contractCall("multi")
								.setNode("0.0.4")
								.sending(1L)
								.hasPrecheck(INVALID_NODE_ACCOUNT)
				).when(
						balanceSnapshot("sponsorBefore", "sponsor"),
						balanceSnapshot("beneficiaryBefore", "beneficiary"),
						cryptoTransfer(tinyBarsFromTo("sponsor", "beneficiary", 1L))
								.payingWith(GENESIS)
								.memo("Hello World!")
								.setNode("0.0.5"),
						getContractInfo("multi")
								.setNode("0.0.5")
								.payingWith("sponsor")
								.nodePayment(0L)
								.hasAnswerOnlyPrecheck(INSUFFICIENT_TX_FEE)
				).then(
						contractCallLocal("multi", LUCKY_NO_LOOKUP_ABI)
								.setNode("0.0.4")
								.payingWith("sponsor")
								.nodePayment(0L)
								.has(resultWith()
										.resultThruAbi(
												LUCKY_NO_LOOKUP_ABI,
												isLiteralResult(new Object[] { BigInteger.valueOf(42) }))),
						getContractInfo("multi")
								.setNode("0.0.4")
								.payingWith("sponsor")
								.nodePayment(0L)
								.logged(),
						getContractBytecode("multi")
								.setNode("0.0.4")
								.payingWith("sponsor")
								.nodePayment(0L)
								.logged(),
						getContractRecords("multi")
								.setNode("0.0.4")
								.payingWith("sponsor")
								.nodePayment(0L)
								.logged(),
						getAccountBalance("sponsor")
								.setNode("0.0.4")
								.hasTinyBars(changeFromSnapshot("sponsorBefore", -1L)),
						getAccountBalance("beneficiary")
								.setNode("0.0.4")
								.hasTinyBars(changeFromSnapshot("beneficiaryBefore", +1L))
				);
	}

	private HapiApiSpec balancesChangeOnTransfer() {
		return customHapiSpec("BalancesChangeOnTransfer")
				.withProperties(Map.of(
						"nodes", "127.0.0.1:50213:0.0.3,127.0.0.1:50214:0.0.4,127.0.0.1:50215:0.0.5"
				)).given(
						cryptoCreate("sponsor").setNode("0.0.3"),
						cryptoCreate("beneficiary").setNode("0.0.4"),
						balanceSnapshot("sponsorBefore", "sponsor"),
						balanceSnapshot("beneficiaryBefore", "beneficiary")
				).when(
						cryptoTransfer(tinyBarsFromTo("sponsor", "beneficiary", 1L))
								.payingWith(GENESIS)
								.memo("Hello World!")
								.setNode("0.0.5")
				).then(
						getAccountBalance("sponsor")
								.hasTinyBars(changeFromSnapshot("sponsorBefore", -1L)),
						getAccountBalance("beneficiary")
								.hasTinyBars(changeFromSnapshot("beneficiaryBefore", +1L))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
