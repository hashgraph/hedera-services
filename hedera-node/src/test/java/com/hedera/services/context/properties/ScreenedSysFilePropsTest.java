package com.hedera.services.context.properties;

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

import com.hedera.services.state.merkle.MerkleToken;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.Set;

import static com.hedera.services.throttling.ThrottlingPropsBuilder.API_THROTTLING_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
class ScreenedSysFilePropsTest {
	Logger log;

	ScreenedSysFileProps subject;

	@BeforeEach
	public void setup() {
		log = mock(Logger.class);
		ScreenedSysFileProps.log = log;

		subject = new ScreenedSysFileProps();
	}

	@Test
	public void delegationWorks() {
		// given:
		subject.from121 = Map.of("tokens.maxPerAccount", 42);

		// expect:
		assertEquals(Set.of("tokens.maxPerAccount"), subject.allPropertyNames());
		assertEquals(42, subject.getProperty("tokens.maxPerAccount"));
		assertTrue(subject.containsProperty("tokens.maxPerAccount"));
		assertFalse(subject.containsProperty("nonsense"));
	}

	@Test
	public void ignoresThrottleProps() {
		// when:
		subject.screenNew(withJust(API_THROTTLING_PREFIX, "42"));

		// then:
		assertTrue(subject.from121.isEmpty());
		// and:
		verify(log, never()).warn(String.format(ScreenedSysFileProps.MISPLACED_PROP_TPL, API_THROTTLING_PREFIX));
	}

	@Test
	public void ignoresNonGlobalDynamic() {
		// when:
		subject.screenNew(withJust("notGlobalDynamic", "42"));

		// then:
		assertTrue(subject.from121.isEmpty());
		// and:
		verify(log).warn(String.format(
				ScreenedSysFileProps.MISPLACED_PROP_TPL,
				"notGlobalDynamic"));
	}

	@Test
	public void warnsOfUnparseableGlobalDynamic() {
		// when:
		subject.screenNew(withJust("tokens.maxPerAccount", "ABC"));

		// then:
		assertTrue(subject.from121.isEmpty());
		// and:
		verify(log).warn(String.format(
				ScreenedSysFileProps.UNPARSEABLE_PROP_TPL,
				"ABC",
				"tokens.maxPerAccount",
				"NumberFormatException"));
	}

	@Test
	public void incorporatesStandardGlobalDynamic() {
		// setup:
		var oldMap = subject.from121;

		// when:
		subject.screenNew(withJust("tokens.maxPerAccount", "42"));

		// then:
		assertEquals(Map.of("tokens.maxPerAccount", 42), subject.from121);
		// and:
		assertNotSame(oldMap, subject.from121);
	}

	@Test
	public void incorporatesLegacyGlobalDynamic() {
		// when:
		subject.screenNew(withJust("configAccountNum", "42"));

		// then:
		assertEquals(1, subject.from121.size());
		assertEquals(42L, subject.from121.get("ledger.maxAccountNum"));
		// and:
		verify(log).warn(String.format(
				ScreenedSysFileProps.DEPRECATED_PROP_TPL,
				"configAccountNum",
				"ledger.maxAccountNum"));
	}

	@Test
	public void incorporatesLegacyGlobalDynamicWithTransform() {
		// when:
		subject.screenNew(withJust("defaultFeeCollectionAccount", "0.0.98"));

		// then:
		assertEquals(1, subject.from121.size());
		assertEquals(98L, subject.from121.get("ledger.fundingAccount"));
		// and:
		verify(log).warn(String.format(
				ScreenedSysFileProps.DEPRECATED_PROP_TPL,
				"defaultFeeCollectionAccount",
				"ledger.fundingAccount"));
	}

	@Test
	public void warnsOfUnusableMaxTokenNameUtf8Bytes() {
		// setup:
		String unsupportableValue = "" + (MerkleToken.UPPER_BOUND_TOKEN_NAME_UTF8_BYTES + 1);
		// when:
		subject.screenNew(withJust("tokens.maxTokenNameUtf8Bytes", unsupportableValue));

		// then:
		assertTrue(subject.from121.isEmpty());
		// and:
		verify(log).warn(String.format(
				ScreenedSysFileProps.UNUSABLE_PROP_TPL,
				unsupportableValue,
				"tokens.maxTokenNameUtf8Bytes"));
	}

	@Test
	public void warnsOfUnusableMaxTokenSymbolUtf8Bytes() {
		// setup:
		String unsupportableValue = "" + (MerkleToken.UPPER_BOUND_SYMBOL_UTF8_BYTES + 1);
		// when:
		subject.screenNew(withJust("tokens.maxSymbolUtf8Bytes", unsupportableValue));

		// then:
		assertTrue(subject.from121.isEmpty());
		// and:
		verify(log).warn(String.format(
				ScreenedSysFileProps.UNUSABLE_PROP_TPL,
				unsupportableValue,
				"tokens.maxSymbolUtf8Bytes"));
	}

	@Test
	public void warnsOfUnusableGlobalDynamic() {
		// when:
		subject.screenNew(withJust("rates.intradayChangeLimitPercent", "-1"));

		// then:
		assertTrue(subject.from121.isEmpty());
		// and:
		verify(log).warn(String.format(
				ScreenedSysFileProps.UNUSABLE_PROP_TPL,
				"-1",
				"rates.intradayChangeLimitPercent"));
	}

	@Test
	public void warnsOfUntransformableGlobalDynamic() {
		// when:
		subject.screenNew(withJust("defaultFeeCollectionAccount", "abc"));

		// then:
		assertTrue(subject.from121.isEmpty());
		// and:
		verify(log).warn(String.format(
				ScreenedSysFileProps.UNTRANSFORMABLE_PROP_TPL,
				"abc",
				"defaultFeeCollectionAccount",
				"IllegalArgumentException"));
	}

	private ServicesConfigurationList withJust(String name, String value) {
		return ServicesConfigurationList.newBuilder()
				.addNameValue(from(name, value))
				.build();
	}

	private Setting from(String name, String value) {
		return Setting.newBuilder().setName(name).setValue(value).build();
	}
}