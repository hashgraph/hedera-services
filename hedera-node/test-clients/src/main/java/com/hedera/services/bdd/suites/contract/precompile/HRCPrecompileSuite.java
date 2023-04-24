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

import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asHexedAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HRCPrecompileSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(HRCPrecompileSuite.class);
    private static final String MULTI_KEY = "multikey";
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String FUNGIBLE_TOKEN_2 = "fungibleToken2";
    private static final String FUNGIBLE_TOKEN_3 = "fungibleToken3";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    private static final String TOKEN_NAME = "TokenA";
    private static final String HRC_CONTRACT = "HRCContract";
    private static final String HRC = "HRC";
    private static final String ACCOUNT = "account";
    private static final String ASSOCIATE_TXN = "associateTxn";
    private static final String ASSOCIATE_TXN_2 = "associateTxn2";
    private static final String ASSOCIATE_TXN_3 = "associateTxn3";
    private static final String DISSOCIATE_TXN = "dissociateTxn";
    private static final String DISSOCIATE_TXN_2 = "dissociateTxn2";
    private static final String TOKEN_SYMBOL = "NFT";
    private static final String RANDOM_KEY = "randomKey";
    private static final String ASSOCIATE = "associate";
    private static final String DISSOCIATE = "dissociate";

    public static void main(String... args) {
        new HRCPrecompileSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                hrcNftAndFungibleTokenAssociateFromEOA(),
                hrcNFTAndFungibleTokenAssociateFromContract(),
                hrcTokenAssociateFromSameEOATwiceShouldFail(),
                hrcTokenDissociateWhenNotAssociatedShouldFail(),
                hrcTokenDissociateWhenBalanceNotZeroShouldFail(),
                hrcTooManyTokenAssociateShouldFail());
    }

    private HapiSpec hrcNftAndFungibleTokenAssociateFromEOA() {
        final AtomicReference<String> fungibleTokenNum = new AtomicReference<>();
        final AtomicReference<String> nonfungibleTokenNum = new AtomicReference<>();

        return defaultHapiSpec("hrcNftAndFungibleTokenAssociateFromEOA")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(5)
                                .name(TOKEN_NAME)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(fungibleTokenNum::set),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .name(TOKEN_NAME)
                                .symbol(TOKEN_SYMBOL)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(nonfungibleTokenNum::set),
                        uploadInitCode(HRC),
                        contractCreate(HRC))
                .when(withOpContext((spec, opLog) -> {
                    var fungibleTokenAddress = asHexedSolidityAddress(asToken(fungibleTokenNum.get()));
                    var nonfungibleTokenAddress = asHexedSolidityAddress(asToken(nonfungibleTokenNum.get()));
                    allRunFor(
                            spec,
                            // Associate fungible token
                            contractCallWithFunctionAbi(
                                            fungibleTokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    ASSOCIATE,
                                                    HRC))
                                    .payingWith(ACCOUNT)
                                    .gas(1_000_000)
                                    .via(ASSOCIATE_TXN),
                            // Associate non-fungible token
                            contractCallWithFunctionAbi(
                                            nonfungibleTokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    ASSOCIATE,
                                                    HRC))
                                    .payingWith(ACCOUNT)
                                    .gas(1_000_000)
                                    .via(ASSOCIATE_TXN_2),
                            // Dissociate fungible token
                            contractCallWithFunctionAbi(
                                            fungibleTokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    DISSOCIATE,
                                                    HRC))
                                    .payingWith(ACCOUNT)
                                    .gas(1_000_000)
                                    .via(DISSOCIATE_TXN),
                            // Dissociate non-fungible token
                            contractCallWithFunctionAbi(
                                            nonfungibleTokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    DISSOCIATE,
                                                    HRC))
                                    .payingWith(ACCOUNT)
                                    .gas(1_000_000)
                                    .via(DISSOCIATE_TXN_2));
                }))
                .then(withOpContext((spec, ignore) -> allRunFor(
                        spec,
                        childRecordsCheck(
                                ASSOCIATE_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        childRecordsCheck(
                                ASSOCIATE_TXN_2,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        childRecordsCheck(
                                DISSOCIATE_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        childRecordsCheck(
                                DISSOCIATE_TXN_2,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))))));
    }

    private HapiSpec hrcNFTAndFungibleTokenAssociateFromContract() {
        return defaultHapiSpec("hrcNFTAndFungibleTokenAssociateFromContract")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(5)
                                .name(TOKEN_NAME)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY),
                        uploadInitCode(HRC_CONTRACT),
                        contractCreate(HRC_CONTRACT),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .name(TOKEN_NAME)
                                .symbol(TOKEN_SYMBOL)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        // Associate fungible token
                        contractCall(
                                        HRC_CONTRACT,
                                        ASSOCIATE,
                                        asHeadlongAddress(
                                                asHexedAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))))
                                .payingWith(ACCOUNT)
                                .via(ASSOCIATE_TXN)
                                .gas(4_000_000)
                                .hasKnownStatus(SUCCESS)
                                .logged(),
                        // Associate non-fungible token
                        contractCall(
                                        HRC_CONTRACT,
                                        ASSOCIATE,
                                        asHeadlongAddress(
                                                asHexedAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))))
                                .payingWith(ACCOUNT)
                                .via(ASSOCIATE_TXN_2)
                                .gas(4_000_000)
                                .hasKnownStatus(SUCCESS)
                                .logged(),
                        // Dissociate fungible token
                        contractCall(
                                        HRC_CONTRACT,
                                        DISSOCIATE,
                                        asHeadlongAddress(
                                                asHexedAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))))
                                .payingWith(ACCOUNT)
                                .via(DISSOCIATE_TXN)
                                .gas(4_000_000)
                                .hasKnownStatus(SUCCESS)
                                .logged(),
                        // Dissociate non-fungible token
                        contractCall(
                                        HRC_CONTRACT,
                                        DISSOCIATE,
                                        asHeadlongAddress(
                                                asHexedAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))))
                                .payingWith(ACCOUNT)
                                .via(DISSOCIATE_TXN_2)
                                .gas(4_000_000)
                                .hasKnownStatus(SUCCESS)
                                .logged())))
                .then(withOpContext((spec, ignore) -> allRunFor(
                        spec,
                        childRecordsCheck(
                                ASSOCIATE_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        childRecordsCheck(
                                ASSOCIATE_TXN_2,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        childRecordsCheck(
                                DISSOCIATE_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        childRecordsCheck(
                                DISSOCIATE_TXN_2,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))))));
    }

    private HapiSpec hrcTokenAssociateFromSameEOATwiceShouldFail() {
        final AtomicReference<String> fungibleTokenNum = new AtomicReference<>();

        return defaultHapiSpec("hrcTokenAssociateFromSameEOATwiceShouldFail")
                .given(
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(RANDOM_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(5)
                                .name(TOKEN_NAME)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(fungibleTokenNum::set),
                        uploadInitCode(HRC),
                        contractCreate(HRC))
                .when(withOpContext((spec, opLog) -> {
                    var fungibleTokenAddress = asHexedSolidityAddress(asToken(fungibleTokenNum.get()));
                    allRunFor(
                            spec,
                            // Associate fungible token
                            contractCallWithFunctionAbi(
                                            fungibleTokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    ASSOCIATE,
                                                    HRC))
                                    .payingWith(ACCOUNT)
                                    .gas(1_000_000)
                                    .via(ASSOCIATE_TXN),
                            // Associate fungible token a second time (should fail)
                            contractCallWithFunctionAbi(
                                            fungibleTokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    ASSOCIATE,
                                                    HRC))
                                    .payingWith(ACCOUNT)
                                    .gas(1_000_000)
                                    .via(ASSOCIATE_TXN_2));
                }))
                .then(withOpContext((spec, ignore) -> allRunFor(
                        spec,
                        childRecordsCheck(
                                ASSOCIATE_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        childRecordsCheck(
                                ASSOCIATE_TXN_2,
                                SUCCESS,
                                recordWith()
                                        .status(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .withStatus(TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)))))));
    }

    private HapiSpec hrcTokenDissociateWhenNotAssociatedShouldFail() {
        final AtomicReference<String> fungibleTokenNum = new AtomicReference<>();

        return defaultHapiSpec("hrcTokenDissociateWhenNotAssociatedShouldFail")
                .given(
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(RANDOM_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(5)
                                .name(TOKEN_NAME)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(fungibleTokenNum::set),
                        uploadInitCode(HRC),
                        contractCreate(HRC))
                .when(withOpContext((spec, opLog) -> {
                    var fungibleTokenAddress = asHexedSolidityAddress(asToken(fungibleTokenNum.get()));
                    allRunFor(
                            spec,
                            // Dissociate fungible token with association (should fail)
                            contractCallWithFunctionAbi(
                                            fungibleTokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    DISSOCIATE,
                                                    HRC))
                                    .payingWith(ACCOUNT)
                                    .gas(1_000_000)
                                    .via(ASSOCIATE_TXN));
                }))
                .then(withOpContext((spec, ignore) -> allRunFor(
                        spec,
                        childRecordsCheck(
                                ASSOCIATE_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .withStatus(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)))))));
    }

    private HapiSpec hrcTokenDissociateWhenBalanceNotZeroShouldFail() {
        final AtomicReference<String> fungibleTokenNum = new AtomicReference<>();

        return defaultHapiSpec("hrcTokenDissociateWhenBalanceNotZeroShouldFail")
                .given(
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(RANDOM_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(5)
                                .name(TOKEN_NAME)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(fungibleTokenNum::set),
                        uploadInitCode(HRC),
                        contractCreate(HRC))
                .when(withOpContext((spec, opLog) -> {
                    var fungibleTokenAddress = asHexedSolidityAddress(asToken(fungibleTokenNum.get()));
                    allRunFor(
                            spec,
                            // Associate fungible token
                            contractCallWithFunctionAbi(
                                            fungibleTokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    ASSOCIATE,
                                                    HRC))
                                    .payingWith(ACCOUNT)
                                    .gas(1_000_000)
                                    .via(ASSOCIATE_TXN),
                            // transfer fungible token
                            cryptoTransfer(moving(3, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
                            // Dissociate fungible token with association (should fail)
                            contractCallWithFunctionAbi(
                                            fungibleTokenAddress,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    DISSOCIATE,
                                                    HRC))
                                    .payingWith(ACCOUNT)
                                    .gas(1_000_000)
                                    .via(ASSOCIATE_TXN_2));
                }))
                .then(withOpContext((spec, ignore) -> allRunFor(
                        spec,
                        childRecordsCheck(
                                ASSOCIATE_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(
                                                        htsPrecompileResult().withStatus(SUCCESS)))),
                        childRecordsCheck(
                                ASSOCIATE_TXN_2,
                                SUCCESS,
                                recordWith()
                                        .status(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)
                                        .contractCallResult(resultWith()
                                                .contractCallResult(htsPrecompileResult()
                                                        .withStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES)))))));
    }

    private HapiSpec hrcTooManyTokenAssociateShouldFail() {
        final AtomicReference<String> fungibleTokenNum1 = new AtomicReference<>();
        final AtomicReference<String> fungibleTokenNum2 = new AtomicReference<>();
        final AtomicReference<String> fungibleTokenNum3 = new AtomicReference<>();

        return defaultHapiSpec("hrcTooManyTokenAssociateShouldFail")
                .given(
                        overriding("tokens.maxPerAccount", "2"),
                        overriding("entities.limitTokenAssociations", "true"),
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(RANDOM_KEY),
                        cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(1)
                                .name(TOKEN_NAME)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(fungibleTokenNum1::set),
                        tokenCreate(FUNGIBLE_TOKEN_2)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(1)
                                .name(TOKEN_NAME)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(fungibleTokenNum2::set),
                        tokenCreate(FUNGIBLE_TOKEN_3)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .name(TOKEN_NAME)
                                .treasury(ACCOUNT)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(fungibleTokenNum3::set),
                        uploadInitCode(HRC),
                        contractCreate(HRC))
                .when(withOpContext((spec, opLog) -> {
                    var fungibleTokenAddress1 = asHexedSolidityAddress(asToken(fungibleTokenNum1.get()));
                    var fungibleTokenAddress2 = asHexedSolidityAddress(asToken(fungibleTokenNum2.get()));
                    var fungibleTokenAddress3 = asHexedSolidityAddress(asToken(fungibleTokenNum3.get()));
                    allRunFor(
                            spec,
                            // Associate fungible token
                            contractCallWithFunctionAbi(
                                            fungibleTokenAddress1,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    ASSOCIATE,
                                                    HRC))
                                    .payingWith(ACCOUNT)
                                    .gas(1_000_000)
                                    .via(ASSOCIATE_TXN),
                            contractCallWithFunctionAbi(
                                            fungibleTokenAddress2,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    ASSOCIATE,
                                                    HRC))
                                    .payingWith(ACCOUNT)
                                    .gas(1_000_000)
                                    .via(ASSOCIATE_TXN_2),
                            contractCallWithFunctionAbi(
                                            fungibleTokenAddress3,
                                            getABIFor(
                                                    com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION,
                                                    ASSOCIATE,
                                                    HRC))
                                    .payingWith(ACCOUNT)
                                    .gas(1_000_000)
                                    .via(ASSOCIATE_TXN_3));
                }))
                .then(
                        overriding("tokens.maxPerAccount", "1000"),
                        overriding("entities.limitTokenAssociations", "false"),
                        withOpContext((spec, ignore) -> allRunFor(
                                spec,
                                childRecordsCheck(
                                        ASSOCIATE_TXN,
                                        SUCCESS,
                                        recordWith()
                                                .status(SUCCESS)
                                                .contractCallResult(resultWith()
                                                        .contractCallResult(htsPrecompileResult()
                                                                .withStatus(SUCCESS)))),
                                childRecordsCheck(
                                        ASSOCIATE_TXN_2,
                                        SUCCESS,
                                        recordWith()
                                                .status(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED)
                                                .contractCallResult(resultWith()
                                                        .contractCallResult(htsPrecompileResult()
                                                                .withStatus(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED)))),
                                childRecordsCheck(
                                        ASSOCIATE_TXN_3,
                                        SUCCESS,
                                        recordWith()
                                                .status(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED)
                                                .contractCallResult(resultWith()
                                                        .contractCallResult(htsPrecompileResult()
                                                                .withStatus(TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED)))))));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
