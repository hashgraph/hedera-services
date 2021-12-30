package com.hedera.services.ledger;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.Map;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class MerkleAccountScopedCheck implements LedgerCheck<MerkleAccount, AccountProperty> {
	private final GlobalDynamicProperties dynamicProperties;
	private final OptionValidator validator;

	private BalanceChange balanceChange;

	MerkleAccountScopedCheck(final GlobalDynamicProperties dynamicProperties, final OptionValidator validator) {
		this.dynamicProperties = dynamicProperties;
		this.validator = validator;
	}

	@Override
	public ResponseCodeEnum checkUsing(final MerkleAccount account, final Map<AccountProperty, Object> changeSet) {
		if ((boolean) getEffective(IS_SMART_CONTRACT, account, changeSet)) {
			return ResponseCodeEnum.INVALID_ACCOUNT_ID;
		}

		if ((boolean) getEffective(IS_DELETED, account, changeSet)) {
			return ResponseCodeEnum.ACCOUNT_DELETED;
		}

		final var balance = (long) getEffective(BALANCE, account, changeSet);

		final var isDetached = dynamicProperties.autoRenewEnabled() &&
				balance == 0L &&
				!validator.isAfterConsensusSecond((long) getEffective(EXPIRY, account, changeSet));
		if (isDetached) {
			return ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
		}

		final var newBalance = balance + balanceChange.units();
		if (newBalance < 0L) {
			return balanceChange.codeForInsufficientBalance();
		}
		balanceChange.setNewBalance(newBalance);

		return OK;
	}

	public MerkleAccountScopedCheck setBalanceChange(final BalanceChange balanceChange) {
		this.balanceChange = balanceChange;
		return this;
	}

	Object getEffective(
			final AccountProperty prop,
			final MerkleAccount account,
			final Map<AccountProperty, Object> changeSet
	) {
		if (changeSet != null && changeSet.containsKey(prop)) {
			return changeSet.get(prop);
		}
		switch (prop) {
			case IS_SMART_CONTRACT:
				return account.isSmartContract();
			case IS_DELETED:
				return account.isDeleted();
			case BALANCE:
				return account.getBalance();
			case EXPIRY:
				return account.getExpiry();
			default:
				throw new IllegalArgumentException("Invalid Property " + prop + " cannot be validated in scoped check");
		}
	}
}
