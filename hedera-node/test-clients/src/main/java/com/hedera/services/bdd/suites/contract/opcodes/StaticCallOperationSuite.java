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

package com.hedera.services.bdd.suites.contract.opcodes;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StaticCallOperationSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(StaticCallOperationSuite.class);
    private static final String STATIC_CALL = "staticcall";

    public static void main(String[] args) {
        new StaticCallOperationSuite().runSuiteAsync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(verifiesExistence());
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    HapiSpec verifiesExistence() {
        final var contract = "CallOperationsChecker";
        final var INVALID_ADDRESS = "0x0000000000000000000000000000000000123456";

        return defaultHapiSpec("VerifiesExistence")
                .given(uploadInitCode(contract), contractCreate(contract))
                .when()
                .then(
                        contractCall(contract, STATIC_CALL, asHeadlongAddress(INVALID_ADDRESS))
                                .hasKnownStatus(INVALID_SOLIDITY_ADDRESS),
                        withOpContext((spec, opLog) -> {
                            final var id = spec.registry().getAccountID(DEFAULT_PAYER);
                            final var solidityAddress = HapiPropertySource.asHexedSolidityAddress(id);

                            final var contractCall = contractCall(
                                            contract, STATIC_CALL, asHeadlongAddress(solidityAddress))
                                    .hasKnownStatus(SUCCESS);

                            final var contractCallLocal =
                                    contractCallLocal(contract, STATIC_CALL, asHeadlongAddress(solidityAddress));

                            allRunFor(spec, contractCall, contractCallLocal);
                        }));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
