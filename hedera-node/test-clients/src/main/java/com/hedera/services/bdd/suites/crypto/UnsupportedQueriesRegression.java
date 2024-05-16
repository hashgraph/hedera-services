/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.crypto;

import static com.hedera.services.bdd.junit.TestTags.CRYPTO;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.getAccountNftInfosNotSupported;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.getBySolidityIdNotSupported;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.getClaimNotSupported;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.getFastRecordNotSupported;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.getStakersNotSupported;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.getTokenNftInfosNotSupported;

import com.hedera.services.bdd.junit.HapiTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(CRYPTO)
public class UnsupportedQueriesRegression {
    @HapiTest
    final Stream<DynamicTest> verifyUnsupportedOps() {
        return defaultHapiSpec("VerifyUnsupportedOps")
                .given()
                .when()
                .then(
                        getClaimNotSupported(),
                        getStakersNotSupported(),
                        getFastRecordNotSupported(),
                        getBySolidityIdNotSupported(),
                        getTokenNftInfosNotSupported(),
                        getAccountNftInfosNotSupported());
    }
}
