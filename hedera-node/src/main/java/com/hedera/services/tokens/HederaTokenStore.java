package com.hedera.services.tokens;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.swirlds.fcmap.FCMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.ledger.accounts.BackingTokenRels.asTokenRel;
import static com.hedera.services.ledger.properties.TokenRelProperty.IS_FROZEN;
import static com.hedera.services.ledger.properties.TokenRelProperty.IS_KYC_GRANTED;
import static com.hedera.services.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hedera.services.state.merkle.MerkleEntityId.fromTokenId;
import static com.hedera.services.state.submerkle.EntityId.ofNullableAccountId;
import static com.hedera.services.tokens.TokenCreationResult.failure;
import static com.hedera.services.tokens.TokenCreationResult.success;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_DECIMALS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_INITIAL_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPING_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_NAME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABlE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static java.util.stream.IntStream.range;

/**
 * Provides a managing store for arbitrary tokens.
 *
 * @author Michael Tinker
 */
public class HederaTokenStore implements TokenStore {
	static final TokenID NO_PENDING_ID = TokenID.getDefaultInstance();

	private final EntityIdSource ids;
	private final OptionValidator validator;
	private final GlobalDynamicProperties properties;
	private final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens;
	private final TransactionalLedger<
			Map.Entry<AccountID, TokenID>,
			TokenRelProperty,
			MerkleTokenRelStatus> tokenRelsLedger;

	private HederaLedger hederaLedger;
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;

	TokenID pendingId = NO_PENDING_ID;
	MerkleToken pendingCreation;

	public HederaTokenStore(
			EntityIdSource ids,
			OptionValidator validator,
			GlobalDynamicProperties properties,
			Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens,
			TransactionalLedger<Map.Entry<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger
	) {
		this.ids = ids;
		this.tokens = tokens;
		this.validator = validator;
		this.properties = properties;
		this.tokenRelsLedger = tokenRelsLedger;
	}

	@Override
	public boolean isCreationPending() {
		return pendingId != NO_PENDING_ID;
	}

	@Override
	public void setHederaLedger(HederaLedger hederaLedger) {
		hederaLedger.setTokenRelsLedger(tokenRelsLedger);
		this.hederaLedger = hederaLedger;
	}

	@Override
	public void setAccountsLedger(TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) {
		this.accountsLedger = accountsLedger;
	}

	@Override
	public ResponseCodeEnum associate(AccountID aId, List<TokenID> tokens) {
		return fullySanityChecked(aId, tokens, (account, tokenIds) -> {
			var accountTokens = hederaLedger.getAssociatedTokens(aId);
			for (TokenID id : tokenIds) {
				if (accountTokens.includes(id)) {
					return TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
				}
			}
			int effectiveRelationships = accountTokens.purge(id -> !exists(id), id -> get(id).isDeleted());
			var validity = OK;
			if ((effectiveRelationships + tokenIds.size()) > properties.maxTokensPerAccount()) {
				validity = TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
			} else {
				accountTokens.associateAll(new HashSet<>(tokenIds));
				for (TokenID id : tokenIds) {
					var relationship = asTokenRel(aId, id);
					tokenRelsLedger.create(relationship);
					var token = get(id);
					tokenRelsLedger.set(
							relationship,
							TokenRelProperty.IS_FROZEN,
							token.hasFreezeKey() && token.accountsAreFrozenByDefault());
					tokenRelsLedger.set(
							relationship,
							TokenRelProperty.IS_KYC_GRANTED,
							!token.hasKycKey());
				}
			}
			hederaLedger.setAssociatedTokens(aId, accountTokens);
			return validity;
		});
	}

	@Override
	public ResponseCodeEnum dissociate(AccountID aId, List<TokenID> tokens) {
		return fullySanityChecked(aId, tokens, (account, tokenIds) -> {
			var accountTokens = hederaLedger.getAssociatedTokens(aId);
			for (TokenID tId : tokenIds) {
				if (!accountTokens.includes(tId)) {
					return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
				}
				var relationship = asTokenRel(aId, tId);
				long balance = (long)tokenRelsLedger.get(relationship, TOKEN_BALANCE);
				if (balance > 0) {
					return TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
				}
			}
			accountTokens.dissociateAll(new HashSet<>(tokenIds));
			tokenIds.forEach(id -> tokenRelsLedger.destroy(asTokenRel(aId, id)));
			hederaLedger.setAssociatedTokens(aId, accountTokens);
			return OK;
		});
	}

