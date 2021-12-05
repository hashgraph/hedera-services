package com.hedera.services.store.tokens;

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

import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.store.Store;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;

import java.util.List;
import java.util.function.Consumer;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

/**
 * Defines a type able to manage arbitrary tokens.
 */
public interface TokenStore extends Store<TokenID, MerkleToken> {
	TokenID MISSING_TOKEN = TokenID.getDefaultInstance();
	Consumer<MerkleToken> DELETION = token -> token.setDeleted(true);

	boolean isKnownTreasury(EntityNum id);

	void addKnownTreasury(EntityNum aId, TokenID tId);

	void removeKnownTreasuryForToken(EntityNum aId, TokenID tId);

	boolean associationExists(EntityNum aId, TokenID tId);

	boolean isTreasuryForToken(EntityNum aId, TokenID tId);

	List<TokenID> listOfTokensServed(EntityNum treasury);

	ResponseCodeEnum freeze(EntityNum aId, TokenID tId);

	ResponseCodeEnum update(TokenUpdateTransactionBody changes, long now);

	ResponseCodeEnum unfreeze(EntityNum aId, TokenID tId);

	ResponseCodeEnum grantKyc(EntityNum aId, TokenID tId);

	ResponseCodeEnum revokeKyc(EntityNum aId, TokenID tId);

	ResponseCodeEnum associate(EntityNum aId, List<TokenID> tokens, boolean automaticAssociation);

	ResponseCodeEnum adjustBalance(EntityNum aId, TokenID tId, long adjustment);

	ResponseCodeEnum changeOwner(NftId nftId, EntityNum from, EntityNum to);

	ResponseCodeEnum changeOwnerWildCard(NftId nftId, EntityNum from, EntityNum to);

	default TokenID resolve(TokenID id) {
		return exists(id) ? id : MISSING_TOKEN;
	}

	default ResponseCodeEnum delete(TokenID id) {
		var idRes = resolve(id);
		if (idRes == MISSING_TOKEN) {
			return INVALID_TOKEN_ID;
		}

		var token = get(id);
		if (token.adminKey().isEmpty()) {
			return TOKEN_IS_IMMUTABLE;
		}
		if (token.isDeleted()) {
			return TOKEN_WAS_DELETED;
		}

		apply(id, DELETION);
		return OK;
	}

	default ResponseCodeEnum tryTokenChange(BalanceChange change) {
		var validity = OK;
		var tokenId = resolve(change.tokenId());
		if (tokenId == MISSING_TOKEN) {
			validity = INVALID_TOKEN_ID;
		}
		final var aId = change.accountId();
		if (validity == OK) {
			if (change.isForNft()) {
				final var cId = change.counterPartyAccountId();
				validity = changeOwner(change.nftId(), aId, cId);
			} else {
				validity = adjustBalance(aId, tokenId, change.units());
				if (validity == INSUFFICIENT_TOKEN_BALANCE) {
					validity = change.codeForInsufficientBalance();
				}
			}
		}
		return validity;
	}
}
