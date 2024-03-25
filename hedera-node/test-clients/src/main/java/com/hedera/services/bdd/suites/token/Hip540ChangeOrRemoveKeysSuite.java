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
        return List.of(keysChange());
    }

    private List<HapiSpec> negativeTests() {
        return List.of();
    }

    @HapiTest
    public HapiSpec keysChange() {
        return defaultHapiSpec("keysChange")
                .given(
                        newKeyNamed("key"),
                        newKeyNamed("newKey"),
                        newKeyNamed("wipeKey"),
                        cryptoCreate("admin").key("key").balance(ONE_HUNDRED_HBARS),
                        // cryptoCreate("misc").key(allZeros).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate("misc").key(otherInvalidKey).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        tokenCreate("tbu")
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(10)
                                .adminKey("key")
                                .wipeKey("wipeKey"))
                .when(tokenUpdate("tbu").wipeKey("newKey").signedBy("key").payingWith("admin"))
                .then(/*getTokenInfo("tbu").logged(),
                        tokenUnfreeze("tbu", "misc").signedBy(GENESIS, "kycFreezeKey"),
                        grantTokenKyc("tbu", "misc").signedBy(GENESIS, "freezeThenKycKey"),
                        getAccountInfo("misc").logged(),
                        cryptoTransfer(moving(5, "tbu").between(TOKEN_TREASURY, "misc")),
                        mintToken("tbu", 10).signedBy(GENESIS, "wipeThenSupplyKey"),
                        burnToken("tbu", 10).signedBy(GENESIS, "wipeThenSupplyKey"),
                        wipeTokenAccount("tbu", "misc", 5).signedBy(GENESIS, "supplyThenWipeKey"),
                        getAccountInfo(TOKEN_TREASURY).logged()*/ );
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
