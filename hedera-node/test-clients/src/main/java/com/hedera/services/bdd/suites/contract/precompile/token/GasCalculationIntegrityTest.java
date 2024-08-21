/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.precompile.token;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.suites.HapiSuite.EXCHANGE_RATES;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.dsl.annotations.Account;
import com.hedera.services.bdd.spec.dsl.annotations.Contract;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.transactions.file.HapiFileUpdate;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Isolated;

@Tag(SMART_CONTRACT)
@DisplayName("updateToken")
@SuppressWarnings("java:S1192")
@HapiTestLifecycle
@Isolated
public class GasCalculationIntegrityTest {

    @Contract(contract = "GasCalculation", creationGas = 4_000_000L)
    static SpecContract updateTokenContract;

    @Contract(contract = "NumericContractComplex", creationGas = 1_000_000L)
    static SpecContract numericContractComplex;

    @Account(maxAutoAssociations = 10, tinybarBalance = ONE_MILLION_HBARS)
    static SpecAccount alice;

    @Account(maxAutoAssociations = 10, tinybarBalance = ONE_HBAR)
    static SpecAccount poorAccount;

    private final Stream<RatesProvider> testCases = Stream.of(
            new RatesProvider(10, 147),
            new RatesProvider(1, 1),
            new RatesProvider(1, 1),
            new RatesProvider(189, 10),
            new RatesProvider(30000, 552522226),
            new RatesProvider(787812, 1112),
            new RatesProvider(14444, 9999));

    private record RatesProvider(int hbarEquiv, int centEquiv) {}

    @BeforeAll
    public static void beforeAll(final @NonNull TestLifecycle lifecycle) {
        lifecycle.doAdhoc(alice.approveCryptoAllowance(numericContractComplex, ONE_HBAR));
    }

    @HapiTest
    @DisplayName("cannot update a missing token")
    public Stream<DynamicTest> cannotUpdateMissingToken() {
        return testCases.flatMap(ratesProvider -> hapiTest(
                updateTokenContract.call("donate", poorAccount).gas(44337L).andAssert(txn -> txn.via("first")),
                getTxnRecord("first").logged()));
    }

    @HapiTest
    @DisplayName("when using cryptoTransferV2 for hBar transfer")
    public Stream<DynamicTest> failToUseCryptoTransferV2() {
        return testCases.flatMap(ratesProvider -> hapiTest(
                numericContractComplex
                        .call("cryptoTransferV2", new long[] {-5, 5}, alice, poorAccount)
                        .gas(43304L)
                        .andAssert(txn -> txn.via("test")),
                getTxnRecord("test").logged()));
    }

    private HapiFileUpdate updateFile(final int hbarEquiv, final int centEquiv) {
        return fileUpdate(EXCHANGE_RATES).contents(spec -> {
            ByteString newRates =
                    spec.ratesProvider().rateSetWith(hbarEquiv, centEquiv).toByteString();
            spec.registry().saveBytes("midnightRate", newRates);
            return newRates;
        });
    }
}
