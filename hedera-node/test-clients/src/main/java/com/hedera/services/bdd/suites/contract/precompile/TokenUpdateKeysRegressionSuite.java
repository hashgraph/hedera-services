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

package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.precompile.FreezeUnfreezeTokenPrecompileV1SecurityModelSuite.TOKEN_FREEZE_FUNC;
import static com.hedera.services.bdd.suites.crypto.AutoAccountCreationSuite.LAZY_CREATE_SPONSOR;
import static com.hedera.services.bdd.suites.regression.factories.IdFuzzingProviderFactory.FUNGIBLE_TOKEN;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class TokenUpdateKeysRegressionSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(TokenUpdateKeysRegressionSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String ACCOUNT = "account";
    public static final String PAYER = "PAYER";
    private static final String CONTRACT_THRESHOLD_KEY = "ContractThresholdKey";
    private static final String FREEZE_CONTRACT = "FreezeUnfreezeContract";
    private static final String IS_TOKEN_FROZEN_FUNC = "isTokenFrozen";
    private static final String TX = "tx";

    public static void main(String... args) {
        new TokenUpdateKeysRegressionSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(regressionTest());
    }

    @HapiTest
    public HapiSpec regressionTest() {
        return defaultHapiSpec("regressionTest")
                .given(
                        // create accounts
                        cryptoCreate(LAZY_CREATE_SPONSOR).balance(ONE_MILLION_HBARS),
                        cryptoCreate(PAYER).balance(ONE_MILLION_HBARS),
                        cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(2),

                        // create contract
                        uploadInitCode(FREEZE_CONTRACT),
                        contractCreate(FREEZE_CONTRACT),

                        // create token
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .adminKey(LAZY_CREATE_SPONSOR)
                                .freezeKey(LAZY_CREATE_SPONSOR)
                                .initialSupply(100L)
                                .treasury(LAZY_CREATE_SPONSOR),

                        // transfer tokens to account
                        cryptoTransfer(moving(1L, FUNGIBLE_TOKEN).between(LAZY_CREATE_SPONSOR, ACCOUNT)))
                .when()
                .then(withOpContext((spec, opLog) -> {
                    allRunFor(
                            spec,
                            // create 1 of M threshold key
                            newKeyNamed(CONTRACT_THRESHOLD_KEY)
                                    .shape(KeyShape.threshOf(1, SECP_256K1_SHAPE, CONTRACT)
                                            .signedWith(sigs(ON, FREEZE_CONTRACT))),

                            // update token's freeze key to 1 of M threshold key
                            // PS: runs fine against mono release/0.46
                            // but the current codebase throws INVALID_SIGNATURE
                            tokenUpdate(FUNGIBLE_TOKEN).freezeKey(CONTRACT_THRESHOLD_KEY),

                            // try to freeze token for account
                            contractCall(
                                            FREEZE_CONTRACT,
                                            TOKEN_FREEZE_FUNC,
                                            asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                            asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID(ACCOUNT))))
                                    .signedBy(PAYER)
                                    .payingWith(PAYER)
                                    .gas(GAS_TO_OFFER)
                                    .via(TX),
                            getTxnRecord(TX).andAllChildRecords().logged(),

                            // check whether the account is frozen
                            contractCallLocal(
                                            FREEZE_CONTRACT,
                                            IS_TOKEN_FROZEN_FUNC,
                                            asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                            asHeadlongAddress(
                                                    asAddress(spec.registry().getAccountID(ACCOUNT))))
                                    .logged());
                }));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
