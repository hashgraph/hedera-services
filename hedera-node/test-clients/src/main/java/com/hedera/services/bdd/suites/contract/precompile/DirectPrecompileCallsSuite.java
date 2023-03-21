/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.directExchangeRatePrecompileContractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.directExchangeRatePrecompileEthereumCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.directHTSPrecompileContractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.directHTSPrecompileEthereumCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.directPrngPrecompileContractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.directPrngPrecompileEthereumCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.contract.HapiBaseCall.EXHANGE_RATE_CONTRACT_ID;
import static com.hedera.services.bdd.spec.transactions.contract.HapiBaseCall.HTS_PRECOMPILED_CONTRACT_ID;
import static com.hedera.services.bdd.spec.transactions.contract.HapiBaseCall.PRNG_CONTRACT_ID;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.suites.HapiSuite;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DirectPrecompileCallsSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(DirectPrecompileCallsSuite.class);

    private static final String DIRECT_CALL_TXN = "directCallTxn";
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";

    public static void main(String... args) {
        new DirectPrecompileCallsSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                directHTSPrecompileContractCallWorksCallingGetTokenType(),
                directHTSPrecompileEthereumCallWorksCallingGetTokenType(),
                directExchangeRatePrecompileContractCallWorks(),
                directExchangeRatePrecompileEthereumCallWorks(),
                directPrngPrecompileContractCallWorks(),
                directPrngPrecompileEthereumCallWorks());
    }

    private HapiSpec directHTSPrecompileContractCallWorksCallingGetTokenType() {
        final AtomicReference<Address> tokenAddress = new AtomicReference<>();
        return defaultHapiSpec("directHTSPrecompileContractCallWorksCallingGetTokenType")
                .given(tokenCreate(FUNGIBLE_TOKEN).tokenType(FUNGIBLE_COMMON).exposingAddressTo(tokenAddress::set))
                .when(sourcing(() -> directHTSPrecompileContractCall("getTokenType", tokenAddress.get())
                        .via(DIRECT_CALL_TXN)
                        .hasKnownStatus(SUCCESS)))
                .then(getTxnRecord(DIRECT_CALL_TXN)
                        .logged()
                        .hasPriority(TransactionRecordAsserts.recordWith()
                                .contractCallResult(ContractFnResultAsserts.resultWith()
                                        .contract(HTS_PRECOMPILED_CONTRACT_ID)
                                        .contractCallResult(htsPrecompileResult()
                                                .forFunction(ParsingConstants.FunctionType.HAPI_GET_TOKEN_TYPE)
                                                .withStatus(SUCCESS)
                                                .withTokenType(0)))));
    }

    private HapiSpec directHTSPrecompileEthereumCallWorksCallingGetTokenType() {
        final AtomicReference<Address> tokenAddress = new AtomicReference<>();
        return defaultHapiSpec("directHTSPrecompileEthereumCallWorksCallingGetTokenType")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .supplyKey(SECP_256K1_SOURCE_KEY)
                                .exposingAddressTo(tokenAddress::set))
                .when(sourcing(() -> directHTSPrecompileEthereumCall("getTokenType", tokenAddress.get())
                        .via(DIRECT_CALL_TXN)
                        .hasKnownStatus(SUCCESS)))
                .then(getTxnRecord(DIRECT_CALL_TXN)
                        .logged()
                        .hasPriority(TransactionRecordAsserts.recordWith()
                                .contractCallResult(ContractFnResultAsserts.resultWith()
                                        .contract(HTS_PRECOMPILED_CONTRACT_ID)
                                        .contractCallResult(htsPrecompileResult()
                                                .forFunction(ParsingConstants.FunctionType.HAPI_GET_TOKEN_TYPE)
                                                .withStatus(SUCCESS)
                                                .withTokenType(1)))));
    }

    private HapiSpec directExchangeRatePrecompileContractCallWorks() {
        final var directCallTxn2 = "directCallTxn2";
        return defaultHapiSpec("directExchangeRatePrecompileEthereumCallWorks")
                .given()
                .when(
                        directExchangeRatePrecompileContractCall(
                                        "tinycentsToTinybars", BigInteger.valueOf(1_000_000_000))
                                .via(DIRECT_CALL_TXN),
                        directExchangeRatePrecompileContractCall(
                                        "tinybarsToTinycents", BigInteger.valueOf(1_000_000_000))
                                .via(directCallTxn2))
                .then(
                        getTxnRecord(DIRECT_CALL_TXN)
                                .logged()
                                .hasPriority(TransactionRecordAsserts.recordWith()
                                        .contractCallResult(ContractFnResultAsserts.resultWith()
                                                .contract(EXHANGE_RATE_CONTRACT_ID))),
                        getTxnRecord(directCallTxn2)
                                .logged()
                                .hasPriority(TransactionRecordAsserts.recordWith()
                                        .contractCallResult(ContractFnResultAsserts.resultWith()
                                                .contract(EXHANGE_RATE_CONTRACT_ID))));
    }

    private HapiSpec directExchangeRatePrecompileEthereumCallWorks() {
        final var directCallTxn2 = "directCallTxn2";
        return defaultHapiSpec("directExchangeRatePrecompileEthereumCallWorks")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)))
                .when(
                        directExchangeRatePrecompileEthereumCall(
                                        "tinycentsToTinybars", BigInteger.valueOf(1_000_000_000))
                                .via(DIRECT_CALL_TXN),
                        directExchangeRatePrecompileEthereumCall(
                                        "tinybarsToTinycents", BigInteger.valueOf(1_000_000_000))
                                .via(directCallTxn2))
                .then(
                        getTxnRecord(DIRECT_CALL_TXN)
                                .logged()
                                .hasPriority(TransactionRecordAsserts.recordWith()
                                        .contractCallResult(ContractFnResultAsserts.resultWith()
                                                .contract(EXHANGE_RATE_CONTRACT_ID))),
                        getTxnRecord(directCallTxn2)
                                .logged()
                                .hasPriority(TransactionRecordAsserts.recordWith()
                                        .contractCallResult(ContractFnResultAsserts.resultWith()
                                                .contract(EXHANGE_RATE_CONTRACT_ID))));
    }

    private HapiSpec directPrngPrecompileContractCallWorks() {
        return defaultHapiSpec("directPrngPrecompileContractCallWorks")
                .given()
                .when(directPrngPrecompileContractCall("getPseudorandomSeed").via(DIRECT_CALL_TXN))
                .then(getTxnRecord(DIRECT_CALL_TXN)
                        .logged()
                        .hasPriority(TransactionRecordAsserts.recordWith()
                                .contractCallResult(
                                        ContractFnResultAsserts.resultWith().contract(PRNG_CONTRACT_ID))));
    }

    private HapiSpec directPrngPrecompileEthereumCallWorks() {
        return defaultHapiSpec("directPrngPrecompileEthereumCallWorks")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoTransfer(tinyBarsFromAccountToAlias(GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS)))
                .when(directPrngPrecompileEthereumCall("getPseudorandomSeed").via(DIRECT_CALL_TXN))
                .then(getTxnRecord(DIRECT_CALL_TXN)
                        .logged()
                        .hasPriority(TransactionRecordAsserts.recordWith()
                                .contractCallResult(
                                        ContractFnResultAsserts.resultWith().contract(PRNG_CONTRACT_ID))));
    }
}
