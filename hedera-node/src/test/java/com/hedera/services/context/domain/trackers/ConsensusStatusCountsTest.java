package com.hedera.services.context.domain.trackers;

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

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBodyOrBuilder;
import com.hederahashgraph.api.proto.java.CryptoDelete;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static com.hedera.services.context.domain.trackers.IssEventStatus.*;
import static org.mockito.BDDMockito.*;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;

@RunWith(JUnitPlatform.class)
class ConsensusStatusCountsTest {
	ConsensusStatusCounts subject;

	@BeforeEach
	private void setup() {
		subject = new ConsensusStatusCounts(new ObjectMapper());
	}

	@Test
	public void incrementsCounts() {
		// when:
		subject.increment(CryptoDelete, FEE_SCHEDULE_FILE_PART_UPLOADED);
		subject.increment(CryptoDelete, FEE_SCHEDULE_FILE_PART_UPLOADED);
		// and:
		subject.increment(ContractCall, SUCCESS);

		// then:
		assertTrue(subject.counts.containsKey(FEE_SCHEDULE_FILE_PART_UPLOADED));
		assertTrue(subject.counts.get(FEE_SCHEDULE_FILE_PART_UPLOADED).containsKey(CryptoDelete));
		assertEquals(2, subject.counts.get(FEE_SCHEDULE_FILE_PART_UPLOADED).get(CryptoDelete).intValue());
		assertTrue(subject.counts.containsKey(SUCCESS));
		assertEquals(1, subject.counts.get(SUCCESS).get(ContractCall).intValue());
	}

	@Test
	public void serializesWell() {
		// given:
		subject.increment(CryptoDelete, FEE_SCHEDULE_FILE_PART_UPLOADED);
		subject.increment(CryptoDelete, FEE_SCHEDULE_FILE_PART_UPLOADED);
		subject.increment(ContractCall, SUCCESS);
		subject.increment(ContractCall, INVALID_SIGNATURE);
		// and:
		var expected = "[ {\n"
				+ "  \"INVALID_SIGNATURE:ContractCall\" : 1\n"
				+ "}, {\n"
				+ "  \"SUCCESS:ContractCall\" : 1\n"
				+ "}, {\n"
				+ "  \"FEE_SCHEDULE_FILE_PART_UPLOADED:CryptoDelete\" : 2\n"
				+ "} ]";

		// when:
		var json = subject.asJson();

		// then:
		assertEquals(expected, json);
	}

	@Test
	public void serializesBadly() throws Exception {
		// setup:
		ObjectWriter bomb = mock(ObjectWriter.class);
		ObjectMapper bad = mock(ObjectMapper.class);
		given(bad.writerWithDefaultPrettyPrinter()).willReturn(bomb);
		willThrow(JsonProcessingException.class).given(bomb).writeValueAsString(any());
		subject = new ConsensusStatusCounts(bad);

		// given:
		subject.increment(CryptoDelete, FEE_SCHEDULE_FILE_PART_UPLOADED);
		subject.increment(CryptoDelete, FEE_SCHEDULE_FILE_PART_UPLOADED);
		subject.increment(ContractCall, SUCCESS);
		subject.increment(ContractCall, INVALID_SIGNATURE);

		// when:
		var json = subject.asJson();

		// then:
		assertEquals("[ ]", json);
	}
}
