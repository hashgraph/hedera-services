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
import com.hedera.services.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class HederaAccountCustomizer extends
		AccountCustomizer<AccountID, MerkleAccount, AccountProperty, HederaAccountCustomizer> {
	private static final Map<Option, AccountProperty> OPTION_PROPERTIES;

	static {
		Map<Option, AccountProperty> optionAccountPropertyMap = new EnumMap<>(Option.class);
		optionAccountPropertyMap.put(Option.KEY, AccountProperty.KEY);
		optionAccountPropertyMap.put(Option.MEMO, AccountProperty.MEMO);
		optionAccountPropertyMap.put(Option.PROXY, AccountProperty.PROXY);
		optionAccountPropertyMap.put(Option.EXPIRY, AccountProperty.EXPIRY);
		optionAccountPropertyMap.put(Option.IS_DELETED, AccountProperty.IS_DELETED);
		optionAccountPropertyMap.put(Option.AUTO_RENEW_PERIOD, AccountProperty.AUTO_RENEW_PERIOD);
		optionAccountPropertyMap.put(Option.IS_SMART_CONTRACT, AccountProperty.IS_SMART_CONTRACT);
		optionAccountPropertyMap.put(Option.IS_RECEIVER_SIG_REQUIRED, AccountProperty.IS_RECEIVER_SIG_REQUIRED);
		optionAccountPropertyMap.put(Option.MAX_AUTOMATIC_ASSOCIATIONS, AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS);
		optionAccountPropertyMap.put(Option.USED_AUTOMATIC_ASSOCIATIONS, AccountProperty.USED_AUTOMATIC_ASSOCIATIONS);
		optionAccountPropertyMap.put(Option.DECLINE_REWARD, AccountProperty.DECLINE_REWARD);
		optionAccountPropertyMap.put(Option.STAKED_TO_ME, AccountProperty.STAKED_TO_ME);
		optionAccountPropertyMap.put(Option.STAKE_PERIOD_START, AccountProperty.STAKE_PERIOD_START);
		optionAccountPropertyMap.put(Option.STAKED_ID, AccountProperty.STAKED_ID);
		optionAccountPropertyMap.put(Option.STAKED_ACCOUNT, AccountProperty.STAKED_ACCOUNT);
		OPTION_PROPERTIES = Collections.unmodifiableMap(optionAccountPropertyMap);
	}

	public HederaAccountCustomizer() {
		super(AccountProperty.class, OPTION_PROPERTIES, new ChangeSummaryManager<>());
	}

	public void customizeSynthetic(final ContractCreateTransactionBody.Builder op) {
		final var changes = getChanges();
		if (changes.containsKey(AccountProperty.MEMO)) {
			op.setMemo((String) changes.get(AccountProperty.MEMO));
		}
		if (changes.containsKey(AccountProperty.AUTO_RENEW_PERIOD)) {
			op.setAutoRenewPeriod(Duration.newBuilder()
					.setSeconds((long) changes.get(AccountProperty.AUTO_RENEW_PERIOD)));
		}
		if (changes.containsKey(AccountProperty.STAKED_ACCOUNT)) {
			op.setStakedAccountId(((EntityId) changes.get(AccountProperty.STAKED_ACCOUNT)).toGrpcAccountId());
		} else if (changes.containsKey(AccountProperty.STAKED_ID)) {
			op.setStakedNodeId(((long) changes.get(AccountProperty.STAKED_ID)));
		}
		if (changes.containsKey(AccountProperty.DECLINE_REWARD)) {
			op.setDeclineReward((boolean) changes.get(AccountProperty.DECLINE_REWARD));
		}
	}

	@Override
	protected HederaAccountCustomizer self() {
		return this;
	}
}
