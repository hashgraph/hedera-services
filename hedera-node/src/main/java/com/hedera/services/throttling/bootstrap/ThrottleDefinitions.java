package com.hedera.services.throttling.bootstrap;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class ThrottleDefinitions {
	List<ThrottleBucket> buckets = new ArrayList<>();

	public List<ThrottleBucket> getBuckets() {
		return buckets;
	}

	public void setBuckets(List<ThrottleBucket> buckets) {
		this.buckets = buckets;
	}

	public static ThrottleDefinitions fromProto(com.hederahashgraph.api.proto.java.ThrottleDefinitions defs) {
		var pojo = new ThrottleDefinitions();
		pojo.buckets.addAll(defs.getThrottleBucketsList().stream()
				.map(ThrottleBucket::fromProto)
				.collect(toList()));
		return pojo;
	}

	public com.hederahashgraph.api.proto.java.ThrottleDefinitions toProto() {
		return com.hederahashgraph.api.proto.java.ThrottleDefinitions.newBuilder()
				.addAllThrottleBuckets(buckets.stream().map(ThrottleBucket::toProto).collect(toList()))
				.build();
	}
}
