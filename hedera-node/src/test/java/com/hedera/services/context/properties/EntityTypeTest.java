package com.hedera.services.context.properties;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class EntityTypeTest {
	@Test
	void worksWithSomeValidTypes() {
		assertEquals(
				EnumSet.allOf(EntityType.class),
				EntityType.csvTypeSet("ACCOUNT,CONTRACT, FILE,SCHEDULE,TOKEN, TOPIC"));
	}

	@Test
	void throwsOnInvalidSpec() {
		assertThrows(IllegalArgumentException.class, () -> EntityType.csvTypeSet("ACCOUNT,CONTRACTUALLY_SPEAKING"));
	}
}