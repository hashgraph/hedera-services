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

import com.hedera.services.context.properties.DeferringPropertySource;
import com.hedera.services.context.properties.PropertySource;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static com.hedera.services.throttling.bucket.BucketConfig.API_THROTTLING_BUCKETS_PREFIX;
import static com.hedera.services.throttling.bucket.BucketConfig.DEFAULT_BURST_PROPERTY;
import static com.hedera.services.throttling.bucket.BucketConfig.DEFAULT_CAPACITY_PROPERTY;
import static com.hedera.services.throttling.bucket.BucketConfig.DEFAULT_QUERY_BUCKET_PROPERTY;
import static com.hedera.services.throttling.bucket.BucketConfig.DEFAULT_TXN_BUCKET_PROPERTY;
import static java.util.Comparator.comparingInt;
import static java.util.Comparator.naturalOrder;
import static java.util.stream.Collectors.joining;

public class ThrottlingPropsBuilder {
	private static final Logger log = LogManager.getLogger(ThrottlingPropsBuilder.class);

	static final double DEFAULT_TXN_CAPACITY_REQUIRED = 1.0;
	static final double DEFAULT_QUERY_CAPACITY_REQUIRED = 1.0;
	static final String DEFAULT_TXN_BUCKET = "defaultTxnBucket";
	static final String DEFAULT_QUERY_BUCKET = "defaultQueryBucket";
	public static final double DEFAULT_BURST = 1.0;
	public static final double DEFAULT_CAPACITY = 1.0 * Integer.MAX_VALUE;
	public static final String API_THROTTLING_PREFIX = "hapi.throttling";
	public static final String API_THROTTLING_CONFIG_PREFIX = API_THROTTLING_PREFIX + ".config";
	private static final String API_THROTTLING_OPS_PREFIX = API_THROTTLING_PREFIX + ".ops";
	public static final String API_THROTTLING_DEFAULT_PREFIX = API_THROTTLING_PREFIX + ".defaults";
	public static final String DEFAULT_TXN_CAPACITY_REQUIRED_PROPERTY =
			API_THROTTLING_DEFAULT_PREFIX + ".txnCapacityRequired";
	public static final String DEFAULT_QUERY_CAPACITY_REQUIRED_PROPERTY =
			API_THROTTLING_DEFAULT_PREFIX + ".queryCapacityRequired";
	private static final UnaryOperator<String> bucketProperty = function ->
			String.format("%s.%s.bucket", API_THROTTLING_OPS_PREFIX, function);
	private static final UnaryOperator<String> capacityRequiredProperty = function ->
			String.format("%s.%s.capacityRequired", API_THROTTLING_OPS_PREFIX, function);
	static final Function<HederaFunctionality, String> asBucketProperty = function ->
			bucketProperty.apply(wordCase(function));
	public static final Function<HederaFunctionality, String> asCapacityRequiredProperty = function ->
			capacityRequiredProperty.apply(wordCase(function));

	static Consumer<String> displayFn = log::info;

	public static PropertySource withPrioritySource(PropertySource properties, int networkSize) {
		var fallback = new HashMap<String, Object>();
		addDefaults(fallback);
		var throttleProps = new DeferringPropertySource(properties, fallback);
		var msg = String.format("Resolved network-wide throttling properties (%d nodes):\n  ", networkSize) +
				throttleProps.allPropertyNames()
						.stream().filter(name -> name.startsWith(API_THROTTLING_PREFIX))
						.sorted(THROTTLE_PROPERTY_ORDER)
						.map(name -> String.format("%s=%s", name, throttleProps.getProperty(name)))
						.collect(joining("\n  "));
		displayFn.accept(msg);
		return throttleProps;
	}

	public static Comparator<String> THROTTLE_PROPERTY_ORDER = comparingInt((String name) ->
			name.startsWith(API_THROTTLING_CONFIG_PREFIX) ? 0
					: (name.startsWith(API_THROTTLING_DEFAULT_PREFIX)
					? 1 : (name.startsWith(API_THROTTLING_BUCKETS_PREFIX) ? 2 : 3)))
			.thenComparing(naturalOrder());

	public static void addDefaults(Map<String, Object> props) {
		props.put(DEFAULT_QUERY_CAPACITY_REQUIRED_PROPERTY, DEFAULT_QUERY_CAPACITY_REQUIRED);
		props.put(DEFAULT_TXN_CAPACITY_REQUIRED_PROPERTY, DEFAULT_TXN_CAPACITY_REQUIRED);
		props.put(DEFAULT_QUERY_BUCKET_PROPERTY, DEFAULT_QUERY_BUCKET);
		props.put(DEFAULT_TXN_BUCKET_PROPERTY, DEFAULT_TXN_BUCKET);
		props.put(DEFAULT_CAPACITY_PROPERTY, DEFAULT_CAPACITY);
		props.put(DEFAULT_BURST_PROPERTY, DEFAULT_BURST);
	}

	public static String wordCase(HederaFunctionality function) {
		var name = function.toString();
		return name.substring(0, 1).toLowerCase() + name.substring(1);
	}
}
