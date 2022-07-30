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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenPauseTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PauseLogic {
    private final TypedTokenStore tokenStore;

    @Inject
    public PauseLogic(final TypedTokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    public void pause(final Id targetTokenId) {
        /* --- Load the model objects --- */
        var token = tokenStore.loadPossiblyPausedToken(targetTokenId);

        /* --- Do the business logic --- */
        token.changePauseStatus(true);

        /* --- Persist the updated models --- */
        tokenStore.commitToken(token);
    }

    public ResponseCodeEnum validateSyntax(final TransactionBody txnBody) {
        TokenPauseTransactionBody op = txnBody.getTokenPause();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        return OK;
    }
}
