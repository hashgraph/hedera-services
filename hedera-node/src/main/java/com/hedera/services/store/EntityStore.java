package com.hedera.services.store;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.validation.OptionValidator;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.NotImplementedException;

import javax.annotation.Nullable;
import java.util.function.Supplier;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
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
 * the expired entity. Such update transactions must use a dedicated
 * expiry-extension service, which will be implemented before TokenUpdate.
 *
 * When saving an entity, invites an injected {@link TransactionRecordService} to
 * inspect the entity for changes that may need to be included in the record
 * of the transaction.
 */
public class EntityStore {
	private final OptionValidator validator;
	private final GlobalDynamicProperties dynamicProperties;
	private final TransactionRecordService transactionRecordService;
	private final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;
	private final Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> tokenRels;

	public EntityStore(
			OptionValidator validator,
			GlobalDynamicProperties dynamicProperties,
			TransactionRecordService transactionRecordService,
			Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts,
			Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> tokenRels
	) {
		this.tokens = tokens;
		this.accounts = accounts;
		this.tokenRels = tokenRels;
		this.validator = validator;
		this.dynamicProperties = dynamicProperties;
		this.transactionRecordService = transactionRecordService;
	}

	/**
	 * Returns a model of the requested token relationship, with operations that
	 * can be used to implement business logic in a transaction.
	 *
	 * The arguments <i>should</i> be model objects that were returned by the
	 * {@link EntityStore#loadToken(Id)} and {@link EntityStore#loadAccount(Id)}
	 * methods, respectively, since it will very rarely (or never) be correct
	 * to do business logic on a relationship whose token or account have not
	 * been validated as usable.
	 *
	 * <b>IMPORTANT:</b> Changes to the returned model are not automatically persisted
	 * to state! The altered model must be passed to {@link EntityStore#saveTokenRelationship(TokenRelationship)}
	 * in order for its changes to be applied to the Swirlds state, and included in the
	 * {@link com.hedera.services.state.submerkle.ExpirableTxnRecord} for the active transaction.
	 *
	 * @param token the token in the relationship to load
	 * @param account the account in the relationship to load
	 * @return a usable model of the token-account relationship
	 * @throws InvalidTransactionException if the requested relationship does not exist
	 */
	public TokenRelationship loadTokenRelationship(Token token, Account account) {
		final var tokenId = token.getId();
		final var accountId = account.getId();
		final var key = new MerkleEntityAssociation(
				accountId.getShard(), accountId.getRealm(), accountId.getNum(),
				tokenId.getShard(), tokenId.getRealm(), tokenId.getNum());
		final var merkleTokenRel = tokenRels.get().get(key);

		validateUsable(merkleTokenRel);

		final var tokenRelationship = new TokenRelationship(token, account);
		tokenRelationship.initBalance(merkleTokenRel.getBalance());
		tokenRelationship.setKycGranted(merkleTokenRel.isKycGranted());
		tokenRelationship.setFrozen(merkleTokenRel.isFrozen());

		return tokenRelationship;
	}

	/**
	 * Persists the given token relationship to the Swirlds state, inviting the injected
	 * {@link TransactionRecordService} to update the {@link com.hedera.services.state.submerkle.ExpirableTxnRecord}
	 * of the active transaction with these changes.
	 *
	 * @param tokenRelationship the token relationship to save
	 */
	public void saveTokenRelationship(TokenRelationship tokenRelationship) {
		final var tokenId = tokenRelationship.getToken().getId();
		final var accountId = tokenRelationship.getAccount().getId();
		final var key = new MerkleEntityAssociation(
				accountId.getShard(), accountId.getRealm(), accountId.getNum(),
				tokenId.getShard(), tokenId.getRealm(), tokenId.getNum());
		final var currentTokenRels = tokenRels.get();

		final var mutableTokenRel = currentTokenRels.getForModify(key);
		mutableTokenRel.setBalance(tokenRelationship.getBalance());
		mutableTokenRel.setFrozen(tokenRelationship.isFrozen());
		mutableTokenRel.setKycGranted(tokenRelationship.isKycGranted());
		currentTokenRels.replace(key, mutableTokenRel);

		transactionRecordService.includeChangesToTokenRel(tokenRelationship);
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

		validateUsable(merkleToken);

		final var token = new Token(id);
		initModelAccounts(token, merkleToken.treasury(), merkleToken.autoRenewAccount());
		initModelFields(token, merkleToken);

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
		mapModelChangesToMutable(token, mutableToken);
		currentTokens.replace(key, mutableToken);

		transactionRecordService.includeChangesToToken(token);
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

		validateUsable(merkleAccount);

		final var account = new Account(id);
		account.setExpiry(merkleAccount.getExpiry());
		account.setBalance(merkleAccount.getBalance());

		return account;
	}

	/**
	 * Persists the given account to the Swirlds state, inviting the injected {@link TransactionRecordService}
	 * to update the {@link com.hedera.services.state.submerkle.ExpirableTxnRecord} of the active transaction
	 * with these changes.
	 *
	 * @param account the account to save
	 */
	public void saveAccount(Account account) {
		throw new NotImplementedException();
	}

	private void validateUsable(MerkleAccount merkleAccount) {
		validateTrue(merkleAccount != null, INVALID_ACCOUNT_ID);
		validateFalse(merkleAccount.isDeleted(), ACCOUNT_DELETED);
		if (dynamicProperties.autoRenewEnabled()) {
			if (merkleAccount.getBalance() == 0) {
				final boolean isExpired = validator.isAfterConsensusSecond(merkleAccount.getExpiry());
				validateFalse(isExpired, ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
			}
		}
	}

	private void validateUsable(MerkleTokenRelStatus merkleTokenRelStatus) {
		validateTrue(merkleTokenRelStatus != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
	}

	private void validateUsable(MerkleToken merkleToken) {
		validateTrue(merkleToken != null, INVALID_TOKEN_ID);
		validateFalse(merkleToken.isDeleted(), TOKEN_WAS_DELETED);
	}

	private void mapModelChangesToMutable(Token token, MerkleToken mutableToken) {
		final var newAutoRenewAccount = token.getAutoRenewAccount();
		if (newAutoRenewAccount != null) {
			mutableToken.setAutoRenewAccount(new EntityId(newAutoRenewAccount.getId()));
		}
		mutableToken.setTreasury(new EntityId(token.getTreasury().getId()));
		mutableToken.setTotalSupply(token.getTotalSupply());
	}

	private void initModelAccounts(Token token, EntityId treasuryId, @Nullable EntityId autoRenewId) {
		if (autoRenewId != null) {
			final var autoRenew = loadAccount(new Id(autoRenewId.shard(), autoRenewId.realm(), autoRenewId.num()));
			token.setAutoRenewAccount(autoRenew);
		}
		final var treasury = loadAccount(new Id(treasuryId.shard(), treasuryId.realm(), treasuryId.num()));
		token.setTreasury(treasury);
	}

	private void initModelFields(Token token, MerkleToken immutableToken) {
		token.initTotalSupply(immutableToken.totalSupply());
		token.setKycKey(immutableToken.getKycKey());
		token.setFreezeKey(immutableToken.getFreezeKey());
		token.setSupplyKey(immutableToken.getSupplyKey());
	}
}
