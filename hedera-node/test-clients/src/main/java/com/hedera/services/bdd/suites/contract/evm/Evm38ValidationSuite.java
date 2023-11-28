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

package com.hedera.services.bdd.suites.contract.evm;

import static com.hedera.services.bdd.spec.HapiPropertySource.asContract;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// @HapiTestSuite
public class Evm38ValidationSuite extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(Evm38ValidationSuite.class);
    private static final String NAME = "name";
    private static final String ERC_721_ABI = "ERC721ABI";
    private static final String NON_EXISTING_MIRROR_ADDRESS = "0000000000000000000000000000000000123456";
    private static final String NON_EXISTING_NON_MIRROR_ADDRESS = "1234561234561234561234561234568888123456";
    private static final String INTERNAL_CALLER_CONTRACT = "InternalCaller";
    private static final String INTERNAL_CALLEE_CONTRACT = "InternalCallee";
    private static final String REVERT_WITH_REVERT_REASON_FUNCTION = "revertWithRevertReason";
    private static final String REVERT_WITHOUT_REVERT_REASON_FUNCTION = "revertWithoutRevertReason";
    private static final String CALL_NON_EXISTING_FUNCTION = "callNonExisting";
    private static final String CALL_EXTERNAL_FUNCTION = "callExternalFunction";
    private static final String CALL_REVERT_WITH_REVERT_REASON_FUNCTION = "callRevertWithRevertReason";
    private static final String CALL_REVERT_WITHOUT_REVERT_REASON_FUNCTION = "callRevertWithoutRevertReason";
    private static final String TRANSFER_TO_FUNCTION = "transferTo";
    private static final String SEND_TO_FUNCTION = "sendTo";
    private static final String CALL_WITH_VALUE_TO_FUNCTION = "callWithValueTo";
    private static final String INNER_TXN = "innerTx";
    private static final Long INTRINSIC_GAS_COST = 21000L;
    private static final Long GAS_LIMIT_FOR_CALL = 25000L;
    private static final Long NOT_ENOUGH_GAS_LIMIT_FOR_CREATION = 500_000L;
    private static final Long ENOUGH_GAS_LIMIT_FOR_CREATION = 900_000L;
    private static final String RECEIVER = "receiver";
    private static final String ECDSA_KEY = "ecdsaKey";
    private static final String CUSTOM_PAYER = "customPayer";
    private static final String EVM_VERSION_PROPERTY = "contracts.evm.version";
    private static final String DYNAMIC_EVM_PROPERTY = "contracts.evm.version.dynamic";
    private static final String EVM_VERSION_038 = "v0.38";
    private static final String CREATE_TRIVIAL = "CreateTrivial";

    public static void main(String... args) {
        new Evm38ValidationSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(invalidContractCall());
    }

    HapiSpec invalidContractCall() {
        final var function = getABIFor(FUNCTION, "getIndirect", CREATE_TRIVIAL);

        return propertyPreservingHapiSpec("InvalidContract")
                .preserving(EVM_VERSION_PROPERTY, DYNAMIC_EVM_PROPERTY)
                .given(
                        overriding(DYNAMIC_EVM_PROPERTY, "true"),
                        overriding(EVM_VERSION_PROPERTY, EVM_VERSION_038),
                        withOpContext(
                                (spec, ctxLog) -> spec.registry().saveContractId("invalid", asContract("0.0.5555"))))
                .when()
                .then(contractCallWithFunctionAbi("invalid", function).hasKnownStatus(INVALID_CONTRACT_ID));
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
