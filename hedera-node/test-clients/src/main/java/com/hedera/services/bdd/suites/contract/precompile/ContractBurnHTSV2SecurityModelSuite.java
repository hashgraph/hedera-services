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

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.*;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V2_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

@HapiTestSuite
@SuppressWarnings("java:S1192") // "string literal should not be duplicated" - this rule makes test suites worse
public class ContractBurnHTSV2SecurityModelSuite extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(ContractMintHTSV1SecurityModelSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final String TOKEN_TREASURY = "treasury";
    private static final KeyShape TRESHOLD_KEY_SHAPE =
            KeyShape.threshOf(1, ED25519, CONTRACT);
    private static final String CONTRACT_KEY = "ContractKey";
    public static final String MINT_CONTRACT = "MintContract";
    private static final String HTS_CALLS = "HTSCalls";
    private static final String BURN_CONTRACT = "BurnToken";
    public static final String TRESHOLD_KEY_CORRECT_CONTRACT_ID = "tresholdKeyWithCorrectContractAndIncorrectSignerPublicKey";
    public static final String TRESHOLD_KEY_WITH_SIGNER_KEY = "tresholdKeyWithIncorrectContractAndCorrectSignerPublicKey";
    public static final String THRESHOLD_KEY = "Tresh1WithRandomEdKeyAndCorrectContractID";
    private static final String MULTI_KEY = "purpose";
    private static final String SIGNER = "anybody";
    private static final String SIGNER2 = "anybody";
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS = "signerAndTokenHaveNoUpdatedKeys";
    private static final String TOKEN_HAS_NO_UPDATED_KEY = "tokenHasUpdatedContractKey";
    private static final String SIGNER_MINTS_WITH_CONTRACT_ID = "signerMintsAndTokenSupplyKeyHasTheIntermediaryContractId";
    private static final String TOKEN_WITH_CONTRACT_KEY = "tokenHasKeyWithTypeContract";
    private static final String SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY = "signerMintsAndTokenSupplyKeyHasTheSignerPublicKey";
    private static final String TREASURY_MINTS = "treasuryIsSignerWithUpdatedKeys";
    private static final String BURN1 = "burn1";
    private static final String SIGNER_AND_PAYER_ARE_DIFFERENT = "signerAndPayerAreDifferentAccounts";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    private static final String TEST_METADATA_1 = "Test metadata 1";
    private static final String BURN_TOKEN = "BurnToken";

    private static final String FIRST = "First!";
    private static final String SECOND = "Second!";
    private static final String THIRD = "Third!";
    private static final String FOURTH = "Fourth!";


    public static void main(final String... args) {
        new ContractBurnHTSV2SecurityModelSuite().runSuiteSync();
    }


    public List<HapiSpec> getSpecsInSuite() {
        return allOf(positiveSpecs())/*, negativeSpecs())*/;
    }

//    List<HapiSpec> negativeSpecs() {
//        return List.of(
//                V2Security002FungibleTokenMintInTreasuryNegative(),
//                V2Security003NonFungibleTokenMintInTreasuryNegative()
//        );
//    }

    List<HapiSpec> positiveSpecs() {
        return List.of(
                V2Security004FungibleTokenBurnPositive(),
                V2Security005NonFungibleTokenBurnPositive()
         );
    }

    List<HapiSpec> negativeSpecs() {
        return List.of(
                V2Security004FungibleTokenBurnNegative()
        );
    }


    @HapiTest
    final HapiSpec V2Security004FungibleTokenBurnPositive() {
        final var initialAmount = 20L;
        final var amountToBurn = 5L;
        final AtomicReference<TokenID> fungible = new AtomicReference<>();
//        final AtomicReference<TokenID> nonFungible = new AtomicReference<>();

        return propertyPreservingHapiSpec("V2Security004FungibleTokenBurnPositive")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V2_SECURITY_MODEL_BLOCK_CUTOFF),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(SIGNER2),
                        cryptoCreate(SIGNER).balance(ONE_MILLION_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(initialAmount)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY)
                                .exposingCreatedIdTo(idLit -> fungible.set(asToken(idLit))),
                        uploadInitCode(BURN_TOKEN)
                       )
                .when(
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCreate(
                                        BURN_TOKEN,
                                        HapiParserUtil.asHeadlongAddress(asAddress(
                                                spec.registry().getTokenID(FUNGIBLE_TOKEN)))),
                                newKeyNamed(CONTRACT_KEY).shape(CONTRACT.signedWith(BURN_TOKEN)),
                                tokenUpdate(FUNGIBLE_TOKEN).supplyKey(CONTRACT_KEY),
                                contractCall(
                                        BURN_TOKEN,
                                        "burnToken",
                                        BigInteger.valueOf(amountToBurn),
                                        new long[0])
                                        .via(SIGNER_MINTS_WITH_CONTRACT_ID)
                                        .gas(GAS_TO_OFFER)
                                        .payingWith(TOKEN_TREASURY)
                                        .signedBy(TOKEN_TREASURY),
                                getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount - amountToBurn), //Expectation
                                //Signer burns
                                newKeyNamed(TRESHOLD_KEY_CORRECT_CONTRACT_ID)
                                        .shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, BURN_TOKEN))), //Create new key and update token and contract with it
                                tokenUpdate(FUNGIBLE_TOKEN).supplyKey(TRESHOLD_KEY_CORRECT_CONTRACT_ID),
                                cryptoUpdate(SIGNER).key(TRESHOLD_KEY_CORRECT_CONTRACT_ID),
                                contractCall(
                                        BURN_TOKEN,
                                        "burnToken",
                                        BigInteger.valueOf(amountToBurn),
                                        new long[0])
                                        .via(SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY)
                                        .gas(GAS_TO_OFFER)
                                        .payingWith(SIGNER)
                                        .signedBy(SIGNER),
                                getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount - 2*amountToBurn),
                                contractCall(
                                        BURN_TOKEN,
                                        "burnToken",
                                        BigInteger.valueOf(amountToBurn),
                                        new long[0])
                                        .via(SIGNER_AND_PAYER_ARE_DIFFERENT)
                                        .gas(GAS_TO_OFFER)
                                        .payingWith(SIGNER2)
                                        .signedBy(SIGNER2, SIGNER),
                                getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount - 3*amountToBurn)//Expectation
                        )))
                .then(
                        getTxnRecord(SIGNER_MINTS_WITH_CONTRACT_ID)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS)),
                        getTxnRecord(SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS)),
                        getTxnRecord(SIGNER_AND_PAYER_ARE_DIFFERENT)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS))
        );
    }

    @HapiTest
    final HapiSpec V2Security005NonFungibleTokenBurnPositive() {
        final var initialAmount =1L;
        final var amountToBurn = 1L;
        final AtomicReference<TokenID> nonFungible = new AtomicReference<>();
        final var serialNumber1 = new long[] {1L};
        final var serialNumber2 = new long[] {2L};
        final var serialNumber3 = new long[] {3L};
//        final AtomicReference<TokenID> nonFungible = new AtomicReference<>();

        return propertyPreservingHapiSpec("V2Security005NonFungibleTokenBurnPositive")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V2_SECURITY_MODEL_BLOCK_CUTOFF),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(SIGNER2),
                        cryptoCreate(SIGNER).balance(ONE_MILLION_HBARS),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY)
                                .exposingCreatedIdTo(idLit -> nonFungible.set(asToken(idLit))),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8(FIRST))),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8(SECOND))),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8(THIRD))),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8(FOURTH))),
                        uploadInitCode(BURN_TOKEN)
                )
                .when(
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCreate(
                                        BURN_TOKEN,
                                        HapiParserUtil.asHeadlongAddress(asAddress(
                                                spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)))),
                                newKeyNamed(CONTRACT_KEY).shape(CONTRACT.signedWith(BURN_TOKEN)),
                                tokenUpdate(NON_FUNGIBLE_TOKEN).supplyKey(CONTRACT_KEY),
                                //Treasury burns
                                contractCall(
                                        BURN_TOKEN,
                                        "burnToken",
                                        BigInteger.valueOf(0),
                                        serialNumber1)
                                        .via(SIGNER_MINTS_WITH_CONTRACT_ID)
                                        .gas(GAS_TO_OFFER)
                                        .payingWith(TOKEN_TREASURY)
                                        .signedBy(TOKEN_TREASURY),
                                getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(4 - amountToBurn), //Expectation
                                //Signer burns
                                newKeyNamed(TRESHOLD_KEY_CORRECT_CONTRACT_ID)
                                        .shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, BURN_TOKEN))), //Create new key and update token and contract with it
                                tokenUpdate(NON_FUNGIBLE_TOKEN).supplyKey(TRESHOLD_KEY_CORRECT_CONTRACT_ID),
                                cryptoUpdate(SIGNER).key(TRESHOLD_KEY_CORRECT_CONTRACT_ID),
                                contractCall(
                                        BURN_TOKEN,
                                        "burnToken",
                                        BigInteger.valueOf(0),
                                        serialNumber2)
                                        .via(SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY)
                                        .gas(GAS_TO_OFFER)
                                        .payingWith(SIGNER)
                                        .signedBy(SIGNER),
                                getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(3 - amountToBurn),
                                contractCall(
                                        BURN_TOKEN,
                                        "burnToken",
                                        BigInteger.valueOf(0),
                                        serialNumber3)
                                        .via(SIGNER_AND_PAYER_ARE_DIFFERENT)
                                        .gas(GAS_TO_OFFER)
                                        .payingWith(SIGNER2)
                                        .signedBy(SIGNER2, SIGNER),
                                getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(2 - amountToBurn)//Expectation
                        )))
                .then(
                        getTxnRecord(SIGNER_MINTS_WITH_CONTRACT_ID)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS)),
                        getTxnRecord(SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS)),
                        getTxnRecord(SIGNER_AND_PAYER_ARE_DIFFERENT)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS))
                );
    }

    @HapiTest
    final HapiSpec V2Security004FungibleTokenBurnNegative() {
        final var initialAmount = 20L;
        final var amountToBurn = 5L;
        final AtomicReference<TokenID> fungible = new AtomicReference<>();
//        final AtomicReference<TokenID> nonFungible = new AtomicReference<>();

        return propertyPreservingHapiSpec("V2Security004FungibleTokenBurnNegative")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V2_SECURITY_MODEL_BLOCK_CUTOFF),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(SIGNER2),
                        /*Create Signer account with balance of one milion hbars */
                        cryptoCreate(SIGNER).balance(ONE_MILLION_HBARS),
                        /*Create fungible token on a treasury account*/
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(initialAmount)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY)
                                .exposingCreatedIdTo(idLit -> fungible.set(asToken(idLit))),
                        uploadInitCode(BURN_TOKEN)
                )
                .when(
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                contractCreate(
                                        BURN_TOKEN,
                                        HapiParserUtil.asHeadlongAddress(asAddress(
                                                spec.registry().getTokenID(FUNGIBLE_TOKEN)))),
                               /* Creating new keys of different types and shapes */
                                newKeyNamed(MULTI_KEY),
                                newKeyNamed(THRESHOLD_KEY)
                                        .shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, BURN_TOKEN))),
                                newKeyNamed(TRESHOLD_KEY_WITH_SIGNER_KEY)
                                        .shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, BURN_TOKEN))),
                                /* Performing a contract call with given params */
                                contractCall(
                                        BURN_TOKEN,
                                        "burnToken",
                                        BigInteger.valueOf(amountToBurn),
                                        new long[0])
                                        .via(SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS)
                                        .gas(GAS_TO_OFFER)
                                        .payingWith(SIGNER)
                                        .signedBy(SIGNER),
                                getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount - amountToBurn) //Expectation
                                //Signer burns
