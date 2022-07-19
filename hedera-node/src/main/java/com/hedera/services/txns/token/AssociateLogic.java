/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.txns.validation.TokenListChecks.repeatsItself;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AssociateLogic {
    private final UsageLimits usageLimits;
    private final TypedTokenStore tokenStore;
    private final AccountStore accountStore;
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public AssociateLogic(
            final UsageLimits usageLimits,
            final TypedTokenStore tokenStore,
            final AccountStore accountStore,
            final GlobalDynamicProperties dynamicProperties) {
        this.tokenStore = tokenStore;
        this.usageLimits = usageLimits;
        this.accountStore = accountStore;
        this.dynamicProperties = dynamicProperties;
    }

    public void associate(final Id accountId, final List<TokenID> tokensList) {
        usageLimits.assertCreatableTokenRels(tokensList.size());
        final var tokenIds = tokensList.stream().map(Id::fromGrpcToken).toList();

        /* Load the models */
        final var account = accountStore.loadAccount(accountId);
        final var tokens = tokenIds.stream().map(tokenStore::loadToken).toList();

        /* Associate and commit the changes */
        final var newTokenRelationships =
                account.associateWith(tokens, tokenStore, false, false, dynamicProperties);

        accountStore.commitAccount(account);
        tokenStore.commitTokenRelationships(newTokenRelationships);
    }

    public ResponseCodeEnum validateSyntax(final TransactionBody txn) {
        final var op = txn.getTokenAssociate();
        if (!op.hasAccount()) {
            return INVALID_ACCOUNT_ID;
        }
        if (repeatsItself(op.getTokensList())) {
            return TOKEN_ID_REPEATED_IN_TOKEN_LIST;
        }

        return OK;
    }
}
