package com.hedera.services.txns.crypto;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.UniqueToken;
import com.hederahashgraph.api.proto.java.NftAllowance;

import java.util.List;
import java.util.Map;

import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.fetchOwnerAccount;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.updateSpender;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.validateAllowanceLimitsOn;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;

/**
 * Base transaction logic for both CryptoApproveAllowance and CryptoAdjustAllowance transactions.
 */
public class BaseAllowancesTransitionLogic {
	private final AccountStore accountStore;
	private final TypedTokenStore tokenStore;
	private final GlobalDynamicProperties dynamicProperties;

	public BaseAllowancesTransitionLogic(final AccountStore accountStore,
			final TypedTokenStore tokenStore,
			final GlobalDynamicProperties dynamicProperties) {
		this.accountStore = accountStore;
		this.tokenStore = tokenStore;
		this.dynamicProperties = dynamicProperties;
	}

	/**
	 * Applies all changes needed for NFT allowances from the transaction. If the key{tokenNum, spenderNum} doesn't
	 * exist in the map the allowance will be inserted. If the key exists, existing allowance values will be
	 * replaced with new allowances given in operation
	 *
	 * @param nftAllowances
	 * @param payerAccount
	 */
	protected void applyNftAllowances(final List<NftAllowance> nftAllowances,
			final Account payerAccount,
			final Map<Long, Account> entitiesChanged,
			final Map<NftId, UniqueToken> nftsTouched) {
		if (nftAllowances.isEmpty()) {
			return;
		}
		for (var allowance : nftAllowances) {
			final var owner = allowance.getOwner();
			final var accountToApprove = fetchOwnerAccount(owner, payerAccount, accountStore, entitiesChanged);
			final var approveForAllNfts = accountToApprove.getMutableApprovedForAllNfts();

			final var spenderId = Id.fromGrpcAccount(allowance.getSpender());
			accountStore.loadAccountOrFailWith(spenderId, INVALID_ALLOWANCE_SPENDER_ID);

			final var tokenId = Id.fromGrpcToken(allowance.getTokenId());

			if (allowance.hasApprovedForAll()) {
				final var key = FcTokenAllowanceId.from(tokenId.asEntityNum(), spenderId.asEntityNum());
				if (allowance.hasApprovedForAll()) {
					if (allowance.getApprovedForAll().getValue()) {
						approveForAllNfts.add(key);
					} else {
						approveForAllNfts.remove(key);
					}
				}
			}
			validateAllowanceLimitsOn(accountToApprove, dynamicProperties.maxAllowanceLimitPerAccount());

			final var nfts = updateSpender(tokenStore, accountToApprove.getId(), spenderId, tokenId,
					allowance.getSerialNumbersList());
			for (var nft : nfts) {
				nftsTouched.put(nft.getNftId(), nft);
			}
			entitiesChanged.put(accountToApprove.getId().num(), accountToApprove);
		}
	}
}
