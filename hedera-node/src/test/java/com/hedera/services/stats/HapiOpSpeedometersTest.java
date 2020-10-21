package com.hedera.services.stats;

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

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.Platform;
import com.swirlds.common.StatEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.function.Function;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
class HapiOpSpeedometersTest {
	Platform platform;
	SpeedometerFactory factory;
	Function<HederaFunctionality, Long> handledSoFar;
	Function<HederaFunctionality, Long> receivedSoFar;
	Function<HederaFunctionality, Long> answeredSoFar;
	Function<HederaFunctionality, Long> submittedSoFar;
	Function<HederaFunctionality, String> statNameFn;

	HapiOpSpeedometers subject;

	@BeforeEach
	public void setup() throws Exception {
		HapiOpSpeedometers.allFunctions = () -> new HederaFunctionality[] {
				CryptoTransfer,
				TokenGetInfo
		};

		handledSoFar = mock(Function.class);
		receivedSoFar = mock(Function.class);
		answeredSoFar = mock(Function.class);
		submittedSoFar = mock(Function.class);

		platform = mock(Platform.class);
		factory = mock(SpeedometerFactory.class);
		statNameFn = HederaFunctionality::toString;

		subject = new HapiOpSpeedometers(
				factory,
				handledSoFar,
				receivedSoFar,
				answeredSoFar,
				submittedSoFar,
				statNameFn);
	}

	@AfterEach
	public void cleanup() {
		HapiOpSpeedometers.allFunctions = HederaFunctionality.class::getEnumConstants;
	}

	@Test
	@Disabled
	public void beginsRationally() {
		// expect:
		assertTrue(subject.receivedOps.containsKey(CryptoTransfer));
		assertTrue(subject.submittedTxns.containsKey(CryptoTransfer));
		assertTrue(subject.handledTxns.containsKey(CryptoTransfer));
		assertFalse(subject.answeredQueries.containsKey(CryptoTransfer));
		// and:
		assertTrue(subject.receivedOps.containsKey(TokenGetInfo));
		assertTrue(subject.answeredQueries.containsKey(TokenGetInfo));
		assertFalse(subject.submittedTxns.containsKey(TokenGetInfo));
		assertFalse(subject.handledTxns.containsKey(TokenGetInfo));
	}
}