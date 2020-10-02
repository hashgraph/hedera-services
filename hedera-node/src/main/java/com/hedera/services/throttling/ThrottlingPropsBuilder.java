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
import com.hedera.services.context.properties.StandardizedPropertySources;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static com.hedera.services.context.properties.StandardizedPropertySources.RESPECT_LEGACY_THROTTLING_PROPERTY;
import static com.hedera.services.throttling.bucket.BucketConfig.*;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusDeleteTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;
import static java.util.Comparator.*;
import static java.util.Comparator.comparingInt;
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
		addFromLegacyBouncer(fallback, properties, networkSize);
		addFromLegacyHcs(fallback, properties, networkSize);
		var throttleProps = new DeferringPropertySource(properties, fallback);
		var msg = String.format("Resolved network-wide throttling properties (%d nodes):\n  ", networkSize) +
				throttleProps.allPropertyNames()
						.stream().filter(name -> name.startsWith(API_THROTTLING_PREFIX))
						.sorted(THROTTLE_PROPERTY_ORDER)
						.map(name -> String.format("%s=%s", name, throttleProps.getProperty(name)))
						.collect(joining("\n  "));
//		displayFn.accept(msg);
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

	public static void addFromLegacyHcs(Map<String, Object> props, PropertySource properties, int networkSize) {
		if (!properties.getBooleanProperty(RESPECT_LEGACY_THROTTLING_PROPERTY)) {
			return;
		}
		hcsFunctions.forEach(function -> {
			var tpsProp = tpsPropFor(function);
			var burstProp = burstPeriodPropFor(function);
			var bucketName = hcsBucketFor(function);
			if (properties.containsProperty(tpsProp) && properties.containsProperty(burstProp)) {
				props.put(asBucketProperty.apply(function), bucketName);
				var burst = properties.getDoubleProperty(burstProp);
				var capacity = sanitized(properties.getDoubleProperty(tpsProp) * burst * networkSize);
				props.put(capacityProperty.apply(bucketName), capacity);
				props.put(burstProperty.apply(bucketName), burst);
			}
		});
	}

	private static String hcsBucketFor(HederaFunctionality function) {
		String name = function.toString().substring("Consensus".length());
		return name.substring(0, 1).toLowerCase() + name.substring(1) + "Bucket";
	}

	private final static EnumSet<HederaFunctionality> hcsFunctions = EnumSet.of(
			ConsensusGetTopicInfo,
			ConsensusSubmitMessage,
			ConsensusCreateTopic,
			ConsensusDeleteTopic,
			ConsensusUpdateTopic);

	public static void addFromLegacyBouncer(Map<String, Object> props, PropertySource properties, int networkSize) {
		if (!properties.getBooleanProperty(RESPECT_LEGACY_THROTTLING_PROPERTY)) {
			return;
		}
		if (properties.containsProperty("throttlingTps")) {
			props.put(
					capacityProperty.apply(DEFAULT_TXN_BUCKET),
					sanitized(1.0 * properties.getIntProperty("throttlingTps") * networkSize));
		}
		if (properties.containsProperty("queriesTps")) {
			props.put(
					capacityProperty.apply(DEFAULT_QUERY_BUCKET),
					sanitized(1.0 * properties.getIntProperty("queriesTps") * networkSize));
		}
		if (properties.containsProperty("simpletransferTps")) {
			props.put(
					capacityProperty.apply("transferBucket"),
					sanitized(1.0 * properties.getIntProperty("simpletransferTps") * networkSize));
			props.put(
					asBucketProperty.apply(HederaFunctionality.CryptoTransfer),
					"transferBucket");
		}
		if (properties.containsProperty("getReceiptTps")) {
			props.put(
					capacityProperty.apply("receiptsBucket"),
					sanitized(1.0 * properties.getIntProperty("getReceiptTps") * networkSize));
			props.put(
					asBucketProperty.apply(HederaFunctionality.TransactionGetReceipt),
					"receiptsBucket");
		}
	}

	private static double sanitized(double capacity) {
		return capacity > 0.0 ? capacity : DEFAULT_CAPACITY;
	}

	private static String tpsPropFor(HederaFunctionality consensusFunction) {
		return customPropFor(consensusFunction, "tps");
	}

	private static String burstPeriodPropFor(HederaFunctionality consensusFunction) {
		return customPropFor(consensusFunction, "burstPeriod");
	}

	private static String customPropFor(HederaFunctionality consensusFunction, String param) {
		return basePropFor(consensusFunction) + "." + param;
	}

	private static String basePropFor(HederaFunctionality consensusFunction) {
		String name = consensusFunction.toString().substring("Consensus".length());
		return String.format("throttling.hcs.%s", name.substring(0, 1).toLowerCase() + name.substring(1));
	}
}
