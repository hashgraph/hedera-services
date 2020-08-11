package com.hedera.services.files.interceptors;

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

import com.hedera.services.config.MockAccountNumbers;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;

import static com.hedera.services.files.interceptors.TxnAwareAuthPolicy.NO_DELETE_VERDICT;
import static com.hedera.services.files.interceptors.TxnAwareAuthPolicy.UNAUTHORIZED_VERDICT;
import static com.hedera.services.files.interceptors.TxnAwareAuthPolicy.YES_VERDICT;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class TxnAwareAuthPolicyTest {
	FileID book = asFile("0.0.101");
	FileID nodeDetails = asFile("0.0.102");
	FileID feeSchedule = asFile("0.0.111");
	FileID exchangeRates = asFile("0.0.112");
	FileID appProps = asFile("0.0.121");
	FileID apiPerms = asFile("0.0.122");
	FileID user = asFile("0.0.13257");

	AccountID civilian = asAccount("0.0.13257");
	AccountID treasury = IdUtils.asAccount("0.0.2");
	AccountID master = IdUtils.asAccount("0.0.50");
	AccountID bookAndDetailsAdmin = IdUtils.asAccount("0.0.55");
	AccountID feesAdmin = IdUtils.asAccount("0.0.56");
	AccountID ratesAdmin = IdUtils.asAccount("0.0.57");
	byte[] SOME_BYTES = "abcdefghijklmnop".getBytes();
	JFileInfo attr;

	PropertySource properties;
	TransactionContext txnCtx;

	TxnAwareAuthPolicy subject;

	@BeforeEach
	private void setup() {
		attr = new JFileInfo(
				false,
				new JContractIDKey(1, 2, 3),
				Instant.now().getEpochSecond());
		txnCtx = mock(TransactionContext.class);

		properties = mock(PropertySource.class);
		given(properties.getLongProperty("hedera.numReservedSystemEntities")).willReturn(1_000L);

		subject = new TxnAwareAuthPolicy(
				new MockFileNumbers(),
				new MockAccountNumbers(),
				properties,
				txnCtx);
	}

	@Test
	public void permitsUserspaceUpdatesAlways() {
		givenPayer(civilian);

		// expect:
		assertEquals(YES_VERDICT, subject.preUpdate(user, SOME_BYTES));
		assertEquals(YES_VERDICT, subject.preAttrChange(user, attr));
	}

	@Test
	public void permitsExchangeRatesAdminAsExpected() {
		givenPayer(ratesAdmin);

		// expect:
		assertEquals(YES_VERDICT, subject.preUpdate(exchangeRates, SOME_BYTES));
		assertEquals(YES_VERDICT, subject.preAttrChange(exchangeRates, attr));
		assertEquals(YES_VERDICT, subject.preUpdate(appProps, SOME_BYTES));
		assertEquals(YES_VERDICT, subject.preAttrChange(appProps, attr));
		assertEquals(YES_VERDICT, subject.preUpdate(apiPerms, SOME_BYTES));
		assertEquals(YES_VERDICT, subject.preAttrChange(apiPerms, attr));
		// and:
		assertEquals(UNAUTHORIZED_VERDICT, subject.preUpdate(feeSchedule, SOME_BYTES));
		assertEquals(UNAUTHORIZED_VERDICT, subject.preAttrChange(feeSchedule, attr));
	}

	@Test
	public void permitsFeeSchedulesAdminAsExpected() {
		givenPayer(feesAdmin);

		// expect:
		assertEquals(YES_VERDICT, subject.preUpdate(feeSchedule, SOME_BYTES));
		// and:
		assertEquals(UNAUTHORIZED_VERDICT, subject.preUpdate(appProps, SOME_BYTES));
		assertEquals(UNAUTHORIZED_VERDICT, subject.preUpdate(apiPerms, SOME_BYTES));
	}

	@Test
	public void permitsBookAdminAsExpected() {
		givenPayer(bookAndDetailsAdmin);

		// expect:
		assertEquals(YES_VERDICT, subject.preUpdate(book, SOME_BYTES));
		assertEquals(YES_VERDICT, subject.preUpdate(nodeDetails, SOME_BYTES));
		assertEquals(YES_VERDICT, subject.preUpdate(appProps, SOME_BYTES));
		assertEquals(YES_VERDICT, subject.preUpdate(apiPerms, SOME_BYTES));
		// and:
		assertEquals(UNAUTHORIZED_VERDICT, subject.preUpdate(feeSchedule, SOME_BYTES));
	}

	@Test
	public void permitsTreasuryUpdatesAlways() {
		givenPayer(treasury);

		// expect:
		assertEquals(YES_VERDICT, subject.preUpdate(book, SOME_BYTES));
	}


	@Test
	public void permitsMasterUpdatesToBookAndDetails() {
		givenPayer(master);

		// expect:
		for (int i = 101; i <= 102; i++) {
			assertEquals(YES_VERDICT, subject.preUpdate(asFile(String.format("0.0.%d", i)), SOME_BYTES));
		}
	}

	@Test
	public void permitsMasterUpdatesToFeesAndRates() {
		givenPayer(master);

		// expect:
		for (int i = 111; i <= 112; i++) {
			assertEquals(YES_VERDICT, subject.preUpdate(asFile(String.format("0.0.%d", i)), SOME_BYTES));
		}
	}

	@Test
	public void permitsMasterUpdatesToAppPropsApiPerms() {
		givenPayer(master);

		// expect:
		for (int i = 121; i <= 122; i++) {
			assertEquals(YES_VERDICT, subject.preUpdate(asFile(String.format("0.0.%d", i)), SOME_BYTES));
		}
	}

	@Test
	public void rejectsMasterOutOfScope() {
		givenPayer(master);

		assertEquals(UNAUTHORIZED_VERDICT, subject.preUpdate(asFile(String.format("0.0.%d", 81)), SOME_BYTES));
	}

	@Test
	public void postUpdateIsNoop() {
		// expect:
		assertDoesNotThrow(() -> subject.postUpdate(book, SOME_BYTES));
	}

	@Test
	public void rejectsCivilianUpdatesAlways() {
		givenPayer(civilian);

		// expect:
		for (int i = 1; i <= 1_000; i++) {
			assertEquals(UNAUTHORIZED_VERDICT, subject.preUpdate(asFile(String.format("0.0.%d", i)), SOME_BYTES));
		}
	}

	@Test
	public void hasExpectedRelevanceAndPriority() {
		// expect:
		assertTrue(subject.priorityForCandidate(asFile("0.0.13257")).isEmpty());
		// and:
		assertEquals(Integer.MIN_VALUE, subject.priorityForCandidate(book).getAsInt());
	}

	@Test
	public void refusesAllSystemDeletes() {
		// given:
		var verdict = subject.preDelete(book);

		// expect:
		assertEquals(NO_DELETE_VERDICT, verdict);
	}

	@Test
	public void allowsAllUserspaceDeletes() {
		// given:
		var verdict = subject.preDelete(user);

		// expect:
		assertEquals(YES_VERDICT, verdict);
	}

	private void givenPayer(AccountID id) {
		given(txnCtx.activePayer()).willReturn(id);
	}
}
