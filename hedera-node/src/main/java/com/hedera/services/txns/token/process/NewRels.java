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
package com.hedera.services.txns.token.process;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NewRels {
    public static List<TokenRelationship> listFrom(
            final Token provisionalToken,
            final TypedTokenStore tokenStore,
            final GlobalDynamicProperties dynamicProperties) {
        final var treasury = provisionalToken.getTreasury();
        final Set<Id> associatedSoFar = new HashSet<>();
        final List<TokenRelationship> newRels = new ArrayList<>();

        associateGiven(
                provisionalToken,
                treasury,
                tokenStore,
                associatedSoFar,
                newRels,
                dynamicProperties);
        for (final var customFee : provisionalToken.getCustomFees()) {
            if (customFee.requiresCollectorAutoAssociation()) {
                final var collector = customFee.getValidatedCollector();
                associateGiven(
                        provisionalToken,
                        collector,
                        tokenStore,
                        associatedSoFar,
                        newRels,
                        dynamicProperties);
            }
        }

        return newRels;
    }

    private static void associateGiven(
            final Token provisionalToken,
            final Account account,
            final TypedTokenStore tokenStore,
            final Set<Id> associatedSoFar,
            final List<TokenRelationship> newRelations,
            final GlobalDynamicProperties dynamicProperties) {
        final var accountId = account.getId();
        if (associatedSoFar.contains(accountId)) {
            return;
        }
        newRelations.addAll(
                account.associateWith(
                        List.of(provisionalToken), tokenStore, false, true, dynamicProperties));
        associatedSoFar.add(accountId);
    }

    private NewRels() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
