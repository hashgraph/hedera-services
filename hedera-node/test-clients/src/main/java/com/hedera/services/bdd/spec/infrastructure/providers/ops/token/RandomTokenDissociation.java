/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.infrastructure.providers.ops.token;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.listeners.TokenAccountRegistryRel;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;

public class RandomTokenDissociation implements OpProvider {
    private final RegistrySourcedNameProvider<TokenAccountRegistryRel> tokenRels;

    public RandomTokenDissociation(RegistrySourcedNameProvider<TokenAccountRegistryRel> tokenRels) {
        this.tokenRels = tokenRels;
    }

    private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(
            TOKEN_WAS_DELETED,
            ACCOUNT_IS_TREASURY,
            ACCOUNT_FROZEN_FOR_TOKEN,
            TOKEN_NOT_ASSOCIATED_TO_ACCOUNT,
            TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);

    @Override
    public Optional<HapiSpecOperation> get() {
        var relToDissociate = tokenRels.getQualifying();
        if (relToDissociate.isEmpty()) {
            return Optional.empty();
        }

        var implicitRel = relToDissociate.get();
        var rel = explicit(implicitRel);
        var op = tokenDissociate(rel.getLeft(), rel.getRight())
                .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                .hasKnownStatusFrom(permissibleOutcomes);
        return Optional.of(op);
    }

    static Pair<String, String> explicit(String implicitRel) {
        var divider = implicitRel.indexOf("|");
        var account = implicitRel.substring(0, divider);
        var token = implicitRel.substring(divider + 1);
        return Pair.of(account, token);
    }
}
