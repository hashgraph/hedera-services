/*
 * -
 *  * ‌
 *  * Hedera Services Node
 *  * ​
 *  * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 *  * ​
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  * ‍
 *
 */

package com.hedera.services.txns.crypto;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.store.AccountStore;
import com.hedera.services.txns.crypto.validators.AllowanceChecks;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;

import java.time.Instant;

import static org.mockito.BDDMockito.given;

public class CryptoApproveTransitionLogicTest {
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private SigImpactHistorian sigImpactHistorian;
	@Mock
	private AccountStore accountStore;
	@Mock
	private AllowanceChecks allowanceChecks;

	CryptoApproveAllowanceTransitionLogic subject;

	@BeforeEach
	private void setup() {
		given(txnCtx.consensusTime()).willReturn(Instant.now());
		subject = new CryptoApproveAllowanceTransitionLogic(txnCtx, sigImpactHistorian, accountStore, allowanceChecks);
	}


}
