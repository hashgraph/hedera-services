package com.hedera.services.throttling;

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

import com.hedera.services.context.properties.PropertySource;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.hedera.services.context.properties.StandardizedPropertySources.RESPECT_LEGACY_THROTTLING_PROPERTY;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.DEFAULT_BURST;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.DEFAULT_CAPACITY;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.DEFAULT_QUERY_BUCKET;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.DEFAULT_QUERY_CAPACITY_REQUIRED;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.DEFAULT_QUERY_CAPACITY_REQUIRED_PROPERTY;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.DEFAULT_TXN_BUCKET;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.DEFAULT_TXN_CAPACITY_REQUIRED;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.DEFAULT_TXN_CAPACITY_REQUIRED_PROPERTY;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.addDefaults;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.addFromLegacyBouncer;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.addFromLegacyHcs;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.asBucketProperty;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.withPrioritySource;
import static com.hedera.services.throttling.bucket.BucketConfig.DEFAULT_BURST_PROPERTY;
import static com.hedera.services.throttling.bucket.BucketConfig.DEFAULT_CAPACITY_PROPERTY;
import static com.hedera.services.throttling.bucket.BucketConfig.DEFAULT_QUERY_BUCKET_PROPERTY;
import static com.hedera.services.throttling.bucket.BucketConfig.DEFAULT_TXN_BUCKET_PROPERTY;
import static com.hedera.services.throttling.bucket.BucketConfig.burstProperty;
import static com.hedera.services.throttling.bucket.BucketConfig.capacityProperty;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusDeleteTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class ThrottlingPropsBuilderTest {
	PropertySource properties;
	Map<String, Object> baseProps;

	@BeforeEach
	private void setup() {
		properties = mock(PropertySource.class);
		given(properties.getBooleanProperty(RESPECT_LEGACY_THROTTLING_PROPERTY)).willReturn(true);
		baseProps = new HashMap<>();
	}

	@Test
	public void loadsAsExpected() {
		// setup:
		int networkSize = 5;
		Consumer<String> oldDisplay = ThrottlingPropsBuilder.displayFn;
		ThrottlingPropsBuilder.displayFn = System.out::println;

		givenLegacyBouncerProperties(networkSize, 10, 100, 1_000, 10_000);
		givenLegacyHcsProperties(
				networkSize,
				1.0, 1.0,
				2.0, 2.0,
				3.0, 3.0,
				4.0, 4.0,
				5.0, 5.0);

		 // when:
		var throttleProps = withPrioritySource(properties, networkSize);
		// and:
		var names = throttleProps.allPropertyNames();

		// then:
		assertEquals(27, names.size());

		// cleanup:
		ThrottlingPropsBuilder.displayFn = oldDisplay;
	}

	@Test
	public void addsDefaults() {
		// given:
		addDefaults(baseProps);

		// expect:
		assertEquals(
				DEFAULT_BURST,
				(double) baseProps.get(DEFAULT_BURST_PROPERTY));
		assertEquals(
				DEFAULT_CAPACITY,
				(double) baseProps.get(DEFAULT_CAPACITY_PROPERTY));
		assertEquals(
				DEFAULT_QUERY_BUCKET,
				baseProps.get(DEFAULT_QUERY_BUCKET_PROPERTY));
		assertEquals(
				DEFAULT_TXN_BUCKET,
				baseProps.get(DEFAULT_TXN_BUCKET_PROPERTY));
		assertEquals(
				DEFAULT_TXN_CAPACITY_REQUIRED,
				baseProps.get(DEFAULT_TXN_CAPACITY_REQUIRED_PROPERTY));
		assertEquals(
				DEFAULT_QUERY_CAPACITY_REQUIRED,
				baseProps.get(DEFAULT_QUERY_CAPACITY_REQUIRED_PROPERTY));
	}

	@Test
	void addsLegacyBouncerTranslationFromZeroTps() {
		givenLegacyBouncerProperties(5, 0, 0, 0, 0);

		// when:
		addFromLegacyBouncer(baseProps, properties, 5);

		// then:
		assertEquals(
				DEFAULT_CAPACITY,
				baseProps.get(capacityProperty.apply(DEFAULT_TXN_BUCKET)));
		assertEquals(
				DEFAULT_CAPACITY,
				baseProps.get(capacityProperty.apply(DEFAULT_QUERY_BUCKET)));
		// and:
		assertEquals(
				DEFAULT_CAPACITY,
				baseProps.get(capacityProperty.apply("transferBucket")));
		assertEquals(
				"transferBucket",
				baseProps.get(asBucketProperty.apply(HederaFunctionality.CryptoTransfer)));
		// and:
		assertEquals(
				DEFAULT_CAPACITY,
				baseProps.get(capacityProperty.apply("receiptsBucket")));
		assertEquals(
				"receiptsBucket",
				baseProps.get(asBucketProperty.apply(HederaFunctionality.TransactionGetReceipt)));
	}

	@Test
	void addsNoLegacyPropsIfNotRespected() {
		givenLegacyBouncerProperties(5, 10, 100, 1_000, 10_000);
		givenLegacyHcsProperties(
				5,
				1.0, 1.0,
				2.0, 2.0,
				3.0, 3.0,
				4.0, 4.0,
				5.0, 5.0);
		// and:
		given(properties.getBooleanProperty(RESPECT_LEGACY_THROTTLING_PROPERTY)).willReturn(false);

		// when:
		addFromLegacyBouncer(baseProps, properties, 5);

		// then:
		assertEquals(0, baseProps.size());
	}

	@Test
	void addsLegacyBouncerTranslation() {
		givenLegacyBouncerProperties(5, 10, 100, 1_000, 10_000);

		// when:
		addFromLegacyBouncer(baseProps, properties, 5);

		// then:
		assertEquals(
				10.0,
				baseProps.get(capacityProperty.apply(DEFAULT_TXN_BUCKET)));
		assertEquals(
				100.0,
				baseProps.get(capacityProperty.apply(DEFAULT_QUERY_BUCKET)));
		// and:
		assertEquals(
				1_000.0,
				baseProps.get(capacityProperty.apply("transferBucket")));
		assertEquals(
				"transferBucket",
				baseProps.get(asBucketProperty.apply(HederaFunctionality.CryptoTransfer)));
		// and:
		assertEquals(
				10_000.0,
				baseProps.get(capacityProperty.apply("receiptsBucket")));
		assertEquals(
				"receiptsBucket",
				baseProps.get(asBucketProperty.apply(HederaFunctionality.TransactionGetReceipt)));
	}

	@Test
	void addsLegacyHcsProperties() {
		// setup:
		int networkSize = 5;

		givenLegacyHcsProperties(
				networkSize,
				1.0, 1.0,
				2.0, 2.0,
				3.0, 3.0,
				4.0, 4.0,
				5.0, 5.0);

		// when:
		addFromLegacyHcs(baseProps, properties, networkSize);

		// then:
		List<HederaFunctionality> functions = List.of(
				ConsensusCreateTopic, ConsensusDeleteTopic, ConsensusUpdateTopic, ConsensusSubmitMessage, ConsensusGetTopicInfo);
		for (int i = 1; i <= 5; i++) {
			var function = functions.get(i - 1);
			var v = (double)i;
			var name = function.toString().substring("Consensus".length());
			name = name.substring(0, 1).toLowerCase() + name.substring(1) + "Bucket";
			assertEquals(
					name,
					baseProps.get(asBucketProperty.apply(function)));
			assertEquals(
					v * v,
					baseProps.get(capacityProperty.apply(name)));
			assertEquals(
					v,
					baseProps.get(burstProperty.apply(name)));
		}
	}

	private void givenLegacyBouncerProperties(
			int networkSize,
			int txnTps,
			int queryTps,
			int transferTps,
			int receiptTps
	) {
		given(properties.getIntProperty("throttlingTps")).willReturn(txnTps / networkSize);
		given(properties.getIntProperty("simpletransferTps")).willReturn(transferTps / networkSize);
		given(properties.getIntProperty("getReceiptTps")).willReturn(receiptTps / networkSize);
		given(properties.getIntProperty("queriesTps")).willReturn(queryTps / networkSize);
		// and:
		given(properties.containsProperty("throttlingTps")).willReturn(true);
		given(properties.containsProperty("simpletransferTps")).willReturn(true);
		given(properties.containsProperty("getReceiptTps")).willReturn(true);
		given(properties.containsProperty("queriesTps")).willReturn(true);
	}

	private void givenLegacyHcsProperties(
			int networkSize,
			double createTopicBurst,
			double createTopicTps,
			double deleteTopicBurst,
			double deleteTopicTps,
			double updateTopicBurst,
			double updateTopicTps,
			double submitMessageBurst,
			double submitMessageTps,
			double getTopicInfoBurst,
			double getTopicInfoTps
	) {
		given(properties.getDoubleProperty("throttling.hcs.createTopic.tps"))
				.willReturn(createTopicTps / networkSize);
		given(properties.getDoubleProperty("throttling.hcs.createTopic.burstPeriod"))
				.willReturn(createTopicBurst);
		given(properties.getDoubleProperty("throttling.hcs.deleteTopic.tps"))
				.willReturn(deleteTopicTps / networkSize);
		given(properties.getDoubleProperty("throttling.hcs.deleteTopic.burstPeriod"))
				.willReturn(deleteTopicBurst);
		given(properties.getDoubleProperty("throttling.hcs.updateTopic.tps"))
				.willReturn(updateTopicTps / networkSize);
		given(properties.getDoubleProperty("throttling.hcs.updateTopic.burstPeriod"))
				.willReturn(updateTopicBurst);
		given(properties.getDoubleProperty("throttling.hcs.submitMessage.tps"))
				.willReturn(submitMessageTps / networkSize);
		given(properties.getDoubleProperty("throttling.hcs.submitMessage.burstPeriod"))
				.willReturn(submitMessageBurst);
		given(properties.getDoubleProperty("throttling.hcs.getTopicInfo.tps"))
				.willReturn(getTopicInfoTps / networkSize);
		given(properties.getDoubleProperty("throttling.hcs.getTopicInfo.burstPeriod"))
				.willReturn(getTopicInfoBurst);

		given(properties.containsProperty("throttling.hcs.createTopic.tps")).willReturn(true);
		given(properties.containsProperty("throttling.hcs.createTopic.burstPeriod")).willReturn(true);
		given(properties.containsProperty("throttling.hcs.deleteTopic.tps")).willReturn(true);
		given(properties.containsProperty("throttling.hcs.deleteTopic.burstPeriod")).willReturn(true);
		given(properties.containsProperty("throttling.hcs.updateTopic.tps")).willReturn(true);
		given(properties.containsProperty("throttling.hcs.updateTopic.burstPeriod")).willReturn(true);
		given(properties.containsProperty("throttling.hcs.submitMessage.tps")).willReturn(true);
		given(properties.containsProperty("throttling.hcs.submitMessage.burstPeriod")).willReturn(true);
		given(properties.containsProperty("throttling.hcs.getTopicInfo.tps")).willReturn(true);
		given(properties.containsProperty("throttling.hcs.getTopicInfo.burstPeriod")).willReturn(true);
	}
}
