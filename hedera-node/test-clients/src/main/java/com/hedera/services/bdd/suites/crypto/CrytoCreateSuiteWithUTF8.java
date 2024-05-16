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
import static com.hedera.services.bdd.spec.HapiSpec.UTF8Mode.FALSE;
import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(CRYPTO)
public class CrytoCreateSuiteWithUTF8 {
    HapiSpec.UTF8Mode utf8Mode = FALSE;

    @HapiTest
    final Stream<DynamicTest> createCryptoTxvWithUTF8Memo() {
        return defaultHapiSpec("CreateCryptoTxvWithUTF8Memo")
                .given(cryptoCreate("UTF8MemoTestAccount").via("utf8MemoTxn"))
                .when()
                .then(getTxnRecord("utf8MemoTxn").logged());
    }

    @HapiTest
    final Stream<DynamicTest> cryptoCreateTxnCustomSpec() {
        return customHapiSpec("UTF8CustomSpecMemoTxn")
                .withProperties(Map.of("default.useMemoUTF8", utf8Mode.toString()))
                .given(cryptoCreate("UTF8CustomSpecTestAccount").via("utf8CustomSpecMemoTxn"))
                .when()
                .then(getTxnRecord("utf8CustomSpecMemoTxn").logged());
    }
}
