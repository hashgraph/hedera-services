package com.hedera.services.ledger.accounts;

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

import com.hedera.services.ledger.properties.TestAccountProperty;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hedera.services.ledger.properties.TestAccountProperty.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.*;

@RunWith(JUnitPlatform.class)
public class AccountCustomizerTest {
	private TestAccountCustomizer subject;
	private ChangeSummaryManager<TestAccount, TestAccountProperty> changeManager;

	private void setupWithMockChangeManager() {
		changeManager = mock(ChangeSummaryManager.class);
		subject = new TestAccountCustomizer(changeManager);
	}

	private void setupWithLiveChangeManager() {
		subject = new TestAccountCustomizer(new ChangeSummaryManager<>());
	}

	@Test
	public void directlyCustomizesAnAccount() {
		setupWithLiveChangeManager();

		// given:
		TestAccount ta = subject.isDeleted(true)
				.expiry(55L)
				.memo("Something!")
				.customizing(new TestAccount());

		// expect:
		assertEquals(ta.value, 55L);
		assertTrue(ta.flag);
		assertEquals(ta.thing, "Something!");
	}

	@Test
	public void setsCustomizedProperties() {
		setupWithLiveChangeManager();

		// given:
		Long id = 1L;
		TransactionalLedger<Long, TestAccountProperty, TestAccount> ledger = mock(TransactionalLedger.class);
		// and:
		String customMemo = "alpha bravo charlie";
		boolean customIsReceiverSigRequired = true;

		// when:
		subject
				.isReceiverSigRequired(customIsReceiverSigRequired)
				.memo(customMemo);
		// and:
		subject.customize(id, ledger);

		// then:
		verify(ledger).set(id, OBJ, customMemo);
		verify(ledger).set(id, FLAG, customIsReceiverSigRequired);
	}

	@Test
	public void changesExpectedKeyProperty() {
		setupWithMockChangeManager();

		// given:
		JKey key = new JKeyList();

		// when:
		subject.key(key);

		// expect:
		verify(changeManager).update(
				any(EnumMap.class),
				argThat(TestAccountCustomizer.OPTION_PROPERTIES.get(KEY)::equals),
				argThat(key::equals));
	}

	@Test
	public void changesExpectedMemoProperty() {
		setupWithMockChangeManager();

		// given:
		String memo = "standardization ftw?";

		// when:
		subject.memo(memo);

		// expect:
		verify(changeManager).update(
				any(EnumMap.class),
				argThat(TestAccountCustomizer.OPTION_PROPERTIES.get(MEMO)::equals),
				argThat(memo::equals));
	}

	@Test
	public void changesExpectedProxyProperty() {
		setupWithMockChangeManager();

		// given:
		EntityId proxy = new EntityId();

		// when:
		subject.proxy(proxy);

		// expect:
		verify(changeManager).update(
				any(EnumMap.class),
				argThat(TestAccountCustomizer.OPTION_PROPERTIES.get(PROXY)::equals),
				argThat(proxy::equals));
	}

	@Test
	public void changesExpectedExpiryProperty() {
		setupWithMockChangeManager();

		// given:
		Long expiry = 1L;

		// when:
		subject.expiry(expiry);

		// expect:
		verify(changeManager).update(
				any(EnumMap.class),
				argThat(TestAccountCustomizer.OPTION_PROPERTIES.get(EXPIRY)::equals),
				argThat(expiry::equals));
	}

	@Test
	public void changesExpectedAutoRenewProperty() {
		setupWithMockChangeManager();

		// given:
		Long autoRenew = 1L;

		// when:
		subject.autoRenewPeriod(autoRenew);

		// expect:
		verify(changeManager).update(
				any(EnumMap.class),
				argThat(TestAccountCustomizer.OPTION_PROPERTIES.get(AUTO_RENEW_PERIOD)::equals),
				argThat(autoRenew::equals));
	}

	@Test
	public void changesExpectedIsSmartContractProperty() {
		setupWithMockChangeManager();

		// given:
		Boolean isSmartContract = Boolean.TRUE;

		// when:
		subject.isSmartContract(isSmartContract);

		// expect:
		verify(changeManager).update(
				any(EnumMap.class),
				argThat(TestAccountCustomizer.OPTION_PROPERTIES.get(IS_SMART_CONTRACT)::equals),
				argThat(isSmartContract::equals));
	}

	@Test
	public void changesExpectedIsDeletedProperty() {
		setupWithMockChangeManager();

		// given:
		Boolean isDeleted = Boolean.TRUE;

		// when:
		subject.isDeleted(isDeleted);

		// expect:
		verify(changeManager).update(
				any(EnumMap.class),
				argThat(TestAccountCustomizer.OPTION_PROPERTIES.get(IS_DELETED)::equals),
				argThat(isDeleted::equals));
	}

	@Test
	public void changesExpectedReceiverSigRequiredProperty() {
		setupWithMockChangeManager();

		// given:
		Boolean isSigRequired = Boolean.FALSE;

		// when:
		subject.isReceiverSigRequired(isSigRequired);

		// expect:
		verify(changeManager).update(
				any(EnumMap.class),
				argThat(TestAccountCustomizer.OPTION_PROPERTIES.get(IS_RECEIVER_SIG_REQUIRED)::equals),
				argThat(isSigRequired::equals));
	}
}
