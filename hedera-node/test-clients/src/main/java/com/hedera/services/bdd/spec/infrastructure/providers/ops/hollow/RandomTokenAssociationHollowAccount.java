/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.listeners.TokenAccountRegistryRel;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.token.RandomTokenAssociation;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class RandomTokenAssociationHollowAccount extends RandomTokenAssociation {
    private final String[] signers;

    public RandomTokenAssociationHollowAccount(
            RegistrySourcedNameProvider<TokenID> tokens,
            RegistrySourcedNameProvider<AccountID> accounts,
            RegistrySourcedNameProvider<TokenAccountRegistryRel> tokenRels,
            String... signers) {
        super(tokens, accounts, tokenRels, new ResponseCodeEnum[] {});
        this.signers = signers;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        if (tokenRels.numPresent() >= ceilingNum) {
            return Optional.empty();
        }

        var account = accounts.getQualifying();
        if (account.isEmpty()) {
            return Optional.empty();
        }

        int numTokensToTry = BASE_RANDOM.nextInt(MAX_TOKENS_PER_OP) + 1;
        Set<String> chosen = new HashSet<>();
        while (numTokensToTry-- > 0) {
            var token = tokens.getQualifyingExcept(chosen);
            token.ifPresent(chosen::add);
        }
        if (chosen.isEmpty()) {
            return Optional.empty();
        }
        String[] toUse = chosen.toArray(new String[0]);

        var op = tokenAssociate(account.get(), toUse)
                .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                .hasKnownStatusFrom(INVALID_SIGNATURE)
                .signedBy(signers);

        return Optional.of(op);
    }
}
