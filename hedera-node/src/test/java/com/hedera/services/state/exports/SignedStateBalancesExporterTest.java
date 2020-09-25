package com.hedera.services.state.exports;

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

import com.hedera.services.ServicesState;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.time.Instant;

import static com.hedera.services.state.merkle.MerkleEntityAssociation.fromAccountTokenRel;
import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;
import static com.hedera.services.state.merkle.MerkleEntityId.fromTokenId;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(JUnitPlatform.class)
class SignedStateBalancesExporterTest {
	FCMap<MerkleEntityId, MerkleToken> tokens = new FCMap<>();
	FCMap<MerkleEntityId, MerkleAccount> accounts = new FCMap<>();
	FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenRels = new FCMap<>();

	MerkleToken token;

	long ledgerFloat = 1_000;

	long thisNodeBalance = 400;
	AccountID thisNode = asAccount("0.0.3");
	long anotherNodeBalance = 100;
	AccountID anotherNode = asAccount("0.0.4");
	long firstNonNodeAccountBalance = 250;
	AccountID firstNonNode = asAccount("0.0.1001");
	long secondNonNodeAccountBalance = 250;
	AccountID secondNonNode = asAccount("0.0.1002");
	AccountID deleted = asAccount("0.0.1003");

	TokenID theToken = asToken("0.0.1004");
	long secondNonNodeTokenBalance = 100;

	GlobalDynamicProperties dynamicProperties = new MockGlobalDynamicProps();

	Instant now = Instant.now();
	Instant shortlyAfter = now.plusSeconds(dynamicProperties.balancesExportPeriodSecs() / 2);
	Instant anEternityLater = now.plusSeconds(dynamicProperties.balancesExportPeriodSecs() * 2);

	ServicesState state;
	PropertySource properties;
	DirectoryAssurance assurance;

	SignedStateBalancesExporter subject;

	@BeforeEach
	public void setUp() throws Exception {
		var thisNodeAccount = MerkleAccountFactory.newAccount().balance(thisNodeBalance).get();
		var anotherNodeAccount = MerkleAccountFactory.newAccount().balance(anotherNodeBalance).get();
		var firstNonNodeAccount = MerkleAccountFactory.newAccount().balance(firstNonNodeAccountBalance).get();
		var secondNonNodeAccount = MerkleAccountFactory.newAccount().balance(secondNonNodeAccountBalance).get();
		var deletedAccount = MerkleAccountFactory.newAccount().accountDeleted(true).get();

		accounts.put(fromAccountId(thisNode), thisNodeAccount);
		accounts.put(fromAccountId(anotherNode), anotherNodeAccount);
		accounts.put(fromAccountId(firstNonNode), firstNonNodeAccount);
		accounts.put(fromAccountId(secondNonNode), secondNonNodeAccount);
		accounts.put(fromAccountId(deleted), deletedAccount);

		token = mock(MerkleToken.class);
		given(token.isDeleted()).willReturn(true);
		tokens.put(fromTokenId(theToken), token);

		tokenRels.put(
				fromAccountTokenRel(secondNonNode, theToken),
				new MerkleTokenRelStatus(secondNonNodeTokenBalance, false, true));

		assurance = mock(DirectoryAssurance.class);

		properties = mock(PropertySource.class);
		given(properties.getLongProperty("ledger.totalTinyBarFloat")).willReturn(ledgerFloat);

		state = mock(ServicesState.class);
		given(state.getNodeAccountId()).willReturn(thisNode);

		subject = new SignedStateBalancesExporter(properties, dynamicProperties);
	}

	@Test
	public void assuresExpectedDir() throws IOException {
		// given:
		subject.directories = assurance;

		// when:
		subject.toCsvFile(state, now);

		// then:
		verify(assurance).ensureExistenceOf(
				dynamicProperties.pathToBalancesExportDir()
						+ File.separator + "balance0.0.3" + File.separator);
	}

	@Test
	public void initsAsExpected() {
		// expect:
		assertEquals(ledgerFloat, subject.expectedFloat);
		assertEquals(SignedStateBalancesExporter.NEVER, subject.periodEnd);
	}

	@Test
	public void exportsWhenPeriodSecsHaveElapsed() {
		assertFalse(subject.isTimeToExport(now));
		assertEquals(now.plusSeconds(dynamicProperties.balancesExportPeriodSecs()), subject.periodEnd);
		assertFalse(subject.isTimeToExport(shortlyAfter));
		assertEquals(now.plusSeconds(dynamicProperties.balancesExportPeriodSecs()), subject.periodEnd);
		assertTrue(subject.isTimeToExport(anEternityLater));
		assertEquals(anEternityLater.plusSeconds(dynamicProperties.balancesExportPeriodSecs()), subject.periodEnd);
	}

	@Test
	public void doesntExportWhenNotEnabled() {
		// setup:
		var otherDynamicProperties = new MockGlobalDynamicProps() {
			@Override
			public boolean shouldExportBalances() {
				return false;
			}
		};
		subject = new SignedStateBalancesExporter(properties, otherDynamicProperties);

		assertFalse(subject.isTimeToExport(now));
		assertFalse(subject.isTimeToExport(anEternityLater));
	}
}