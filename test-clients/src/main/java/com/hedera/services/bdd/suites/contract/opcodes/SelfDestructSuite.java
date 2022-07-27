/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SelfDestructSuite extends HapiApiSuite {
    private final Logger LOGGER = LogManager.getLogger(SelfDestructSuite.class);

    public static void main(String... args) {
        new SelfDestructSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return LOGGER;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                new HapiApiSpec[] {
                    HSCS_EVM_008_SelfDestructInConstructorWorks(),
                    HSCS_EVM_008_SelfDestructWhenCalling()
                });
    }

    private HapiApiSpec HSCS_EVM_008_SelfDestructInConstructorWorks() {
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
                                    final var nextNum =
                                            registry.getAccountID(nextAccount).getAccountNum();
                                    assertEquals(
                                            destroyedNum + 2,
                                            nextNum,
                                            "Two ID numbers should be consumed by the"
                                                    + " self-destroying contract");
                                }));
    }

    private HapiApiSpec HSCS_EVM_008_SelfDestructWhenCalling() {
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