	@Override
	public boolean exists(TokenID id) {
		return pendingId.equals(id) || tokens.get().containsKey(fromTokenId(id));
	}

	@Override
	public MerkleToken get(TokenID id) {
		throwIfMissing(id);

		return pendingId.equals(id) ? pendingCreation : tokens.get().get(fromTokenId(id));
	}

	@Override
	public void apply(TokenID id, Consumer<MerkleToken> change) {
		throwIfMissing(id);

		var key = fromTokenId(id);
		var token = tokens.get().getForModify(key);
		Exception thrown = null;
		try {
			change.accept(token);
		} catch (Exception e) {
			thrown = e;
		}
		tokens.get().replace(key, token);
		if (thrown != null) {
			throw new IllegalArgumentException("Token change failed unexpectedly!", thrown);
		}
	}

	@Override
	public ResponseCodeEnum grantKyc(AccountID aId, TokenID tId) {
		return setHasKyc(aId, tId, true);
	}

	@Override
	public ResponseCodeEnum revokeKyc(AccountID aId, TokenID tId) {
		return setHasKyc(aId, tId, false);
	}

	@Override
	public ResponseCodeEnum unfreeze(AccountID aId, TokenID tId) {
		return setIsFrozen(aId, tId, false);
	}

	@Override
	public ResponseCodeEnum freeze(AccountID aId, TokenID tId) {
		return setIsFrozen(aId, tId, true);
	}

	private ResponseCodeEnum setHasKyc(
			AccountID aId,
			TokenID tId,
			boolean value
	) {
		return manageFlag(
				aId,
				tId,
				value,
				TOKEN_HAS_NO_KYC_KEY,
				TokenRelProperty.IS_KYC_GRANTED,
				MerkleToken::kycKey);
	}

	private ResponseCodeEnum setIsFrozen(
			AccountID aId,
			TokenID tId,
			boolean value
	) {
		return manageFlag(
				aId,
				tId,
				value,
				TOKEN_HAS_NO_FREEZE_KEY,
				TokenRelProperty.IS_FROZEN,
				MerkleToken::freezeKey);
	}

	@Override
	public ResponseCodeEnum adjustBalance(AccountID aId, TokenID tId, long adjustment) {
		return sanityChecked(aId, tId, token -> tryAdjustment(aId, tId, adjustment));
	}

	@Override
	public ResponseCodeEnum wipe(AccountID aId, TokenID tId, long amount, boolean skipKeyCheck) {
		return sanityChecked(aId, tId, token -> {
			if (!skipKeyCheck && !token.hasWipeKey()) {
				return TOKEN_HAS_NO_WIPE_KEY;
			}
			if (ofNullableAccountId(aId).equals(token.treasury())) {
				return CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
			}
			if (amount <= 0) {
				return INVALID_WIPING_AMOUNT;
			}

			var relationship = asTokenRel(aId, tId);
			long balance = (long)tokenRelsLedger.get(relationship, TOKEN_BALANCE);
			if (amount > balance) {
				return INVALID_WIPING_AMOUNT;
			}
			tokenRelsLedger.set(relationship, TOKEN_BALANCE, balance - amount);
			hederaLedger.updateTokenXfers(tId, aId, -amount);
			apply(tId, t -> t.adjustTotalSupplyBy(-amount));

			return OK;
		});
	}

	@Override
	public ResponseCodeEnum burn(TokenID tId, long amount) {
		return changeSupply(tId, amount, -1, INVALID_TOKEN_BURN_AMOUNT);
	}

	@Override
	public ResponseCodeEnum mint(TokenID tId, long amount) {
		return changeSupply(tId, amount, +1, INVALID_TOKEN_MINT_AMOUNT);
	}