//                                newKeyNamed(TRESHOLD_KEY_CORRECT_CONTRACT_ID)
//                                        .shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, BURN_TOKEN))), //Create new key and update token and contract with it
//                                tokenUpdate(FUNGIBLE_TOKEN).supplyKey(TRESHOLD_KEY_CORRECT_CONTRACT_ID),
//                                cryptoUpdate(SIGNER).key(TRESHOLD_KEY_CORRECT_CONTRACT_ID),
//                                contractCall(
//                                        BURN_TOKEN,
//                                        "burnToken",
//                                        BigInteger.valueOf(amountToBurn),
//                                        new long[0])
//                                        .via(SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY)
//                                        .gas(GAS_TO_OFFER)
//                                        .payingWith(SIGNER)
//                                        .signedBy(SIGNER),
//                                getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount - 2*amountToBurn),
//                                contractCall(
//                                        BURN_TOKEN,
//                                        "burnToken",
//                                        BigInteger.valueOf(amountToBurn),
//                                        new long[0])
//                                        .via(SIGNER_AND_PAYER_ARE_DIFFERENT)
//                                        .gas(GAS_TO_OFFER)
//                                        .payingWith(SIGNER2)
//                                        .signedBy(SIGNER2, SIGNER),
//                                getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount - 3*amountToBurn)//Expectation
                        )))
                .then(
                        getTxnRecord(SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE))
//                        getTxnRecord(SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY)
//                                .andAllChildRecords()
//                                .hasChildRecords(recordWith().status(SUCCESS)),
//                        getTxnRecord(SIGNER_AND_PAYER_ARE_DIFFERENT)
//                                .andAllChildRecords()
//                                .hasChildRecords(recordWith().status(SUCCESS))
                );
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
