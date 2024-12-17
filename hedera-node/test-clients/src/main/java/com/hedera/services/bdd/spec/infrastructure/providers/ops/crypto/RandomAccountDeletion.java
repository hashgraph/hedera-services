/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.LookupUtils;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoDelete;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Optional;

public class RandomAccountDeletion implements OpProvider {
    private final RegistrySourcedNameProvider<AccountID> accounts;
    private final ResponseCodeEnum[] permissiblePrechecks = standardPrechecksAnd(ACCOUNT_DELETED, INVALID_ACCOUNT_ID);
    private final ResponseCodeEnum[] permissibleOutcomes =
            standardOutcomesAnd(ACCOUNT_DELETED, INVALID_ACCOUNT_ID, TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);
    private final ResponseCodeEnum[] customOutcomes;

    public RandomAccountDeletion(RegistrySourcedNameProvider<AccountID> accounts, ResponseCodeEnum[] customOutcomes) {
        this.accounts = accounts;
        this.customOutcomes = customOutcomes;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        final var involved = LookupUtils.twoDistinct(accounts);
        if (involved.isEmpty()) {
            return Optional.empty();
        }
        HapiCryptoDelete op = cryptoDelete(involved.get().getKey())
                .purging()
                .transfer(involved.get().getValue())
                .hasPrecheckFrom(plus(permissiblePrechecks, customOutcomes))
                .hasKnownStatusFrom(plus(permissibleOutcomes, customOutcomes));
        return Optional.of(op);
    }
}
