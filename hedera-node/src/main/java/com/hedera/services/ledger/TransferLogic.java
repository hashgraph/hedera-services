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

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.accounts.AutoAccountsManager;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.store.tokens.views.UniqTokenViewsManager;
import com.hedera.services.txns.crypto.AutoAccountCreateLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

import static com.hedera.services.ledger.properties.AccountProperty.ALREADY_USED_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_NFTS_OWNED;
import static com.hedera.services.ledger.properties.AccountProperty.TOKENS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

@Singleton
public class TransferLogic {
	private static final List<AccountProperty> TOKEN_TRANSFER_SIDE_EFFECTS =
			List.of(TOKENS, NUM_NFTS_OWNED, ALREADY_USED_AUTOMATIC_ASSOCIATIONS);
	private final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	private final TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger;
	private final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger;
	private final SideEffectsTracker sideEffectsTracker;
	private final TokenStore tokenStore;
	private final MerkleAccountScopedCheck scopedCheck;
	private final UniqTokenViewsManager tokenViewsManager;
	private final AutoAccountCreateLogic autoAccountCreator;
	private final GlobalDynamicProperties dynamicProperties;
	private final AutoAccountsManager autoAccounts;

	@Inject
	public TransferLogic(final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger,
			final TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger,
			final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger,
			final TokenStore tokenStore,
			final SideEffectsTracker sideEffectsTracker,
			final UniqTokenViewsManager tokenViewsManager,
			final GlobalDynamicProperties dynamicProperties,
			final OptionValidator validator,
			final AutoAccountCreateLogic autoAccountCreator,
			final AutoAccountsManager autoAccounts) {
		this.accountsLedger = accountsLedger;
		this.nftsLedger = nftsLedger;
		this.tokenRelsLedger = tokenRelsLedger;
		this.sideEffectsTracker = sideEffectsTracker;
		this.tokenStore = tokenStore;
		this.tokenViewsManager = tokenViewsManager;
		this.autoAccountCreator = autoAccountCreator;
		this.autoAccounts = autoAccounts;
		this.dynamicProperties = dynamicProperties;

		scopedCheck = new MerkleAccountScopedCheck(dynamicProperties, validator);
	}

	public void transfer(List<BalanceChange> changes) {
		var validity = OK;
		long autoAccountCreationFeeAccumulated = 0;
		for (var change : changes) {
			if (change.isForHbar()) {
				/* if the change has only alias set, account number is not set, and the alias
				is not present in the autoAccountsMap then add the alias to list for auto
				creations */
				if (change.hasUniqueAlias(autoAccounts)) {
					var creationResult = autoAccountCreator.createAutoAccount(change, accountsLedger);
					validity = creationResult.getLeft();
					autoAccountCreationFeeAccumulated += creationResult.getRight();
				} else {
					validity = accountsLedger.validate(change.accountId(), scopedCheck.setBalanceChange(change));
				}
			} else {
				validity = tokenStore.tryTokenChange(change);
			}
			if (validity != OK) {
				break;
			}
		}

		if (autoAccountCreationFeeAccumulated != 0) {
			changes.add(BalanceChange.hbarAdjust(
					Id.fromGrpcAccount(dynamicProperties.fundingAccount()), autoAccountCreationFeeAccumulated));
		}

		if (validity == OK) {
			adjustHbarUnchecked(changes);
		} else {
			dropPendingTokenChanges();
			dropPendingAutoCreations();
			throw new InvalidTransactionException(validity);
		}
	}

	private void adjustHbarUnchecked(final List<BalanceChange> changes) {
		for (var change : changes) {
			if (change.isForHbar()) {
				final AccountID accountId;
				if (change.accountId().getAccountNum() == 0 && !change.alias().isEmpty()) {
					accountId = autoAccounts.getAutoAccountsMap().get(change.alias()).toGrpcAccountId();
				} else {
					accountId = change.accountId();
				}
				final var newBalance = change.getNewBalance();
				accountsLedger.set(accountId, BALANCE, newBalance);
				sideEffectsTracker.trackHbarChange(accountId, change.units());
			}
		}
	}

	private void dropPendingAutoCreations() {
		accountsLedger.undoCreations();
		autoAccountCreator.clearTempCreations();
		sideEffectsTracker.resetTrackedAutoCreatedAccount();
	}

	public void dropPendingTokenChanges() {
		if (tokenRelsLedger.isInTransaction()) {
			tokenRelsLedger.rollback();
		}
		if (nftsLedger.isInTransaction()) {
			nftsLedger.rollback();
		}
		if (tokenViewsManager.isInTransaction()) {
			tokenViewsManager.rollback();
		}
		accountsLedger.undoChangesOfType(TOKEN_TRANSFER_SIDE_EFFECTS);
		sideEffectsTracker.resetTrackedTokenChanges();
	}
}
