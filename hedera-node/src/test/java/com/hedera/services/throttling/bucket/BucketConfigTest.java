package com.hedera.services.throttling.bucket;

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

import com.hedera.services.throttling.ThrottlingPropsBuilder;
import org.junit.jupiter.api.Test;
import com.hedera.services.context.properties.PropertySource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.*;
import static com.hedera.services.throttling.bucket.BucketConfig.*;

@RunWith(JUnitPlatform.class)
class BucketConfigTest {
	String bucket = "mine";
	String overflow = "yours";

	PropertySource properties;

	@BeforeEach
	private void setup() {
		properties = mock(PropertySource.class);

		given(properties.containsProperty(burstProperty.apply(bucket))).willReturn(false);
		given(properties.containsProperty(capacityProperty.apply(bucket))).willReturn(false);
		given(properties.containsProperty(overflowProperty.apply(bucket))).willReturn(false);
	}

	@Test
	public void identifiesCandidates() {
		// setup:
		Set<String> props = Set.of(
				DEFAULT_QUERY_BUCKET_PROPERTY,
				DEFAULT_TXN_BUCKET_PROPERTY,
				API_THROTTLING_BUCKETS_PREFIX + ".one.capacity",
				API_THROTTLING_BUCKETS_PREFIX + ".five.burstPeriod",
				API_THROTTLING_BUCKETS_PREFIX + ".seven.overflow",
				API_THROTTLING_BUCKETS_PREFIX + ".two.capaicty",
				API_THROTTLING_BUCKETS_PREFIX + ".three.overflow"
		);
		// and:
		List<String> expected = List.of(
				"zero", "one", "three", "four", "five", "six", "seven").stream().sorted().collect(Collectors.toList());

		given(properties.getStringProperty(DEFAULT_QUERY_BUCKET_PROPERTY)).willReturn("zero");
		given(properties.getStringProperty(DEFAULT_TXN_BUCKET_PROPERTY)).willReturn("six");
		given(properties.getStringProperty(API_THROTTLING_BUCKETS_PREFIX + ".three.overflow"))
				.willReturn("four");
		given(properties.getStringProperty(API_THROTTLING_BUCKETS_PREFIX + ".seven.overflow"))
				.willThrow(RuntimeException.class);

		given(properties.allPropertyNames()).willReturn(props);

		// when:
		List<String> actual = bucketsIn(properties);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	public void acquiresFromExpected() {
		givenCapacityProp(bucket, 555);
		givenBurstProp(bucket, 111);
		givenOverflowProp(bucket, overflow);

		// when:
		var config = namedIn(properties, bucket);
		// and:
		var throttle = config.asNodeThrottle(5);

		// then:
		assertEquals(555, config.capacity());
		assertEquals(111, config.burstPeriod());
		assertEquals(overflow, config.overflow());
		// and:
		assertEquals(1.0, throttle.primary().getTps());
		assertEquals(111.0, throttle.primary().getBurstPeriod());
	}

	@Test
	public void acquiresFromExpectedDefaults() {
		givenDefaultBurstPeriod(555);
		givenDefaultCapacity(111);
		givenOverflowProp(bucket, overflow);

		// when:
		var config = namedIn(properties, bucket);

		// then:
		assertEquals(555, config.burstPeriod());
		assertEquals(111, config.capacity());
		assertEquals(overflow, config.overflow());
	}

	@Test
	public void acquiresFromDefaultIfBoom() {
		givenWiredCapacityProp(bucket);
		givenDefaultCapacity(123);

		// when:
		var config = namedIn(properties, bucket);

		// then:
		assertEquals(123, config.capacity());
	}

	@Test
	public void acquiresFromFallbackIfBoomBoom() {
		givenWiredCapacityProp(bucket);
		givenWiredDefaultCapacity();

		// when:
		var config = namedIn(properties, bucket);

		// then:
		assertEquals(ThrottlingPropsBuilder.DEFAULT_CAPACITY, config.capacity());
	}

	@Test
	public void acquiresFromFallbacks() {
		// when:
		var config = namedIn(properties, bucket);

		// then:
		assertEquals(ThrottlingPropsBuilder.DEFAULT_BURST, config.burstPeriod());
		assertEquals(ThrottlingPropsBuilder.DEFAULT_CAPACITY, config.capacity());
		assertFalse(config.hasOverflow());
	}

	@Test
	public void omitsOverflowIfMissing() {
		givenCapacityProp(bucket, 555);
		givenBurstProp(bucket, 111);

		// when:
		var config = namedIn(properties, bucket);

		// then:
		assertEquals(555, config.capacity());
		assertEquals(111, config.burstPeriod());
		assertFalse(config.hasOverflow());
	}

	private void givenCapacityProp(String bucket, double value) {
		given(properties.getDoubleProperty(capacityProperty.apply(bucket))).willReturn(value);
		given(properties.containsProperty(capacityProperty.apply(bucket))).willReturn(true);
	}

	private void givenWiredCapacityProp(String bucket) {
		given(properties.containsProperty(capacityProperty.apply(bucket))).willReturn(true);
		given(properties.getDoubleProperty(capacityProperty.apply(bucket))).willThrow(RuntimeException.class);
	}

	private void givenBurstProp(String bucket, double value) {
		given(properties.getDoubleProperty(burstProperty.apply(bucket))).willReturn(value);
		given(properties.containsProperty(burstProperty.apply(bucket))).willReturn(true);
	}

	private void givenOverflowProp(String bucket, String value) {
		given(properties.getStringProperty(overflowProperty.apply(bucket))).willReturn(value);
		given(properties.containsProperty(overflowProperty.apply(bucket))).willReturn(true);
	}

	private void givenDefaultCapacity(double value) {
		given(properties.containsProperty(DEFAULT_CAPACITY_PROPERTY)).willReturn(true);
		given(properties.getDoubleProperty(DEFAULT_CAPACITY_PROPERTY)).willReturn(value);
	}

	private void givenWiredDefaultCapacity() {
		given(properties.containsProperty(DEFAULT_CAPACITY_PROPERTY)).willReturn(true);
		given(properties.getDoubleProperty(DEFAULT_CAPACITY_PROPERTY)).willThrow(RuntimeException.class);
	}

	private void givenDefaultBurstPeriod(double value) {
		given(properties.containsProperty(DEFAULT_BURST_PROPERTY)).willReturn(true);
		given(properties.getDoubleProperty(DEFAULT_BURST_PROPERTY)).willReturn(value);
	}
}
