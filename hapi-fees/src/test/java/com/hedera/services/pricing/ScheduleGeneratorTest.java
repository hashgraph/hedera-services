package com.hedera.services.pricing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduleGeneratorTest {
	private static final String EXPECTED_SCHEDULES_LOC = "src/test/resources/expectedFeeSchedules.json";

	private ScheduleGenerator subject = new ScheduleGenerator();

	@Test
	void generatesExpectedSchedules() throws IOException {
		// given:
		final var expected = Files.readString(Paths.get(EXPECTED_SCHEDULES_LOC));

		// when:
		final var actual = subject.feeSchedulesFor(ScheduleGenerator.SUPPORTED_FUNCTIONS);

		// then:
		assertEquals(expected, actual);
	}
}