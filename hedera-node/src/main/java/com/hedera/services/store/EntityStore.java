package com.hedera.services.store;

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.validation.OptionValidator;
import com.swirlds.fcmap.FCMap;

import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

/**
 * Loads and saves entities to and from the Swirlds state, hiding the details of
 * Merkle types from client code by providing an interface in terms of model
 * objects whose methods can perform validated business logic.
 *
 * When loading an entity, fails fast by throwing an {@link InvalidTransactionException}
 * if the entity is not usable in normal business logic. There are three such
 * cases:
 * <ol>
 *     <li>The entity is missing.</li>
 *     <li>The entity is deleted.</li>
 *     <li>The entity is expired and pending removal.</li>
 * </ol>
 * Note that in the third case, there <i>is</i> one valid use of the entity;
 * namely, in an update transaction whose only purpose is to manually renew
 * the expired entity. Such update transactions must use a dedicated expiry-extension
 * service, which will be implemented shortly.
 *
 * When saving an entity, invites an injected {@link TransactionRecordService} to
 * inspect the entity for changes that may need to be included in the record
 * of the transaction.
 */
public class EntityStore {
	private final OptionValidator validator;
	private final TransactionRecordService transactionRecordService;
	private final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;

	public EntityStore(
			OptionValidator validator,
			TransactionRecordService transactionRecordService,
			Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts
	) {
		this.tokens = tokens;
		this.accounts = accounts;
		this.validator = validator;
		this.transactionRecordService = transactionRecordService;
	}

	/**
	 * Returns a model of the requested token, with operations that can be used to
	 * implement business logic in a transaction.
	 *
	 * <b>IMPORTANT:</b> Changes to the returned model are not automatically persisted
	 * to state! The altered model must be passed to {@link EntityStore#saveToken(Token)}
	 * in order for its changes to be applied to the Swirlds state, and included in the
	 * {@link com.hedera.services.state.submerkle.ExpirableTxnRecord} for the active transaction.
	 *
	 * @param id the token to load
	 * @return a usable model of the token
	 * @throws InvalidTransactionException if the requested token is missing, deleted, or expired and pending removal
	 */
	public Token loadToken(Id id) {
		final var key = new MerkleEntityId(id.getShard(), id.getRealm(), id.getNum());
		final var merkleToken = tokens.get().get(key);

		assertTokenIsUsable(merkleToken);

		final var token = new Token(id);

		final var autoRenewId = merkleToken.autoRenewAccount();
		if (autoRenewId != null) {
			final var autoRenew = loadAccount(new Id(autoRenewId.shard(), autoRenewId.realm(), autoRenewId.num()));
			token.setAutoRenewAccount(autoRenew);
		}
		final var treasuryId = merkleToken.treasury();
		final var treasury = loadAccount(new Id(treasuryId.shard(), treasuryId.realm(), treasuryId.num()));
		token.setTreasury(treasury);
		token.setTotalSupply(merkleToken.totalSupply());

		return token;
	}

	/**
	 * Persists the given token to the Swirlds state, inviting the injected {@link TransactionRecordService}
	 * to update the {@link com.hedera.services.state.submerkle.ExpirableTxnRecord} of the active transaction
	 * with these changes.
	 *
	 * @param token the token to save
	 */
	public void saveToken(Token token) {
		final var id = token.getId();
		final var key = new MerkleEntityId(id.getShard(), id.getRealm(), id.getNum());
		final var currentTokens = tokens.get();

		final var mutableToken = currentTokens.getForModify(key);
		mapChangesFrom(token, mutableToken);
		currentTokens.replace(key, mutableToken);

		transactionRecordService.includeChangesToToken(token);
	}

	private void mapChangesFrom(Token token, MerkleToken mutableToken) {
		final var newAutoRenewAccount = token.getAutoRenewAccount();
		if (newAutoRenewAccount != null) {
			mutableToken.setAutoRenewAccount(new EntityId(newAutoRenewAccount.getId()));
		}
		mutableToken.setTreasury(new EntityId(token.getTreasury().getId()));
		mutableToken.setTotalSupply(token.getTotalSupply());
	}

	/**
	 * Returns a model of the requested token, with operations that can be used to
	 * implement business logic in a transaction.
	 *
	 * <b>IMPORTANT:</b> Changes to the returned model are not automatically persisted
	 * to state! The altered model must be passed to {@link EntityStore#saveAccount(Account)}
	 * in order for its changes to be applied to the Swirlds state, and included in the
	 * {@link com.hedera.services.state.submerkle.ExpirableTxnRecord} for the active transaction.
	 *
	 * @param id the account to load
	 * @return a usable model of the account
	 * @throws InvalidTransactionException if the requested account is missing, deleted, or expired and pending removal
	 */
	public Account loadAccount(Id id) {
		final var key = new MerkleEntityId(id.getShard(), id.getRealm(), id.getNum());
		final var merkleAccount = accounts.get().get(key);

		assertAccountIsUsable(merkleAccount);

		final var account = new Account(id);
		account.setExpiry(merkleAccount.getExpiry());
		account.setBalance(merkleAccount.getBalance());

		return account;
	}

	public void saveAccount(Account account) {

	}

	private void assertAccountIsUsable(MerkleAccount merkleAccount) {
		if (merkleAccount == null) {
			throw new InvalidTransactionException(INVALID_ACCOUNT_ID);
		}
		if (merkleAccount.isDeleted()) {
			throw new InvalidTransactionException(ACCOUNT_DELETED);
		}
		if (merkleAccount.getBalance() == 0 && validator.isAfterConsensusSecond(merkleAccount.getExpiry())) {
			throw new InvalidTransactionException(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
		}
	}

	private void assertTokenIsUsable(MerkleToken merkleToken) {
		if (merkleToken == null) {
			throw new InvalidTransactionException(INVALID_TOKEN_ID);
		}
		if (merkleToken.isDeleted()) {
			throw new InvalidTransactionException(TOKEN_WAS_DELETED);
		}
	}
}
