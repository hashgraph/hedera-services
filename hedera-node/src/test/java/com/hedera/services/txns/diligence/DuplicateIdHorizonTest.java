package com.hedera.services.txns.diligence;

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

import com.hedera.services.txns.diligence.DuplicateIdHorizon;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@RunWith(JUnitPlatform.class)
public class DuplicateIdHorizonTest {
	TransactionID txnId = TransactionID.newBuilder().setAccountID(asAccount("0.0.2")).build();

	@Test
	public void equalityWorks() {
		// given:
		DuplicateIdHorizon duplicateHorizon = new DuplicateIdHorizon(5L, txnId);

		// expect:
		assertTrue(duplicateHorizon.equals(duplicateHorizon));
		assertFalse(duplicateHorizon.equals(new Object()));
		assertTrue(duplicateHorizon.equals(new DuplicateIdHorizon(5L, txnId)));
		assertFalse(duplicateHorizon.equals(new DuplicateIdHorizon(6L, txnId)));
	}

	@Test
	public void toStringWorks() {
		// given:
		DuplicateIdHorizon duplicateHorizon = new DuplicateIdHorizon(5L, txnId);

		// when:
		String desc = duplicateHorizon.toString();

		// expect:
		assertTrue(desc.contains("DuplicateIdHorizon"));
		assertTrue(desc.contains("horizon=5"));
		assertTrue(desc.contains("accountNum: 2"));
	}
}
