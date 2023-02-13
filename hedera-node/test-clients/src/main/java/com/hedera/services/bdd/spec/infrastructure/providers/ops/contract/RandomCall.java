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
package com.hedera.services.bdd.spec.infrastructure.providers.ops.contract;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static java.util.Collections.EMPTY_LIST;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.meta.ActionableContractCall;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCall;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.Optional;

public class RandomCall implements OpProvider {
    private final EntityNameProvider<ActionableContractCall> calls;

    private final ResponseCodeEnum[] permissiblePrechecks = standardPrechecksAnd(CONTRACT_DELETED);
    private final ResponseCodeEnum[] permissibleOutcomes =
            standardOutcomesAnd(INVALID_CONTRACT_ID, CONTRACT_DELETED);

    public RandomCall(EntityNameProvider<ActionableContractCall> calls) {
        this.calls = calls;
    }

    @Override
    public List<HapiSpecOperation> suggestedInitializers() {
        return EMPTY_LIST;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        Optional<String> call = calls.getQualifying();
        if (call.isEmpty()) {
            return Optional.empty();
        }

        HapiContractCall op =
                contractCallFrom(call.get())
                        .hasPrecheckFrom(permissiblePrechecks)
                        .hasKnownStatusFrom(permissibleOutcomes);

        return Optional.of(op);
    }
}
