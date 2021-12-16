package com.hedera.services.bdd.suites.contract.precompile;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.legacy.core.CommonUtils;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.BURN_AFTER_NESTED_MINT_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.BURN_TOKEN_ABI;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractByteCode;

public class ContractBurnHTSSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(ContractBurnHTSSuite.class);

    public static void main(String... args) {
        new ContractBurnHTSSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunAsync() {
        return true;
    }

    @Override
    protected List<HapiApiSpec> getSpecsInSuite() {
        return allOf(
                positiveSpecs(),
                negativeSpecs()
        );
    }

    List<HapiApiSpec> negativeSpecs() {
        return List.of();
    }

    List<HapiApiSpec> positiveSpecs() {
        return List.of(
                HSCS_PREC_004_token_burn_of_fungible_token_units(),
                HSCS_PREC_005_token_burn_of_NFT(),
                HSCS_PREC_011_burn_after_nested_mint()
        );
    }

    private HapiApiSpec HSCS_PREC_004_token_burn_of_fungible_token_units() {
        final var theAccount = "anybody";
        final var token = "Token";
        final var treasuryForToken = "TokenTreasury";
        final var theContract = "burn token";
        final var multiKey = "purpose";

        return defaultHapiSpec("HSCS_PREC_004_token_burn_of_fungible_token_units")
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(treasuryForToken),
                        tokenCreate(token)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(50L)
                                .supplyKey(multiKey)
                                .treasury(treasuryForToken),
                        fileCreate("bytecode").payingWith(theAccount),
                        updateLargeFile(theAccount, "bytecode", extractByteCode(ContractResources.BURN_TOKEN)),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(theContract, ContractResources.BURN_TOKEN_CONSTRUCTOR_ABI,
                                                        asAddress(spec.registry().getTokenID(token)))
                                                        .payingWith(theAccount)
                                                        .bytecode("bytecode")
                                                        .via("creationTx")
                                                        .gas(28_000))),
                        getTxnRecord("creationTx").logged(),
                        tokenAssociate(theAccount, token),
                        tokenAssociate(theContract, token)
                )
                .when(
                        contractCall(theContract, BURN_TOKEN_ABI, 1, new ArrayList<Long>())
                                .payingWith(theAccount)
                                .alsoSigningWithFullPrefix(multiKey)
                                .gas(48_000)
                                .via("burn"),

                        getTxnRecord("burn").andAllChildRecords().logged()

                )
                .then(
                        getAccountBalance(treasuryForToken).hasTokenBalance(token, 49)

                );
    }

    private HapiApiSpec HSCS_PREC_005_token_burn_of_NFT() {
        final var theAccount = "anybody";
        final var token = "Token";
        final var treasuryForToken = "TokenTreasury";
        final var theContract = "burn token";
        final var multiKey = "purpose";

        return defaultHapiSpec("HSCS_PREC_005_token_burn_of_NFT")
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(treasuryForToken),
                        tokenCreate(token)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0L)
                                .supplyKey(multiKey)
                                .treasury(treasuryForToken),
                        mintToken(token, List.of(copyFromUtf8("First!"))),
                        mintToken(token, List.of(copyFromUtf8("Second!"))),
                        fileCreate("bytecode").payingWith(theAccount),
                        updateLargeFile(theAccount, "bytecode", extractByteCode(ContractResources.BURN_TOKEN)),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(theContract, ContractResources.BURN_TOKEN_CONSTRUCTOR_ABI,
                                                        asAddress(spec.registry().getTokenID(token)))
                                                        .payingWith(theAccount)
                                                        .bytecode("bytecode")
                                                        .via("creationTx")
                                                        .gas(28_000))),
                        getTxnRecord("creationTx").logged(),
                        tokenAssociate(theAccount, token),
                        tokenAssociate(theContract, token)
                )
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    var serialNumbers = new ArrayList<>();
                                    serialNumbers.add(1L);
                                    allRunFor(
                                            spec,
                                            contractCall(theContract, BURN_TOKEN_ABI, 0, serialNumbers)
                                                    .payingWith(theAccount)
                                                    .alsoSigningWithFullPrefix(multiKey)
                                                    .gas(48_000)
                                                    .via("burn"));
                                }
                                ),

                        getTxnRecord("burn").andAllChildRecords().logged()

                )
                .then(
                        getAccountBalance(treasuryForToken).hasTokenBalance(token, 1)

                );
    }

    private HapiApiSpec HSCS_PREC_011_burn_after_nested_mint() {
        final var theAccount = "anybody";
        final var token = "Token";
        final var treasuryForToken = "TokenTreasury";
        final var outerContract = "BurnTokenContract";
        final var nestedContract = "NestedBurnContract";
        final var multiKey = "purpose";

        return defaultHapiSpec("HSCS_PREC_011_burn_after_nested_mint")
                .given(
                        newKeyNamed(multiKey),
                        cryptoCreate(theAccount).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(treasuryForToken),
                        tokenCreate(token)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(50L)
                                .supplyKey(multiKey)
                                .treasury(treasuryForToken),
                        fileCreate(outerContract).path(ContractResources.BURN_TOKEN_CONTRACT),
                        fileCreate(nestedContract).path(ContractResources.NESTED_BURN),
                        contractCreate(outerContract)
                                .bytecode(outerContract)
                                .gas(100_000),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(nestedContract, ContractResources.NESTED_BURN_CONSTRUCTOR_ABI,
                                                        getNestedContractAddress(outerContract, spec))
                                                        .payingWith(theAccount)
                                                        .bytecode(nestedContract)
                                                        .via("creationTx")
                                                        .gas(28_000))),
                        getTxnRecord("creationTx").logged(),
                        tokenAssociate(nestedContract, token),
                        tokenAssociate(outerContract, token)

                )
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(nestedContract, BURN_AFTER_NESTED_MINT_ABI,
                                                        1, asAddress(spec.registry().getTokenID(token)), new ArrayList<>())
                                                        .payingWith(theAccount)
                                                        .via("burnAfterNestedMint")
                                                        .alsoSigningWithFullPrefix(multiKey))),
                        getTxnRecord("burnAfterNestedMint").andAllChildRecords().logged()

                )
                .then(
                        getAccountBalance(treasuryForToken).hasTokenBalance(token, 50)
                );
    }

    @NotNull
    private String getNestedContractAddress(String outerContract, HapiApiSpec spec) {
        return CommonUtils.calculateSolidityAddress(
                (int) spec.registry().getContractId(outerContract).getShardNum(),
                spec.registry().getContractId(outerContract).getRealmNum(),
                spec.registry().getContractId(outerContract).getContractNum());
    }


    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
