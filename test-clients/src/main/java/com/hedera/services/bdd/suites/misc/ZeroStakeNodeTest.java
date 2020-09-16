package com.hedera.services.bdd.suites.misc;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;

public class ZeroStakeNodeTest extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ZeroStakeNodeTest.class);

	private static final String LUCKY_NO_LOOKUP_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"pick\"," +
			"\"outputs\":[{\"internalType\":\"uint32\",\"name\":\"\",\"type\":\"uint32\"}],\"payable\":false," +
			"\"stateMutability\":\"view\",\"type\":\"function\"}";


	public static void main(String... args) throws Exception {
		new ZeroStakeNodeTest().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						zeroStakeBehavesAsExpectedJRS(),
				}
		);
	}

	/**
	 * This test has to be run with nodes in spec-defaults set as the full list of ipAddresses and node ids of the
	 * network
	 * with zero stake nodes. Assumes that node 0.0.7 and node 0.0.8 are started with zero stake in a 6 node network.
	 **/
	private HapiApiSpec zeroStakeBehavesAsExpectedJRS() {
		return defaultHapiSpec("zeroStakeBehavesAsExpectedJRS")
				.given(
						cryptoCreate("sponsor"),
						cryptoCreate("beneficiary"),
						fileCreate("bytecode").fromResource("Multipurpose.bin"),
						contractCreate("multi").bytecode("bytecode"),
						contractCreate("impossible")
								.setNode("0.0.7")
								.bytecode("bytecode")
								.hasPrecheck(INVALID_NODE_ACCOUNT),
						contractUpdate("multi")
								.setNode("0.0.8")
								.newMemo("Oops!")
								.hasPrecheck(INVALID_NODE_ACCOUNT),
						contractDelete("multi")
								.setNode("0.0.7")
								.hasPrecheck(INVALID_NODE_ACCOUNT),
						contractCall("multi")
								.setNode("0.0.8")
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
								.setNode("0.0.7")
								.payingWith("sponsor")
								.nodePayment(0L)
								.has(resultWith()
										.resultThruAbi(
												LUCKY_NO_LOOKUP_ABI,
												isLiteralResult(new Object[] { BigInteger.valueOf(42) }))),
						getContractInfo("multi")
								.setNode("0.0.7")
								.payingWith("sponsor")
								.nodePayment(0L)
								.logged(),
						getContractBytecode("multi")
								.setNode("0.0.8")
								.payingWith("sponsor")
								.nodePayment(0L)
								.logged(),
						getContractRecords("multi")
								.setNode("0.0.8")
								.payingWith("sponsor")
								.nodePayment(0L)
								.logged(),
						getAccountInfo("beneficiary")
								.setNode("0.0.7")
								.payingWith("sponsor")
								.nodePayment(0L)
								.logged(),
						getFileInfo("bytecode")
								.setNode("0.0.8")
								.payingWith("sponsor")
								.nodePayment(0L)
								.logged(),
						getAccountBalance("sponsor")
								.setNode("0.0.7")
								.hasTinyBars(changeFromSnapshot("sponsorBefore", -1L)),
						getAccountBalance("beneficiary")
								.setNode("0.0.8")
								.hasTinyBars(changeFromSnapshot("beneficiaryBefore", +1L))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
