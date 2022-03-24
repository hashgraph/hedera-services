package com.hedera.services.context.properties;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public enum EntityType {
	ACCOUNT, CONTRACT, FILE, SCHEDULE, TOKEN, TOPIC;

	public static Set<EntityType> csvTypeSet(final String propertyValue) {
		return Arrays.stream(propertyValue.split(","))
				.map(String::strip)
				.map(EntityType::valueOf)
				.collect(Collectors.toCollection(() -> EnumSet.noneOf(EntityType.class)));
	}
}
