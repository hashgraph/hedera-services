package com.hedera.services.properties;

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

import com.hedera.services.context.properties.StandardizedPropertySources;
import com.hedera.services.context.properties.PropertySource;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import com.hedera.services.legacy.logic.ApplicationConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.*;
import static com.hedera.services.throttling.bucket.BucketConfig.*;
import static com.hedera.services.context.properties.StandardizedPropertySources.*;

@RunWith(JUnitPlatform.class)
public class StandardizedPropertySourcesTest {
	Predicate fileSourceExists;
	PropertySource bootstrapProps;

	StandardizedPropertySources subject;

	@BeforeEach
	private void setup() {
		bootstrapProps = mock(PropertySource.class);
		fileSourceExists = mock(Predicate.class);
	}

	@Test
	void initsLegacyPropsFlagToTrue() {
		givenImpliedSubject();

		// given:
		var properties = subject.asResolvingSource();

		// expect:
		assertTrue(properties.getBooleanProperty(StandardizedPropertySources.RESPECT_LEGACY_THROTTLING_PROPERTY));
	}

	@Test
	void restoresLegacyPropsFlagToTrueIfMissing() {
		givenImpliedSubject();

		// given:
		var properties = subject.asResolvingSource();
		// and:
		subject.updateThrottlePropsFrom(ServicesConfigurationList.getDefaultInstance());

		// expect:
		assertTrue(properties.getBooleanProperty(StandardizedPropertySources.RESPECT_LEGACY_THROTTLING_PROPERTY));
	}

	@Test
	void castsThrottlePropsAppropriately() {
		// setup:
		var bucket = "B";
		var function = HederaFunctionality.ContractGetRecords;
		var config = ServicesConfigurationList.newBuilder()
				.addNameValue(from("random", "no-throttle-relation"))
				.addNameValue(from(RESPECT_LEGACY_THROTTLING_PROPERTY, "false"))
				.addNameValue(from(DEFAULT_BURST_PROPERTY, "1.23"))
				.addNameValue(from(DEFAULT_CAPACITY_PROPERTY, "1.23"))
				.addNameValue(from(DEFAULT_QUERY_CAPACITY_REQUIRED_PROPERTY, "1.23"))
				.addNameValue(from(DEFAULT_TXN_CAPACITY_REQUIRED_PROPERTY, "1.23"))
				.addNameValue(from(DEFAULT_QUERY_BUCKET_PROPERTY, "something"))
				.addNameValue(from(capacityProperty.apply(bucket), "1.23"))
				.addNameValue(from(burstProperty.apply(bucket), "1.23"))
				.addNameValue(from(asCapacityRequiredProperty.apply(function), "1.23"))
				.build();

		givenImpliedSubject();
		// and:
		var props = subject.asResolvingSource();

		// when:
		subject.updateThrottlePropsFrom(config);

		// then:
		assertFalse(props.getBooleanProperty(RESPECT_LEGACY_THROTTLING_PROPERTY));
		assertEquals(1.23, props.getDoubleProperty(DEFAULT_BURST_PROPERTY));
		assertEquals(1.23, props.getDoubleProperty(DEFAULT_CAPACITY_PROPERTY));
		assertEquals(1.23, props.getDoubleProperty(DEFAULT_QUERY_CAPACITY_REQUIRED_PROPERTY));
		assertEquals(1.23, props.getDoubleProperty(DEFAULT_TXN_CAPACITY_REQUIRED_PROPERTY));
		assertEquals(1.23, props.getDoubleProperty(capacityProperty.apply(bucket)));
		assertEquals(1.23, props.getDoubleProperty(burstProperty.apply(bucket)));
		assertEquals(1.23, props.getDoubleProperty(asCapacityRequiredProperty.apply(function)));
		assertEquals("something", props.getStringProperty(DEFAULT_QUERY_BUCKET_PROPERTY));
	}

	@Test
	void recoversFromUncastableProps() {
		// setup:
		var bucket = "B";
		var function = HederaFunctionality.ContractGetRecords;
		var config = ServicesConfigurationList.newBuilder()
				.addNameValue(from(DEFAULT_BURST_PROPERTY, "asdf"))
				.build();

		givenImpliedSubject();
		// and:
		var props = subject.asResolvingSource();

		// when:
		subject.updateThrottlePropsFrom(config);

		// then:
		assertFalse(props.containsProperty(DEFAULT_BURST_PROPERTY));
	}

