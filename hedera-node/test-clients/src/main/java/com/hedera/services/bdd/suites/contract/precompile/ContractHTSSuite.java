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

package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.getNestedContractAddress;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ContractHTSSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ContractHTSSuite.class);

    public static final String VERSATILE_TRANSFERS = "VersatileTransfers";
    public static final String FEE_DISTRIBUTOR = "FeeDistributor";

    private static final long GAS_TO_OFFER = 2_000_000L;
    private static final long TOTAL_SUPPLY = 1_000;
    private static final String TOKEN_TREASURY = "treasury";

    private static final String A_TOKEN = "TokenA";

    private static final String ACCOUNT = "sender";
    private static final String RECEIVER = "receiver";

    private static final String UNIVERSAL_KEY = "multipurpose";

    public static void main(String... args) {
        new ContractHTSSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<HapiSpec> negativeSpecs() {
        return List.of(nonZeroTransfersFail());
    }

    List<HapiSpec> positiveSpecs() {
        return List.of();
    }

    private HapiSpec nonZeroTransfersFail() {
        final var theSecondReceiver = "somebody2";
        return defaultHapiSpec("NonZeroTransfersFail")
                .given(
                        newKeyNamed(UNIVERSAL_KEY),
                        cryptoCreate(ACCOUNT).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER),
                        cryptoCreate(theSecondReceiver),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(A_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(TOTAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        uploadInitCode(VERSATILE_TRANSFERS, FEE_DISTRIBUTOR),
                        contractCreate(FEE_DISTRIBUTOR),
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCreate(
                                        VERSATILE_TRANSFERS,
                                        asHeadlongAddress(getNestedContractAddress(FEE_DISTRIBUTOR, spec))))),
                        tokenAssociate(ACCOUNT, List.of(A_TOKEN)),
                        tokenAssociate(VERSATILE_TRANSFERS, List.of(A_TOKEN)),
                        tokenAssociate(RECEIVER, List.of(A_TOKEN)),
                        tokenAssociate(theSecondReceiver, List.of(A_TOKEN)),
                        cryptoTransfer(moving(200, A_TOKEN).between(TOKEN_TREASURY, ACCOUNT)))
                .when(withOpContext((spec, opLog) -> {
                    final var receiver1 = asAddress(spec.registry().getAccountID(RECEIVER));
                    final var receiver2 = asAddress(spec.registry().getAccountID(theSecondReceiver));

                    final var accounts = new Address[] {
                        HapiParserUtil.asHeadlongAddress(receiver1), HapiParserUtil.asHeadlongAddress(receiver2)
                    };
                    final var amounts = new long[] {5L, 5L};

                    allRunFor(
                            spec,
                            contractCall(
                                            VERSATILE_TRANSFERS,
                                            "distributeTokens",
                                            HapiParserUtil.asHeadlongAddress(
                                                    asAddress(spec.registry().getTokenID(A_TOKEN))),
                                            accounts,
                                            amounts)
                                    .alsoSigningWithFullPrefix(ACCOUNT)
                                    .gas(GAS_TO_OFFER)
                                    .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                    .via("distributeTx"));
                }))
                .then(childRecordsCheck(
                        "distributeTx",
                        CONTRACT_REVERT_EXECUTED,
                        recordWith()
                                .status(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN)
                                .contractCallResult(resultWith()
                                        .contractCallResult(
                                                htsPrecompileResult().withStatus(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN)))));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
