/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.records;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithTuple;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createDefaultContract;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.addLogInfo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.takeBalanceSnapshots;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateRecordTransactionFees;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateTransferListForBalances;
import static java.util.function.Function.identity;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ContractRecordsSanityCheckSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ContractRecordsSanityCheckSuite.class);
    private static final String BALANCE_LOOKUP = "BalanceLookup";
    public static final String PAYABLE_CONTRACT = "PayReceivable";
    public static final String ALTRUISTIC_TXN = "altruisticTxn";

    public static void main(String... args) {
        new ContractRecordsSanityCheckSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                contractCallWithSendRecordSanityChecks(),
                circularTransfersRecordSanityChecks(),
                contractCreateRecordSanityChecks(),
                contractUpdateRecordSanityChecks(),
                contractDeleteRecordSanityChecks());
    }

    private HapiSpec contractDeleteRecordSanityChecks() {
        return defaultHapiSpec("ContractDeleteRecordSanityChecks")
                .given(
                        flattened(
                                uploadInitCode(BALANCE_LOOKUP),
                                contractCreate(BALANCE_LOOKUP).balance(1_000L),
                                takeBalanceSnapshots(
                                        BALANCE_LOOKUP,
                                        FUNDING,
                                        NODE,
                                        STAKING_REWARD,
                                        NODE_REWARD,
                                        DEFAULT_PAYER)))
                .when(contractDelete(BALANCE_LOOKUP).via("txn").transferAccount(DEFAULT_PAYER))
                .then(
                        validateTransferListForBalances(
                                "txn",
                                List.of(
                                        FUNDING,
                                        NODE,
                                        STAKING_REWARD,
                                        NODE_REWARD,
                                        DEFAULT_PAYER,
                                        BALANCE_LOOKUP),
                                Set.of(BALANCE_LOOKUP)),
                        validateRecordTransactionFees("txn"));
    }

    private HapiSpec contractCreateRecordSanityChecks() {
        return defaultHapiSpec("ContractCreateRecordSanityChecks")
                .given(
                        flattened(
                                uploadInitCode(BALANCE_LOOKUP),
                                takeBalanceSnapshots(
                                        FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER)))
                .when(contractCreate(BALANCE_LOOKUP).balance(1_000L).via("txn"))
                .then(
                        validateTransferListForBalances(
                                "txn",
                                List.of(
                                        FUNDING,
                                        NODE,
                                        STAKING_REWARD,
                                        NODE_REWARD,
                                        DEFAULT_PAYER,
                                        BALANCE_LOOKUP)),
                        validateRecordTransactionFees("txn"));
    }

    private HapiSpec contractCallWithSendRecordSanityChecks() {
        return defaultHapiSpec("ContractCallWithSendRecordSanityChecks")
                .given(
                        flattened(
                                uploadInitCode(PAYABLE_CONTRACT),
                                contractCreate(PAYABLE_CONTRACT),
                                UtilVerbs.takeBalanceSnapshots(
                                        PAYABLE_CONTRACT,
                                        FUNDING,
                                        NODE,
                                        STAKING_REWARD,
                                        NODE_REWARD,
                                        DEFAULT_PAYER)))
                .when(
                        contractCall(PAYABLE_CONTRACT, "deposit", BigInteger.valueOf(1_000L))
                                .via("txn")
                                .sending(1_000L))
                .then(
                        validateTransferListForBalances(
                                "txn",
                                List.of(
                                        FUNDING,
                                        NODE,
                                        STAKING_REWARD,
                                        NODE_REWARD,
                                        DEFAULT_PAYER,
                                        PAYABLE_CONTRACT)),
                        validateRecordTransactionFees("txn"));
    }

    private HapiSpec circularTransfersRecordSanityChecks() {
        final var contractName = "CircularTransfers";
        int numAltruists = 3;
        ToLongFunction<String> initBalanceFn = ignore -> 1_000_000L;
        long initKeepAmountDivisor = 2;
        BigInteger stopBalance = BigInteger.valueOf(399_999L);

        String[] canonicalAccounts = {FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER};
        String[] altruists =
                IntStream.range(0, numAltruists)
                        .mapToObj(i -> String.format("Altruist%s", (char) ('A' + i)))
                        .toArray(String[]::new);

        return defaultHapiSpec("CircularTransfersRecordSanityChecks")
                .given(
                        flattened(
                                uploadInitCode(contractName),
                                Stream.of(altruists)
                                        .map(
                                                suffix ->
                                                        createDefaultContract(contractName + suffix)
                                                                .bytecode(contractName))
                                        .toArray(HapiSpecOperation[]::new),
                                Stream.of(altruists)
                                        .map(
                                                suffix ->
                                                        contractCallWithTuple(
                                                                        contractName + suffix,
                                                                        SET_NODES_ABI,
                                                                        spec ->
                                                                                Tuple.singleton(
                                                                                        Stream.of(
                                                                                                        altruists)
                                                                                                .map(
                                                                                                        a ->
                                                                                                                BigInteger
                                                                                                                        .valueOf(
                                                                                                                                spec.registry()
                                                                                                                                        .getContractId(
                                                                                                                                                contractName
                                                                                                                                                        + a)
                                                                                                                                        .getContractNum()))
                                                                                                .toArray(
                                                                                                        BigInteger
                                                                                                                        []
                                                                                                                ::new)))
                                                                .gas(120_000)
                                                                .via(
                                                                        "txnFor"
                                                                                + contractName
                                                                                + suffix)
                                                                .sending(
                                                                        initBalanceFn.applyAsLong(
                                                                                contractName
                                                                                        + suffix)))
                                        .toArray(HapiSpecOperation[]::new),
                                UtilVerbs.takeBalanceSnapshots(
                                        Stream.of(
                                                        Stream.of(altruists)
                                                                .map(
                                                                        suffix ->
                                                                                contractName
                                                                                        + suffix),
                                                        Stream.of(canonicalAccounts))
                                                .flatMap(identity())
                                                .toArray(String[]::new))))
                .when(
                        contractCallWithFunctionAbi(
                                        contractName + altruists[0],
                                        RECEIVE_AND_SEND_ABI,
                                        initKeepAmountDivisor,
                                        stopBalance)
                                .via(ALTRUISTIC_TXN))
                .then(
                        validateTransferListForBalances(
                                ALTRUISTIC_TXN,
                                Stream.concat(
                                                Stream.of(canonicalAccounts),
                                                Stream.of(altruists)
                                                        .map(suffix -> contractName + suffix))
                                        .toList()),
                        validateRecordTransactionFees(ALTRUISTIC_TXN),
                        addLogInfo(
                                (spec, infoLog) -> {
                                    long[] finalBalances =
                                            IntStream.range(0, numAltruists)
                                                    .mapToLong(
                                                            ignore -> initBalanceFn.applyAsLong(""))
                                                    .toArray();
                                    int i = 0;
                                    long divisor = initKeepAmountDivisor;
                                    while (true) {
                                        long toKeep = finalBalances[i] / divisor;
                                        if (toKeep < stopBalance.longValue()) {
                                            break;
                                        }
                                        int j = (i + 1) % numAltruists;
                                        finalBalances[j] += (finalBalances[i] - toKeep);
                                        finalBalances[i] = toKeep;
                                        i = j;
                                        divisor++;
                                    }

                                    infoLog.info("Expected Final Balances");
                                    infoLog.info("-----------------------");
                                    for (i = 0; i < numAltruists; i++) {
                                        infoLog.info("  {} = {} tinyBars", i, finalBalances[i]);
                                    }
                                }));
    }

    private HapiSpec contractUpdateRecordSanityChecks() {
        return defaultHapiSpec("ContractUpdateRecordSanityChecks")
                .given(
                        flattened(
                                newKeyNamed("newKey").type(KeyFactory.KeyType.SIMPLE),
                                uploadInitCode(BALANCE_LOOKUP),
                                contractCreate(BALANCE_LOOKUP).balance(1_000L),
                                takeBalanceSnapshots(
                                        FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER)))
                .when(contractUpdate(BALANCE_LOOKUP).newKey("newKey").via("txn").fee(95_000_000L))
                .then(
                        validateTransferListForBalances(
                                "txn",
                                List.of(FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER)),
                        validateRecordTransactionFees("txn"));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    private static final String SET_NODES_ABI =
            "{ \"constant\": false, \"inputs\": [ { \"internalType\": \"uint64[]\", \"name\":"
                + " \"accounts\", \"type\": \"uint64[]\" }     ], \"name\": \"setNodes\","
                + " \"outputs\": [], \"payable\": true, \"stateMutability\": \"payable\", \"type\":"
                + " \"function\" }";

    private static final String RECEIVE_AND_SEND_ABI =
            "{ \"constant\": false, \"inputs\": [ { \"internalType\": \"uint32\", \"name\":"
                + " \"keepAmountDivisor\", \"type\": \"uint32\" }, { \"internalType\": \"uint256\","
                + " \"name\": \"stopBalance\", \"type\": \"uint256\" } ], \"name\":"
                + " \"receiveAndSend\", \"outputs\": [], \"payable\": true, \"stateMutability\":"
                + " \"payable\", \"type\": \"function\" }";
}
