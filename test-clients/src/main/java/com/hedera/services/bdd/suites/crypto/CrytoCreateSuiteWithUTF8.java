package com.hedera.services.bdd.suites.crypto;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.HapiApiSpec.UTF8Mode.*;

public class CrytoCreateSuiteWithUTF8 extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(CrytoCreateSuiteWithUTF8.class);

    HapiApiSpec.UTF8Mode utf8Mode = FALSE;

    public static void main(String... args) {
        new CrytoCreateSuiteWithUTF8().runSuiteSync();
    }

    @Override
    protected List<HapiApiSpec> getSpecsInSuite() {
        return allOf(
                positiveTests()
        );
    }

    private List<HapiApiSpec> positiveTests() {
        return Arrays.asList(
                createCryptoTxvWithUTF8Memo(),
                cryptoCreateTxnCustomSpec()
        );
    }

    private HapiApiSpec createCryptoTxvWithUTF8Memo() {
        return defaultHapiSpec("CreateCryptoTxvWithUTF8Memo")
                .given(
                        cryptoCreate("UTF8MemoTestAccount")
                                .via("utf8MemoTxn")
                ).when().then(
                        getTxnRecord("utf8MemoTxn").logged()
                );
    }

    private HapiApiSpec cryptoCreateTxnCustomSpec(){
        return customHapiSpec("UTF8CustomSpecMemoTxn")
                .withProperties( Map.of("default.useMemoUTF8", utf8Mode.toString()) )
                .given(
                        cryptoCreate("UTF8CustomSpecTestAccount")
                        .via("utf8CustomSpecMemoTxn")
                )
                .when().then(
                        getTxnRecord("utf8CustomSpecMemoTxn").logged()
                );
    }

    @Override
    protected Logger getResultsLogger() { return log; }
}
