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
import com.hedera.services.ledger.properties.TokenScopedPropertyValue;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenManagement;
import com.swirlds.fcmap.FCMap;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.IS_FROZEN;
import static com.hedera.services.ledger.properties.AccountProperty.IS_KYC_GRANTED;
import static com.hedera.services.state.merkle.MerkleEntityId.fromTokenId;
import static com.hedera.services.state.submerkle.EntityId.ofNullableAccountId;
import static com.hedera.services.tokens.TokenCreationResult.failure;
import static com.hedera.services.tokens.TokenCreationResult.success;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_HAS_NO_TOKEN_RELATIONSHIP;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_INITIAL_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_REF;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPING_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_NAME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NAME_ALREADY_IN_USE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABlE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_ALREADY_IN_USE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
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

	private HederaLedger hederaLedger;
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger;

	Map<String, TokenID> symbolKeyedIds = new HashMap<>();
	Map<String, TokenID> nameKeyedIds = new HashMap<>();

	TokenID pendingId = NO_PENDING_ID;
	MerkleToken pendingCreation;

	public HederaTokenStore(
			EntityIdSource ids,
			OptionValidator validator,
			GlobalDynamicProperties properties,
			Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens
	) {
		this.ids = ids;
		this.tokens = tokens;
		this.validator = validator;
		this.properties = properties;

		tokens.get().entrySet().forEach(entry ->
				symbolKeyedIds.put(entry.getValue().symbol(), entry.getKey().toTokenId()));

		tokens.get().entrySet().forEach(entry ->
				nameKeyedIds.put(entry.getValue().name(), entry.getKey().toTokenId()));
	}

	@Override
	public boolean isCreationPending() {
		return pendingId != NO_PENDING_ID;
	}

	@Override
	public void setHederaLedger(HederaLedger hederaLedger) {
		this.hederaLedger = hederaLedger;
	}

	@Override
	public void setLedger(TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger) {
		this.ledger = ledger;
	}

	@Override
	public boolean exists(TokenID id) {
		return pendingId.equals(id) || tokens.get().containsKey(fromTokenId(id));
	}

	@Override
	public boolean symbolExists(String symbol) {
		return symbolKeyedIds.containsKey(symbol);
	}

	@Override
	public boolean nameExists(String name) {
		return nameKeyedIds.containsKey(name);
	}

	@Override
	public TokenID lookup(String symbol) {
		throwIfSymbolMissing(symbol);

		return symbolKeyedIds.get(symbol);
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
				IS_KYC_GRANTED,
				MerkleToken::accountsKycGrantedByDefault,
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
				IS_FROZEN,
				MerkleToken::accountsAreFrozenByDefault,
				MerkleToken::freezeKey);
	}

	@Override
	public ResponseCodeEnum adjustBalance(AccountID aId, TokenID tId, long adjustment) {
		return sanityChecked(aId, tId, token -> {
			var account = ledger.getTokenRef(aId);
			if (!unsaturated(account) && !account.hasRelationshipWith(tId)) {
				return TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
			}

			var validity = account.validityOfAdjustment(tId, token, adjustment);
			if (validity != OK) {
				return validity;
			}
			adjustUnchecked(aId, tId, token, adjustment);
			hederaLedger.updateTokenXfers(tId, aId, adjustment);
			return OK;
		});
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

			var account = ledger.getTokenRef(aId);
			if (!account.hasRelationshipWith(tId)) {
				return ACCOUNT_HAS_NO_TOKEN_RELATIONSHIP;
			}
			var balance = account.getTokenBalance(tId);
			if (amount > balance) {
				return INVALID_WIPING_AMOUNT;
			}

			adjustUnchecked(aId, tId, token, -amount);
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

	private void adjustUnchecked(AccountID aId, TokenID tId, MerkleToken token, long amount) {
		var scopedAdjustment = new TokenScopedPropertyValue(tId, token, amount);
		ledger.set(aId, BALANCE, scopedAdjustment);
	}

	private ResponseCodeEnum changeSupply(
			TokenID tId,
			long amount,
			long sign,
			ResponseCodeEnum failure
	) {
		if (amount < 0) {
			return failure;
		}
		if (!exists(tId)) {
			return INVALID_TOKEN_ID;
		}
		var token = get(tId);
		if (token.isDeleted()) {
			return TOKEN_WAS_DELETED;
		}
		if (!token.hasSupplyKey()) {
			return TOKEN_HAS_NO_SUPPLY_KEY;
		}
		var change = sign * amount;
		var toBeUpdatedTotalSupply = token.totalSupply() + change;
		if (toBeUpdatedTotalSupply < 0) {
			return failure;
		}
		apply(tId, t -> t.adjustTotalSupplyBy(change));
		return adjustBalance(token.treasury().toGrpcAccountId(), tId, change);
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

	private ResponseCodeEnum initialSupplyAndDecimalsCheck(long initialSupply, int decimals) {
		if (initialSupply < 0) {
			return INVALID_INITIAL_SUPPLY;
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
		symbolKeyedIds.put(pendingCreation.symbol(), pendingId);
		nameKeyedIds.put(pendingCreation.name(), pendingId);

		resetPendingCreation();
	}

	@Override
	public void rollbackCreation() {
		throwIfNoCreationPending();

		ids.reclaimLastId();
		resetPendingCreation();
	}

	@Override
	public ResponseCodeEnum update(TokenManagement changes, long now) {
		var tId = resolve(changes.getToken());
		if (tId == MISSING_TOKEN) {
			return INVALID_TOKEN_REF;
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
				symbolKeyedIds.remove(token.symbol());
				token.setSymbol(newSymbol);
				symbolKeyedIds.put(newSymbol, tId);
			}
			if (hasNewTokenName) {
				var newName = changes.getName();
				nameKeyedIds.remove(token.name());
				token.setName(newName);
				nameKeyedIds.put(newName, tId);
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

	public static boolean affectsExpiryAtMost(TokenManagement op) {
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

	private ResponseCodeEnum keyValidity(
			TokenManagement op,
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

	private void throwIfSymbolMissing(String symbol) {
		if (!symbolExists(symbol)) {
			throw new IllegalArgumentException(String.format("No such symbol '%s'!", symbol));
		}
	}

	private void throwIfNameMissing(String name) {
		if (!nameExists(name)) {
			throw new IllegalArgumentException(String.format("No such name '%s'!", name));
		}
	}

	private ResponseCodeEnum freezeSemanticsCheck(Optional<JKey> candidate, boolean freezeDefault) {
		if (candidate.isEmpty() && freezeDefault) {
			return TOKEN_HAS_NO_FREEZE_KEY;
		}
		return OK;
	}

	private ResponseCodeEnum symbolCheck(String symbol) {
		if (symbolKeyedIds.containsKey(symbol)) {
			return TOKEN_SYMBOL_ALREADY_IN_USE;
		}
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
		if (nameKeyedIds.containsKey(name)) {
			return TOKEN_NAME_ALREADY_IN_USE;
		}
		if (name.length() < 1) {
			return MISSING_TOKEN_NAME;
		}
		if (name.length() > properties.maxTokensNameLength()) {
			return TOKEN_NAME_TOO_LONG;
		}
		return OK;
	}

	private ResponseCodeEnum accountCheck(AccountID id, ResponseCodeEnum failure) {
		if (!ledger.exists(id) || (boolean) ledger.get(id, AccountProperty.IS_DELETED)) {
			return failure;
		}
		return OK;
	}

	private ResponseCodeEnum manageFlag(
			AccountID aId,
			TokenID tId,
			boolean value,
			ResponseCodeEnum keyFailure,
			AccountProperty flagProperty,
			Predicate<MerkleToken> defaultValueCheck,
			Function<MerkleToken, Optional<JKey>> controlKeyFn
	) {
		return sanityChecked(aId, tId, token -> {
			if (controlKeyFn.apply(token).isEmpty()) {
				return keyFailure;
			}

			var account = ledger.getTokenRef(aId);
			if (!account.hasRelationshipWith(tId) && saturated(account) && defaultValueCheck.test(token) != value) {
				return TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
			}

			var scopedFreeze = new TokenScopedPropertyValue(tId, token, value);
			ledger.set(aId, flagProperty, scopedFreeze);
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
		if (token.isDeleted()) {
			return TOKEN_WAS_DELETED;
		}

		return action.apply(token);
	}

	private ResponseCodeEnum checkExistence(AccountID aId, TokenID tId) {
		var validity = ledger.exists(aId)
				? OK
				: INVALID_ACCOUNT_ID;
		if (validity != OK) {
			return validity;
		}
		return exists(tId) ? OK : INVALID_TOKEN_ID;
	}

	private boolean unsaturated(MerkleAccount account) {
		return account.numTokenRelationships() < properties.maxTokensPerAccount();
	}

	private boolean saturated(MerkleAccount account) {
		return account.numTokenRelationships() >= properties.maxTokensPerAccount();
	}
}
