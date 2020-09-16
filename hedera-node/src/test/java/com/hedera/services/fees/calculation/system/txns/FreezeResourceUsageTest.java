package com.hedera.services.fees.calculation.system.txns;

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

import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class FreezeResourceUsageTest {
	private FreezeResourceUsage subject;

	private TransactionBody nonFreezeTxn;
	private TransactionBody freezeTxn;

	@BeforeEach
	private void setup() throws Throwable {
		freezeTxn = mock(TransactionBody.class);
		given(freezeTxn.hasFreeze()).willReturn(true);

		nonFreezeTxn = mock(TransactionBody.class);
		given(nonFreezeTxn.hasFreeze()).willReturn(false);

		subject = new FreezeResourceUsage();
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(freezeTxn));
		assertFalse(subject.applicableTo(nonFreezeTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// expect:
		assertEquals(FeeData.getDefaultInstance(), subject.usageGiven(null, null, null));
	}
}
