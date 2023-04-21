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

package com.hedera.services.bdd.suites.leaky;

import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.precompile.WipeTokenAccountPrecompileSuite.GAS_TO_OFFER;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.ACCOUNT;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.MULTI_KEY;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.TRANSFER_TXN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenID;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LeakyContractTransferSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(LeakyContractTestsSuite.class);

    public static void main(String... args) {
        new LeakyContractTransferSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            validateTransferTokenAndTransferFrom(),
        });
    }

    private HapiSpec validateTransferTokenAndTransferFrom() {
        final var contractB = "TokenTransfer";
        final var contractC = "TokenTransfer2";

        final AtomicReference<TokenID> fungibleTokenID = new AtomicReference<>();

        return propertyPreservingHapiSpec("validateTransferTokenAndTransferFrom")
                .preserving("contracts.maxNumWithHapiSigsAccess", "contracts.allowSystemUseOfHapiSigs")
                .given(
                        // make sure that the tokens and contract are under the new security regime
                        overriding("contracts.maxNumWithHapiSigsAccess", "0"),
                        overriding("contracts.allowSystemUseOfHapiSigs", ""),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(10 * ONE_MILLION_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(MULTI_KEY)
                                .initialSupply(100)
                                .exposingCreatedIdTo(id -> fungibleTokenID.set(asToken(id))),
                        uploadInitCode(contractB),
                        contractCreate(contractB).balance(1000L),
                        uploadInitCode(contractC),
                        contractCreate(contractC),
                        tokenAssociate(contractB, VANILLA_TOKEN),
                        tokenAssociate(contractC, VANILLA_TOKEN),
                        cryptoTransfer(moving(10, VANILLA_TOKEN).between(TOKEN_TREASURY, contractB)))
                .when(
                        // call transferToken sending B's tokens to C. Should succeed.
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCall(
                                                contractB,
                                                "transferTokenPublic",
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getTokenID(VANILLA_TOKEN))),
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getContractId(contractB))),
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getContractId(contractC))),
                                                2L)
                                        .via(TRANSFER_TXN)
                                        .gas(GAS_TO_OFFER)
                                        .hasKnownStatus(SUCCESS))),
                        // call transferFrom sending B's tokens to C. Should fail because there is no approval.
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCall(
                                                contractB,
                                                "transferFromPublic",
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getTokenID(VANILLA_TOKEN))),
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getContractId(contractB))),
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getContractId(contractC))),
                                                BigInteger.TWO)
                                        .via(TRANSFER_TXN)
                                        .gas(GAS_TO_OFFER)
                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED))),
                        // call approve to give B approval to spend B's tokens.
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCall(
                                                contractB,
                                                "approvePublic",
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getTokenID(VANILLA_TOKEN))),
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getContractId(contractB))),
                                                BigInteger.TEN)
                                        .via(TRANSFER_TXN)
                                        .gas(GAS_TO_OFFER)
                                        .hasKnownStatus(SUCCESS))),
                        // call transferFrom sending B's tokens to C. Should succeed now that there is approval.
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCall(
                                                contractB,
                                                "transferFromPublic",
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getTokenID(VANILLA_TOKEN))),
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getContractId(contractB))),
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getContractId(contractC))),
                                                BigInteger.TWO)
                                        .via(TRANSFER_TXN)
                                        .gas(GAS_TO_OFFER)
                                        .hasKnownStatus(SUCCESS))),
                        // call transferFrom sending B's tokens to C from C's contract. Should fail.
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCall(
                                                contractC,
                                                "transferFromPublic",
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getTokenID(VANILLA_TOKEN))),
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getContractId(contractB))),
                                                HapiParserUtil.asHeadlongAddress(asAddress(
                                                        spec.registry().getContractId(contractC))),
                                                BigInteger.TWO)
                                        .via(TRANSFER_TXN)
                                        .gas(GAS_TO_OFFER)
                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
