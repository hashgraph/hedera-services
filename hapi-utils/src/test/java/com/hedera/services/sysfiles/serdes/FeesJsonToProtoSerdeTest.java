package com.hedera.services.sysfiles.serdes;

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
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.SubType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;

import static com.hedera.services.sysfiles.serdes.FeesJsonToProtoSerde.loadFeeScheduleFromJson;
import static com.hedera.services.sysfiles.serdes.FeesJsonToProtoSerde.loadFeeScheduleFromStream;
import static com.hedera.services.sysfiles.serdes.FeesJsonToProtoSerde.parseFeeScheduleFromJson;
import static com.hedera.services.sysfiles.serdes.FeesJsonToProtoSerde.stringToSubType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeesJsonToProtoSerdeTest {
	private static final String UNTYPED_FEE_SCHEDULE_REPR_PATH = "src/test/resources/sysfiles/r4FeeSchedule.bin";
	private static final String TYPED_FEE_SCHEDULE_JSON_PATH = "src/test/resources/sysfiles/r16feeSchedules.json";
	private static final String TYPED_FEE_SCHEDULE_JSON_RESOURCE = "sysfiles/r16feeSchedules.json";
	private static final String TMP_PROTO_LOC = "tbd.bin";

	@Test
	void isUninstantiable() throws NoSuchMethodException {
		// setup:
		Constructor<?> constructor;
		// and:
		constructor = FeesJsonToProtoSerde.class.getDeclaredConstructor();
		constructor.setAccessible(true);

		// expect:
		final var e = assertThrows(InvocationTargetException.class, constructor::newInstance);
		assertTrue(e.getCause() instanceof UnsupportedOperationException);
	}

	@Test
	void serializesTypedFeeScheduleFromStream() throws Exception {
		// setup:
		final var in = java.nio.file.Files.newInputStream(Paths.get(TYPED_FEE_SCHEDULE_JSON_PATH));

		// given:
		final var typedSchedules = loadFeeScheduleFromStream(in);
		// and:
		final var tmpFile = new File(TMP_PROTO_LOC);
		Files.write(typedSchedules.toByteArray(), tmpFile);

		// and sanity check:
		Assertions.assertDoesNotThrow(() ->
				CurrentAndNextFeeSchedule.parseFrom(java.nio.file.Files.readAllBytes(Paths.get(TMP_PROTO_LOC))));
		// cleanup:
		tmpFile.delete();
	}

	@Test
	void serializesTypedFeeScheduleFromLoadedJsonResource() throws Exception {
		// given:
		final var typedSchedules = loadFeeScheduleFromJson(TYPED_FEE_SCHEDULE_JSON_RESOURCE);
		// and:
		final var tmpFile = new File(TMP_PROTO_LOC);
		Files.write(typedSchedules.toByteArray(), tmpFile);

		// and sanity check:
		Assertions.assertDoesNotThrow(() ->
				CurrentAndNextFeeSchedule.parseFrom(java.nio.file.Files.readAllBytes(Paths.get(TMP_PROTO_LOC))));

		// cleanup:
		tmpFile.delete();
	}

	@Test
	void serializesTypedFeeScheduleFromParsedJsonLiteral() throws Exception {
		// setup:
		final var jsonLiteral = new String(java.nio.file.Files.readAllBytes(Paths.get(TYPED_FEE_SCHEDULE_JSON_PATH)));

		// given:
		final var typedSchedules = parseFeeScheduleFromJson(jsonLiteral);
		final var tmpFile = new File(TMP_PROTO_LOC);
		// and:
		Files.write(typedSchedules.toByteArray(), tmpFile);

		// and sanity check:
		Assertions.assertDoesNotThrow(() ->
				CurrentAndNextFeeSchedule.parseFrom(java.nio.file.Files.readAllBytes(Paths.get(TMP_PROTO_LOC))));

		// cleanup:
		tmpFile.delete();
	}

	@Test
	void preservesR4Behavior() throws Exception {
		// given:
		CurrentAndNextFeeSchedule expectedR4 =
				CurrentAndNextFeeSchedule.parseFrom(Files.toByteArray(new File(UNTYPED_FEE_SCHEDULE_REPR_PATH)));

		// when:
		CurrentAndNextFeeSchedule actual = loadFeeScheduleFromJson("sysfiles/R4FeeSchedule.json");

		// then:
		assertEquals(expectedR4, actual);
	}

	@Test
	void throwIseOnFailure() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> loadFeeScheduleFromJson("no-such-resource.json"));
	}

	@Test
	void stringToSubTypeAsExpected() {
		var str = "TOKEN_FUNGIBLE_COMMON";
		assertEquals(SubType.TOKEN_FUNGIBLE_COMMON, stringToSubType(str));

		str = "TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES";
		assertEquals(SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES, stringToSubType(str));

		str = "TOKEN_NON_FUNGIBLE_UNIQUE";
		assertEquals(SubType.TOKEN_NON_FUNGIBLE_UNIQUE, stringToSubType(str));

		str = "TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES";
		assertEquals(SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES, stringToSubType(str));

		str = "SCHEDULE_CREATE_CONTRACT_CALL";
		assertEquals(SubType.SCHEDULE_CREATE_CONTRACT_CALL, stringToSubType(str));

		str = "blah";
		assertEquals(SubType.DEFAULT, stringToSubType(str));
	}
}
