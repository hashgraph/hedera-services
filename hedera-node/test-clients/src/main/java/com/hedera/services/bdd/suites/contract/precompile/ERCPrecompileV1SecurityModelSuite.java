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

package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.spec.HapiPropertySource.asContractString;
import static com.hedera.services.bdd.spec.HapiPropertySource.contractIdFromHexedMirrorAddress;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asHexedAddress;
import static com.hedera.services.bdd.suites.contract.Utils.captureChildCreate2MetaFor;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class ERCPrecompileV1SecurityModelSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(ERCPrecompileV1SecurityModelSuite.class);

    private static final long GAS_TO_OFFER = 1_000_000L;
    private static final String MULTI_KEY = "purpose";
    private static final String OWNER = "owner";
    private static final String ACCOUNT = "anybody";
    private static final String TOKEN_NAME = "TokenA";
    public static final String TRANSFER = "transfer";

    public static void main(String... args) {
        new ERCPrecompileV1SecurityModelSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return allOf(erc20(), erc721());
    }

    List<Stream<DynamicTest>> erc20() {
        return List.of(transferErc20TokenAliasedSender());
    }

    List<Stream<DynamicTest>> erc721() {
        return List.of();
    }

    final Stream<DynamicTest> transferErc20TokenAliasedSender() {
        final var aliasedTransferTxn = "aliasedTransferTxn";
        final var addLiquidityTxn = "addLiquidityTxn";
        final var create2Txn = "create2Txn";

        final var account_A = "AccountA";
        final var account_B = "AccountB";

        final var aliasedTransfer = "AliasedTransfer";
        final byte[][] aliasedAddress = new byte[1][1];

        final AtomicReference<String> childMirror = new AtomicReference<>();
        final AtomicReference<String> childEip1014 = new AtomicReference<>();

        return propertyPreservingHapiSpec("transferErc20TokenAliasedSender")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenAssociateToAccount,TokenCreate",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(OWNER),
                        cryptoCreate(ACCOUNT),
                        cryptoCreate(account_A).key(MULTI_KEY).balance(ONE_MILLION_HBARS),
                        cryptoCreate(account_B).balance(ONE_MILLION_HBARS),
                        tokenCreate(TOKEN_NAME)
                                .adminKey(MULTI_KEY)
                                .initialSupply(10000)
                                .treasury(account_A),
                        tokenAssociate(account_B, TOKEN_NAME),
                        uploadInitCode(aliasedTransfer),
                        contractCreate(aliasedTransfer).gas(300_000),
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCall(
                                                aliasedTransfer,
                                                "deployWithCREATE2",
                                                asHeadlongAddress(asHexedAddress(
                                                        spec.registry().getTokenID(TOKEN_NAME))))
                                        .exposingResultTo(result -> {
                                            final var res = (Address) result[0];
                                            aliasedAddress[0] = res.value().toByteArray();
                                        })
                                        .payingWith(ACCOUNT)
                                        .alsoSigningWithFullPrefix(MULTI_KEY)
                                        .via(create2Txn)
                                        .gas(GAS_TO_OFFER)
                                        .hasKnownStatus(SUCCESS))))
                .when(
                        captureChildCreate2MetaFor(2, 0, "setup", create2Txn, childMirror, childEip1014),
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCall(
                                                aliasedTransfer,
                                                "giveTokensToOperator",
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getTokenID(TOKEN_NAME))),
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getAccountID(account_A))),
                                                1500L)
                                        .payingWith(ACCOUNT)
                                        .alsoSigningWithFullPrefix(MULTI_KEY)
                                        .via(addLiquidityTxn)
                                        .gas(GAS_TO_OFFER)
                                        .hasKnownStatus(SUCCESS))),
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCall(
                                                aliasedTransfer,
                                                TRANSFER,
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getAccountID(account_B))),
                                                BigInteger.valueOf(1000))
                                        .payingWith(ACCOUNT)
                                        .alsoSigningWithFullPrefix(MULTI_KEY)
                                        .via(aliasedTransferTxn)
                                        .gas(GAS_TO_OFFER)
                                        .hasKnownStatus(SUCCESS))))
                .then(
                        sourcing(() -> getContractInfo(
                                        asContractString(contractIdFromHexedMirrorAddress(childMirror.get())))
                                .hasToken(ExpectedTokenRel.relationshipWith(TOKEN_NAME)
                                        .balance(500))
                                .logged()),
                        getAccountBalance(account_B).hasTokenBalance(TOKEN_NAME, 1000),
                        getAccountBalance(account_A).hasTokenBalance(TOKEN_NAME, 8500));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
