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
package com.hedera.services.bdd.suites.contract.opcodes;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SelfDestructSuite extends HapiSuite {
    private final Logger LOGGER = LogManager.getLogger(SelfDestructSuite.class);

    public static void main(String... args) {
        new SelfDestructSuite().runSuiteAsync();
    }

    @Override
    protected Logger getResultsLogger() {
        return LOGGER;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                hscsEvm008SelfDestructInConstructorWorks(), hscsEvm008SelfDestructWhenCalling());
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    private HapiSpec hscsEvm008SelfDestructInConstructorWorks() {
        final var contract = "FactorySelfDestructConstructor";
        final var nextAccount = "civilian";

        return defaultHapiSpec("HSCS_EVM_008_SelfDestructInConstructorWorks")
                .given(uploadInitCode(contract))
                .when(
                        contractCreate(contract).balance(3 * ONE_HBAR).via("contractCreate"),
                        cryptoCreate(nextAccount))
                .then(
                        getAccountInfo(contract).hasCostAnswerPrecheck(ACCOUNT_DELETED),
                        getContractInfo(contract).has(contractWith().isDeleted()),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var registry = spec.registry();
                                    final var destroyedNum =
                                            registry.getContractId(contract).getContractNum();
                                    final var childInfoQuery =
                                            getContractInfo("0.0." + (destroyedNum + 1))
                                                    .has(contractWith().isNotDeleted());
                                    allRunFor(spec, childInfoQuery);
                                }));
    }

    private HapiSpec hscsEvm008SelfDestructWhenCalling() {
        final var contract = "SelfDestructCallable";
        return defaultHapiSpec("HSCS_EVM_008_SelfDestructWhenCalling")
                .given(cryptoCreate("acc").balance(5 * ONE_HUNDRED_HBARS), uploadInitCode(contract))
                .when(contractCreate(contract).via("cc").payingWith("acc").hasKnownStatus(SUCCESS))
                .then(
                        contractCall(contract, "destroy").payingWith("acc"),
                        getAccountInfo(contract).hasCostAnswerPrecheck(ACCOUNT_DELETED),
                        getContractInfo(contract).has(contractWith().isDeleted()));
    }
}
