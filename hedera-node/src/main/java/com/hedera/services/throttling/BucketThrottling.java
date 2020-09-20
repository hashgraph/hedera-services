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
import com.hedera.services.throttling.bucket.BucketConfig;
import com.hedera.services.throttling.bucket.BucketThrottle;
import com.hedera.services.throttling.bucket.CapacityTest;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.AddressBook;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.throttling.ThrottlingPropsBuilder.*;
import static com.hedera.services.throttling.bucket.BucketConfig.*;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;
import static java.util.Comparator.comparing;
import static java.util.EnumSet.complementOf;
import static java.util.stream.Collectors.*;

public class BucketThrottling implements FunctionalityThrottling {
	private static final Logger log = LogManager.getLogger(BucketThrottling.class);

	static Consumer<String> displayFn = log::info;

	private final PropertySource properties;
	private final Supplier<AddressBook> book;
	private final Function<PropertySource, Map<String, BucketConfig>> getBuckets;
	private final BiFunction<PropertySource, Integer, PropertySource> getThrottleProps;

	private static final EnumSet<HederaFunctionality> REAL = complementOf(EnumSet.of(NONE, UNRECOGNIZED));
	HederaFunctionality[] functions = Arrays.stream(HederaFunctionality.class.getEnumConstants())
			.filter(REAL::contains)
			.sorted(comparing(Object::toString))
			.toArray(HederaFunctionality[]::new);

	EnumMap<HederaFunctionality, CapacityTest> capacities = new EnumMap<>(HederaFunctionality.class);

	public BucketThrottling(
			Supplier<AddressBook> book,
			PropertySource properties,
			Function<PropertySource, Map<String, BucketConfig>> getBuckets,
			BiFunction<PropertySource, Integer, PropertySource> getThrottleProps
	) {
		this.book = book;
		this.properties = properties;
		this.getBuckets = getBuckets;
		this.getThrottleProps = getThrottleProps;
	}

	@Override
	public boolean shouldThrottle(HederaFunctionality function) {
		var capacity = capacities.get(function);
		if (capacity != null) {
			var answer = !capacity.isAvailable();
			log.debug("Should throttle {}? {} says, '{}'.", function, capacity, answer);
			return answer;
		} else {
			log.warn("No capacity test was available for {}, so throttling it!", function);
			return true;
		}
	}

	public void rebuild() {
		var throttleProps = getThrottleProps.apply(properties, book.get().getSize());
		var config = getBuckets.apply(throttleProps);
		var throttles = throttlesGiven(throttleProps, config);
		var newCapacities = new EnumMap<HederaFunctionality, CapacityTest>(HederaFunctionality.class);
		Arrays.stream(functions)
				.forEach(function -> newCapacities.put(function, testGiven(throttleProps, function, throttles)));
		capacities = newCapacities;
		var sb = new StringBuilder("Resolved node-level throttling:");
		List.of(functions).stream()
				.sorted(comparing(HederaFunctionality::toString))
				.forEach(f -> sb.append(String.format("\n  %s=%s", f, capacities.get(f))));
		displayFn.accept(sb.toString());
	}

	Map<String, BucketThrottle> throttlesGiven(PropertySource props, Map<String, BucketConfig> config) {
		var networkSize = book.get().getSize();
		var throttles = config.keySet()
				.stream()
				.collect(toMap(Function.identity(), bucket -> config.get(bucket).asNodeThrottle(networkSize)));
		throttles.forEach((bucket, throttle) -> {
			var overflowProp = overflowProperty.apply(bucket);
			if (props.containsProperty(overflowProp)) {
				var overflow = props.getStringProperty(overflowProp);
				if (throttles.containsKey(overflow)) {
					throttle.setOverflow(throttles.get(overflow));
				}
			}
		});
		return throttles;
	}

	CapacityTest testGiven(
			PropertySource props,
			HederaFunctionality function,
			Map<String, BucketThrottle> throttles
	) {
		var bucketProp = asBucketProperty.apply(function);
		var bucket = props.containsProperty(bucketProp)
				? props.getStringProperty(bucketProp)
				: props.getStringProperty(defaultBucketPropFor(function));

		var requirementProp = asCapacityRequiredProperty.apply(function);
		var required = props.containsProperty(requirementProp)
				? props.getDoubleProperty(requirementProp)
				: props.getDoubleProperty(defaultRequirementPropFor(function));

		var throttle = throttles.get(bucket);
		return new CapacityTest(required, throttle);
	}

	private String defaultBucketPropFor(HederaFunctionality function) {
		return QUERY_FUNCTIONS.contains(function)
				? DEFAULT_QUERY_BUCKET_PROPERTY
				: DEFAULT_TXN_BUCKET_PROPERTY;
	}

	private String defaultRequirementPropFor(HederaFunctionality function) {
		return QUERY_FUNCTIONS.contains(function)
				? DEFAULT_QUERY_CAPACITY_REQUIRED_PROPERTY
				: DEFAULT_TXN_CAPACITY_REQUIRED_PROPERTY;
	}

	private static final EnumSet<HederaFunctionality> QUERY_FUNCTIONS = EnumSet.of(
			ConsensusGetTopicInfo,
			GetBySolidityID,
			ContractCallLocal,
			ContractGetInfo,
			ContractGetBytecode,
			ContractGetRecords,
			CryptoGetAccountBalance,
			CryptoGetAccountRecords,
			CryptoGetInfo,
			CryptoGetLiveHash,
			FileGetContents,
			FileGetInfo,
			TransactionGetReceipt,
			TransactionGetRecord,
			GetVersionInfo,
			TokenGetInfo
	);
}
