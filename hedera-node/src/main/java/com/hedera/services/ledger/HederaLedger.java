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
import com.hedera.services.exceptions.DeletedAccountException;
import com.hedera.services.exceptions.DetachedAccountException;
import com.hedera.services.exceptions.InconsistentAdjustmentsException;
import com.hedera.services.exceptions.InsufficientFundsException;
import com.hedera.services.exceptions.NonZeroNetTransfersException;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hedera.services.ledger.accounts.BackingTokenRels.asTokenRel;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.properties.AccountProperty.PROXY;
import static com.hedera.services.ledger.properties.AccountProperty.TOKENS;
import static com.hedera.services.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hedera.services.txns.validation.TransferListChecks.isNetZeroAdjustment;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Provides a ledger for Hedera Services crypto and smart contract
 * accounts with transactional semantics. Changes to the ledger are
 * <b>only</b> allowed in the scope of a transaction.
 *
 * All changes that are made during a transaction are summarized as
 * per-account changesets. These changesets are committed to a
 * wrapped {@link TransactionalLedger}; or dropped entirely in case
 * of a rollback.
 *
 * The ledger delegates history of each transaction to an injected
 * {@link AccountRecordsHistorian} by invoking its {@code addNewRecords}
 * immediately before the final {@link TransactionalLedger#commit()}.
 *
 * We should think of the ledger as using double-booked accounting,
 * (e.g., via the {@link HederaLedger#doTransfer(AccountID, AccountID, long)}
 * method); but it is necessary to provide "unsafe" single-booked
 * methods like {@link HederaLedger#adjustBalance(AccountID, long)} in
 * order to match transfer semantics the EVM expects.
 *
 * @author Michael Tinker
 */
@SuppressWarnings("unchecked")
public class HederaLedger {
	private static final Logger log = LogManager.getLogger(HederaLedger.class);

	private static final int MAX_CONCEIVABLE_TOKENS_PER_TXN = 1_000;
	private static final long[] NO_NEW_BALANCES = new long[0];

	static final String NO_ACTIVE_TXN_CHANGE_SET = "{*NO ACTIVE TXN*}";
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

	private final TokenStore tokenStore;
	private final EntityIdSource ids;
	private final OptionValidator validator;
	private final GlobalDynamicProperties dynamicProperties;
	private final TransferList.Builder netTransfers = TransferList.newBuilder();
	private final AccountRecordsHistorian historian;
	private final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;

	private TransactionalLedger<
			Pair<AccountID, TokenID>,
			TokenRelProperty,
			MerkleTokenRelStatus> tokenRelsLedger = null;

	int numTouches = 0;
	final TokenID[] tokensTouched = new TokenID[MAX_CONCEIVABLE_TOKENS_PER_TXN];
	final Map<TokenID, TransferList.Builder> netTokenTransfers = new HashMap<>();

	public HederaLedger(
			TokenStore tokenStore,
			EntityIdSource ids,
			EntityCreator creator,
			OptionValidator validator,
			AccountRecordsHistorian historian,
			GlobalDynamicProperties dynamicProperties,
			TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger
	) {
		this.ids = ids;
		this.validator = validator;
		this.historian = historian;
		this.tokenStore = tokenStore;
		this.accountsLedger = accountsLedger;
		this.dynamicProperties = dynamicProperties;

		historian.setCreator(creator);
		tokenStore.setAccountsLedger(accountsLedger);
		tokenStore.setHederaLedger(this);
	}

	public void setTokenRelsLedger(
			TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger
	) {
		this.tokenRelsLedger = tokenRelsLedger;
	}

	/* -- TRANSACTIONAL SEMANTICS -- */
	public void begin() {
		accountsLedger.begin();
		if (tokenRelsLedger != null) {
			tokenRelsLedger.begin();
		}
	}

	public void rollback() {
		accountsLedger.rollback();
		if (tokenRelsLedger != null && tokenRelsLedger.isInTransaction()) {
			tokenRelsLedger.rollback();
		}
		netTransfers.clear();
		clearNetTokenTransfers();
	}

	public void commit() {
		throwIfPendingStateIsInconsistent();
		historian.finalizeExpirableTransactionRecord();
		accountsLedger.commit();
		historian.saveExpirableTransactionRecord();
		historian.noteNewExpirationEvents();
		if (tokenRelsLedger != null && tokenRelsLedger.isInTransaction()) {
			tokenRelsLedger.commit();
		}
		netTransfers.clear();
		clearNetTokenTransfers();
	}

	public TransferList netTransfersInTxn() {
		return pendingNetTransfersInTxn().build();
	}

	private TransferList.Builder pendingNetTransfersInTxn() {
		accountsLedger.throwIfNotInTxn();
		purgeZeroAdjustments(netTransfers);
		return netTransfers;
	}

	public List<TokenTransferList> netTokenTransfersInTxn() {
		if (numTouches == 0) {
			return Collections.emptyList();
		}
		List<TokenTransferList> all = new ArrayList<>();
		Arrays.sort(tokensTouched, 0, numTouches, TOKEN_ID_COMPARATOR);
		for (int i = 0; i < numTouches; i++) {
			var token = tokensTouched[i];
			if (i == 0 || !token.equals(tokensTouched[i - 1])) {
				var netTransfersHere = netTokenTransfers.get(token);
				purgeZeroAdjustments(netTransfersHere);
				all.add(TokenTransferList.newBuilder()
						.setToken(token)
						.addAllTransfers(netTransfersHere.getAccountAmountsList())
						.build());
			}
		}
		return all;
	}

	public String currentChangeSet() {
		if (accountsLedger.isInTransaction()) {
			var sb = new StringBuilder("--- ACCOUNTS ---\n")
					.append(accountsLedger.changeSetSoFar());
			if (tokenRelsLedger != null) {
				sb.append("\n--- TOKEN RELATIONSHIPS ---\n")
						.append(tokenRelsLedger.changeSetSoFar());
			}
			return sb.toString();
		} else {
			return NO_ACTIVE_TXN_CHANGE_SET;
		}
	}

	/* -- CURRENCY MANIPULATION -- */
	public long getBalance(AccountID id) {
		return (long) accountsLedger.get(id, BALANCE);
	}

	public void adjustBalance(AccountID id, long adjustment) {
		long newBalance = computeNewBalance(id, adjustment);
		setBalance(id, newBalance);

		updateXfers(id, adjustment, netTransfers);
	}

	public void doTransfers(TransferList accountAmounts) {
		throwIfNetAdjustmentIsNonzero(accountAmounts);
		long[] newBalances = computeNewBalances(accountAmounts);
		for (int i = 0; i < newBalances.length; i++) {
			setBalance(accountAmounts.getAccountAmounts(i).getAccountID(), newBalances[i]);
		}

		for (AccountAmount aa : accountAmounts.getAccountAmountsList()) {
			updateXfers(aa.getAccountID(), aa.getAmount(), netTransfers);
		}
	}

	void doTransfer(AccountID from, AccountID to, long adjustment) {
		long newFromBalance = computeNewBalance(from, -1 * adjustment);
		long newToBalance = computeNewBalance(to, adjustment);
		setBalance(from, newFromBalance);
		setBalance(to, newToBalance);

		updateXfers(from, -1 * adjustment, netTransfers);
		updateXfers(to, adjustment, netTransfers);
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

	public ResponseCodeEnum revokeKyc(AccountID aId, TokenID tId) {
		return tokenStore.revokeKyc(aId, tId);
	}

	public ResponseCodeEnum freeze(AccountID aId, TokenID tId) {
		return tokenStore.freeze(aId, tId);
	}

	public ResponseCodeEnum unfreeze(AccountID aId, TokenID tId) {
		return tokenStore.unfreeze(aId, tId);
	}

	public void dropPendingTokenChanges() {
		if (tokenRelsLedger.isInTransaction()) {
			tokenRelsLedger.rollback();
		}
		clearNetTokenTransfers();
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

	public ResponseCodeEnum doZeroSum(List<BalanceChange> changes) {
		var validity = OK;
		for (var change : changes) {
			if (change.isForHbar())	 {
				validity = plausibilityOf(change);
			} else {
				validity = tokenStore.tryTokenChange(change);
			}
			if (validity != OK) {
				break;
			}
		}

		if (validity == OK) {
			adjustHbarUnchecked(changes);
		} else {
			dropPendingTokenChanges();
		}
		return validity;
	}

	/* -- ACCOUNT META MANIPULATION -- */
	public AccountID create(AccountID sponsor, long balance, HederaAccountCustomizer customizer) {
		long newSponsorBalance = computeNewBalance(sponsor, -1 * balance);
		setBalance(sponsor, newSponsorBalance);

		var id = ids.newAccountId(sponsor);
		spawn(id, balance, customizer);

		updateXfers(sponsor, -1 * balance, netTransfers);

		return id;
	}

	public void spawn(AccountID id, long balance, HederaAccountCustomizer customizer) {
		accountsLedger.create(id);
		setBalance(id, balance);
		customizer.customize(id, accountsLedger);

		updateXfers(id, balance, netTransfers);
	}

	public void customize(AccountID id, HederaAccountCustomizer customizer) {
		if ((boolean) accountsLedger.get(id, IS_DELETED)) {
			throw new DeletedAccountException(id);
		}
		customizer.customize(id, accountsLedger);
	}

	public void customizeDeleted(AccountID id, HederaAccountCustomizer customizer) {
		if (!(boolean) accountsLedger.get(id, IS_DELETED)) {
			throw new DeletedAccountException(id);
		}
		customizer.customize(id, accountsLedger);
	}

	public void delete(AccountID id, AccountID beneficiary) {
		doTransfer(id, beneficiary, getBalance(id));
		accountsLedger.set(id, IS_DELETED, true);
	}

	public void destroy(AccountID id) {
		accountsLedger.destroy(id);
		for (int i = 0; i < netTransfers.getAccountAmountsCount(); i++) {
			if (netTransfers.getAccountAmounts(i).getAccountID().equals(id)) {
				netTransfers.removeAccountAmounts(i);
				return;
			}
		}
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

	public boolean isDeleted(AccountID id) {
		return (boolean) accountsLedger.get(id, IS_DELETED);
	}

	public boolean isDetached(AccountID id) {
		return dynamicProperties.autoRenewEnabled()
				&& !(boolean) accountsLedger.get(id, IS_SMART_CONTRACT)
				&& (long) accountsLedger.get(id, BALANCE) == 0L
				&& !validator.isAfterConsensusSecond((long) accountsLedger.get(id, EXPIRY));
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

	private ResponseCodeEnum plausibilityOf(BalanceChange change) {
		final var id = change.accountId();
		if (!exists(id) || isSmartContract(id)) {
			return INVALID_ACCOUNT_ID;
		}
		if ((boolean) accountsLedger.get(id, IS_DELETED)) {
			return ACCOUNT_DELETED;
		}
		if (isDetached(id)) {
			return ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
		}
		final var balance = getBalance(id);
		final var newBalance = balance + change.units();
		if (newBalance < 0L) {
			return change.codeForInsufficientBalance();
		}
		change.setNewBalance(newBalance);
		return OK;
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

	private void throwIfNetAdjustmentIsNonzero(TransferList accountAmounts) {
		if (!isNetZeroAdjustment(accountAmounts)) {
			throw new NonZeroNetTransfersException(accountAmounts);
		}
	}

	private void throwIfPendingStateIsInconsistent() {
		if (!isNetZeroAdjustment(pendingNetTransfersInTxn())) {
			throw new InconsistentAdjustmentsException();
		}
	}

	private long[] computeNewBalances(TransferList accountAmounts) {
		int n = accountAmounts.getAccountAmountsCount();
		if (n == 0) {
			return NO_NEW_BALANCES;
		}

		int i = 0;
		long[] newBalances = new long[n];
		for (AccountAmount adjustment : accountAmounts.getAccountAmountsList()) {
			newBalances[i++] = computeNewBalance(adjustment.getAccountID(), adjustment.getAmount());
		}
		return newBalances;
	}

	private void setBalance(AccountID id, long newBalance) {
		accountsLedger.set(id, BALANCE, newBalance);
	}

	public void updateTokenXfers(TokenID tId, AccountID aId, long amount) {
		tokensTouched[numTouches++] = tId;
		var xfers = netTokenTransfers.computeIfAbsent(tId, ignore -> TransferList.newBuilder());
		updateXfers(aId, amount, xfers);
	}

	private void updateXfers(AccountID account, long amount, TransferList.Builder xfers) {
		int loc = 0, diff = -1;
		var soFar = xfers.getAccountAmountsBuilderList();
		for (; loc < soFar.size(); loc++) {
			diff = ACCOUNT_ID_COMPARATOR.compare(account, soFar.get(loc).getAccountID());
			if (diff <= 0) {
				break;
			}
		}
		if (diff == 0) {
			var aa = soFar.get(loc);
			long current = aa.getAmount();
			aa.setAmount(current + amount);
		} else {
			if (loc == soFar.size()) {
				xfers.addAccountAmounts(aaBuilderWith(account, amount));
			} else {
				xfers.addAccountAmounts(loc, aaBuilderWith(account, amount));
			}
		}
	}

	private AccountAmount.Builder aaBuilderWith(AccountID account, long amount) {
		return AccountAmount.newBuilder().setAccountID(account).setAmount(amount);
	}

	private void clearNetTokenTransfers() {
		for (int i = 0; i < numTouches; i++) {
			netTokenTransfers.get(tokensTouched[i]).clearAccountAmounts();
		}
		numTouches = 0;
	}

	private void purgeZeroAdjustments(TransferList.Builder xfers) {
		int lastZeroRemoved;
		do {
			lastZeroRemoved = -1;
			for (int i = 0; i < xfers.getAccountAmountsCount(); i++) {
				if (xfers.getAccountAmounts(i).getAmount() == 0) {
					xfers.removeAccountAmounts(i);
					lastZeroRemoved = i;
					break;
				}
			}
		} while (lastZeroRemoved != -1);
	}

	private void adjustHbarUnchecked(List<BalanceChange> changes) {
		for (var change : changes) {
			if (change.isForHbar())	{
				final var accountId = change.accountId();
				setBalance(accountId, change.getNewBalance());
				updateXfers(accountId, change.units(), netTransfers);
			}
		}
	}
}
