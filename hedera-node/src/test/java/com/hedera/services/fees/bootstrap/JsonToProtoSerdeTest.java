package com.hedera.services.fees.bootstrap;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.google.common.io.Files;
import com.hederahashgraph.api.proto.java.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Paths;

import static com.hedera.services.fees.bootstrap.JsonToProtoSerde.loadFeeScheduleFromJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JsonToProtoSerdeTest {
	public final static String R4_FEE_SCHEDULE_REPR_PATH = "src/test/resources/testfiles/r4FeeSchedule.bin";

	@Test
	public void serializeR5FeeSchedule() throws Exception {
		// given:
		var r5SchedulesResource = "testfiles/2020-04-22-FeeSchedule.json";

		// when:
		var r5Schedules = loadFeeScheduleFromJson(r5SchedulesResource);
		// and:
		var tmpFile = new File("2020-04-22-FeeSchedule.bin");

		// then:
		Files.write(r5Schedules.toByteArray(), tmpFile);
		// and sanity check:
		Assertions.assertDoesNotThrow(() ->
				CurrentAndNextFeeSchedule.parseFrom(
						java.nio.file.Files.readAllBytes(Paths.get("2020-04-22-FeeSchedule.bin"))));

		// cleanup:
		tmpFile.delete();
	}


	@Test
	public void preservesR4Behavior() throws Exception {
		// given:
		CurrentAndNextFeeSchedule expectedR4 =
				CurrentAndNextFeeSchedule.parseFrom(Files.toByteArray(new File(R4_FEE_SCHEDULE_REPR_PATH)));

		// when:
		CurrentAndNextFeeSchedule actual = loadFeeScheduleFromJson("testfiles/R4FeeSchedule.json");

		// then:
		assertEquals(expectedR4, actual);
	}

	@Test
	public void throwIseOnFailure() {
		// expect:
		assertThrows(IllegalStateException.class, () -> loadFeeScheduleFromJson("no-such-resource.json"));
	}
}
