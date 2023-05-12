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

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static java.util.Collections.EMPTY_LIST;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RandomContractDeletion implements OpProvider {
    private final RegistrySourcedNameProvider<AccountID> accounts;
    private final RegistrySourcedNameProvider<ContractID> contracts;
    private boolean transferToContract = true;

    private final ResponseCodeEnum[] permissiblePrechecks =
            standardPrechecksAnd(ACCOUNT_DELETED, CONTRACT_DELETED, INVALID_ACCOUNT_ID);
    private final ResponseCodeEnum[] permissibleOutcomes =
            standardOutcomesAnd(ACCOUNT_DELETED, CONTRACT_DELETED, INVALID_ACCOUNT_ID, INVALID_CONTRACT_ID);

    public RandomContractDeletion(
            RegistrySourcedNameProvider<AccountID> accounts, RegistrySourcedNameProvider<ContractID> contracts) {
        this.accounts = accounts;
        this.contracts = contracts;
    }

    @Override
    public List<HapiSpecOperation> suggestedInitializers() {
        return EMPTY_LIST;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        final var tbd = contracts.getQualifying();
        if (tbd.isEmpty()) {
            return Optional.empty();
        }

        boolean contractThisTime = transferToContract;
        Optional<String> transfer;
        if (contractThisTime) {
            transfer = contracts.getQualifyingExcept(Set.of(tbd.get()));
        } else {
            transfer = accounts.getQualifying();
        }
        transferToContract = !transferToContract;

        if (transfer.isEmpty()) {
            return Optional.empty();
        }

        var op = contractDelete(tbd.get())
                .purging()
                .hasPrecheckFrom(permissiblePrechecks)
                .hasKnownStatusFrom(permissibleOutcomes);
        if (contractThisTime) {
            op.transferContract(transfer.get());
        } else {
            op.transferAccount(transfer.get());
        }

        return Optional.of(op);
    }
}
