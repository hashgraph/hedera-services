package com.hedera.services.ledger.accounts;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;

import java.util.Map;

public final class HederaAccountCustomizer extends
		AccountCustomizer<EntityNum, MerkleAccount, AccountProperty, HederaAccountCustomizer> {
	private static final Map<Option, AccountProperty> OPTION_PROPERTIES = Map.of(
			Option.KEY, AccountProperty.KEY,
			Option.MEMO, AccountProperty.MEMO,
			Option.PROXY, AccountProperty.PROXY,
			Option.EXPIRY, AccountProperty.EXPIRY,
			Option.IS_DELETED, AccountProperty.IS_DELETED,
			Option.AUTO_RENEW_PERIOD, AccountProperty.AUTO_RENEW_PERIOD,
			Option.IS_SMART_CONTRACT, AccountProperty.IS_SMART_CONTRACT,
			Option.IS_RECEIVER_SIG_REQUIRED, AccountProperty.IS_RECEIVER_SIG_REQUIRED,
			Option.MAX_AUTOMATIC_ASSOCIATIONS, AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS,
			Option.ALREADY_USED_AUTOMATIC_ASSOCIATIONS, AccountProperty.ALREADY_USED_AUTOMATIC_ASSOCIATIONS
	);

	public HederaAccountCustomizer() {
		super(AccountProperty.class, OPTION_PROPERTIES, new ChangeSummaryManager<>());
	}

	@Override
	protected HederaAccountCustomizer self() {
		return this;
	}
}
