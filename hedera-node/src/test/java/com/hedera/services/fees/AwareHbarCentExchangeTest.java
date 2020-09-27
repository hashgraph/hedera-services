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
import com.hedera.services.context.TransactionContext;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.Before;
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
class AwareHbarCentExchangeTest {
	TransactionContext txnCtx;

	AwareHbarCentExchange subject;

	@BeforeEach
	public void setUp() throws Exception {
		txnCtx = mock(TransactionContext.class);

		subject = new AwareHbarCentExchange(txnCtx);
	}


}