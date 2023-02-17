/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountRecords;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static java.util.Collections.EMPTY_LIST;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountRecords;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.Optional;

public class RandomAccountRecords implements OpProvider {
    private final RegistrySourcedNameProvider<AccountID> accounts;

    private final ResponseCodeEnum[] permissibleCostAnswerPrechecks = standardPrechecksAnd(ACCOUNT_DELETED);
    private final ResponseCodeEnum[] permissibleAnswerOnlyPrechecks =
            standardPrechecksAnd(ACCOUNT_DELETED, INSUFFICIENT_TX_FEE);

    public RandomAccountRecords(RegistrySourcedNameProvider<AccountID> accounts) {
        this.accounts = accounts;
    }

    @Override
    public List<HapiSpecOperation> suggestedInitializers() {
        return EMPTY_LIST;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        Optional<String> account = accounts.getQualifying();
        if (account.isEmpty()) {
            return Optional.empty();
        }

        HapiGetAccountRecords op = getAccountRecords(account.get())
                .hasCostAnswerPrecheckFrom(permissibleCostAnswerPrechecks)
                .hasAnswerOnlyPrecheckFrom(permissibleAnswerOnlyPrechecks);

        return Optional.of(op);
    }
}
