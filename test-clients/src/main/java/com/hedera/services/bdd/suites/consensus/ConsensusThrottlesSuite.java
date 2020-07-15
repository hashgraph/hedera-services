package com.hedera.services.bdd.suites.consensus;

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

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

/**
 * Set throttle tps to less than 1 (everything should be throttled with a burst period of 1.0 less).
 * Verify precheck code on submitted transaction is BUSY.
 * Reset throttle tps to 1000 (unthrottle the transaction).
 * Verify precheck code on submitted transaction is not BUSY.
 */
public class ConsensusThrottlesSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ConsensusThrottlesSuite.class);
	private static final String CREATE_TOPIC_THROTTLE_TPS = "throttling.hcs.createTopic.tps";
	private static final String CREATE_TOPIC_THROTTLE_BP = "throttling.hcs.createTopic.burstPeriod";
	private static final String SUBMIT_MESSAGE_THROTTLE_TPS = "throttling.hcs.submitMessage.tps";
	private static final String SUBMIT_MESSAGE_THROTTLE_BP = "throttling.hcs.submitMessage.burstPeriod";
	private static final String SUBMIT_MESSAGE_TOPIC_NAME = "submitMessage throttling";
	private static final String UPDATE_TOPIC_THROTTLE_TPS = "throttling.hcs.updateTopic.tps";
	private static final String UPDATE_TOPIC_THROTTLE_BP = "throttling.hcs.updateTopic.burstPeriod";
	private static final String UPDATE_TOPIC_TOPIC_NAME = "updateTopic throttling";
	private static final String DELETE_TOPIC_THROTTLE_TPS = "throttling.hcs.deleteTopic.tps";
	private static final String DELETE_TOPIC_THROTTLE_BP = "throttling.hcs.deleteTopic.burstPeriod";
	private static final String DELETE_TOPIC_TOPIC_NAME = "deleteTopic throttling";
	private static final String GET_TOPIC_INFO_THROTTLE_TPS = "throttling.hcs.getTopicInfo.tps";
	private static final String GET_TOPIC_INFO_THROTTLE_BP = "throttling.hcs.getTopicInfo.burstPeriod";
	private static final String GET_TOPIC_INFO_TOPIC_NAME = "getTopicInfo throttling";

	public static void main(String... args) {
		new ConsensusThrottlesSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						throttlesCreateTopicAsExpectedWithThrottleZer0(),
						throttlesCreateTopicAsExpected(),
						throttlesSubmitMessageAsExpected(),
						throttlesUpdateTopicAsExpected(),
						throttlesDeleteTopicAsExpected(),
						throttlesGetTopicInfoAsExpected(),
//						openAllThrottles(),
				}
		);
	}

	private HapiApiSpec throttlesCreateTopicAsExpectedWithThrottleZer0() {
		return  defaultHapiSpec("throttlesCreateTopicAsExpectedWithThrottleSetToZer0")
		.given(
				cryptoCreate("civilian"),
				fileUpdate(APP_PROPERTIES)
						.overridingProps(Map.of(CREATE_TOPIC_THROTTLE_TPS, "0"))
		)
		.when()
		.then(
				createTopic("unthrottled topic")
						.payingWith("civilian")
						.hasPrecheck(OK)
		);
	}

	private HapiApiSpec throttlesCreateTopicAsExpected() {
		return defaultHapiSpec("ThrottlesCreateTopicAsExpected")
				.given(
						cryptoCreate("civilian"),
						fileUpdate(APP_PROPERTIES)
								.overridingProps(Map.of(CREATE_TOPIC_THROTTLE_TPS, "0.9"))
				).when(
						createTopic("throttled topic")
								.payingWith("civilian")
								.hasPrecheck(BUSY),
						fileUpdate(APP_PROPERTIES)
								.overridingProps(Map.of(CREATE_TOPIC_THROTTLE_TPS, "1000.0")).hasKnownStatus(SUCCESS)
				).then(
						createTopic("unthrottled topic")
								.payingWith("civilian")
								.hasPrecheck(OK)
				);
	}

	private HapiApiSpec throttlesSubmitMessageAsExpected() {
		return defaultHapiSpec("ThrottlesSubmitMessageAsExpected")
				.given(
						cryptoCreate("civilian"),
						createTopic(SUBMIT_MESSAGE_TOPIC_NAME),
						fileUpdate(APP_PROPERTIES)
								.overridingProps(Map.of(SUBMIT_MESSAGE_THROTTLE_TPS, "0.9"))
				).when(
						submitMessageTo(SUBMIT_MESSAGE_TOPIC_NAME)
								.payingWith("civilian")
								.message("throttled")
								.hasPrecheck(BUSY),
						fileUpdate(APP_PROPERTIES)
								.overridingProps(Map.of(SUBMIT_MESSAGE_THROTTLE_TPS, "1000.0")).hasKnownStatus(SUCCESS)
				).then(
						submitMessageTo(SUBMIT_MESSAGE_TOPIC_NAME)
								.payingWith("civilian")
								.message("unthrottled")
								.hasPrecheck(OK)
				);
	}

	private HapiApiSpec throttlesUpdateTopicAsExpected() {
		return defaultHapiSpec("ThrottlesUpdateTopicAsExpected")
				.given(
						cryptoCreate("civilian"),
						createTopic(UPDATE_TOPIC_TOPIC_NAME),
						fileUpdate(APP_PROPERTIES)
								.overridingProps(Map.of(UPDATE_TOPIC_THROTTLE_TPS, "0.9"))
				).when(
						updateTopic(UPDATE_TOPIC_TOPIC_NAME)
								.payingWith("civilian")
								.memo("throttled")
								.hasPrecheck(BUSY),
						fileUpdate(APP_PROPERTIES)
								.overridingProps(Map.of(UPDATE_TOPIC_THROTTLE_TPS, "1000.0")).hasKnownStatus(SUCCESS)
				).then(
						updateTopic(UPDATE_TOPIC_TOPIC_NAME)
								.payingWith("civilian")
								.memo("unthrottled")
								.hasPrecheck(OK)
				);
	}

	private HapiApiSpec throttlesDeleteTopicAsExpected() {
		return defaultHapiSpec("ThrottlesDeleteTopicAsExpected")
				.given(
						cryptoCreate("civilian"),
						createTopic(DELETE_TOPIC_TOPIC_NAME),
						fileUpdate(APP_PROPERTIES)
								.overridingProps(Map.of(DELETE_TOPIC_THROTTLE_TPS, "0.9"))
				).when(
						deleteTopic(DELETE_TOPIC_TOPIC_NAME)
								.payingWith("civilian")
								.hasPrecheck(BUSY),
						fileUpdate(APP_PROPERTIES)
								.overridingProps(Map.of(DELETE_TOPIC_THROTTLE_TPS, "1000.0")).hasKnownStatus(SUCCESS)
				).then(
						deleteTopic(DELETE_TOPIC_TOPIC_NAME)
								.payingWith("civilian")
								.hasPrecheck(OK)
								.hasKnownStatus(UNAUTHORIZED)
				);
	}

	private HapiApiSpec throttlesGetTopicInfoAsExpected() {
		return defaultHapiSpec("ThrottlesGetTopicInfoAsExpected")
				.given(
						cryptoCreate("civilian"),
						createTopic(GET_TOPIC_INFO_TOPIC_NAME),
						// 1.9 instead of 0.9 - there is a cost check done _before_ getTopicInfo which counts against
						// the throttle as well. This gets past the cost answer so we can hasAnswerOnlyPrecheck()
						fileUpdate(APP_PROPERTIES)
								.overridingProps(Map.of(GET_TOPIC_INFO_THROTTLE_TPS, "1.1"))
				).when(
						getTopicInfo(GET_TOPIC_INFO_TOPIC_NAME)
								.payingWith("civilian")
								.hasAnswerOnlyPrecheck(BUSY),
						fileUpdate(APP_PROPERTIES)
								.overridingProps(Map.of(GET_TOPIC_INFO_THROTTLE_TPS, "1000.0")).hasKnownStatus(SUCCESS)
				).then(
						getTopicInfo(GET_TOPIC_INFO_TOPIC_NAME)
								.payingWith("civilian")
								.hasAnswerOnlyPrecheck(OK)
				);
	}

	private HapiApiSpec openAllThrottles() {
		return defaultHapiSpec("OpenAllThrottles")
				.given( ).when( ).then(
						fileUpdate(APP_PROPERTIES)
								.overridingProps(Map.of(
										CREATE_TOPIC_THROTTLE_TPS, "33",
										CREATE_TOPIC_THROTTLE_BP, "1",
										UPDATE_TOPIC_THROTTLE_TPS, "33",
										UPDATE_TOPIC_THROTTLE_BP, "1",
										DELETE_TOPIC_THROTTLE_TPS, "33",
										DELETE_TOPIC_THROTTLE_BP, "1",
										GET_TOPIC_INFO_THROTTLE_TPS, "33",
										GET_TOPIC_INFO_THROTTLE_BP, "1",
										SUBMIT_MESSAGE_THROTTLE_TPS, "333",
										SUBMIT_MESSAGE_THROTTLE_BP, "1"))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
