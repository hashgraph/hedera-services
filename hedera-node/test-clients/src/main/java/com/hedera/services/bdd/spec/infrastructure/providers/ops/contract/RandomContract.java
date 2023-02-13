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

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.meta.ActionableContractCall;
import com.hedera.services.bdd.spec.infrastructure.meta.ActionableContractCallLocal;
import com.hedera.services.bdd.spec.infrastructure.meta.SupportedContract;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCreate;
import com.hedera.services.bdd.spec.transactions.file.HapiFileCreate;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class RandomContract implements OpProvider {
    public static final int DEFAULT_CEILING_NUM = 100;

    private int ceilingNum = DEFAULT_CEILING_NUM;

    private final AtomicInteger opNo = new AtomicInteger();
    private final EntityNameProvider<Key> keys;
    private final RegistrySourcedNameProvider<ContractID> contracts;
    private final SupportedContract[] choices = SupportedContract.values();
    private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(INVALID_CONTRACT_ID);

    public RandomContract(
            EntityNameProvider<Key> keys, RegistrySourcedNameProvider<ContractID> contracts) {
        this.keys = keys;
        this.contracts = contracts;
    }

    public RandomContract ceiling(int n) {
        ceilingNum = n;
        return this;
    }

    @Override
    public List<HapiSpecOperation> suggestedInitializers() {
        List<HapiSpecOperation> ops = new ArrayList<>();
        for (SupportedContract choice : choices) {
            HapiFileCreate op =
                    fileCreate(fileFor(choice)).noLogging().path(choice.getPathToBytecode());
            ops.add(op);
        }
        return ops;
    }

    private String fileFor(SupportedContract choice) {
        return (choice.toString() + "-bytecode");
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        if (contracts.numPresent() >= ceilingNum) {
            return Optional.empty();
        }

        Optional<String> key = keys.getQualifying();
        if (key.isEmpty()) {
            return Optional.empty();
        }

        int n = opNo.getAndIncrement();
        final String tentativeContract = my("contract" + n);
        final SupportedContract choice = choices[n % choices.length];

        HapiContractCreate op =
                contractCreate(tentativeContract)
                        .adminKey(key.get())
                        .bytecode(fileFor(choice))
                        .skipAccountRegistration()
                        .hasPrecheckFrom(STANDARD_PERMISSIBLE_PRECHECKS)
                        .hasKnownStatusFrom(permissibleOutcomes)
                        .uponSuccess(
                                registry -> {
                                    registry.saveContractChoice(tentativeContract, choice);
                                    AtomicInteger tag = new AtomicInteger();
                                    choice.getCallDetails()
                                            .forEach(
                                                    detail -> {
                                                        ActionableContractCall call =
                                                                new ActionableContractCall(
                                                                        tentativeContract, detail);
                                                        registry.saveActionableCall(
                                                                tentativeContract
                                                                        + "-"
                                                                        + tag.getAndIncrement(),
                                                                call);
                                                    });
                                    choice.getLocalCallDetails()
                                            .forEach(
                                                    detail -> {
                                                        ActionableContractCallLocal call =
                                                                new ActionableContractCallLocal(
                                                                        tentativeContract, detail);
                                                        registry.saveActionableLocalCall(
                                                                tentativeContract
                                                                        + "-"
                                                                        + tag.getAndIncrement(),
                                                                call);
                                                    });
                                });
        return Optional.of(op);
    }

    private String my(String opName) {
        return unique(opName, RandomContract.class);
    }
}
