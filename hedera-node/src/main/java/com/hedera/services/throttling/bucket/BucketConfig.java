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

import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.throttling.ThrottlingPropsBuilder;
import com.swirlds.throttle.Throttle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import static com.hedera.services.throttling.ThrottlingPropsBuilder.*;

import static java.util.stream.Collectors.toList;

public class BucketConfig {
	private static final Logger log = LogManager.getLogger(BucketConfig.class);

	public static final String API_THROTTLING_BUCKETS_PREFIX = API_THROTTLING_PREFIX + ".buckets";
	public static final String DEFAULT_TXN_BUCKET_PROPERTY = API_THROTTLING_DEFAULT_PREFIX + ".txnBucket";
	public static final String DEFAULT_QUERY_BUCKET_PROPERTY = API_THROTTLING_DEFAULT_PREFIX + ".queryBucket";
	public static final String DEFAULT_CAPACITY_PROPERTY = API_THROTTLING_DEFAULT_PREFIX + ".capacity";
	public static final String DEFAULT_BURST_PROPERTY = API_THROTTLING_DEFAULT_PREFIX + ".burstPeriod";
	public static final String DEFAULT_OVERFLOW_PROPERTY = "<NONE>";

	public static final String DEFAULT_OVERFLOW = "<NONE>";

	public static final Pattern validPropertySpecPattern = Pattern.compile(
			API_THROTTLING_BUCKETS_PREFIX + "[.](\\w+)[.](capacity|burstPeriod|overflow)");

	public static final UnaryOperator<String> capacityProperty = bucket ->
			String.format("%s.%s.capacity", API_THROTTLING_BUCKETS_PREFIX, bucket);
	public static final UnaryOperator<String> burstProperty = bucket ->
			String.format("%s.%s.burstPeriod", API_THROTTLING_BUCKETS_PREFIX, bucket);
	public static final UnaryOperator<String> overflowProperty = bucket ->
			String.format("%s.%s.overflow", API_THROTTLING_BUCKETS_PREFIX, bucket);

	public static List<String> bucketsIn(PropertySource properties) {
		Set<String> buckets = new HashSet<>();
		for (String name : properties.allPropertyNames()) {
			var matcher = validPropertySpecPattern.matcher(name);
			if (matcher.matches()) {
				var bucket = matcher.group(1);
				buckets.add(bucket);
				if (matcher.group(2).equals("overflow")) {
					try {
						buckets.add(properties.getStringProperty(name));
					} catch (Exception e) {
						log.warn("Ignore overflow config for bucket {}, unparseable!", bucket, e);
					}
				}
			} else if (DEFAULT_QUERY_BUCKET_PROPERTY.equals(name)) {
				buckets.add(properties.getStringProperty(name));
			} else if (DEFAULT_TXN_BUCKET_PROPERTY.equals(name)) {
				buckets.add(properties.getStringProperty(name));
			}
		}
		return buckets.stream().sorted().collect(toList());
	}

	private final Optional<String> overflow;
	private final double capacity;
	private final double burstPeriod;
	private final String name;

	private BucketConfig(String name, double capacity, double burstPeriod, String overflow) {
		this.name = name;
		this.capacity = capacity;
		this.burstPeriod = burstPeriod;
		this.overflow = Optional.of(overflow);
	}

	private BucketConfig(String name, double capacity, double burstPeriod) {
		this.name = name;
		this.capacity = capacity;
		this.burstPeriod = burstPeriod;
		this.overflow = Optional.empty();
	}

	public BucketThrottle asNodeThrottle(int networkSize) {
		return new BucketThrottle(name, new Throttle((capacity / networkSize) / burstPeriod, burstPeriod));
	}

	public static BucketConfig namedIn(PropertySource properties, String name) {
		var overflow = lookupValueOrFallbackOrDefault(
				DEFAULT_OVERFLOW,
				overflowProperty.apply(name),
				DEFAULT_OVERFLOW_PROPERTY,
				properties,
				properties::getStringProperty);
		var capacity = lookupValueOrFallbackOrDefault(
				ThrottlingPropsBuilder.DEFAULT_CAPACITY,
				capacityProperty.apply(name),
				DEFAULT_CAPACITY_PROPERTY,
				properties,
				properties::getDoubleProperty);
		var burst = lookupValueOrFallbackOrDefault(
				ThrottlingPropsBuilder.DEFAULT_BURST,
				burstProperty.apply(name),
				DEFAULT_BURST_PROPERTY,
				properties,
				properties::getDoubleProperty);

		return (overflow == DEFAULT_OVERFLOW)
				? new BucketConfig(name, capacity, burst)
				: new BucketConfig(name, capacity, burst, overflow);
	}

	private static <T> T lookupValueOrFallbackOrDefault(
			T defaultT,
			String primary,
			String fallback,
			PropertySource properties,
			Function<String, T> lookup
	) {
		if (properties.containsProperty(primary)) {
			try {
				return lookup.apply(primary);
			} catch (Exception ignore) {
			}
		}

		if (properties.containsProperty(fallback)) {
			try {
				return lookup.apply(fallback);
			} catch (Exception ignore) {
			}
		}

		return defaultT;
	}

	public boolean hasOverflow() {
		return overflow.isPresent();
	}

	public String overflow() {
		return overflow.get();
	}

	public double capacity() {
		return capacity;
	}

	public double burstPeriod() {
		return burstPeriod;
	}
}
