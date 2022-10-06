/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.store.tokens;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNEXPECTED_TOKEN_DECIMALS;

import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.store.Store;
import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import java.util.function.Consumer;

/** Defines a type able to manage arbitrary tokens. */
public interface TokenStore extends Store<TokenID, MerkleToken> {
    TokenID MISSING_TOKEN = TokenID.getDefaultInstance();
    Consumer<MerkleToken> DELETION = token -> token.setDeleted(true);

    boolean associationExists(AccountID aId, TokenID tId);

    ResponseCodeEnum freeze(AccountID aId, TokenID tId);

    ResponseCodeEnum update(TokenUpdateTransactionBody changes, long now);

    ResponseCodeEnum unfreeze(AccountID aId, TokenID tId);

    ResponseCodeEnum grantKyc(AccountID aId, TokenID tId);

    ResponseCodeEnum revokeKyc(AccountID aId, TokenID tId);

    ResponseCodeEnum autoAssociate(AccountID aId, TokenID tokenId);

    ResponseCodeEnum adjustBalance(AccountID aId, TokenID tId, long adjustment);

    ResponseCodeEnum changeOwner(NftId nftId, AccountID from, AccountID to);

    ResponseCodeEnum changeOwnerWildCard(NftId nftId, AccountID from, AccountID to);

    boolean matchesTokenDecimals(TokenID tId, int expectedDecimals);

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
        if (change.hasExpectedDecimals()
                && !matchesTokenDecimals(change.tokenId(), change.getExpectedDecimals())) {
            validity = UNEXPECTED_TOKEN_DECIMALS;
        }
        if (validity == OK) {
            if (change.isForNft()) {
                validity =
                        changeOwner(
                                change.nftId(), change.accountId(), change.counterPartyAccountId());
            } else {
                validity = adjustBalance(change.accountId(), tokenId, change.getAggregatedUnits());
                if (validity == INSUFFICIENT_TOKEN_BALANCE) {
                    validity = change.codeForInsufficientBalance();
                }
            }
        }
        return validity;
    }
}
