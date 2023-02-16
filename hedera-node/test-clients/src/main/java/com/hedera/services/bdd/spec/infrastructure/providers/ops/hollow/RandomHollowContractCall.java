/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.suites.regression.factories.AccountCompletionFuzzingFactory.CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCall;
import com.hederahashgraph.api.proto.java.AccountID;

public class RandomHollowContractCall extends RandomOperationSignedByHollowAccount {

    public RandomHollowContractCall(
            HapiSpecRegistry registry, RegistrySourcedNameProvider<AccountID> accounts) {
        super(registry, accounts);
    }

    @Override
    protected HapiContractCall generateOpSignedBy(String keyName) {
        return contractCall(CONTRACT)
                .payingWith(keyName)
                .sigMapPrefixes(uniqueWithFullPrefixesFor(keyName))
                .hasPrecheckFrom(OK, INSUFFICIENT_PAYER_BALANCE, BUSY)
                .hasKnownStatusFrom(SUCCESS, INSUFFICIENT_PAYER_BALANCE)
                .noLogging();
    }
}
