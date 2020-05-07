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
import com.hedera.services.ledger.properties.ChangeSummaryManager;

import java.util.Map;

import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.*;
import static com.hedera.services.ledger.properties.TestAccountProperty.*;

public class TestAccountCustomizer extends
		AccountCustomizer<Long, TestAccount, TestAccountProperty, TestAccountCustomizer> {
	public static final Map<Option, TestAccountProperty> OPTION_PROPERTIES = Map.of(
			KEY, OBJ,
			MEMO, OBJ,
			PROXY, OBJ,
			EXPIRY, LONG,
			IS_DELETED, FLAG,
			AUTO_RENEW_PERIOD, LONG,
			IS_SMART_CONTRACT, FLAG,
			IS_RECEIVER_SIG_REQUIRED, FLAG,
			FUNDS_SENT_RECORD_THRESHOLD, LONG,
			FUNDS_RECEIVED_RECORD_THRESHOLD, LONG
	);

	public TestAccountCustomizer(ChangeSummaryManager<TestAccount, TestAccountProperty> changeManager) {
		super(TestAccountProperty.class, OPTION_PROPERTIES, changeManager);
	}

	@Override
	protected TestAccountCustomizer self() {
		return this;
	}
}
