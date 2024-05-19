/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;

import com.hedera.services.bdd.junit.ContextRequirement;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.DynamicTest;

public class GasLimitThrottlingSuite {
    private static final String CONTRACT = "Benchmark";
    private static final String USE_GAS_THROTTLE_PROP = "contracts.throttle.throttleByGas";
    private static final String CONS_MAX_GAS_PROP = "contracts.maxGasPerSec";
    public static final String PAYER_ACCOUNT = "payerAccount";

    @LeakyHapiTest(ContextRequirement.PROPERTY_OVERRIDES)
    final Stream<DynamicTest> txOverGasLimitThrottled() {
        final Map<String, String> startingProps = new HashMap<>();
        final var MAX_GAS_PER_SECOND = 1_000_001L;
        return defaultHapiSpec("TXOverGasLimitThrottled")
                .given(
                        remembering(startingProps, USE_GAS_THROTTLE_PROP, CONS_MAX_GAS_PROP),
                        overridingTwo(
                                USE_GAS_THROTTLE_PROP, "true", CONS_MAX_GAS_PROP, String.valueOf(MAX_GAS_PER_SECOND)))
                .when(
                        cryptoCreate(PAYER_ACCOUNT).balance(ONE_MILLION_HBARS),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT))
                .then(
                        contractCall(
                                        CONTRACT,
                                        "twoSSTOREs",
                                        Bytes.fromHexString(
                                                        "0x0000000000000000000000000000000000000000000000000000000000000005")
                                                .toArray())
                                .gas(MAX_GAS_PER_SECOND + 1L)
                                .payingWith(PAYER_ACCOUNT)
                                .hasPrecheckFrom(MAX_GAS_LIMIT_EXCEEDED, BUSY),
                        overridingAllOfDeferred(() -> startingProps));
    }
}
