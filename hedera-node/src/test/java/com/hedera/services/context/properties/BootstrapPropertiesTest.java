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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class BootstrapPropertiesTest {
	BootstrapProperties subject = new BootstrapProperties();

	private String STD_PROPS_RESOURCE = "bootstrap/standard.properties";
	private String INVALID_PROPS_RESOURCE = "bootstrap/not.properties";
	private String UNREADABLE_PROPS_RESOURCE = "bootstrap/unreadable.properties";
	private String INCOMPLETE_STD_PROPS_RESOURCE = "bootstrap/incomplete.properties";

	private String OVERRIDE_PROPS_LOC = "src/test/resources/bootstrap/override.properties";
	private String EMPTY_OVERRIDE_PROPS_LOC = "src/test/resources/bootstrap/empty-override.properties";

	private static final Map<String, Object> expectedProps = Map.ofEntries(
			entry("bootstrap.feeSchedulesJson.resource", "FeeSchedule.json"),
			entry("bootstrap.genesisB64Keystore.keyName", "START_ACCOUNT"),
			entry("bootstrap.genesisB64Keystore.path", "data/onboard/StartUpAccount.txt"),
			entry("bootstrap.genesisPemPassphrase.path", "TBD"),
			entry("bootstrap.genesisPem.path", "TBD"),
			entry("bootstrap.hapiPermissions.path", "data/config/api-permission.properties"),
			entry("bootstrap.ledger.nodeAccounts.initialBalance", 0L),
			entry("bootstrap.ledger.systemAccounts.initialBalance", 0L),
			entry("bootstrap.networkProperties.path", "data/config/application.properties"),
			entry("bootstrap.rates.currentHbarEquiv", 1),
			entry("bootstrap.rates.currentCentEquiv", 12),
			entry("bootstrap.rates.currentExpiry", 4102444800L),
			entry("bootstrap.rates.nextHbarEquiv", 1),
			entry("bootstrap.rates.nextCentEquiv", 15),
			entry("bootstrap.rates.nextExpiry", 4102444800L),
			entry("bootstrap.system.entityExpiry", 4102444800L),
			entry("accounts.addressBookAdmin", 55L),
			entry("balances.exportDir.path", "/opt/hgcapp/accountBalances/"),
			entry("balances.exportEnabled", true),
			entry("balances.exportPeriodSecs", 600),
			entry("balances.exportTokenBalances", true),
			entry("balances.nodeBalanceWarningThreshold", 0L),
			entry("accounts.exchangeRatesAdmin", 57L),
			entry("accounts.feeSchedulesAdmin", 56L),
			entry("accounts.freezeAdmin", 58L),
			entry("accounts.systemAdmin", 50L),
			entry("accounts.systemDeleteAdmin", 59L),
			entry("accounts.systemUndeleteAdmin", 60L),
			entry("accounts.treasury", 2L),
			entry("contracts.defaultLifetime", 7890000L),
			entry("contracts.localCall.estRetBytes", 32),
			entry("contracts.maxGas", 300000),
			entry("contracts.maxStorageKb", 1024),
			entry("files.addressBook", 101L),
			entry("files.diskFsBaseDir.path", "data/diskFs/"),
			entry("files.networkProperties", 121L),
			entry("files.exchangeRates", 112L),
			entry("files.feeSchedules", 111L),
			entry("files.hapiPermissions", 122L),
			entry("files.nodeDetails", 102L),
			entry("files.softwareUpdateZip", 150L),
			entry("grpc.port", 50211),
			entry("grpc.tlsPort", 50212),
			entry("hedera.numReservedSystemEntities", 1_000L),
			entry("hedera.profiles.active", Profile.PROD),
			entry("hedera.realm", 0L),
			entry("hedera.shard", 0L),
			entry("hedera.transaction.maxMemoUtf8Bytes", 100),
			entry("hedera.transaction.minValidDuration", 15L),
			entry("hedera.transaction.maxValidDuration", 180L),
			entry("hedera.transaction.minValidityBufferSecs", 10),
			entry("ledger.fundingAccount", 98L),
			entry("ledger.keepRecordsInState", false),
			entry("ledger.maxAccountNum", 100_000_000L),
			entry("ledger.numSystemAccounts", 100),
			entry("ledger.transfers.maxLen", 10),
			entry("ledger.tokenTransfers.maxLen", 10),
			entry("ledger.totalTinyBarFloat", 5000000000000000000L),
			entry("ledger.autoRenewPeriod.maxDuration", 8000001L),
			entry("ledger.autoRenewPeriod.minDuration", 6999999L),
			entry("precheck.account.maxLookupRetries", 10),
			entry("precheck.account.lookupRetryBackoffIncrementMs", 10),
			entry("tokens.maxPerAccount", 1_000),
			entry("tokens.maxSymbolUtf8Bytes", 100),
			entry("tokens.maxTokenNameUtf8Bytes",100),
			entry("files.maxSizeKb", 1024),
			entry("fees.tokenTransferUsageMultiplier", 380),
			entry("cache.records.ttl", 180),
			entry("rates.intradayChangeLimitPercent", 25),
			entry("stats.runningAvgHalfLifeSecs", 10.0),
			entry("stats.hapiOps.speedometerUpdateIntervalMs", 3_000L),
			entry("stats.speedometerHalfLifeSecs", 10.0)
	);

	@BeforeEach
	void setUp() {
		subject.BOOTSTRAP_OVERRIDE_PROPS_LOC = EMPTY_OVERRIDE_PROPS_LOC;
	}

	@Test
	public void throwsIseIfUnreadable() {
		// given:
		subject.BOOTSTRAP_PROPS_RESOURCE = UNREADABLE_PROPS_RESOURCE;

		// expect:
		assertThrows(IllegalStateException.class, subject::ensureProps);
	}

	@Test
	public void throwsIseIfIoExceptionOccurs() {
		// setup:
		var bkup = BootstrapProperties.resourceStreamProvider;
		subject.BOOTSTRAP_PROPS_RESOURCE = STD_PROPS_RESOURCE;
		// and:
		BootstrapProperties.resourceStreamProvider = ignore -> {
			throw new IOException("Oops!");
		};

		// expect:
		assertThrows(IllegalStateException.class, subject::ensureProps);

		// cleanup:
		BootstrapProperties.resourceStreamProvider = bkup;
	}

	@Test
	public void throwsIseIfInvalid() {
		// given:
		subject.BOOTSTRAP_PROPS_RESOURCE = INVALID_PROPS_RESOURCE;

		// expect:
		assertThrows(IllegalStateException.class, subject::ensureProps);
	}

	@Test
	public void ensuresFilePropsFromExtant() {
		// given:
		subject.BOOTSTRAP_PROPS_RESOURCE = STD_PROPS_RESOURCE;

		// when:
		subject.ensureProps();

		// then:
		for (String name : BootstrapProperties.BOOTSTRAP_PROP_NAMES) {
			assertEquals(expectedProps.get(name), subject.getProperty(name), name + " has the wrong value!");
		}
		// and:
		assertEquals(expectedProps, subject.bootstrapProps);
	}

	@Test
	public void includesOverrides() {
		// given:
		subject.BOOTSTRAP_PROPS_RESOURCE = STD_PROPS_RESOURCE;
		subject.BOOTSTRAP_OVERRIDE_PROPS_LOC = OVERRIDE_PROPS_LOC;

		// when:
		subject.ensureProps();

		// then:
		assertEquals(30, subject.getProperty("tokens.maxPerAccount"));
	}

	@Test
	public void doesntThrowOnMissingOverridesFile() {
		// given:
		subject.BOOTSTRAP_PROPS_RESOURCE = STD_PROPS_RESOURCE;
		subject.BOOTSTRAP_OVERRIDE_PROPS_LOC = "im-not-here";

		// expect:
		assertDoesNotThrow(subject::ensureProps);
	}

	@Test
	public void throwsIaeOnMissingPropRequest() {
		// given:
		subject.BOOTSTRAP_PROPS_RESOURCE = STD_PROPS_RESOURCE;
		// and:
		subject.ensureProps();

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.getProperty("not-a-real-prop"));
	}

	@Test
	public void throwsIseIfMissingProps() {
		// given:
		subject.BOOTSTRAP_PROPS_RESOURCE = INCOMPLETE_STD_PROPS_RESOURCE;

		// when:
		assertThrows(IllegalStateException.class, subject::ensureProps);
	}

	@Test
	public void logsLoadedPropsOnInit() {
		// setup:
		BootstrapProperties.log = mock(Logger.class);

		// given:
		subject.BOOTSTRAP_PROPS_RESOURCE = STD_PROPS_RESOURCE;
		// and:
		subject.getProperty("bootstrap.feeSchedulesJson.resource");

		// expect:
		verify(BootstrapProperties.log).info(
				argThat((String s) -> s.startsWith("Resolved bootstrap")));
		// cleanup:
		BootstrapProperties.log = LogManager.getLogger(BootstrapProperties.class);
	}
}