	private ResponseCodeEnum changeSupply(
			TokenID tId,
			long amount,
			long sign,
			ResponseCodeEnum failure
	) {
		return tokenSanityCheck(tId, token -> {
			if (amount <= 0) {
				return failure;
			}
			if (!token.hasSupplyKey()) {
				return TOKEN_HAS_NO_SUPPLY_KEY;
			}

			var change = sign * amount;
			var toBeUpdatedTotalSupply = token.totalSupply() + change;
			if (toBeUpdatedTotalSupply < 0) {
				return failure;
			}

			var aId = token.treasury().toGrpcAccountId();
			var validity = tryAdjustment(aId, tId, change);
			if (validity != OK) {
				return validity;
			}

			apply(tId, t -> t.adjustTotalSupplyBy(change));

			return OK;
		});
	}

	@Override
	public TokenCreationResult createProvisionally(TokenCreateTransactionBody request, AccountID sponsor, long now) {
		var validity = symbolCheck(request.getSymbol());
		if (validity != OK) {
			return failure(validity);
		}
		validity = nameCheck(request.getName());
		if (validity != OK) {
			return failure(validity);
		}
		validity = accountCheck(request.getTreasury(), INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
		if (validity != OK) {
			return failure(validity);
		}
		if (request.hasAutoRenewAccount()) {
			validity = accountCheck(request.getAutoRenewAccount(), INVALID_AUTORENEW_ACCOUNT);
			if (validity == OK) {
				validity = isValidAutoRenewPeriod(request.getAutoRenewPeriod()) ? OK : INVALID_RENEWAL_PERIOD;
			}
			if (validity != OK) {
				return failure(validity);
			}
		} else {
			if (request.getExpiry() <= now) {
				return failure(INVALID_EXPIRATION_TIME);
			}
		}
		validity = initialSupplyAndDecimalsCheck(request.getInitialSupply(), request.getDecimals());
		if (validity != OK) {
			return failure(validity);
		}
		var freezeKey = asUsableFcKey(request.getFreezeKey());
		validity = freezeSemanticsCheck(freezeKey, request.getFreezeDefault());
		if (validity != OK) {
			return failure(validity);
		}
		var adminKey = asUsableFcKey(request.getAdminKey());
		var kycKey = asUsableFcKey(request.getKycKey());
		var wipeKey = asUsableFcKey(request.getWipeKey());
		var supplyKey = asUsableFcKey(request.getSupplyKey());

		var expiry = expiryOf(request, now);
		pendingId = ids.newTokenId(sponsor);
		pendingCreation = new MerkleToken(
				expiry,
				request.getInitialSupply(),
				request.getDecimals(),
				request.getSymbol(),
				request.getName(),
				request.getFreezeDefault(),
				kycKey.isEmpty(),
				ofNullableAccountId(request.getTreasury()));
		adminKey.ifPresent(pendingCreation::setAdminKey);
		kycKey.ifPresent(pendingCreation::setKycKey);
		wipeKey.ifPresent(pendingCreation::setWipeKey);
		freezeKey.ifPresent(pendingCreation::setFreezeKey);
		supplyKey.ifPresent(pendingCreation::setSupplyKey);
		if (request.hasAutoRenewAccount()) {
			pendingCreation.setAutoRenewAccount(ofNullableAccountId(request.getAutoRenewAccount()));
			pendingCreation.setAutoRenewPeriod(request.getAutoRenewPeriod());
		}

		return success(pendingId);
	}

	private ResponseCodeEnum tryAdjustment(AccountID aId, TokenID tId, long adjustment) {
		var relationship = asTokenRel(aId, tId);
		if ((boolean)tokenRelsLedger.get(relationship, IS_FROZEN)) {
			return ACCOUNT_FROZEN_FOR_TOKEN;
		}
		if (!(boolean)tokenRelsLedger.get(relationship, IS_KYC_GRANTED)) {
			return ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
		}
		long balance = (long)tokenRelsLedger.get(relationship, TOKEN_BALANCE);
		long newBalance = balance + adjustment;
		if (newBalance < 0) {
			return INSUFFICIENT_TOKEN_BALANCE;
		}
		tokenRelsLedger.set(relationship, TOKEN_BALANCE, newBalance);
		hederaLedger.updateTokenXfers(tId, aId, adjustment);
		return OK;
	}

	private ResponseCodeEnum initialSupplyAndDecimalsCheck(long initialSupply, int decimals) {
		if (initialSupply < 0) {
			return INVALID_TOKEN_INITIAL_SUPPLY;
		}
		return decimals < 0 ? INVALID_TOKEN_DECIMALS : OK;
	}

	private boolean isValidAutoRenewPeriod(long secs) {
		return validator.isValidAutoRenewPeriod(Duration.newBuilder().setSeconds(secs).build());
	}

	private long expiryOf(TokenCreateTransactionBody request, long now) {
		return request.hasAutoRenewAccount()
				? now + request.getAutoRenewPeriod()
				: request.getExpiry();
	}

	@Override
	public void commitCreation() {
		throwIfNoCreationPending();

		tokens.get().put(fromTokenId(pendingId), pendingCreation);

		resetPendingCreation();
	}

	@Override
	public void rollbackCreation() {
		throwIfNoCreationPending();

		ids.reclaimLastId();
		resetPendingCreation();
	}

	@Override
	public ResponseCodeEnum update(TokenUpdateTransactionBody changes, long now) {
		var tId = resolve(changes.getToken());
		if (tId == MISSING_TOKEN) {
			return INVALID_TOKEN_ID;
		}
		var validity = OK;
		var isExpiryOnly = affectsExpiryAtMost(changes);
		var hasNewSymbol = changes.getSymbol().length() > 0;
		if (hasNewSymbol) {
			validity = symbolCheck(changes.getSymbol());
			if (validity != OK) {
				return validity;
			}
		}
		var hasNewTokenName = changes.getName().length() > 0;
		if (hasNewTokenName) {
			validity = nameCheck(changes.getName());
			if (validity != OK) {
				return validity;
			}
		}
		var hasAutoRenewAccount = changes.hasAutoRenewAccount();
		if (hasAutoRenewAccount) {
			validity = accountCheck(changes.getAutoRenewAccount(), INVALID_AUTORENEW_ACCOUNT);
			if (validity != OK) {
				return validity;
			}
		}

		Optional<JKey> newKycKey = changes.hasKycKey() ? asUsableFcKey(changes.getKycKey()) : Optional.empty();
		Optional<JKey> newWipeKey = changes.hasWipeKey() ? asUsableFcKey(changes.getWipeKey()) : Optional.empty();
		Optional<JKey> newAdminKey = changes.hasAdminKey() ? asUsableFcKey(changes.getAdminKey()) : Optional.empty();
		Optional<JKey> newSupplyKey = changes.hasSupplyKey() ? asUsableFcKey(changes.getSupplyKey()) : Optional.empty();
		Optional<JKey> newFreezeKey = changes.hasFreezeKey() ? asUsableFcKey(changes.getFreezeKey()) : Optional.empty();

		var keyValidity = keyValidity(changes, newKycKey, newAdminKey, newWipeKey, newSupplyKey, newFreezeKey);
		if (keyValidity != OK) {
			return keyValidity;
		}
		var appliedValidity = new AtomicReference<>(OK);
		apply(tId, token -> {
			var candidateExpiry = changes.getExpiry();
			if (candidateExpiry != 0 && candidateExpiry < token.expiry()) {
				appliedValidity.set(INVALID_EXPIRATION_TIME);
			}
			if (hasAutoRenewAccount || token.hasAutoRenewAccount()) {
				if ((changes.getAutoRenewPeriod() != 0 || !token.hasAutoRenewAccount())
						&& !isValidAutoRenewPeriod(changes.getAutoRenewPeriod())) {
					appliedValidity.set(INVALID_RENEWAL_PERIOD);
				}
			}
			if (!token.hasKycKey() && newKycKey.isPresent()) {
				appliedValidity.set(TOKEN_HAS_NO_KYC_KEY);
			}
			if (!token.hasFreezeKey() && newFreezeKey.isPresent()) {
				appliedValidity.set(TOKEN_HAS_NO_FREEZE_KEY);
			}
			if (!token.hasWipeKey() && newWipeKey.isPresent()) {
				appliedValidity.set(TOKEN_HAS_NO_WIPE_KEY);
			}
			if (!token.hasSupplyKey() && newSupplyKey.isPresent()) {
				appliedValidity.set(TOKEN_HAS_NO_SUPPLY_KEY);
			}
			if (!token.hasAdminKey() && !isExpiryOnly) {
				appliedValidity.set(TOKEN_IS_IMMUTABlE);
			}
			if (OK != appliedValidity.get()) {
				return;
			}
			if (changes.hasAdminKey()) {
				token.setAdminKey(asFcKeyUnchecked(changes.getAdminKey()));
			}
			if (changes.hasAutoRenewAccount()) {
				token.setAutoRenewAccount(ofNullableAccountId(changes.getAutoRenewAccount()));
			}
			if (token.hasAutoRenewAccount()) {
				if (changes.getAutoRenewPeriod() > 0) {
					token.setAutoRenewPeriod(changes.getAutoRenewPeriod());
				}
			}
			if (changes.hasFreezeKey()) {
				token.setFreezeKey(asFcKeyUnchecked(changes.getFreezeKey()));
			}
			if (changes.hasKycKey()) {
				token.setKycKey(asFcKeyUnchecked(changes.getKycKey()));
			}
			if (changes.hasSupplyKey()) {
				token.setSupplyKey(asFcKeyUnchecked(changes.getSupplyKey()));
			}
			if (changes.hasWipeKey()) {
				token.setWipeKey(asFcKeyUnchecked(changes.getWipeKey()));
			}
			if (hasNewSymbol) {
				var newSymbol = changes.getSymbol();
				token.setSymbol(newSymbol);
			}
			if (hasNewTokenName) {
				var newName = changes.getName();
				token.setName(newName);
			}
			if (changes.hasTreasury()) {
				var treasuryId = ofNullableAccountId(changes.getTreasury());
				token.setTreasury(treasuryId);
			}
			if (changes.getExpiry() != 0) {
				token.setExpiry(changes.getExpiry());
			}
		});
		return appliedValidity.get();
	}

	public static boolean affectsExpiryAtMost(TokenUpdateTransactionBody op) {
		return !op.hasAdminKey() &&
				!op.hasKycKey() &&
				!op.hasWipeKey() &&
				!op.hasFreezeKey() &&
				!op.hasSupplyKey() &&
				!op.hasTreasury() &&
				!op.hasAutoRenewAccount() &&
				op.getSymbol().length() == 0 &&
				op.getName().length() == 0 &&
				op.getAutoRenewPeriod() == 0;
	}

	private ResponseCodeEnum fullySanityChecked(
			AccountID aId,
			List<TokenID> tokens,
			BiFunction<AccountID, List<TokenID>, ResponseCodeEnum> action
	) {
		var validity = checkAccountExistence(aId);
		if (validity != OK) {
			return validity;
		}
		List<TokenID> tokenIds = new ArrayList<>();
		for (TokenID tID : tokens) {
			var id = resolve(tID);
			if (id == MISSING_TOKEN) {
				return INVALID_TOKEN_ID;
			}
			var token = get(id);
			if (token.isTokenDeleted()) {
				return TOKEN_WAS_DELETED;
			}
			tokenIds.add(id);
		}
		return action.apply(aId, tokenIds);
	}

	private ResponseCodeEnum keyValidity(
			TokenUpdateTransactionBody op,
			Optional<JKey> newKycKey,
			Optional<JKey> newAdminKey,
			Optional<JKey> newWipeKey,
			Optional<JKey> newSupplyKey,
			Optional<JKey> newFreezeKey
	) {
		if (op.hasAdminKey() && newAdminKey.isEmpty()) {
			return INVALID_ADMIN_KEY;
		}
		if (op.hasKycKey() && newKycKey.isEmpty()) {
			return INVALID_KYC_KEY;
		}
		if (op.hasWipeKey() && newWipeKey.isEmpty()) {
			return INVALID_WIPE_KEY;
		}
		if (op.hasSupplyKey() && newSupplyKey.isEmpty()) {
			return INVALID_SUPPLY_KEY;
		}
		if (op.hasFreezeKey() && newFreezeKey.isEmpty()) {
			return INVALID_FREEZE_KEY;
		}
		return OK;
	}

	private void resetPendingCreation() {
		pendingId = NO_PENDING_ID;
		pendingCreation = null;
	}

	private void throwIfNoCreationPending() {
		if (pendingId == NO_PENDING_ID) {
			throw new IllegalStateException("No pending token creation!");
		}
	}

	private void throwIfMissing(TokenID id) {
		if (!exists(id)) {
			throw new IllegalArgumentException(String.format("No such token '%s'!", readableId(id)));
		}
	}

	private ResponseCodeEnum freezeSemanticsCheck(Optional<JKey> candidate, boolean freezeDefault) {
		if (candidate.isEmpty() && freezeDefault) {
			return TOKEN_HAS_NO_FREEZE_KEY;
		}
		return OK;
	}

	private ResponseCodeEnum symbolCheck(String symbol) {
		if (symbol.length() < 1) {
			return MISSING_TOKEN_SYMBOL;
		}
		if (symbol.length() > properties.maxTokenSymbolLength()) {
			return TOKEN_SYMBOL_TOO_LONG;
		}
		return range(0, symbol.length()).mapToObj(symbol::charAt).allMatch(Character::isUpperCase)
				? OK
				: INVALID_TOKEN_SYMBOL;
	}

	private ResponseCodeEnum nameCheck(String name) {
		if (name.length() < 1) {
			return MISSING_TOKEN_NAME;
		}
		if (name.length() > properties.maxTokensNameLength()) {
			return TOKEN_NAME_TOO_LONG;
		}
		return OK;
	}

	private ResponseCodeEnum accountCheck(AccountID id, ResponseCodeEnum failure) {
		if (!accountsLedger.exists(id) || (boolean) accountsLedger.get(id, AccountProperty.IS_DELETED)) {
			return failure;
		}
		return OK;
	}

	private ResponseCodeEnum manageFlag(
			AccountID aId,
			TokenID tId,
			boolean value,
			ResponseCodeEnum keyFailure,
			TokenRelProperty flagProperty,
			Function<MerkleToken, Optional<JKey>> controlKeyFn
	) {
		return sanityChecked(aId, tId, token -> {
			if (controlKeyFn.apply(token).isEmpty()) {
				return keyFailure;
			}
			var relationship = asTokenRel(aId, tId);
			tokenRelsLedger.set(relationship, flagProperty, value);
			return OK;
		});
	}

	private ResponseCodeEnum sanityChecked(
			AccountID aId,
			TokenID tId,
			Function<MerkleToken, ResponseCodeEnum> action
	) {
		var validity = checkExistence(aId, tId);
		if (validity != OK) {
			return validity;
		}

		var token = get(tId);
		if (token.isTokenDeleted()) {
			return TOKEN_WAS_DELETED;
		}

		var key = asTokenRel(aId, tId);
		if (!tokenRelsLedger.exists(key)) {
			return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
		}

		return action.apply(token);
	}

	private ResponseCodeEnum tokenSanityCheck(
			TokenID tId,
			Function<MerkleToken, ResponseCodeEnum> action
	) {
		var validity = exists(tId) ? OK : INVALID_TOKEN_ID;
		if (validity != OK) {
			return validity;
		}

		var token = get(tId);
		if (token.isTokenDeleted()) {
			return TOKEN_WAS_DELETED;
		}

		return action.apply(token);
	}

	private ResponseCodeEnum checkExistence(AccountID aId, TokenID tId) {
		var validity = checkAccountExistence(aId);
		if (validity != OK) {
			return validity;
		}
		return exists(tId) ? OK : INVALID_TOKEN_ID;
	}

	private ResponseCodeEnum checkAccountExistence(AccountID aId) {
		return accountsLedger.exists(aId)
				? (hederaLedger.isDeleted(aId) ? ACCOUNT_DELETED : OK)
				: INVALID_ACCOUNT_ID;
	}
}
