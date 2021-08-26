package com.hedera.services.context.properties;

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

import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.Set;

import static com.hedera.services.context.properties.ScreenedSysFileProps.DEPRECATED_PROP_TPL;
import static com.hedera.services.context.properties.ScreenedSysFileProps.MISPLACED_PROP_TPL;
import static com.hedera.services.context.properties.ScreenedSysFileProps.UNPARSEABLE_PROP_TPL;
import static com.hedera.services.context.properties.ScreenedSysFileProps.UNTRANSFORMABLE_PROP_TPL;
import static com.hedera.services.context.properties.ScreenedSysFileProps.UNUSABLE_PROP_TPL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(LogCaptureExtension.class)
class ScreenedSysFilePropsTest {
	Logger log;

	@LoggingTarget
	private LogCaptor logCaptor;

	@LoggingSubject
	private ScreenedSysFileProps subject;

	@BeforeEach
	void setup() {
		subject = new ScreenedSysFileProps();
	}

	@Test
	void delegationWorks() {
		// given:
		subject.from121 = Map.of("tokens.maxPerAccount", 42);

		// expect:
		assertEquals(Set.of("tokens.maxPerAccount"), subject.allPropertyNames());
		assertEquals(42, subject.getProperty("tokens.maxPerAccount"));
		assertTrue(subject.containsProperty("tokens.maxPerAccount"));
		assertFalse(subject.containsProperty("nonsense"));
	}

	@Test
	void ignoresNonGlobalDynamic() {
		// when:
		subject.screenNew(withJust("notGlobalDynamic", "42"));

		// then:
		assertTrue(subject.from121.isEmpty());
		// and:
		assertThat(logCaptor.warnLogs(), contains(
				String.format(MISPLACED_PROP_TPL, "notGlobalDynamic")));
	}

	@Test
	void warnsOfUnparseableGlobalDynamic() {
		// when:
		subject.screenNew(withJust("tokens.maxPerAccount", "ABC"));

		// then:
		assertTrue(subject.from121.isEmpty());
		// and:
		assertThat(logCaptor.warnLogs(), contains(
				String.format(UNPARSEABLE_PROP_TPL, "ABC", "tokens.maxPerAccount", "NumberFormatException")));
	}

	@Test
	void incorporatesStandardGlobalDynamic() {
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
	void incorporatesLegacyGlobalDynamic() {
		// when:
		subject.screenNew(withJust("configAccountNum", "42"));

		// then:
		assertEquals(1, subject.from121.size());
		assertEquals(42L, subject.from121.get("ledger.maxAccountNum"));
		// and:
		assertThat(logCaptor.warnLogs(), contains(
				String.format(DEPRECATED_PROP_TPL, "configAccountNum", "ledger.maxAccountNum")));
	}

	@Test
	void incorporatesLegacyGlobalDynamicWithTransform() {
		// when:
		subject.screenNew(withJust("defaultFeeCollectionAccount", "0.0.98"));

		// then:
		assertEquals(1, subject.from121.size());
		assertEquals(98L, subject.from121.get("ledger.fundingAccount"));
		// and:
		assertThat(logCaptor.warnLogs(), contains(
				String.format(DEPRECATED_PROP_TPL, "defaultFeeCollectionAccount", "ledger.fundingAccount")));
	}

	@Test
	void warnsOfUnparseableWhitelist() {
		// given:
		var unparseableValue = "CryptoCreate,CryptoTransfer,Oops";

		// when:
		subject.screenNew(withJust("scheduling.whitelist", unparseableValue));

		// then:
		assertTrue(subject.from121.isEmpty());
		// and:
		assertThat(logCaptor.warnLogs(), contains(
				String.format(
						UNPARSEABLE_PROP_TPL, unparseableValue, "scheduling.whitelist", "IllegalArgumentException")));
	}

	@Test
	void warnsOfUnusableWhitelist() {
		// given:
		var unusableValue = "CryptoCreate,CryptoTransfer,CryptoGetAccountBalance";

		// when:
		subject.screenNew(withJust("scheduling.whitelist", unusableValue));

		// then:
		assertTrue(subject.from121.isEmpty());
		// and:
		assertThat(logCaptor.warnLogs(), contains(
				String.format(UNUSABLE_PROP_TPL, unusableValue, "scheduling.whitelist")));
	}

	@Test
	void warnsOfUnusableMaxTokenNameUtf8Bytes() {
		// setup:
		String unsupportableValue = "" + (MerkleToken.UPPER_BOUND_TOKEN_NAME_UTF8_BYTES + 1);
		// when:
		subject.screenNew(withJust("tokens.maxTokenNameUtf8Bytes", unsupportableValue));

		// then:
		assertTrue(subject.from121.isEmpty());
		// and:
		assertThat(logCaptor.warnLogs(), contains(
				String.format(UNUSABLE_PROP_TPL, unsupportableValue, "tokens.maxTokenNameUtf8Bytes")));
	}

	@Test
	void warnsOfUnusableTransfersMaxLen() {
		// setup:
		String unsupportableValue = "" + 1;
		// when:
		subject.screenNew(withJust("ledger.transfers.maxLen", unsupportableValue));

		// then:
		assertTrue(subject.from121.isEmpty());
		// and:
		assertThat(logCaptor.warnLogs(), contains(
				String.format(UNUSABLE_PROP_TPL, unsupportableValue, "ledger.transfers.maxLen")));
	}

	@Test
	void warnsOfUnusableTokenTransfersMaxLen() {
		// setup:
		String unsupportableValue = "" + 1;
		// when:
		subject.screenNew(withJust("ledger.tokenTransfers.maxLen", unsupportableValue));

		// then:
		assertTrue(subject.from121.isEmpty());
		// and:
		assertThat(logCaptor.warnLogs(), contains(
				String.format(UNUSABLE_PROP_TPL, unsupportableValue, "ledger.tokenTransfers.maxLen")));
	}

	@Test
	void warnsOfUnusableMaxTokenSymbolUtf8Bytes() {
		// setup:
		String unsupportableValue = "" + (MerkleToken.UPPER_BOUND_SYMBOL_UTF8_BYTES + 1);
		// when:
		subject.screenNew(withJust("tokens.maxSymbolUtf8Bytes", unsupportableValue));

		// then:
		assertTrue(subject.from121.isEmpty());
		// and:
		assertThat(logCaptor.warnLogs(), contains(
				String.format(UNUSABLE_PROP_TPL, unsupportableValue, "tokens.maxSymbolUtf8Bytes")));
	}

	@Test
	void warnsOfUnusableGlobalDynamic() {
		// when:
		subject.screenNew(withJust("rates.intradayChangeLimitPercent", "-1"));

		// then:
		assertTrue(subject.from121.isEmpty());
		// and:
		assertThat(logCaptor.warnLogs(), contains(
				String.format(UNUSABLE_PROP_TPL, "-1", "rates.intradayChangeLimitPercent")));
	}

	@Test
	void warnsOfUntransformableGlobalDynamic() {
		// when:
		subject.screenNew(withJust("defaultFeeCollectionAccount", "abc"));

		// then:
		assertTrue(subject.from121.isEmpty());
		// and:
		assertThat(logCaptor.warnLogs(), contains(
				"Property name 'defaultFeeCollectionAccount' is deprecated, please use 'ledger.fundingAccount' instead!",
				String.format(
						UNTRANSFORMABLE_PROP_TPL, "abc", "defaultFeeCollectionAccount", "IllegalArgumentException"),
				"Property 'defaultFeeCollectionAccount' is not global/dynamic, please find it a proper home!"));
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
