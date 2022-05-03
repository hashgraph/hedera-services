package com.hedera.services.fees.charging;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.ContractStoragePriceTiers;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import org.apache.tuweni.bytes.Bytes;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_ACCOUNT_ID;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;

@Singleton
public class RecordedStorageFeeCharging implements StorageFeeCharging {
	// Used to get the current exchange rate
	private final HbarCentExchange exchange;
	// Used to track the storage fee payments in a succeeding child record
	private final RecordsHistorian recordsHistorian;
	// Used to get the current consensus time
	private final TransactionContext txnCtx;
	// Used to get the storage slot lifetime and pricing tiers
	private final GlobalDynamicProperties dynamicProperties;

	@Inject
	public RecordedStorageFeeCharging(
			final HbarCentExchange exchange,
			final RecordsHistorian recordsHistorian,
			final TransactionContext txnCtx,
			final GlobalDynamicProperties dynamicProperties
	) {
		this.txnCtx = txnCtx;
		this.exchange = exchange;
		this.recordsHistorian = recordsHistorian;
		this.dynamicProperties = dynamicProperties;
	}

	public void chargeStorageFees(
			final long numKvPairs,
			final Map<Long, Bytes> newBytecodes,
			final Map<AccountID, Integer> newUsageDeltas,
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts
	) {
		chargeStorageFeesInternal(numKvPairs, newBytecodes, newUsageDeltas, accounts);
	}

	void chargeStorageFeesInternal(
			final long numKvPairs,
			final Map<Long, Bytes> newBytecodes,
			final Map<AccountID, Integer> newUsageDeltas,
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts
	) {
		final var now = txnCtx.consensusTime();
		final var rate = exchange.activeRate(now);
		final var thisSecond = now.getEpochSecond();
		final var slotLifetime = dynamicProperties.storageSlotLifetime();
		final var storagePriceTiers = dynamicProperties.storagePriceTiers();
		if (!newBytecodes.isEmpty()) {
			throw new AssertionError("Not implemented");
		}
		if (!newUsageDeltas.isEmpty()) {
			chargeForSlots(thisSecond, numKvPairs, slotLifetime, rate, storagePriceTiers, newUsageDeltas, accounts);
		}
	}

	private void chargeForSlots(
			final long now,
			final long numKvPairs,
			final long slotLifetime,
			final ExchangeRate rate,
			final ContractStoragePriceTiers storagePriceTiers,
			final Map<AccountID, Integer> newUsageDeltas,
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts
	) {
		newUsageDeltas.forEach((id, delta) -> {
			if (delta > 0) {
				final var lifetime = (long) accounts.get(id, EXPIRY) - now;
				var leftToPay = storagePriceTiers.slotPrice(rate, slotLifetime, numKvPairs, delta, lifetime);
				final var autoRenew = (EntityId) accounts.get(id, AUTO_RENEW_ACCOUNT_ID);
				if (!MISSING_ENTITY_ID.equals(autoRenew)) {
					final var debited = charge(autoRenew.toGrpcAccountId(), leftToPay, false, accounts);
					leftToPay -= debited;
				}
				if (leftToPay > 0) {
					charge(id, leftToPay, true, accounts);
				}
			}
		});
	}

	private long charge(
			final AccountID id,
			final long amount,
			final boolean isLastResort,
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts
	) {
		System.out.println("Trying to charge " + amount
				+ " (lastResort? " + isLastResort + ") to 0.0." + id.getAccountNum());
		var paid = 0L;
		final var balance = (long) accounts.get(id, BALANCE);
		if (amount > balance) {
			validateFalse(isLastResort, INSUFFICIENT_ACCOUNT_BALANCE);
			accounts.set(id, BALANCE, 0L);
			paid = balance;
		} else {
			accounts.set(id, BALANCE, balance - amount);
			paid = amount;
		}
		final var fundingId = dynamicProperties.fundingAccount();
		final var fundingBalance = (long) accounts.get(fundingId, BALANCE);
		accounts.set(fundingId, BALANCE, fundingBalance + paid);
		return paid;
	}
}
