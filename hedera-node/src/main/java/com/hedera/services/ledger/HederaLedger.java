package com.hedera.services.ledger;

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

import com.hedera.services.exceptions.DeletedAccountException;
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
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.tokens.TokenStore;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.fcqueue.FCQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.hedera.services.ledger.accounts.BackingTokenRels.asTokenRel;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.properties.AccountProperty.RECORDS;
import static com.hedera.services.ledger.properties.AccountProperty.TOKENS;
import static com.hedera.services.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hedera.services.tokens.TokenStore.MISSING_TOKEN;
import static com.hedera.services.txns.crypto.CryptoTransferTransitionLogic.tryTransfers;
import static com.hedera.services.txns.validation.TransferListChecks.isNetZeroAdjustment;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;

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

	static final TransactionalLedger<
			Map.Entry<AccountID, TokenID>,
			TokenRelProperty,
			MerkleTokenRelStatus> UNUSABLE_TOKEN_RELS_LEDGER = null;

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
	private final TransferList.Builder netTransfers = TransferList.newBuilder();
	private final AccountRecordsHistorian historian;
	private final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;

	int numTouches = 0;
	final TokenID[] tokensTouched = new TokenID[MAX_CONCEIVABLE_TOKENS_PER_TXN];
	final Map<TokenID, TransferList.Builder> netTokenTransfers = new HashMap<>();
	TransactionalLedger<
			Map.Entry<AccountID, TokenID>,
			TokenRelProperty,
			MerkleTokenRelStatus> tokenRelsLedger = UNUSABLE_TOKEN_RELS_LEDGER;

	public HederaLedger(
			TokenStore tokenStore,
			EntityIdSource ids,
			EntityCreator creator,
			AccountRecordsHistorian historian,
			TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger
	) {
		this.ids = ids;
		this.historian = historian;
		this.tokenStore = tokenStore;
		this.accountsLedger = accountsLedger;

		creator.setLedger(this);
		historian.setLedger(this);
		historian.setCreator(creator);
		tokenStore.setAccountsLedger(accountsLedger);
		tokenStore.setHederaLedger(this);
	}

	public void setTokenRelsLedger(
			TransactionalLedger<Map.Entry<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger
	) {
		this.tokenRelsLedger = tokenRelsLedger;
	}

	/* -- TRANSACTIONAL SEMANTICS -- */
	public void begin() {
		accountsLedger.begin();
		if (tokenRelsLedger != UNUSABLE_TOKEN_RELS_LEDGER) {
			tokenRelsLedger.begin();
		}
	}

	public void rollback() {
		accountsLedger.rollback();
		if (tokenRelsLedger != UNUSABLE_TOKEN_RELS_LEDGER && tokenRelsLedger.isInTransaction()) {
			tokenRelsLedger.rollback();
		}
		netTransfers.clear();
		clearNetTokenTransfers();
	}

	public void commit() {
		throwIfPendingStateIsInconsistent();
		historian.addNewRecords();
		accountsLedger.commit();
		if (tokenRelsLedger != UNUSABLE_TOKEN_RELS_LEDGER && tokenRelsLedger.isInTransaction()) {
			tokenRelsLedger.commit();
		}
		netTransfers.clear();
		clearNetTokenTransfers();
	}

	public TransferList netTransfersInTxn() {
		return pendingNetTransfersInTxn().build();
	}

	public TransferList.Builder pendingNetTransfersInTxn() {
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
			if (tokenRelsLedger != UNUSABLE_TOKEN_RELS_LEDGER) {
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

	public void doTransfer(AccountID from, AccountID to, long adjustment) {
		long newFromBalance = computeNewBalance(from, -1 * adjustment);
		long newToBalance = computeNewBalance(to, adjustment);
		setBalance(from, newFromBalance);
		setBalance(to, newToBalance);

		updateXfers(from, -1 * adjustment, netTransfers);
		updateXfers(to, adjustment, netTransfers);
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
		if (tokenRelsLedger == UNUSABLE_TOKEN_RELS_LEDGER) {
			throw new IllegalStateException("Ledger has no manageable token relationships!");
		}

		var tokens = (MerkleAccountTokens) accountsLedger.get(aId, TOKENS);
		for (TokenID tId : tokens.asIds()) {
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

	public ResponseCodeEnum doAtomicTransfers(CryptoTransferTransactionBody txn) {
		return zeroSumTransfers(txn.getTransfers(), txn.getTokenTransfersList());
	}

	private ResponseCodeEnum zeroSumTransfers(
			TransferList hbarTransfers,
			List<TokenTransferList> allTokenTransfers
	) {
		var validity = OK;
		for (TokenTransferList tokenTransfers : allTokenTransfers) {
			var id = tokenStore.resolve(tokenTransfers.getToken());
			if (id == MISSING_TOKEN) {
				validity = INVALID_TOKEN_ID;
			}
			if (validity == OK) {
				var adjustments = tokenTransfers.getTransfersList();
				for (AccountAmount adjustment : adjustments) {
					validity = adjustTokenBalance(adjustment.getAccountID(), id, adjustment.getAmount());
					if (validity != OK) {
						break;
					}
				}
			}
			if (validity != OK) {
				break;
			}
		}
		if (validity == OK) {
			validity = checkNetOfTokenTransfers();
		}
		if (validity == OK) {
			if (hbarTransfers.getAccountAmountsCount() > 0) {
				validity = tryTransfers(this, hbarTransfers);
			}
		}
		if (validity != OK) {
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

	public boolean isSmartContract(AccountID id) {
		return (boolean) accountsLedger.get(id, IS_SMART_CONTRACT);
	}

	public boolean isDeleted(AccountID id) {
		return (boolean) accountsLedger.get(id, IS_DELETED);
	}

	public boolean isPendingCreation(AccountID id) {
		return accountsLedger.existsPending(id);
	}

	public MerkleAccount get(AccountID id) {
		return accountsLedger.get(id);
	}

	/* -- TRANSACTION HISTORY MANIPULATION -- */
	public long addRecord(AccountID id, ExpirableTxnRecord record) {
		return addReturningEarliestExpiry(id, RECORDS, record);
	}

	private long addReturningEarliestExpiry(AccountID id, AccountProperty property, ExpirableTxnRecord record) {
		FCQueue<ExpirableTxnRecord> records = (FCQueue<ExpirableTxnRecord>) accountsLedger.get(id, property);
		records.offer(record);
		accountsLedger.set(id, property, records);
		return records.peek().getExpiry();
	}

	public long purgeExpiredRecords(AccountID id, long now, Consumer<ExpirableTxnRecord> cb) {
		return purge(id, RECORDS, now, cb);
	}

	private long purge(
			AccountID id,
			AccountProperty recordsProp,
			long now,
			Consumer<ExpirableTxnRecord> cb
	) {
		FCQueue<ExpirableTxnRecord> records = (FCQueue<ExpirableTxnRecord>) accountsLedger.get(id, recordsProp);
		int numBefore = records.size();

		long newEarliestExpiry = purgeForNewEarliestExpiry(records, now, cb);
		accountsLedger.set(id, recordsProp, records);

		int numPurged = numBefore - records.size();
		LedgerTxnEvictionStats.INSTANCE.recordPurgedFromAnAccount(numPurged);
		log.debug("Purged {} records from account {}",
				() -> numPurged,
				() -> readableId(id));

		return newEarliestExpiry;
	}

	private long purgeForNewEarliestExpiry(
			FCQueue<ExpirableTxnRecord> records,
			long now,
			Consumer<ExpirableTxnRecord> cb
	) {
		long newEarliestExpiry = -1;
		while (!records.isEmpty() && records.peek().getExpiry() <= now) {
			cb.accept(records.poll());
		}
		if (!records.isEmpty()) {
			newEarliestExpiry = records.peek().getExpiry();
		}
		return newEarliestExpiry;
	}

	/* -- HELPERS -- */
	private boolean isLegalToAdjust(long balance, long adjustment) {
		return (balance + adjustment >= 0);
	}

	private long computeNewBalance(AccountID id, long adjustment) {
		if ((boolean) accountsLedger.get(id, IS_DELETED)) {
			throw new DeletedAccountException(id);
		}
		long balance = getBalance(id);
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

	private ResponseCodeEnum checkNetOfTokenTransfers() {
		if (numTouches == 0) {
			return OK;
		}
		for (int i = 0; i < numTouches; i++) {
			var token = tokensTouched[i];
			if (i == 0 || !token.equals(tokensTouched[i - 1])) {
				if (!isNetZeroAdjustment(netTokenTransfers.get(token))) {
					return TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
				}
			}
		}
		return OK;
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

	public boolean isKnownTreasury(AccountID aId) {
		return tokenStore.isKnownTreasury(aId);
	}

	public enum LedgerTxnEvictionStats {
		INSTANCE;

		private int recordsPurged = 0;
		private int accountsTouched = 0;

		public int recordsPurged() {
			return recordsPurged;
		}

		public int accountsTouched() {
			return accountsTouched;
		}

		public void reset() {
			accountsTouched = 0;
			recordsPurged = 0;
		}

		public void recordPurgedFromAnAccount(int n) {
			accountsTouched++;
			recordsPurged += n;
		}
	}
}
