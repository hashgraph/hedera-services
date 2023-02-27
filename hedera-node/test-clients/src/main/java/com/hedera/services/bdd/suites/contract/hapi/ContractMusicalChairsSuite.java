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

package com.hedera.services.bdd.suites.contract.hapi;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ContractMusicalChairsSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ContractMusicalChairsSuite.class);

    public static void main(String... args) {
        new ContractMusicalChairsSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(playGame());
    }

    private HapiSpec playGame() {
        final var dj = "dj";
        final var players = IntStream.range(1, 30).mapToObj(i -> "Player" + i).toList();
        final var contract = "MusicalChairs";

        List<HapiSpecOperation> given = new ArrayList<>();
        List<HapiSpecOperation> when = new ArrayList<>();
        List<HapiSpecOperation> then = new ArrayList<>();

        ////// Create contract //////
        given.add(cryptoCreate(dj).balance(10 * ONE_HUNDRED_HBARS));
        given.add(getAccountInfo(DEFAULT_CONTRACT_SENDER).savingSnapshot(DEFAULT_CONTRACT_SENDER));
        given.add(uploadInitCode(contract));
        given.add(withOpContext((spec, opLog) -> allRunFor(
                spec,
                contractCreate(
                                contract,
                                asHeadlongAddress(spec.registry()
                                        .getAccountInfo(DEFAULT_CONTRACT_SENDER)
                                        .getContractAccountID()))
                        .payingWith(dj))));

        ////// Add the players //////
        players.stream().map(TxnVerbs::cryptoCreate).forEach(given::add);

        ////// Start the music! //////
        when.add(contractCall(contract, "startMusic").payingWith(DEFAULT_CONTRACT_SENDER));

        ////// 100 "random" seats taken //////
        new Random(0x1337)
                .ints(100, 0, 29)
                .forEach(i -> when.add(contractCall(contract, "sitDown")
                        .payingWith(players.get(i))
                        .refusingEthConversion()
                        .hasAnyStatusAtAll())); // sometimes a player sits
        // too soon, so don't fail
        // on reverts

        ////// Stop the music! //////
        then.add(contractCall(contract, "stopMusic").payingWith(DEFAULT_CONTRACT_SENDER));

        ////// And the winner is..... //////
        then.add(withOpContext((spec, opLog) -> allRunFor(
                spec,
                contractCallLocal(contract, "whoIsOnTheBubble")
                        .has(resultWith()
                                .resultThruAbi(
                                        getABIFor(FUNCTION, "whoIsOnTheBubble", contract),
                                        isLiteralResult(new Object[] {
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID("Player13")))
                                        }))))));

        return defaultHapiSpec("playGame")
                .given(given.toArray(HapiSpecOperation[]::new))
                .when(when.toArray(HapiSpecOperation[]::new))
                .then(then.toArray(HapiSpecOperation[]::new));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
