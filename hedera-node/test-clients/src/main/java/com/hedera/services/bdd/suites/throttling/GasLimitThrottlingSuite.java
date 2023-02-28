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

package com.hedera.services.bdd.suites.throttling;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOfDeferred;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.remembering;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class GasLimitThrottlingSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(GasLimitThrottlingSuite.class);
    private static final String CONTRACT = "Benchmark";
    private static final String USE_GAS_THROTTLE_PROP = "contracts.throttle.throttleByGas";
    private static final String CONS_MAX_GAS_PROP = "contracts.maxGasPerSec";
    public static final String PAYER_ACCOUNT = "payerAccount";

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(txsUnderGasLimitAllowed(), txOverGasLimitThrottled());
    }

    public static void main(String... args) {
        new GasLimitThrottlingSuite().runSuiteSync();
    }

    private HapiSpec txsUnderGasLimitAllowed() {
        final var NUM_CALLS = 10;
        final Map<String, String> startingProps = new HashMap<>();
        return defaultHapiSpec("TXsUnderGasLimitAllowed")
                .given(
                        remembering(startingProps, USE_GAS_THROTTLE_PROP, CONS_MAX_GAS_PROP),
                        overridingTwo(
                                USE_GAS_THROTTLE_PROP, "true",
                                CONS_MAX_GAS_PROP, "10000000"))
                .when(
                        /* we need the payer account, see SystemPrecheck IS_THROTTLE_EXEMPT */
                        cryptoCreate(PAYER_ACCOUNT).balance(ONE_MILLION_HBARS),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT).payingWith(PAYER_ACCOUNT))
                .then(
                        UtilVerbs.inParallel(asOpArray(NUM_CALLS, i -> contractCall(
                                        CONTRACT,
                                        "twoSSTOREs",
                                        Bytes.fromHexString("0x05").toArray())
                                .gas(100_000)
                                .payingWith(PAYER_ACCOUNT)
                                .hasKnownStatusFrom(SUCCESS, OK))),
                        UtilVerbs.sleepFor(1000),
                        contractCall(
                                        CONTRACT,
                                        "twoSSTOREs",
                                        Bytes.fromHexString("0x06").toArray())
                                .gas(1_000_000L)
                                .payingWith(PAYER_ACCOUNT)
                                .hasKnownStatusFrom(SUCCESS, OK),
                        overridingAllOfDeferred(() -> startingProps));
    }

    private HapiSpec txOverGasLimitThrottled() {
        final Map<String, String> startingProps = new HashMap<>();
        return defaultHapiSpec("TXOverGasLimitThrottled")
                .given(
                        remembering(startingProps, USE_GAS_THROTTLE_PROP, CONS_MAX_GAS_PROP),
                        overridingTwo(
                                USE_GAS_THROTTLE_PROP, "true",
                                CONS_MAX_GAS_PROP, "1000001"))
                .when(
                        cryptoCreate(PAYER_ACCOUNT).balance(ONE_MILLION_HBARS),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT))
                .then(
                        contractCall(
                                        CONTRACT,
                                        "twoSSTOREs",
                                        Bytes.fromHexString("0x05").toArray())
                                .gas(1_000_001)
                                .payingWith(PAYER_ACCOUNT)
                                .hasPrecheck(BUSY),
                        overridingAllOfDeferred(() -> startingProps));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
