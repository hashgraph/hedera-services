/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.leaky;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.resetToDefault;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_CONTRACT_CALL_RESULTS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_ETHEREUM_DATA;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.node.app.hapi.utils.ethereum.EthTxData.EthTransactionType;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(SMART_CONTRACT)
@SuppressWarnings("java:S5960")
public class LeakyEthereumTestsSuite extends HapiSuite {

    private static final String PAY_RECEIVABLE_CONTRACT = "PayReceivable";
    private static final Logger log = LogManager.getLogger(LeakyEthereumTestsSuite.class);

    public static void main(String... args) {
        new LeakyEthereumTestsSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return Stream.of(legacyUnprotectedEtxBeforeEIP155(), legacyEtxAfterEIP155())
                .toList();
    }

    // test unprotected legacy ethereum transactions before EIP155
    // this tests the behaviour when the `v` field is 27 or 28
    // in this case the passed chainId = 0 so ETX is before EIP155
    // and so `v` is calculated -> v = {0,1} + 27
    // source: https://eips.ethereum.org/EIPS/eip-155
    @HapiTest
    HapiSpec legacyUnprotectedEtxBeforeEIP155() {
        final String DEPOSIT = "deposit";
        final long depositAmount = 20_000L;
        final Integer chainId = 0;

        return propertyPreservingHapiSpec(
                        "legacyUnprotectedEtxBeforeEIP155",
                        NONDETERMINISTIC_ETHEREUM_DATA,
                        NONDETERMINISTIC_CONTRACT_CALL_RESULTS)
                .preserving(CHAIN_ID_PROP)
                .given(
                        overriding(CHAIN_ID_PROP, "" + chainId),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD))
                .when(
                        overriding(CHAIN_ID_PROP, "" + chainId),
                        ethereumCall(PAY_RECEIVABLE_CONTRACT, DEPOSIT, BigInteger.valueOf(depositAmount))
                                .type(EthTransactionType.LEGACY_ETHEREUM)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .via("legacyBeforeEIP155")
                                .nonce(0)
                                .chainId(chainId)
                                .gasPrice(50L)
                                .maxPriorityGas(2L)
                                .gasLimit(1_000_000L)
                                .sending(depositAmount)
                                .hasKnownStatus(ResponseCodeEnum.SUCCESS))
                .then(
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                getTxnRecord("legacyBeforeEIP155")
                                        .logged()
                                        .hasPriority(recordWith().status(SUCCESS)))),
                        resetToDefault(CHAIN_ID_PROP));
    }

    // test legacy ethereum transactions after EIP155
    // this tests the behaviour when the `v` field is 37 or 38
    // in this case the passed chainId = 1 so ETX is after EIP155
    // and so `v` is calculated -> v = {0,1} + CHAIN_ID * 2 + 35
    // source: https://eips.ethereum.org/EIPS/eip-155
    @HapiTest
    HapiSpec legacyEtxAfterEIP155() {
        final String DEPOSIT = "deposit";
        final long depositAmount = 20_000L;
        final Integer chainId = 1;

        return propertyPreservingHapiSpec(
                        "legacyEtxAfterEIP155", NONDETERMINISTIC_ETHEREUM_DATA, NONDETERMINISTIC_CONTRACT_CALL_RESULTS)
                .preserving(CHAIN_ID_PROP)
                .given(
                        overriding(CHAIN_ID_PROP, "" + chainId),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        contractCreate(PAY_RECEIVABLE_CONTRACT).adminKey(THRESHOLD))
                .when(
                        overriding(CHAIN_ID_PROP, "" + chainId),
                        ethereumCall(PAY_RECEIVABLE_CONTRACT, DEPOSIT, BigInteger.valueOf(depositAmount))
                                .type(EthTransactionType.LEGACY_ETHEREUM)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .via("legacyAfterEIP155")
                                .nonce(0)
                                .chainId(chainId)
                                .gasPrice(50L)
                                .maxPriorityGas(2L)
                                .gasLimit(1_000_000L)
                                .sending(depositAmount)
                                .hasKnownStatus(ResponseCodeEnum.SUCCESS))
                .then(
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                getTxnRecord("legacyAfterEIP155")
                                        .logged()
                                        .hasPriority(recordWith().status(SUCCESS)))),
                        resetToDefault(CHAIN_ID_PROP));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
