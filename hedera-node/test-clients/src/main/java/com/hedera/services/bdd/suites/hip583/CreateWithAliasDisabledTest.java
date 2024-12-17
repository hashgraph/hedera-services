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

package com.hedera.services.bdd.suites.hip583;

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.keys.KeyShape.SECP256K1;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumContractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.FIVE_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SOURCE_KEY;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.PAY_RECEIVABLE_CONTRACT;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.AUTO_RENEW_PERIOD;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.CREATE_TOKEN_WITH_ALL_CUSTOM_FEES_AVAILABLE;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.DEFAULT_AMOUNT_TO_SEND;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.ECDSA_KEY;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.EXISTING_TOKEN;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.FIRST_CREATE_TXN;
import static com.hedera.services.bdd.suites.contract.precompile.CreatePrecompileSuite.TOKEN_CREATE_CONTRACT;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.ACCOUNT;
import static com.hedera.services.bdd.suites.ethereum.EthereumSuite.GAS_LIMIT;
import static com.hedera.services.bdd.suites.leaky.LeakyContractTestsSuite.GAS_TO_OFFER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;

import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.contract.Utils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;

/**
 * Tests expected behavior when the {@code cryptoCreateWithAlias.enabled} feature flag is off for
 * <a href="https://hips.hedera.com/hip/hip-583">HIP-583, "Expand alias support in CryptoCreate &amp; CryptoTransfer Transactions"</a>.
 */
@HapiTestLifecycle
public class CreateWithAliasDisabledTest {
    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
        testLifecycle.overrideInClass(Map.of("cryptoCreateWithAlias.enabled", "false"));
    }

    @HapiTest
    final Stream<DynamicTest> etx026AccountWithoutAliasCanMakeEthTxnsDueToAutomaticAliasCreation() {
        final String ACCOUNT = "account";
        return hapiTest(
                newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                cryptoCreate(ACCOUNT).key(SECP_256K1_SOURCE_KEY).balance(ONE_HUNDRED_HBARS),
                ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
                        .type(EthTxData.EthTransactionType.EIP1559)
                        .signingWith(SECP_256K1_SOURCE_KEY)
                        .payingWith(ACCOUNT)
                        .maxGasAllowance(FIVE_HBARS)
                        .nonce(0)
                        .gasLimit(GAS_LIMIT)
                        .hasKnownStatus(INVALID_ACCOUNT_ID));
    }

    @HapiTest
    final Stream<DynamicTest> createTokenWithInvalidFeeCollector() {
        return hapiTest(
                newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(ECDSA_KEY),
                uploadInitCode(TOKEN_CREATE_CONTRACT),
                contractCreate(TOKEN_CREATE_CONTRACT).gas(GAS_TO_OFFER),
                tokenCreate(EXISTING_TOKEN),
                withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_CREATE_CONTRACT,
                                        CREATE_TOKEN_WITH_ALL_CUSTOM_FEES_AVAILABLE,
                                        spec.registry()
                                                .getKey(ECDSA_KEY)
                                                .getECDSASecp256K1()
                                                .toByteArray(),
                                        HapiParserUtil.asHeadlongAddress(
                                                (byte[]) ArrayUtils.toPrimitive(Utils.asSolidityAddress(0, 0, 15252L))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(EXISTING_TOKEN))),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        AUTO_RENEW_PERIOD)
                                .via(FIRST_CREATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(ACCOUNT)
                                .refusingEthConversion()
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))),
                getTxnRecord(FIRST_CREATE_TXN).andAllChildRecords().logged(),
                getAccountBalance(ACCOUNT).logged(),
                getAccountBalance(TOKEN_CREATE_CONTRACT).logged(),
                getContractInfo(TOKEN_CREATE_CONTRACT).logged(),
                childRecordsCheck(
                        FIRST_CREATE_TXN,
                        CONTRACT_REVERT_EXECUTED,
                        TransactionRecordAsserts.recordWith()
                                .status(INVALID_CUSTOM_FEE_COLLECTOR)
                                .contractCallResult(ContractFnResultAsserts.resultWith()
                                        .error(INVALID_CUSTOM_FEE_COLLECTOR.name()))));
    }
}
