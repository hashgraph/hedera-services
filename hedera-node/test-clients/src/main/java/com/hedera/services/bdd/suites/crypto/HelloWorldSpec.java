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
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;

import com.hedera.services.bdd.junit.HapiTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(CRYPTO)
public class HelloWorldSpec {
    @HapiTest
    final Stream<DynamicTest> balancesChangeOnTransfer() {
        return defaultHapiSpec("BalancesChangeOnTransfer")
                .given(
                        cryptoCreate("sponsor"),
                        cryptoCreate("beneficiary"),
                        balanceSnapshot("sponsorBefore", "sponsor"),
                        balanceSnapshot("beneficiaryBefore", "beneficiary"))
                .when(cryptoTransfer(tinyBarsFromTo("sponsor", "beneficiary", 1L))
                        .payingWith(GENESIS)
                        .memo("Hello World!"))
                .then(
                        getAccountBalance("sponsor").hasTinyBars(changeFromSnapshot("sponsorBefore", -1L)),
                        getAccountBalance("beneficiary").hasTinyBars(changeFromSnapshot("beneficiaryBefore", +1L)));
    }
}
