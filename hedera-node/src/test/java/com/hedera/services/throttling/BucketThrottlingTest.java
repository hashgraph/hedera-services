package com.hedera.services.throttling;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.throttling.bucket.BucketConfig;
import com.hedera.services.throttling.bucket.LegacyBucketThrottle;
import com.hedera.services.throttling.bucket.CapacityTest;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.AddressBook;
import com.swirlds.common.throttle.Throttle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.logging.log4j.Logger;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.BDDMockito.*;
import static com.hedera.services.throttling.bucket.BucketConfig.*;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.*;

class BucketThrottlingTest {
	int networkSize = 5;
	HederaFunctionality txn = FileAppend;
	HederaFunctionality query = HederaFunctionality.ConsensusGetTopicInfo;
	double required = 2.0;
	double queryRequired = 1.0;
	double txnRequired = 10.0;

	Throttle unitThrottle;
	Throttle deciThrottle;
	BucketConfig txnBucketConfig;
	BucketConfig queryBucketConfig;
	BucketConfig bucketConfig;
	BucketConfig overflowConfig;
	String queries = "defaultQueryBucket";
	LegacyBucketThrottle queryBucket;
	String txns = "defaultTxnBucket";
	LegacyBucketThrottle txnBucket;
	String b = "bucket";
	LegacyBucketThrottle bucket;
	String o = "overflow";
	LegacyBucketThrottle overflow;
	Map<String, BucketConfig> buckets;

	AddressBook book;
	PropertySource throttleProps;
	PropertySource properties;
	Function<PropertySource, Map<String, BucketConfig>> getBuckets;
	BiFunction<PropertySource, Integer, PropertySource> getThrottleProps;
	Logger log;

	BucketThrottling subject;

	@BeforeEach
	@SuppressWarnings("unchecked")
	private void setup() {
		unitThrottle = new Throttle(1.0, 1.0);
		deciThrottle = new Throttle(10.0, 1.0);
		bucket = new LegacyBucketThrottle(unitThrottle);
		overflow = new LegacyBucketThrottle(deciThrottle);
		queryBucket = new LegacyBucketThrottle(deciThrottle);
		txnBucket = new LegacyBucketThrottle(deciThrottle);
		log = mock(Logger.class);

		bucketConfig = mock(BucketConfig.class);
		given(bucketConfig.asNodeThrottle(networkSize)).willReturn(bucket);
		overflowConfig = mock(BucketConfig.class);
		given(overflowConfig.asNodeThrottle(networkSize)).willReturn(overflow);
		txnBucketConfig = mock(BucketConfig.class);
		given(txnBucketConfig.asNodeThrottle(networkSize)).willReturn(txnBucket);
		queryBucketConfig = mock(BucketConfig.class);
		given(queryBucketConfig.asNodeThrottle(networkSize)).willReturn(queryBucket);
		given(log.isDebugEnabled()).willReturn(true);

		buckets = new HashMap<>();
		buckets.put(b, bucketConfig);
		buckets.put(o, overflowConfig);
		buckets.put(queries, queryBucketConfig);
		buckets.put(txns, txnBucketConfig);

		book = mock(AddressBook.class);
		given(book.getSize()).willReturn(networkSize);

		throttleProps = mock(PropertySource.class);
		given(throttleProps.getStringProperty(DEFAULT_TXN_BUCKET_PROPERTY)).willReturn(txns);
		given(throttleProps.getStringProperty(DEFAULT_QUERY_BUCKET_PROPERTY)).willReturn(queries);
		given(throttleProps.getDoubleProperty(DEFAULT_TXN_CAPACITY_REQUIRED_PROPERTY)).willReturn(txnRequired);
		given(throttleProps.getDoubleProperty(DEFAULT_QUERY_CAPACITY_REQUIRED_PROPERTY)).willReturn(queryRequired);
		given(throttleProps.containsProperty(overflowProperty.apply(b))).willReturn(true);
		given(throttleProps.getStringProperty(overflowProperty.apply(b))).willReturn(o);
		given(throttleProps.containsProperty(asBucketProperty.apply(txn))).willReturn(true);
		given(throttleProps.getStringProperty(asBucketProperty.apply(txn))).willReturn(b);
		given(throttleProps.containsProperty(asBucketProperty.apply(query))).willReturn(true);
		given(throttleProps.getStringProperty(asBucketProperty.apply(query))).willReturn(b);
		given(throttleProps.containsProperty(asCapacityRequiredProperty.apply(txn))).willReturn(true);
		given(throttleProps.getDoubleProperty(asCapacityRequiredProperty.apply(txn))).willReturn(required);
		given(throttleProps.containsProperty(asCapacityRequiredProperty.apply(query))).willReturn(true);
		given(throttleProps.getDoubleProperty(asCapacityRequiredProperty.apply(query))).willReturn(required);

		properties = mock(PropertySource.class);

		getBuckets = mock(Function.class);
		given(getBuckets.apply(throttleProps)).willReturn(buckets);

		getThrottleProps = mock(BiFunction.class);
		given(getThrottleProps.apply(properties, networkSize)).willReturn(throttleProps);

		subject = new BucketThrottling(() -> book, properties, getBuckets, getThrottleProps);

		subject.log = log;
	}

