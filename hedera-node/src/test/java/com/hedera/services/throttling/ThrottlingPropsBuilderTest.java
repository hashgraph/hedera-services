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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static com.hedera.services.throttling.ThrottlingPropsBuilder.DEFAULT_BURST;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.DEFAULT_CAPACITY;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.DEFAULT_QUERY_BUCKET;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.DEFAULT_QUERY_CAPACITY_REQUIRED;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.DEFAULT_QUERY_CAPACITY_REQUIRED_PROPERTY;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.DEFAULT_TXN_BUCKET;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.DEFAULT_TXN_CAPACITY_REQUIRED;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.DEFAULT_TXN_CAPACITY_REQUIRED_PROPERTY;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.addDefaults;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.withPrioritySource;
import static com.hedera.services.throttling.bucket.BucketConfig.DEFAULT_BURST_PROPERTY;
import static com.hedera.services.throttling.bucket.BucketConfig.DEFAULT_CAPACITY_PROPERTY;
import static com.hedera.services.throttling.bucket.BucketConfig.DEFAULT_QUERY_BUCKET_PROPERTY;
import static com.hedera.services.throttling.bucket.BucketConfig.DEFAULT_TXN_BUCKET_PROPERTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.verify;

class ThrottlingPropsBuilderTest {
	PropertySource properties;
	Map<String, Object> baseProps;

	Consumer<String> mockLog;

	@BeforeEach
	private void setup() {
		mockLog = mock(Consumer.class);
		properties = mock(PropertySource.class);

		baseProps = new HashMap<>();
	}

	@Test
	void showsActiveProps() {
		// setup:
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

		ThrottlingPropsBuilder.displayFn = mockLog;

		given(properties.getProperty("hapi.throttling.buckets.createTopicBucket.burstPeriod")).willReturn("2.6");
		given(properties.containsProperty("hapi.throttling.buckets.createTopicBucket.burstPeriod"))
				.willReturn(true);
		given(properties.getProperty("hapi.throttling.buckets.createTopicBucket.capacity")).willReturn("13.0");
		given(properties.containsProperty("hapi.throttling.buckets.createTopicBucket.capacity"))
				.willReturn(true);
		given(properties.allPropertyNames()).willReturn(Set.of(
				"hapi.throttling.buckets.createTopicBucket.burstPeriod",
				"hapi.throttling.buckets.createTopicBucket.capacity"));
		// and:
		String expectedLog = "Resolved network-wide throttling properties (5 nodes):\n"
				+ "  hapi.throttling.defaults.burstPeriod=1.0\n"
				+ "  hapi.throttling.defaults.capacity=2.147483647E9\n"
				+ "  hapi.throttling.defaults.queryBucket=defaultQueryBucket\n"
				+ "  hapi.throttling.defaults.queryCapacityRequired=1.0\n"
				+ "  hapi.throttling.defaults.txnBucket=defaultTxnBucket\n"
				+ "  hapi.throttling.defaults.txnCapacityRequired=1.0\n"
				+ "  hapi.throttling.buckets.createTopicBucket.burstPeriod=2.6\n"
				+ "  hapi.throttling.buckets.createTopicBucket.capacity=13.0";

		// when:
		withPrioritySource(properties, 5);

		// then:
		verify(mockLog).accept(captor.capture());
		assertEquals(expectedLog, captor.getValue());
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
}