	private Setting from(String name, String value) {
		return Setting.newBuilder()
				.setName(name)
				.setValue(value)
				.build();
	}

	@Test
	public void hasExpectedProps() {
		given(fileSourceExists.test(any())).willReturn(true);
		givenImpliedSubject();

		// when:
		PropertySource properties = subject.asResolvingSource();

		// then:
		assertTrue(properties.containsProperty("bootstrap.genesisB64Keystore.keyName"));
		assertTrue(properties.containsProperty("bootstrap.genesisB64Keystore.path"));
		assertTrue(properties.containsProperty("bootstrap.feeSchedulesJson.resource"));
		assertTrue(properties.containsProperty("bootstrap.permissions.path"));
		assertTrue(properties.containsProperty("bootstrap.properties.path"));
		assertTrue(properties.containsProperty("bootstrap.rates.currentHbarEquiv"));
		assertTrue(properties.containsProperty("bootstrap.rates.currentCentEquiv"));
		assertTrue(properties.containsProperty("bootstrap.rates.currentExpiry"));
		assertTrue(properties.containsProperty("bootstrap.rates.nextHbarEquiv"));
		assertTrue(properties.containsProperty("bootstrap.rates.nextCentEquiv"));
		assertTrue(properties.containsProperty("bootstrap.rates.nextExpiry"));
		assertTrue(properties.containsProperty("bootstrap.systemFilesExpiry"));
		assertTrue(properties.containsProperty("cache.records.ttl"));
		assertTrue(properties.containsProperty("contracts.defaultSendThreshold"));
		assertTrue(properties.containsProperty("contracts.defaultReceiveThreshold"));
		assertTrue(properties.containsProperty("contracts.maxStorageKb"));
		assertTrue(properties.containsProperty("dev.defaultListeningNodeAccount"));
		assertTrue(properties.containsProperty("dev.onlyDefaultNodeListens"));
		assertTrue(properties.containsProperty("exchangeRates.intradayChange.limitPercent"));
		assertTrue(properties.containsProperty("files.addressBook.num"));
		assertTrue(properties.containsProperty("files.addressBookAdmin.idNum"));
		assertTrue(properties.containsProperty("files.apiPermissions.num"));
		assertTrue(properties.containsProperty("files.applicationProperties.num"));
		assertTrue(properties.containsProperty("files.exchangeRates.num"));
		assertTrue(properties.containsProperty("files.exchangeRatesAdmin.idNum"));
		assertTrue(properties.containsProperty("files.feeSchedules.num"));
		assertTrue(properties.containsProperty("files.feeSchedulesAdmin.idNum"));
		assertTrue(properties.containsProperty("files.firstInAdminScope.num"));
		assertTrue(properties.containsProperty("files.lastInAdminScope.num"));
		assertTrue(properties.containsProperty("files.maxSizeKb"));
		assertTrue(properties.containsProperty("files.nodeDetails.num"));
		assertTrue(properties.containsProperty("grpc.port"));
		assertTrue(properties.containsProperty("hedera.accountsExportPath"));
		assertTrue(properties.containsProperty("hedera.createSystemFilesOnStartup"));
		assertTrue(properties.containsProperty("hedera.createSystemAccountsOnStartup"));
		assertTrue(properties.containsProperty("hedera.exitOnNodeStartupFailure"));
		assertTrue(properties.containsProperty("hedera.exportAccountsOnStartup"));
		assertTrue(properties.containsProperty("hedera.exportBalancesOnNewSignedState"));
		assertTrue(properties.containsProperty("hedera.firstProtectedEntity.num"));
		assertTrue(properties.containsProperty("hedera.lastProtectedEntity.num"));
		assertTrue(properties.containsProperty("hedera.masterAccount.idNum"));
		assertTrue(properties.containsProperty("hedera.profiles.active"));
		assertTrue(properties.containsProperty("hedera.realm"));
		assertTrue(properties.containsProperty("hedera.recordStream.logDir"));
		assertTrue(properties.containsProperty("hedera.recordStream.logPeriod"));
		assertTrue(properties.containsProperty("hedera.shard"));
		assertTrue(properties.containsProperty("hedera.transaction.maxMemoUtf8Bytes"));
		assertTrue(properties.containsProperty("hedera.transaction.maxValidDuration"));
		assertTrue(properties.containsProperty("hedera.transaction.minValidDuration"));
		assertTrue(properties.containsProperty("hedera.transaction.minValididityBufferSecs"));
		assertTrue(properties.containsProperty("hedera.treasuryAccount.idNum"));
		assertTrue(properties.containsProperty("hedera.versionInfo.resource"));
		assertTrue(properties.containsProperty("hedera.versionInfo.protoKey"));
		assertTrue(properties.containsProperty("hedera.versionInfo.servicesKey"));
		assertTrue(properties.containsProperty("iss.reset.periodSecs"));
		assertTrue(properties.containsProperty("iss.roundsToDump"));
		assertTrue(properties.containsProperty("ledger.autoRenewPeriod.maxDuration"));
		assertTrue(properties.containsProperty("ledger.autoRenewPeriod.minDuration"));
		assertTrue(properties.containsProperty("ledger.float.hbars"));
		assertTrue(properties.containsProperty("ledger.funding.account"));
		assertTrue(properties.containsProperty("ledger.records.ttl"));
		assertTrue(properties.containsProperty("ledger.systemAccount.initialHbars"));
		assertTrue(properties.containsProperty("ledger.transfers.maxLen"));
		assertTrue(properties.containsProperty("throttling.hcs.createTopic.tps"));
		assertTrue(properties.containsProperty("throttling.hcs.createTopic.burstPeriod"));
		assertTrue(properties.containsProperty("throttling.hcs.updateTopic.tps"));
		assertTrue(properties.containsProperty("throttling.hcs.updateTopic.burstPeriod"));
		assertTrue(properties.containsProperty("throttling.hcs.deleteTopic.tps"));
		assertTrue(properties.containsProperty("throttling.hcs.deleteTopic.burstPeriod"));
		assertTrue(properties.containsProperty("throttling.hcs.submitMessage.tps"));
		assertTrue(properties.containsProperty("throttling.hcs.submitMessage.burstPeriod"));
		assertTrue(properties.containsProperty("throttling.hcs.getTopicInfo.tps"));
		assertTrue(properties.containsProperty("throttling.hcs.getTopicInfo.burstPeriod"));
		assertTrue(properties.containsProperty("validation.preConsensus.accountKey.maxLookupRetries"));
		assertTrue(properties.containsProperty("validation.preConsensus.accountKey.retryBackoffIncrementMs"));
	}

