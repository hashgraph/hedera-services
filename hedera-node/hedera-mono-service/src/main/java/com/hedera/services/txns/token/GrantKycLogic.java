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
package com.hedera.services.txns.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GrantKycLogic {
    private final TypedTokenStore tokenStore;
    private final AccountStore accountStore;

    @Inject
    public GrantKycLogic(final TypedTokenStore tokenStore, final AccountStore accountStore) {
        this.tokenStore = tokenStore;
        this.accountStore = accountStore;
    }

    public void grantKyc(final Id targetTokenId, final Id targetAccountId) {
        /* --- Load the model objects --- */
        final var loadedToken = tokenStore.loadToken(targetTokenId);
        final var loadedAccount = accountStore.loadAccount(targetAccountId);
        final var tokenRelationship = tokenStore.loadTokenRelationship(loadedToken, loadedAccount);

        /* --- Do the business logic --- */
        tokenRelationship.changeKycState(true);

        /* --- Persist the updated models --- */
        tokenStore.commitTokenRelationships(List.of(tokenRelationship));
    }

    public ResponseCodeEnum validate(final TransactionBody txnBody) {
        TokenGrantKycTransactionBody op = txnBody.getTokenGrantKyc();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        if (!op.hasAccount()) {
            return INVALID_ACCOUNT_ID;
        }

        return OK;
    }
}
