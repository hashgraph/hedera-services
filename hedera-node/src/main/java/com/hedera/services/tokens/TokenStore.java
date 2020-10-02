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

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

/**
 * Defines a type able to manage arbitrary tokens.
 *
 * @author Michael Tinker
 */
public interface TokenStore {
	TokenID MISSING_TOKEN = TokenID.getDefaultInstance();
	Consumer<MerkleToken> DELETION = token -> token.setDeleted(true);

	void setAccountsLedger(TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger);
	void setHederaLedger(HederaLedger ledger);

	void apply(TokenID id, Consumer<MerkleToken> change);
	boolean exists(TokenID id);
	boolean isKnownTreasury(AccountID id);
	boolean isTreasuryForToken(AccountID aId, TokenID tId);
	MerkleToken get(TokenID id);

	ResponseCodeEnum burn(TokenID tId, long amount);
	ResponseCodeEnum mint(TokenID tId, long amount);
	ResponseCodeEnum wipe(AccountID aId, TokenID tId, long wipingAmount, boolean skipKeyCheck);
	ResponseCodeEnum freeze(AccountID aId, TokenID tId);
	ResponseCodeEnum update(TokenUpdateTransactionBody changes, long now);
	ResponseCodeEnum unfreeze(AccountID aId, TokenID tId);
	ResponseCodeEnum grantKyc(AccountID aId, TokenID tId);
	ResponseCodeEnum revokeKyc(AccountID aId, TokenID tId);
	ResponseCodeEnum associate(AccountID aId, List<TokenID> tokens);
	ResponseCodeEnum dissociate(AccountID aId, List<TokenID> tokens);
	ResponseCodeEnum adjustBalance(AccountID aId, TokenID tId, long adjustment);

	TokenCreationResult createProvisionally(TokenCreateTransactionBody request, AccountID sponsor, long now);
	void commitCreation();
	void rollbackCreation();
	boolean isCreationPending();

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
}
