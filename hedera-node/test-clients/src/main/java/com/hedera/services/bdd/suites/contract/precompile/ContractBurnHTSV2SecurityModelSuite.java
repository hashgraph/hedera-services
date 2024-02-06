/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.*;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V2_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
@SuppressWarnings("java:S1192")
public class ContractBurnHTSV2SecurityModelSuite extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(ContractMintHTSV1SecurityModelSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final String TOKEN_TREASURY = "treasury";
    private static final KeyShape TRESHOLD_KEY_SHAPE = KeyShape.threshOf(1, ED25519, CONTRACT);
    private static final String CONTRACT_KEY = "ContractKey";
    public static final String MINT_CONTRACT = "MintContract";
    private static final String HTS_CALLS = "HTSCalls";
    public static final String TRESHOLD_KEY_CORRECT_CONTRACT_ID =
            "tresholdKeyWithCorrectContractAndIncorrectSignerPublicKey";
    public static final String TRESHOLD_KEY_WITH_SIGNER_KEY =
            "tresholdKeyWithIncorrectContractAndCorrectSignerPublicKey";
    private static final String SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY_AND_WRONG_CONTRACT_ID =
            "signerMintsAndTokenSupplyKeyHasTheSignerPublicKeyAndTheWrongContractId";
    public static final String THRESHOLD_KEY = "Tresh1WithRandomEdKeyAndCorrectContractID";
    private static final String SIGNER = "anybody";
    private static final String SIGNER2 = "anybody";
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS = "signerAndTokenHaveNoUpdatedKeys";
    private static final String SIGNER_BURNS_WITH_CONTRACT_ID =
            "signerBurnsAndTokenSupplyKeyHasTheIntermediaryContractId";
    private static final String SIGNER_BURNS_WITH_TRESHOLD_KEY = "tokenAndSignerHaveThresholdKey";
    private static final String SIGNER_BURNS_WITH_SIGNER_PUBLIC_KEY =
            "signerBurnsAndTokenSupplyKeyHasTheSignerPublicKey";
    private static final String SIGNER_AND_PAYER_ARE_DIFFERENT = "signerAndPayerAreDifferentAccounts";
    private static final String TOKEN_HAS_NO_UPDATED_KEY = "tokenHasUpdatedContractKey";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    private static final String BURN_TOKEN = "BurnToken";
    private static final String FIRST = "First!";
    private static final String SECOND = "Second!";
    private static final String THIRD = "Third!";
    private static final String FOURTH = "Fourth!";

    public static void main(final String... args) {
        new ContractBurnHTSV2SecurityModelSuite().runSuiteSync();
    }

    public List<HapiSpec> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<HapiSpec> positiveSpecs() {
        return List.of(V2Security004FungibleTokenBurnPositive(), V2Security005NonFungibleTokenBurnPositive());
    }

    List<HapiSpec> negativeSpecs() {
        return List.of(V2Security004FungibleTokenBurnNegative(), V2Security004NonFungibleTokenBurnNegative());
    }

    @HapiTest
    final HapiSpec V2Security004FungibleTokenBurnPositive() {
        final var initialAmount = 20L;
        final var amountToBurn = 5L;
        final AtomicReference<TokenID> fungible = new AtomicReference<>();

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
                        uploadInitCode(BURN_TOKEN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                BURN_TOKEN,
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)))),
                        // Create a key with shape contract and the contractId of BURN_TOKEN contract
                        newKeyNamed(CONTRACT_KEY).shape(CONTRACT.signedWith(BURN_TOKEN)),
                        // Update the token supply key to with the created key
                        tokenUpdate(FUNGIBLE_TOKEN).supplyKey(CONTRACT_KEY),
                        // Test Case 1: Signer paying and signing a token burn transaction
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        // The signer will have a key with the contractId (key type CONTRACT)
                        contractCall(BURN_TOKEN, "burnToken", BigInteger.valueOf(amountToBurn), new long[0])
                                .via(SIGNER_BURNS_WITH_CONTRACT_ID)
                                .gas(GAS_TO_OFFER)
                                .payingWith(SIGNER)
                                .signedBy(SIGNER),
                        // Assert that the token is burdned - total supply should be decreased with the amount that was
                        // burned
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount - amountToBurn),
                        // Create a new key with treshhold key with correct contract id
                        newKeyNamed(TRESHOLD_KEY_CORRECT_CONTRACT_ID)
                                .shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, BURN_TOKEN))),
                        // Update token supply key with the correct contract id
                        tokenUpdate(FUNGIBLE_TOKEN).supplyKey(TRESHOLD_KEY_CORRECT_CONTRACT_ID),
                        // Update signer with the correct contract id
                        cryptoUpdate(SIGNER).key(TRESHOLD_KEY_CORRECT_CONTRACT_ID),
                        // Test Case 2: the Treasury account is paying and signing a token burn transaction,
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        contractCall(BURN_TOKEN, "burnToken", BigInteger.valueOf(amountToBurn), new long[0])
                                .via(SIGNER_BURNS_WITH_SIGNER_PUBLIC_KEY)
                                .gas(GAS_TO_OFFER)
                                .payingWith(TOKEN_TREASURY)
                                .signedBy(TOKEN_TREASURY),
                        // Assert that the token is burned - total supply should be increased with the amount to burn.
                        // NOTE: it is multiplied by 2 because of the burned amount in the previous test
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount - 2 * amountToBurn),
                        // Test Case 3: one account  paying and another one signing a token burn transaction
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        contractCall(BURN_TOKEN, "burnToken", BigInteger.valueOf(amountToBurn), new long[0])
                                .via(SIGNER_AND_PAYER_ARE_DIFFERENT)
                                .gas(GAS_TO_OFFER)
                                .payingWith(SIGNER2)
                                .signedBy(SIGNER2, SIGNER),
                        // Assert that the token is burned - total supply should be increased with the amount to burn.
                        // NOTE: it is multiplied by 3 because of the burned amount in the previous tests
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount - 3 * amountToBurn),
                        // Create a key with thresh 1/2 with sigs: new ed25519 key, contractId of burnToken contract
                        newKeyNamed(TRESHOLD_KEY_CORRECT_CONTRACT_ID)
                                .shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, BURN_TOKEN))),
                        // Update the token supply key to with the created key
                        tokenUpdate(FUNGIBLE_TOKEN).supplyKey(TRESHOLD_KEY_CORRECT_CONTRACT_ID),
                        // Test Case 4: Signer paying and signing a token burn transaction.
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        // The signer will have a key with the contractId (key type TRESHOLD_KEY)
                        contractCall(BURN_TOKEN, "burnToken", BigInteger.valueOf(amountToBurn), new long[0])
                                .via(SIGNER_BURNS_WITH_TRESHOLD_KEY)
                                .gas(GAS_TO_OFFER)
                                .payingWith(SIGNER)
                                .signedBy(SIGNER),
                        // Assert that the token is burned - total supply should be decreased with the amount that was
                        // burned
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount - 4 * amountToBurn))))
                .then(
                        // Verify that each test case has 1 successful child record
                        getTxnRecord(SIGNER_BURNS_WITH_CONTRACT_ID)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS)),
                        getTxnRecord(SIGNER_BURNS_WITH_SIGNER_PUBLIC_KEY)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS)),
                        getTxnRecord(SIGNER_AND_PAYER_ARE_DIFFERENT)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS)),
                        getTxnRecord(SIGNER_BURNS_WITH_TRESHOLD_KEY)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS)));
    }

    @HapiTest
    final HapiSpec V2Security005NonFungibleTokenBurnPositive() {
        final var amountToBurn = 1L;
        final AtomicReference<TokenID> nonFungible = new AtomicReference<>();
        final var serialNumber1 = new long[] {1L};
        final var serialNumber2 = new long[] {2L};
        final var serialNumber3 = new long[] {3L};

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
                        uploadInitCode(BURN_TOKEN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                BURN_TOKEN,
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)))),
                        // Create a key with shape contract and the contractId of burnToken contract
                        newKeyNamed(CONTRACT_KEY).shape(CONTRACT.signedWith(BURN_TOKEN)),
                        tokenUpdate(NON_FUNGIBLE_TOKEN).supplyKey(CONTRACT_KEY),
                        // Test Case 1: Treasury account is paying and signing a token burn transaction, where the token
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        // The signer will have a key with the contractId (key type CONTRACT)
                        contractCall(BURN_TOKEN, "burnToken", BigInteger.valueOf(0), serialNumber1)
                                .via(SIGNER_BURNS_WITH_CONTRACT_ID)
                                .gas(GAS_TO_OFFER)
                                .payingWith(TOKEN_TREASURY)
                                .signedBy(TOKEN_TREASURY),
                        getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(4 - amountToBurn),
                        // Test Case 2: Signer account is paying and signing a token burn transaction, where the token
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        // The signer will have a key with the contractId (key type CONTRACT)
                        contractCall(BURN_TOKEN, "burnToken", BigInteger.valueOf(0), serialNumber2)
                                .via(SIGNER_BURNS_WITH_SIGNER_PUBLIC_KEY)
                                .gas(GAS_TO_OFFER)
                                .payingWith(SIGNER)
                                .signedBy(SIGNER),
                        getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(3 - amountToBurn),
                        // Test Case 3: one account  paying and another one signing a token burn transaction,
                        // SIGNER → call → CONTRACT → call →PRECOMPILE
                        contractCall(BURN_TOKEN, "burnToken", BigInteger.valueOf(0), serialNumber3)
                                .via(SIGNER_AND_PAYER_ARE_DIFFERENT)
                                .gas(GAS_TO_OFFER)
                                .payingWith(SIGNER2)
                                .signedBy(SIGNER2, TOKEN_TREASURY),
                        getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(2 - amountToBurn))))
                .then(
                        // Verify that each test case has 1 successful child record
                        getTxnRecord(SIGNER_BURNS_WITH_CONTRACT_ID)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS)),
                        getTxnRecord(SIGNER_BURNS_WITH_SIGNER_PUBLIC_KEY)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS)),
                        getTxnRecord(SIGNER_AND_PAYER_ARE_DIFFERENT)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS)));
    }

    @HapiTest
    final HapiSpec V2Security004FungibleTokenBurnNegative() {
        final var initialAmount = 20L;
        final var amountToBurn = 5L;
        final AtomicReference<TokenID> fungible = new AtomicReference<>();

        return propertyPreservingHapiSpec("V2Security004FungibleTokenBurnNegative")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V2_SECURITY_MODEL_BLOCK_CUTOFF),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(SIGNER).balance(ONE_MILLION_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(initialAmount)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY)
                                .exposingCreatedIdTo(idLit -> fungible.set(asToken(idLit))),
                        uploadInitCode(HTS_CALLS),
                        contractCreate(HTS_CALLS),
                        uploadInitCode(MINT_CONTRACT),
                        sourcing(() -> contractCreate(
                                MINT_CONTRACT, HapiParserUtil.asHeadlongAddress(asAddress(fungible.get())))))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        HTS_CALLS,
                                        "burnTokenCall",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        BigInteger.valueOf(amountToBurn),
                                        new long[0])
                                .via(SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS)
                                .gas(GAS_TO_OFFER)
                                .payingWith(SIGNER)
                                .signedBy(SIGNER),
                        // verify that the total supply of the tokens is not affected
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount),
                        getTxnRecord(SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS)
                                .andAllChildRecords()
                                .logged(),
                        // Create a key with thresh 1/2 with sigs:  new ed25519 key, contractId of MINT_CONTRACT
                        // contract. MINT_CONTRACT is used only as a "wrong" contract id
                        newKeyNamed(TRESHOLD_KEY_WITH_SIGNER_KEY)
                                .shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, MINT_CONTRACT))),
                        // Update the signer of the transaction to have the threshold key with the wrong contract id
                        cryptoUpdate(SIGNER).key(TRESHOLD_KEY_WITH_SIGNER_KEY),
                        // Update the token's supply to have the threshold key with the wrong contract id
                        tokenUpdate(FUNGIBLE_TOKEN).supplyKey(TRESHOLD_KEY_WITH_SIGNER_KEY),
                        // Test Case 2: Signer paying and signing a token burn transaction, when the token
                        // is expected to  be burned by the token treasury
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        // The signer and the token have a threshold key with the signer's public key
                        // and the wrong contract id (MINT_CONTRACT)
                        contractCall(
                                        HTS_CALLS,
                                        "burnTokenCall",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        BigInteger.valueOf(amountToBurn),
                                        new long[0])
                                .via(SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY_AND_WRONG_CONTRACT_ID)
                                .gas(GAS_TO_OFFER)
                                .alsoSigningWithFullPrefix(SIGNER)
                                .payingWith(SIGNER),
                        // verify that the total supply is not affected
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount),
                        getTxnRecord(SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS)
                                .andAllChildRecords()
                                .logged(),
                        // Create a key with thresh 1/2 with sigs: new ed25519 key, contractId of HTS_CALLS contract
                        // Here the key has the contract`id of the correct contract
                        newKeyNamed(THRESHOLD_KEY).shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, HTS_CALLS))),
                        // Set the token's supply key to the initial one
                        tokenUpdate(FUNGIBLE_TOKEN).supplyKey(TOKEN_TREASURY),
                        // Update the Signer with the correct threshold key
                        cryptoUpdate(SIGNER).key(THRESHOLD_KEY),
                        // Test Case 3: Signer paying and signing a token burn transaction, when the token
                        // is expected to  be burned by the token treasury account
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        // The token has no updated supply key. The signer has the correct threshold key
                        contractCall(
                                        HTS_CALLS,
                                        "burnTokenCall",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        BigInteger.valueOf(amountToBurn),
                                        new long[0])
                                .via(TOKEN_HAS_NO_UPDATED_KEY)
                                .gas(GAS_TO_OFFER)
                                .signedBy(SIGNER)
                                .payingWith(SIGNER),
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount),
                        getTxnRecord(SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS)
                                .andAllChildRecords()
                                .logged())))
                .then(
                        // Verify that the child records fail with the expected status
                        getTxnRecord(SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        getTxnRecord(SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY_AND_WRONG_CONTRACT_ID)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        getTxnRecord(TOKEN_HAS_NO_UPDATED_KEY)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)));
    }

    @HapiTest
    final HapiSpec V2Security004NonFungibleTokenBurnNegative() {
        final var initialAmount = 1L;
        final var amountToBurn = 1L;
        final AtomicReference<TokenID> nonFungible = new AtomicReference<>();
        final var serialNumber1 = new long[] {1L};

        return propertyPreservingHapiSpec("V2Security004NonFungibleTokenBurnNegative")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V2_SECURITY_MODEL_BLOCK_CUTOFF),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(SIGNER).balance(ONE_MILLION_HBARS),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY)
                                .exposingCreatedIdTo(idLit -> nonFungible.set(asToken(idLit))),
                        // Mint NFT, so that we can verify that the burn fails as expected
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8(FIRST))),
                        uploadInitCode(HTS_CALLS),
                        contractCreate(HTS_CALLS),
                        uploadInitCode(MINT_CONTRACT),
                        sourcing(() -> contractCreate(
                                MINT_CONTRACT, HapiParserUtil.asHeadlongAddress(asAddress(nonFungible.get())))))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        HTS_CALLS,
                                        "burnTokenCall",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        BigInteger.valueOf(0),
                                        serialNumber1)
                                .via(SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS)
                                .gas(GAS_TO_OFFER)
                                .payingWith(SIGNER)
                                .signedBy(SIGNER),
                        // verify that the total supply is not affected
                        getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(1L),
                        getTxnRecord(SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS)
                                .andAllChildRecords()
                                .logged(),
                        // Create a key with thresh 1/2 with sigs:  new ed25519 key, contract id of MINT_CONTRACT
                        // contract. MINT_CONTRACT is only used as a "wrong" contractId
                        // Here the key has the contract`id of the wrong contract
                        newKeyNamed(TRESHOLD_KEY_WITH_SIGNER_KEY)
                                .shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, MINT_CONTRACT))),
                        // Update the signer of the transaction to have the threshold key with the wrong contract id
                        cryptoUpdate(SIGNER).key(TRESHOLD_KEY_WITH_SIGNER_KEY),
                        // Update the token's supply to have the threshold key with the wrong contract id
                        tokenUpdate(NON_FUNGIBLE_TOKEN).supplyKey(TRESHOLD_KEY_WITH_SIGNER_KEY),
                        // Test Case 2: Signer paying and signing a token burn transaction, when the token
                        // is expected to  be burned by the token treasury account
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        // The signer and the token have a threshold key with the signer's public key
                        // and the wrong contract id
                        contractCall(
                                        HTS_CALLS,
                                        "burnTokenCall",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        BigInteger.valueOf(0),
                                        serialNumber1)
                                .via(SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY_AND_WRONG_CONTRACT_ID)
                                .gas(GAS_TO_OFFER)
                                .alsoSigningWithFullPrefix(SIGNER)
                                .payingWith(SIGNER),
                        // verify that the total supply is not affected
                        getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(1L),
                        getTxnRecord(SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS)
                                .andAllChildRecords()
                                .logged(),
                        // Create a key with thresh 1/2 with sigs: new ed25519 key, contractId of HTS_CALLS contract
                        // Here the key has the contract`id of the correct contract
                        newKeyNamed(THRESHOLD_KEY).shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, HTS_CALLS))),
                        // Set the token's supply key to the initial one
                        tokenUpdate(NON_FUNGIBLE_TOKEN).supplyKey(TOKEN_TREASURY),
                        // Update the Signer with the correct threshold key
                        cryptoUpdate(SIGNER).key(THRESHOLD_KEY),
                        // Test Case 3: Signer paying and signing a token burn transaction, when the token
                        // is expected to  be burned by the token treasury account
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        // The token has no updated supply key. The signer has the correct threshold key
                        contractCall(
                                        HTS_CALLS,
                                        "burnTokenCall",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        BigInteger.valueOf(0),
                                        serialNumber1)
                                .via(TOKEN_HAS_NO_UPDATED_KEY)
                                .gas(GAS_TO_OFFER)
                                .signedBy(SIGNER)
                                .payingWith(SIGNER),
                        getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(1L),
                        getTxnRecord(SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS)
                                .andAllChildRecords()
                                .logged())))
                .then(
                        // Verify that the child records fail with the expected status
                        getTxnRecord(SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        getTxnRecord(SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY_AND_WRONG_CONTRACT_ID)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        getTxnRecord(TOKEN_HAS_NO_UPDATED_KEY)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)));
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
