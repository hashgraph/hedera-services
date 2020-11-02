package com.hedera.services.fees;

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
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.services.security.ops.SystemOpAuthorization.AUTHORIZED;
import static com.hedera.services.security.ops.SystemOpAuthorization.UNNECESSARY;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class StandardExemptionsTest {
	SystemOpPolicies policies;
	SignedTxnAccessor accessor;

	StandardExemptions subject;

	@BeforeEach
	public void setup() {
		policies = mock(SystemOpPolicies.class);
		accessor = mock(SignedTxnAccessor.class);

		subject = new StandardExemptions(new MockAccountNumbers(), policies);
	}

	@Test
	public void sysAdminPaysNoFees() {
		given(accessor.getPayer()).willReturn(account(50));

		// expect:
		assertTrue(subject.hasExemptPayer(accessor));
	}

	@Test
	public void authorizedOpsAreExempt() {
		given(accessor.getPayer()).willReturn(account(60));
		given(policies.check(accessor)).willReturn(AUTHORIZED);

		// expect:
		assertTrue(subject.hasExemptPayer(accessor));
	}

	@Test
	public void unnecessaryOpsAreNotExempt() {
		given(accessor.getPayer()).willReturn(account(60));
		given(policies.check(accessor)).willReturn(UNNECESSARY);

		// expect:
		assertFalse(subject.hasExemptPayer(accessor));
	}

	@Test
	public void treasuryPaysNoFees() {
		given(accessor.getPayer()).willReturn(account(2));

		// expect:
		assertTrue(subject.hasExemptPayer(accessor));
	}

	private AccountID account(long num) {
		return IdUtils.asAccount(String.format("0.0.%d", num));
	}
}