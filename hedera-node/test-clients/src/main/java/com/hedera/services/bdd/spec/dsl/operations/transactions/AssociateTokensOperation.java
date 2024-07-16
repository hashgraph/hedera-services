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

package com.hedera.services.bdd.spec.dsl.operations.transactions;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecToken;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenAssociate;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Associates an account with one or more tokens.
 */
public class AssociateTokensOperation extends AbstractSpecTransaction<AssociateTokensOperation, HapiTokenAssociate>
        implements SpecOperation {
    private final SpecAccount account;
    private final List<SpecToken> tokens;

    // non-standard ArrayList initializer
    @SuppressWarnings({"java:S3599", "java:S1171"})
    public AssociateTokensOperation(@NonNull final SpecAccount account, @NonNull final List<SpecToken> tokens) {
        super(new ArrayList<>() {
            {
                add(account);
                addAll(tokens);
            }
        });
        this.account = account;
        this.tokens = tokens;
    }

    @Override
    protected AssociateTokensOperation self() {
        return this;
    }

    @NonNull
    @Override
    protected SpecOperation computeDelegate(@NonNull final HapiSpec spec) {
        return tokenAssociate(
                account.name(), tokens.stream().map(SpecToken::name).toArray(String[]::new));
    }
}
