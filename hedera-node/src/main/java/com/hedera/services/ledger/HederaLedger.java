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
import com.hedera.services.exceptions.DeletedAccountException;
import com.hedera.services.exceptions.DetachedAccountException;
import com.hedera.services.exceptions.InconsistentAdjustmentsException;
import com.hedera.services.exceptions.InsufficientFundsException;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.contracts.MutableEntityAccess;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.store.tokens.views.UniqueTokenViewsManager;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Comparator;
import java.util.List;

import static com.hedera.services.ledger.TransferLogic.dropTokenChanges;
import static com.hedera.services.ledger.backing.BackingTokenRels.asTokenRel;
import static com.hedera.services.ledger.properties.AccountProperty.ALREADY_USED_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.properties.AccountProperty.KEY;
import static com.hedera.services.ledger.properties.AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.MEMO;
import static com.hedera.services.ledger.properties.AccountProperty.PROXY;
import static com.hedera.services.ledger.properties.AccountProperty.TOKENS;
import static com.hedera.services.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hedera.services.txns.validation.TransferListChecks.isNetZeroAdjustment;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Provides a ledger for Hedera Services crypto and smart contract
 * accounts with transactional semantics. Changes to the ledger are
 * <b>only</b> allowed in the scope of a transaction.
 * <p>
 * All changes that are made during a transaction are summarized as
 * per-account changesets. These changesets are committed to a
 * wrapped {@link TransactionalLedger}; or dropped entirely in case
 * of a rollback.
 * <p>
 * The ledger delegates history of each transaction to an injected
 * {@link AccountRecordsHistorian} by invoking its {@code addNewRecords}
 * immediately before the final {@link TransactionalLedger#commit()}.
 * <p>
 * We should think of the ledger as using double-booked accounting,
 * (e.g., via the {@link HederaLedger#doTransfer(AccountID, AccountID, long)}
 * method); but it is necessary to provide "unsafe" single-booked
 * methods like {@link HederaLedger#adjustBalance(AccountID, long)} in
 * order to match transfer semantics the EVM expects.
 */
public class HederaLedger {
	public static final String NO_ACTIVE_TXN_CHANGE_SET = "{*NO ACTIVE TXN*}";

	public static final Comparator<AccountID> ACCOUNT_ID_COMPARATOR = Comparator
			.comparingLong(AccountID::getAccountNum)
			.thenComparingLong(AccountID::getShardNum)
			.thenComparingLong(AccountID::getRealmNum);
	public static final Comparator<TokenID> TOKEN_ID_COMPARATOR = Comparator
			.comparingLong(TokenID::getTokenNum)
			.thenComparingLong(TokenID::getRealmNum)
			.thenComparingLong(TokenID::getShardNum);
	public static final Comparator<FileID> FILE_ID_COMPARATOR = Comparator
			.comparingLong(FileID::getFileNum)
			.thenComparingLong(FileID::getShardNum)
			.thenComparingLong(FileID::getRealmNum);
	public static final Comparator<ContractID> CONTRACT_ID_COMPARATOR = Comparator
			.comparingLong(ContractID::getContractNum)
			.thenComparingLong(ContractID::getShardNum)
			.thenComparingLong(ContractID::getRealmNum);

	private final TokenStore tokenStore;
	private final TransferLogic transferLogic;
	private final EntityIdSource ids;
	private final OptionValidator validator;
	private final SideEffectsTracker sideEffectsTracker;
	private final GlobalDynamicProperties dynamicProperties;
	private final AccountRecordsHistorian historian;
	private final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;

	private MutableEntityAccess mutableEntityAccess;
	private UniqueTokenViewsManager tokenViewsManager = null;
	private TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger = null;
	private TransactionalLedger<
			Pair<AccountID, TokenID>,
			TokenRelProperty,
			MerkleTokenRelStatus> tokenRelsLedger = null;

	private final AutoCreationLogic autoCreationLogic;

	public HederaLedger(
			final TokenStore tokenStore,
			final EntityIdSource ids,
			final EntityCreator creator,
			final OptionValidator validator,
			final SideEffectsTracker sideEffectsTracker,
			final AccountRecordsHistorian historian,
			final GlobalDynamicProperties dynamicProperties,
			final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger,
			final TransferLogic transferLogic,
			final AutoCreationLogic autoCreationLogic
	) {
		this.ids = ids;
		this.validator = validator;
		this.historian = historian;
		this.tokenStore = tokenStore;
		this.accountsLedger = accountsLedger;
		this.dynamicProperties = dynamicProperties;
		this.sideEffectsTracker = sideEffectsTracker;
		this.transferLogic = transferLogic;
		this.autoCreationLogic = autoCreationLogic;

		creator.setLedger(this);
		historian.setCreator(creator);
		tokenStore.setAccountsLedger(accountsLedger);
		tokenStore.setHederaLedger(this);
	}

	public void setMutableEntityAccess(final MutableEntityAccess mutableEntityAccess) {
		this.mutableEntityAccess = mutableEntityAccess;
	}

	public void setTokenViewsManager(final UniqueTokenViewsManager tokenViewsManager) {
		this.tokenViewsManager = tokenViewsManager;
	}

	public void setNftsLedger(final TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger) {
		this.nftsLedger = nftsLedger;
	}

	public void setTokenRelsLedger(
			final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger
	) {
		this.tokenRelsLedger = tokenRelsLedger;
	}

	public TransactionalLedger<AccountID, AccountProperty, MerkleAccount> getAccountsLedger() {
		return accountsLedger;
	}

	public TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> getNftsLedger() {
		return nftsLedger;
	}

	public TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> getTokenRelsLedger() {
		return tokenRelsLedger;
	}

	/* -- TRANSACTIONAL SEMANTICS -- */
	public void begin() {
		autoCreationLogic.reset();
		accountsLedger.begin();
		mutableEntityAccess.begin();
		if (tokenRelsLedger != null) {
			tokenRelsLedger.begin();
		}
		if (nftsLedger != null) {
			nftsLedger.begin();
		}
		if (tokenViewsManager != null) {
			tokenViewsManager.begin();
		}
	}

	public void rollback() {
		accountsLedger.rollback();
		mutableEntityAccess.rollback();
		if (tokenRelsLedger != null && tokenRelsLedger.isInTransaction()) {
			tokenRelsLedger.rollback();
		}
		if (nftsLedger != null && nftsLedger.isInTransaction()) {
			nftsLedger.rollback();
		}
		if (tokenViewsManager != null && tokenViewsManager.isInTransaction()) {
			tokenViewsManager.rollback();
		}
	}

	public void commit() {
		throwIfPendingStateIsInconsistent();
		historian.saveExpirableTransactionRecords();
		historian.noteNewExpirationEvents();
		accountsLedger.commit();
		mutableEntityAccess.commit();
		if (tokenRelsLedger != null && tokenRelsLedger.isInTransaction()) {
			tokenRelsLedger.commit();
		}
		if (nftsLedger != null && nftsLedger.isInTransaction()) {
			nftsLedger.commit();
		}
		if (tokenViewsManager != null && tokenViewsManager.isInTransaction()) {
			tokenViewsManager.commit();
		}
	}

	public String currentChangeSet() {
		if (accountsLedger.isInTransaction()) {
			var sb = new StringBuilder("--- ACCOUNTS ---\n")
					.append(accountsLedger.changeSetSoFar());
			if (tokenRelsLedger != null) {
				sb.append("\n--- TOKEN RELATIONSHIPS ---\n")
						.append(tokenRelsLedger.changeSetSoFar());
			}
			if (nftsLedger != null) {
				sb.append("\n--- NFTS ---\n")
						.append(nftsLedger.changeSetSoFar());
			}
			sb.append("\n--- TOKENS ---\n")
					.append(mutableEntityAccess.currentManagedChangeSet());
			return sb.toString();
		} else {
			return NO_ACTIVE_TXN_CHANGE_SET;
		}
	}

	/* -- CURRENCY MANIPULATION -- */
	public long getBalance(final AccountID id) {
		return (long) accountsLedger.get(id, BALANCE);
	}

	public void adjustBalance(final AccountID id, final long adjustment) {
		long newBalance = computeNewBalance(id, adjustment);
		setBalance(id, newBalance);
		sideEffectsTracker.trackHbarChange(id, adjustment);
	}

	void doTransfer(AccountID from, AccountID to, long adjustment) {
		long newFromBalance = computeNewBalance(from, -1 * adjustment);
		long newToBalance = computeNewBalance(to, adjustment);

		setBalance(from, newFromBalance);
		setBalance(to, newToBalance);

		sideEffectsTracker.trackHbarChange(from, -1 * adjustment);
		sideEffectsTracker.trackHbarChange(to, adjustment);
	}

	/* --- TOKEN MANIPULATION --- */
	public MerkleAccountTokens getAssociatedTokens(AccountID aId) {
		return (MerkleAccountTokens) accountsLedger.get(aId, TOKENS);
	}

	public void setAssociatedTokens(AccountID aId, MerkleAccountTokens tokens) {
		accountsLedger.set(aId, TOKENS, tokens);
	}

	public long getTokenBalance(AccountID aId, TokenID tId) {
		var relationship = asTokenRel(aId, tId);
		return (long) tokenRelsLedger.get(relationship, TOKEN_BALANCE);
	}

	public boolean allTokenBalancesVanish(AccountID aId) {
		if (tokenRelsLedger == null) {
			throw new IllegalStateException("Ledger has no manageable token relationships!");
		}

		var tokens = (MerkleAccountTokens) accountsLedger.get(aId, TOKENS);
		for (TokenID tId : tokens.asTokenIds()) {
			if (tokenStore.get(tId).isDeleted()) {
				continue;
			}
			var relationship = asTokenRel(aId, tId);
			var balance = (long) tokenRelsLedger.get(relationship, TOKEN_BALANCE);
			if (balance > 0) {
				return false;
			}
		}
		return true;
	}

	public boolean isKnownTreasury(AccountID aId) {
		return tokenStore.isKnownTreasury(aId);
	}

	public ResponseCodeEnum adjustTokenBalance(AccountID aId, TokenID tId, long adjustment) {
		return tokenStore.adjustBalance(aId, tId, adjustment);
	}

	public ResponseCodeEnum grantKyc(AccountID aId, TokenID tId) {
		return tokenStore.grantKyc(aId, tId);
	}

	public ResponseCodeEnum freeze(AccountID aId, TokenID tId) {
		return tokenStore.freeze(aId, tId);
	}

	public ResponseCodeEnum unfreeze(AccountID aId, TokenID tId) {
		return tokenStore.unfreeze(aId, tId);
	}

	public void dropPendingTokenChanges() {
		dropTokenChanges(sideEffectsTracker, tokenViewsManager, nftsLedger, accountsLedger, tokenRelsLedger);
	}

	public ResponseCodeEnum doTokenTransfer(
			TokenID tId,
			AccountID from,
			AccountID to,
			long adjustment
	) {
		var validity = OK;
		validity = adjustTokenBalance(from, tId, -adjustment);
		if (validity == OK) {
			validity = adjustTokenBalance(to, tId, adjustment);
		}

		if (validity != OK) {
			dropPendingTokenChanges();
		}
		return validity;
	}

	public void doZeroSum(List<BalanceChange> changes) {
		transferLogic.doZeroSum(changes);
	}

	/* -- ACCOUNT META MANIPULATION -- */
	public AccountID create(AccountID sponsor, long balance, HederaAccountCustomizer customizer) {
		long newSponsorBalance = computeNewBalance(sponsor, -1 * balance);
		setBalance(sponsor, newSponsorBalance);

		var id = ids.newAccountId(sponsor);
		spawn(id, balance, customizer);

		sideEffectsTracker.trackHbarChange(sponsor, -balance);
		return id;
	}

	public void spawn(AccountID id, long balance, HederaAccountCustomizer customizer) {
		accountsLedger.create(id);
		setBalance(id, balance);
		customizer.customize(id, accountsLedger);

		sideEffectsTracker.trackHbarChange(id, balance);
	}

	public void customize(AccountID id, HederaAccountCustomizer customizer) {
		if ((boolean) accountsLedger.get(id, IS_DELETED)) {
			throw new DeletedAccountException(id);
		}
		customizer.customize(id, accountsLedger);
	}

	/**
	 * Updates the provided {@link AccountID} with the {@link HederaAccountCustomizer}. All properties from the
	 * customizer are applied to the {@link MerkleAccount} provisionally
	 *
	 * @param id
	 * 		target account
	 * @param customizer
	 * 		properties to update
	 */
	public void customizePotentiallyDeleted(AccountID id, HederaAccountCustomizer customizer) {
		customizer.customize(id, accountsLedger);
	}

	public void delete(AccountID id, AccountID beneficiary) {
		doTransfer(id, beneficiary, getBalance(id));
		accountsLedger.set(id, IS_DELETED, true);
	}

	/* -- ACCOUNT PROPERTY ACCESS -- */
	public boolean exists(AccountID id) {
		return accountsLedger.exists(id);
	}

	public long expiry(AccountID id) {
		return (long) accountsLedger.get(id, EXPIRY);
	}

	public long autoRenewPeriod(AccountID id) {
		return (long) accountsLedger.get(id, AUTO_RENEW_PERIOD);
	}

	public EntityId proxy(AccountID id) {
		return (EntityId) accountsLedger.get(id, PROXY);
	}

	public boolean isSmartContract(AccountID id) {
		return (boolean) accountsLedger.get(id, IS_SMART_CONTRACT);
	}

	public boolean isReceiverSigRequired(AccountID id) {
		return (boolean) accountsLedger.get(id, IS_RECEIVER_SIG_REQUIRED);
	}

	public int maxAutomaticAssociations(AccountID id) {
		return (int) accountsLedger.get(id, MAX_AUTOMATIC_ASSOCIATIONS);
	}

	public int alreadyUsedAutomaticAssociations(AccountID id) {
		return (int) accountsLedger.get(id, ALREADY_USED_AUTOMATIC_ASSOCIATIONS);
	}

	public void setMaxAutomaticAssociations(AccountID id, int max) {
		accountsLedger.set(id, MAX_AUTOMATIC_ASSOCIATIONS, max);
	}

	public void setAlreadyUsedAutomaticAssociations(AccountID id, int usedCount) {
		accountsLedger.set(id, ALREADY_USED_AUTOMATIC_ASSOCIATIONS, usedCount);
	}

	public boolean isDeleted(AccountID id) {
		return (boolean) accountsLedger.get(id, IS_DELETED);
	}

	public boolean isDetached(AccountID id) {
		return dynamicProperties.autoRenewEnabled()
				&& !(boolean) accountsLedger.get(id, IS_SMART_CONTRACT)
				&& (long) accountsLedger.get(id, BALANCE) == 0L
				&& !validator.isAfterConsensusSecond((long) accountsLedger.get(id, EXPIRY));
	}

	public JKey key(AccountID id) {
		return (JKey) accountsLedger.get(id, KEY);
	}

	public String memo(AccountID id) {
		return (String) accountsLedger.get(id, MEMO);
	}

	public boolean isPendingCreation(AccountID id) {
		return accountsLedger.existsPending(id);
	}

	public MerkleAccount get(AccountID id) {
		return accountsLedger.getFinalized(id);
	}

	/* -- HELPERS -- */
	private boolean isLegalToAdjust(long balance, long adjustment) {
		return (balance + adjustment >= 0);
	}

	private long computeNewBalance(AccountID id, long adjustment) {
		if ((boolean) accountsLedger.get(id, IS_DELETED)) {
			throw new DeletedAccountException(id);
		}
		if (isDetached(id)) {
			throw new DetachedAccountException(id);
		}
		final long balance = getBalance(id);
		if (!isLegalToAdjust(balance, adjustment)) {
			throw new InsufficientFundsException(id, adjustment);
		}
		return balance + adjustment;
	}

	private void throwIfPendingStateIsInconsistent() {
		if (!isNetZeroAdjustment(sideEffectsTracker.getNetTrackedHbarChanges())) {
			throw new InconsistentAdjustmentsException();
		}
	}

	private void setBalance(AccountID id, long newBalance) {
		accountsLedger.set(id, BALANCE, newBalance);
	}

	/* -- Only used by unit tests --- */
	TransferList netTransfersInTxn() {
		accountsLedger.throwIfNotInTxn();
		return sideEffectsTracker.getNetTrackedHbarChanges();
	}

	List<TokenTransferList> netTokenTransfersInTxn() {
		return sideEffectsTracker.getNetTrackedTokenUnitAndOwnershipChanges();
	}
}
