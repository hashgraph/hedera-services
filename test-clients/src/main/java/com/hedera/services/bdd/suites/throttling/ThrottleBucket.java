package com.hedera.services.bdd.suites.throttling;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class ThrottleBucket {
	int burstPeriod;
	String name;
	List<ThrottleGroup> throttleGroups = new ArrayList<>();

	public int getBurstPeriod() {
		return burstPeriod;
	}

	public void setBurstPeriod(int burstPeriod) {
		this.burstPeriod = burstPeriod;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<ThrottleGroup> getThrottleGroups() {
		return throttleGroups;
	}

	public void setThrottleGroups(List<ThrottleGroup> throttleGroups) {
		this.throttleGroups = throttleGroups;
	}

	public static ThrottleBucket fromProto(com.hederahashgraph.api.proto.java.ThrottleBucket bucket) {
		var pojo = new ThrottleBucket();
		pojo.name = bucket.getName();
		pojo.burstPeriod = bucket.getBurstPeriod();
		pojo.throttleGroups.addAll(bucket.getThrottleGroupsList().stream()
				.map(ThrottleGroup::fromProto)
				.collect(toList()));
		return pojo;
	}

	public com.hederahashgraph.api.proto.java.ThrottleBucket toProto() {
		return com.hederahashgraph.api.proto.java.ThrottleBucket.newBuilder()
				.setName(name)
				.setBurstPeriod(burstPeriod)
				.addAllThrottleGroups(throttleGroups.stream()
						.map(ThrottleGroup::toProto)
						.collect(toList()))
				.build();
	}
}