	@Test
	public void selectedSuppliersWork() {
		given(fileSourceExists.test(any())).willReturn(true);
		givenImpliedSubject();

		// when:
		PropertySource properties = subject.asResolvingSource();

		// then:
		assertDoesNotThrow(() ->
				properties.getBooleanProperty("hedera.createSystemFilesOnStartup"));
		assertDoesNotThrow(() ->
				properties.getIntProperty("validation.preConsensus.accountKey.maxLookupRetries"));
		assertDoesNotThrow(() ->
				properties.getIntProperty("validation.preConsensus.accountKey.retryBackoffIncrementMs"));
	}

	@Test
	public void failsOnMissingAppProps() {
		given(fileSourceExists.test(ApplicationConstants.PROPERTY_FILE)).willReturn(false);
		givenImpliedSubject();

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.assertSourcesArePresent());
	}

	@Test
	public void failsOnMissingApiPermissionProps() {
		given(fileSourceExists.test(ApplicationConstants.PROPERTY_FILE)).willReturn(true);
		given(fileSourceExists.test(ApplicationConstants.API_ACCESS_FILE)).willReturn(false);
		givenImpliedSubject();

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.assertSourcesArePresent());
	}

	@Test
	public void uneventfulInitIfSourcesAvailable() {
		given(fileSourceExists.test(any())).willReturn(true);
		givenImpliedSubject();

		// expect:
		assertDoesNotThrow(() -> subject.assertSourcesArePresent());
	}

	private void givenImpliedSubject() {
		subject = new StandardizedPropertySources(bootstrapProps, fileSourceExists);
	}
}
