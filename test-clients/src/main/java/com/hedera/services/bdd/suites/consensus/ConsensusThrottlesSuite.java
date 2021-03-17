package com.hedera.services.bdd.suites.consensus;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class ConsensusThrottlesSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ConsensusThrottlesSuite.class);
	private static final String SUBMIT_MESSAGE_TOPIC_NAME = "submitMessage throttling";
	private static final String UPDATE_TOPIC_TOPIC_NAME = "updateTopic throttling";
	private static final String DELETE_TOPIC_TOPIC_NAME = "deleteTopic throttling";
	private static final String GET_TOPIC_INFO_TOPIC_NAME = "getTopicInfo throttling";

	private static final String UNACHIEVABLE_CAPACITY = "2000000000";

	public static void main(String... args) {
		new ConsensusThrottlesSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						throttlesCreateTopicAsExpected(),
						throttlesSubmitMessageAsExpected()
						//throttlesUpdateTopicAsExpected(),
						//throttlesDeleteTopicAsExpected(),
						//throttlesGetTopicInfoAsExpected(),
				}
		);
	}

	private HapiApiSpec throttlesCreateTopicAsExpected() {
		return defaultHapiSpec("ThrottlesCreateTopicAsExpected")
				.given(
						cryptoCreate("civilian"),
						fileUpdate(APP_PROPERTIES).payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"hapi.throttling.buckets.createTopicBucket.capacity",
										"0.0"))
				).when(
						createTopic("throttled topic")
								.payingWith("civilian")
								.hasPrecheck(BUSY),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"hapi.throttling.buckets.createTopicBucket.capacity",
										"1300.0"))
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
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"hapi.throttling.ops.consensusSubmitMessage.capacityRequired",
										UNACHIEVABLE_CAPACITY))
				).when(
						submitMessageTo(SUBMIT_MESSAGE_TOPIC_NAME)
								.payingWith("civilian")
								.message("throttled")
								.hasPrecheck(BUSY),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"hapi.throttling.ops.consensusSubmitMessage.capacityRequired",
										"1.67"))
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
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"hapi.throttling.ops.consensusUpdateTopic.capacityRequired",
										UNACHIEVABLE_CAPACITY))
				).when(
						updateTopic(UPDATE_TOPIC_TOPIC_NAME)
								.payingWith("civilian")
								.memo("throttled")
								.hasPrecheck(BUSY),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"hapi.throttling.ops.consensusUpdateTopic.capacityRequired",
										"1.67"))
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
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"hapi.throttling.ops.consensusDeleteTopic.capacityRequired",
										UNACHIEVABLE_CAPACITY))
				).when(
						deleteTopic(DELETE_TOPIC_TOPIC_NAME)
								.payingWith("civilian")
								.hasPrecheck(BUSY),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"hapi.throttling.ops.consensusDeleteTopic.capacityRequired",
										"1.67"))
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
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"hapi.throttling.ops.consensusGetTopicInfo.capacityRequired",
										UNACHIEVABLE_CAPACITY))
				).when(
						getTopicInfo(GET_TOPIC_INFO_TOPIC_NAME)
								.nodePayment(1_000)
								.payingWith("civilian")
								.hasAnswerOnlyPrecheck(BUSY),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"hapi.throttling.ops.consensusGetTopicInfo.capacityRequired",
										"1.67"))
				).then(
						getTopicInfo(GET_TOPIC_INFO_TOPIC_NAME)
								.payingWith("civilian")
								.hasAnswerOnlyPrecheck(OK)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
