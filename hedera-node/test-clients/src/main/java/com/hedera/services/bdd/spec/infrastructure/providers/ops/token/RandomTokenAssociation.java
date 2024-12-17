/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.listeners.TokenAccountRegistryRel;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RandomTokenAssociation implements OpProvider {
    static final Logger log = LogManager.getLogger(RandomTokenAssociation.class);

    public static final int MAX_TOKENS_PER_OP = 2;
    public static final int DEFAULT_CEILING_NUM = 10_000;

    protected int ceilingNum = DEFAULT_CEILING_NUM;

    private final ResponseCodeEnum[] customOutcomes;
    protected final RegistrySourcedNameProvider<TokenID> tokens;
    protected final RegistrySourcedNameProvider<AccountID> accounts;
    protected final RegistrySourcedNameProvider<TokenAccountRegistryRel> tokenRels;
    private final ResponseCodeEnum[] permissibleOutcomes =
            standardOutcomesAnd(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT, TOKEN_WAS_DELETED, ACCOUNT_DELETED);

    public RandomTokenAssociation(
            RegistrySourcedNameProvider<TokenID> tokens,
            RegistrySourcedNameProvider<AccountID> accounts,
            RegistrySourcedNameProvider<TokenAccountRegistryRel> tokenRels,
            ResponseCodeEnum[] customOutcomes) {
        this.tokens = tokens;
        this.accounts = accounts;
        this.tokenRels = tokenRels;
        this.customOutcomes = customOutcomes;
    }

    public RandomTokenAssociation ceiling(int n) {
        ceilingNum = n;
        return this;
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
                .payingWith(account.get())
                .signedBy(account.get())
                .hasPrecheckFrom(plus(STANDARD_PERMISSIBLE_PRECHECKS, customOutcomes))
                .hasKnownStatusFrom(permissibleOutcomes);

        return Optional.of(op);
    }
}
