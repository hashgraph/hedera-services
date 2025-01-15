/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.hip551;

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.atomicBatch;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

@HapiTestLifecycle
public class AtomicBatchTest {

    @HapiTest
    // just test that the batch is submitted
    public Stream<DynamicTest> simpleBatchTest() {
        return hapiTest(atomicBatch(
                cryptoCreate("PAYER").balance(ONE_HBAR), cryptoCreate("SENDER").balance(1L)));
    }
}
