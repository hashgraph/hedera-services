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

package com.hedera.services.bdd.suites.token;

import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.Key;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(TOKEN)
public class Hip540ChangeOrRemoveKeysSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(Hip540ChangeOrRemoveKeysSuite.class);
    private static final Key allZeros = Key.newBuilder()
            .setECDSASecp256K1(ByteString.fromHex("0000000000000000000000000000000000000000"))
            .build();
    private static final Key otherInvalidKey =
            Key.newBuilder().setECDSASecp256K1(ByteString.fromHex("00a9fF0a")).build();

    public static void main(String... args) {
        new Hip540ChangeOrRemoveKeysSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(positiveTests(), negativeTests());
    }

    private List<HapiSpec> positiveTests() {
        return List.of(changeToInvalid());
    }

    private List<HapiSpec> negativeTests() {
        return List.of();
    }

    @HapiTest
    public HapiSpec changeToInvalid() {
        String saltedName = salted("primary");
        final var civilian = "civilian";
        return defaultHapiSpec("changeToInvalid")
                .given(
                        cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed("adminKey"),
                        newKeyNamed("freezeKey"),
                        newKeyNamed("newFreezeKey"),
                        tokenCreate("primary")
                                .name(saltedName)
                                .initialSupply(500)
                                .adminKey("adminKey")
                                .freezeKey("freezeKey")
                                .payingWith(civilian))
                .when(tokenUpdate("primary")
                        .freezeKey("newFreezeKey")
                        .signedBy(civilian, "freezeKey")
                        .payingWith(civilian))
                .then(getTokenInfo("primary").logged());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