	@Test
	void rebuildsAsExpected() {
		// setup:
		subject.functions = new HederaFunctionality[] { FileAppend, ConsensusGetTopicInfo };
		var oldCapacities = subject.capacities;

		// given:
		subject.rebuild();

		// then:
		assertEquals(2, subject.capacities.size());
		assertNotSame(oldCapacities, subject.capacities);
	}

	@Test
	void buildsExpectedThrottles() {
		// when:
		var throttles = subject.throttlesGiven(throttleProps, buckets);

		// then:
		assertEquals(bucket, throttles.get(b));
		assertEquals(overflow, throttles.get(o));
		// and:
		assertTrue(bucket.hasOverflow());
		assertEquals(overflow, bucket.overflow());
	}

	@Test
	void buildsExpectedTestForTxnFromSpecs() {
		// given:
		var throttles = subject.throttlesGiven(throttleProps, buckets);

		// when:
		var test = subject.testGiven(throttleProps, txn, throttles);

		// then:
		assertEquals(bucket, test.getBucket());
		assertEquals(required, test.getCapacityRequired());
	}

	@Test
	void buildsExpectedTestForQueryFromSpecs() {
		// given:
		var throttles = subject.throttlesGiven(throttleProps, buckets);

		// when:
		var test = subject.testGiven(throttleProps, query, throttles);

		// then:
		assertEquals(bucket, test.getBucket());
		assertEquals(required, test.getCapacityRequired());
	}

	@Test
	void buildsExpectedTestForTxnFromDefaults() {
		given(throttleProps.containsProperty(asBucketProperty.apply(txn))).willReturn(false);
		given(throttleProps.containsProperty(asCapacityRequiredProperty.apply(txn))).willReturn(false);
		// and:
		var throttles = subject.throttlesGiven(throttleProps, buckets);

		// when:
		var test = subject.testGiven(throttleProps, txn, throttles);

		// then:
		assertEquals(txnBucket, test.getBucket());
		assertEquals(txnRequired, test.getCapacityRequired());
	}

	@Test
	void buildsExpectedTestForQueryFromDefaults() {
		given(throttleProps.containsProperty(asBucketProperty.apply(query))).willReturn(false);
		given(throttleProps.containsProperty(asCapacityRequiredProperty.apply(query))).willReturn(false);
		// and:
		var throttles = subject.throttlesGiven(throttleProps, buckets);

		// when:
		var test = subject.testGiven(throttleProps, query, throttles);

		// then:
		assertEquals(queryBucket, test.getBucket());
		assertEquals(queryRequired, test.getCapacityRequired());
	}

	@Test
	void survivesMissingOverflow() {
		given(throttleProps.getStringProperty(overflowProperty.apply(b))).willReturn("MISSING");

		// expect:
		assertDoesNotThrow(() -> subject.throttlesGiven(throttleProps, buckets));
	}

	@Test
	void doesntThrottleIfCapAvail() {
		// setup:
		var test = mock(CapacityTest.class);
		subject.capacities = new EnumMap<>(HederaFunctionality.class);

		given(test.isAvailable()).willReturn(true);
		// and:
		subject.capacities.put(txn, test);

		// when:
		var flag = subject.shouldThrottle(txn);

		// then:
		assertFalse(flag);
		verify(test).isAvailable();
	}

	@Test
	void throttlesIfCapNotAvail() {
		// setup:
		var test = mock(CapacityTest.class);
		subject.capacities = new EnumMap<>(HederaFunctionality.class);

		given(test.isAvailable()).willReturn(false);
		// and:
		subject.capacities.put(txn, test);

		// when:
		var flag = subject.shouldThrottle(txn);

		// then:
		assertTrue(flag);
		verify(test).isAvailable();
	}

	@Test
	void throttlesByDefault() {
		// setup:
		subject.capacities = new EnumMap<>(HederaFunctionality.class);

		// when:
		var flag = subject.shouldThrottle(txn);

		// then:
		assertTrue(flag);
	}
}
