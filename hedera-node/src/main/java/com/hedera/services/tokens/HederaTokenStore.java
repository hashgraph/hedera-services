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
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCode;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenManagement;
import com.swirlds.fcmap.FCMap;
import org.bouncycastle.asn1.cms.OtherKeyAttribute;

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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_DIVISIBILITY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_FLOAT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_REF;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
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
	private final GlobalDynamicProperties properties;
	private final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens;

	private HederaLedger hederaLedger;
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger;

	Map<String, TokenID> symbolKeyedIds = new HashMap<>();

	TokenID pendingId = NO_PENDING_ID;
	MerkleToken pendingCreation;

	public HederaTokenStore(
			EntityIdSource ids,
			GlobalDynamicProperties properties,
			Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens
	) {
		this.ids = ids;
		this.tokens = tokens;
		this.properties = properties;

		tokens.get().entrySet().forEach(entry ->
				symbolKeyedIds.put(entry.getValue().symbol(), entry.getKey().toTokenId()));
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
				MerkleToken::accountKycGrantedByDefault,
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
	public ResponseCodeEnum wipe(AccountID aId, TokenID tId, boolean skipKeyCheck) {
		return sanityChecked(aId, tId, token -> {
			if (!skipKeyCheck && !token.hasWipeKey()) {
				return TOKEN_HAS_NO_WIPE_KEY;
			}
			if (ofNullableAccountId(aId).equals(token.treasury())) {
				return CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
			}
			var account = ledger.getTokenRef(aId);
			var balance = account.getTokenBalance(tId);
			var outcome = account.wipeTokenRelationship(tId);
			if (outcome == OK) {
				ledger.markForMerge(aId);
				if (balance > 0) {
					var treasury = token.treasury().toGrpcAccountId();
					adjustUnchecked(treasury, tId, token, balance);
					hederaLedger.updateTokenXfers(tId, aId, -balance);
					hederaLedger.updateTokenXfers(tId, treasury, balance);
				}
			}
			return outcome;
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
		var divisibility = token.divisibility();
		var proposedFloat = token.tokenFloat() + change;
		var validity = floatAndDivisibilityCheck(proposedFloat, divisibility);
		if (validity != OK) {
			return failure;
		}
		apply(tId, t -> t.adjustFloatBy(change));
		long tinyAdjustment = BigInteger.valueOf(change)
				.multiply(BigInteger.valueOf(10).pow(divisibility))
				.longValueExact();
		return adjustBalance(token.treasury().toGrpcAccountId(), tId, tinyAdjustment);
	}

	@Override
	public TokenCreationResult createProvisionally(TokenCreation request, AccountID sponsor) {
		var adminKey = asUsableFcKey(request.getAdminKey());
		if (adminKey.isEmpty()) {
			return failure(INVALID_ADMIN_KEY);
		}

		var validity = symbolCheck(request.getSymbol());
		if (validity != OK) {
			return failure(validity);
		}
		validity = treasuryCheck(request.getTreasury());
		if (validity != OK) {
			return failure(validity);
		}
		validity = floatAndDivisibilityCheck(request.getFloat(), request.getDivisibility());
		if (validity != OK) {
			return failure(validity);
		}
		var freezeKey = asUsableFcKey(request.getFreezeKey());
		validity = freezeSemanticsCheck(freezeKey, request.getFreezeDefault());
		if (validity != OK) {
			return failure(validity);
		}
		var kycKey = asUsableFcKey(request.getKycKey());
		var wipeKey = asUsableFcKey(request.getWipeKey());
		var supplyKey = asUsableFcKey(request.getSupplyKey());

		pendingId = ids.newTokenId(sponsor);
		pendingCreation = new MerkleToken(
				request.getFloat(),
				request.getDivisibility(),
				adminKey.get(),
				request.getSymbol(),
				request.getFreezeDefault(),
				kycKey.isEmpty() || request.getKycDefault(),
				ofNullableAccountId(request.getTreasury()));
		kycKey.ifPresent(pendingCreation::setKycKey);
		wipeKey.ifPresent(pendingCreation::setWipeKey);
		freezeKey.ifPresent(pendingCreation::setFreezeKey);
		supplyKey.ifPresent(pendingCreation::setSupplyKey);

		return success(pendingId);
	}

	@Override
	public void commitCreation() {
		throwIfNoCreationPending();

		tokens.get().put(fromTokenId(pendingId), pendingCreation);
		symbolKeyedIds.put(pendingCreation.symbol(), pendingId);

		resetPendingCreation();
	}

	@Override
	public void rollbackCreation() {
		throwIfNoCreationPending();

		ids.reclaimLastId();
		resetPendingCreation();
	}

	@Override
	public ResponseCodeEnum update(TokenManagement changes) {
		var tId = resolve(changes.getToken());
		if (tId == MISSING_TOKEN) {
			return INVALID_TOKEN_REF;
		}
		var hasNewSymbol = changes.getSymbol().length() > 0;
		if (hasNewSymbol) {
			var validity = symbolCheck(changes.getSymbol());
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
			if (OK != appliedValidity.get()) {
				return;
			}
			if (changes.hasAdminKey()) {
				token.setAdminKey(asFcKeyUnchecked(changes.getAdminKey()));
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
			if (changes.hasTreasury()) {
				var treasuryId = EntityId.ofNullableAccountId(changes.getTreasury());
				token.setTreasury(treasuryId);
			}
		});
		return appliedValidity.get();
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

	private ResponseCodeEnum freezeSemanticsCheck(Optional<JKey> candidate, boolean freezeDefault) {
		if (candidate.isEmpty() && freezeDefault) {
			return TOKEN_HAS_NO_FREEZE_KEY;
		}
		return OK;
	}

	private ResponseCodeEnum floatAndDivisibilityCheck(long tokenFloat, int divisibility) {
		if (tokenFloat < 0) {
			return INVALID_TOKEN_FLOAT;
		}

		try {
			var divisibilityFloat = BigInteger.valueOf(10)
					.pow(divisibility);
			if (divisibilityFloat.longValue() < 0) {
				return INVALID_TOKEN_DIVISIBILITY;
			}

			var tinyTokenFloat = divisibilityFloat
					.multiply(BigInteger.valueOf(tokenFloat));
			return tinyTokenFloat.longValueExact() >= 0 ? OK : INVALID_TOKEN_DIVISIBILITY;
		} catch (ArithmeticException ignore) {
			return INVALID_TOKEN_DIVISIBILITY;
		}
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

	private ResponseCodeEnum treasuryCheck(AccountID id) {
		if (!ledger.exists(id) || (boolean) ledger.get(id, AccountProperty.IS_DELETED)) {
			return INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
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
