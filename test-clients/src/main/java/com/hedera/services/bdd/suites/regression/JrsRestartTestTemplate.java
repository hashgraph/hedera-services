package com.hedera.services.bdd.suites.regression;

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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnFactory.bannerWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.expectedEntitiesExist;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.scheduleOpsEnablement;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;

/**
 * This restart test uses the named entities under src/main/resource/jrs/entities/JrsRestartTestTemplate
 *
 * FILES
 * - bytecode (EVM constructor bytecode for multipurpose contract)
 *
 * ACCOUNTS
 * - sender (balance = 1tℏ)
 * - receiver (balance = 99tℏ, receiverSigRequired = true)
 * - treasury (treasury account for token jrsToken)
 * - autoRenew (auto-renew account for topic ofGeneralInterest)
 *
 * TOPICS
 * - ofGeneralInterest (has submit key)
 *
 * TOKENS
 * - jrsToken
 *
 * SCHEDULES
 * - pendingXfer (1tℏ from sender to receiver; has sender sig only)
 *
 * CONTRACTS
 * - multipurpose
 */
public class JrsRestartTestTemplate extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(JrsRestartTestTemplate.class);

	private static final String ENTITIES_DIR = "src/main/resource/jrs/entities/JrsRestartTestTemplate";

	private static final String SENDER = "sender";
	private static final String MULTIPURPOSE = "Multipurpose";
	private static final String RECEIVER = "receiver";
	private static final String TREASURY = "treasury";
	private static final String JRS_TOKEN = "jrsToken";
	private static final String PENDING_XFER = "pendingXfer";
	private static final String BYTECODE_FILE = "bytecode";
	private static final String BYTECODE_FILE_MEMO = "EVM bytecode for Multipurpose contract";

	public static void main(String... args) {
		var hero = new JrsRestartTestTemplate();

		hero.runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						enableHSS(),
						jrsRestartTemplate(),
				}
		);
	}

	private HapiApiSpec enableHSS() {
		return defaultHapiSpec("enableHSS")
				.given(
						// Directly puting this request in the customHapiSpec before expectedEntitiesExist() doesn't work
						scheduleOpsEnablement()
				).when().then(
				);
	}

	private HapiApiSpec jrsRestartTemplate() {
		return customHapiSpec("JrsRestartTemplate")
				.withProperties(Map.of(
						"persistentEntities.dir.path", ENTITIES_DIR
				)).given(
						expectedEntitiesExist()
				).when().then(
						withOpContext((spec, opLog) -> {
							boolean isPostRestart = spec.setup().ciPropertiesMap().getBoolean("postRestart");
							if (isPostRestart) {
								opLog.info("\n\n" + bannerWith("POST-RESTART VALIDATION PHASE"));
								allRunFor(spec, postRestartValidation());
							} else {
								opLog.info("\n\n" + bannerWith("PRE-RESTART SETUP PHASE"));
								allRunFor(spec, preRestartSetup());
							}
						})
				);
	}

	private HapiSpecOperation[] preRestartSetup() {
		return new HapiSpecOperation[] {
				assertionsHold((spec, opLog) -> {
					/* For this template, nothing to setup beyond the entity auto-creation. */
				})
		};
	}

	private HapiSpecOperation[] postRestartValidation() {
		return List.of(
				postRestartAccountValidation(),
				postRestartScheduleValidation(),
				postRestartTopicValidation(),
				postRestartTokenValidation(),
				postRestartFileValidation(),
				postRestartContractValidation()
		)
				.stream()
				.flatMap(Arrays::stream)
				.toArray(HapiSpecOperation[]::new);
	}

	private HapiSpecOperation[] postRestartContractValidation() {
		return new HapiSpecOperation[] {
				contractCall(MULTIPURPOSE, "believeIn", 256),
				contractCallLocal(MULTIPURPOSE, "pick")
						.has(resultWith()
						.resultThruAbi(
								getABIFor(FUNCTION, "pick", MULTIPURPOSE),
								isLiteralResult(new Object[] { BigInteger.valueOf(256) }))),
		};
	}

	private HapiSpecOperation[] postRestartAccountValidation() {
		return new HapiSpecOperation[] {
				getAccountBalance(RECEIVER).hasTinyBars(99L)
		};
	}

	private HapiSpecOperation[] postRestartFileValidation() {
		return new HapiSpecOperation[] {
				getFileInfo(BYTECODE_FILE).hasMemo(BYTECODE_FILE_MEMO)
		};
	}

	private HapiSpecOperation[] postRestartTopicValidation() {
		return new HapiSpecOperation[] {
				submitMessageTo("ofGeneralInterest")
						.message("Brave new world, isn't it?")
		};
	}

	private HapiSpecOperation[] postRestartTokenValidation() {
		return new HapiSpecOperation[] {
				tokenAssociate(SENDER, JRS_TOKEN),
				cryptoTransfer(moving(1, JRS_TOKEN).between(TREASURY, SENDER))
						.hasKnownStatus(ACCOUNT_FROZEN_FOR_TOKEN),
				tokenUnfreeze(JRS_TOKEN, SENDER),
				cryptoTransfer(moving(1, JRS_TOKEN).between(TREASURY, SENDER))
						.hasKnownStatus(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN),
				grantTokenKyc(JRS_TOKEN, SENDER),
				cryptoTransfer(moving(1, JRS_TOKEN).between(TREASURY, SENDER))
		};
	}

	private HapiSpecOperation[] postRestartScheduleValidation() {
		return new HapiSpecOperation[] {
				getAccountInfo(SENDER).has(accountWith()
						.balance(1L)),
				getAccountInfo(RECEIVER).has(accountWith()
						.balance(99L)),

				scheduleSign(PENDING_XFER)
						.alsoSigningWith(RECEIVER),

				getAccountInfo(RECEIVER).has(accountWith()
						.balance(100L)),
				getAccountInfo(SENDER).has(accountWith()
						.balance(0L))
		};
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
