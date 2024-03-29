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
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.*;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
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
    private static final String DELEGATE_CONTRACT_KEY_NAME = "contractKey";
    private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE =
            KeyShape.threshOf(1, KeyShape.SIMPLE, DELEGATE_CONTRACT);
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
    private static final String SIGNER_HAS_KEY_WITH_CORRECT_CONTRACT_ID =
            "signerBurnsAndTokenSupplyKeyHasTheSignerPublicKey";
    private static final String SIGNER_AND_PAYER_ARE_DIFFERENT = "signerAndPayerAreDifferentAccounts";
    private static final String TOKEN_HAS_NO_UPDATED_KEY = "tokenHasUpdatedContractKey";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    private static final String BURN_TOKEN = "BurnToken";
    private static final String MIXED_BURN_TOKEN = "MixedBurnToken";
    private static final String FIRST = "First!";
    private static final String SECOND = "Second!";
    private static final String THIRD = "Third!";
    private static final String FOURTH = "Fourth!";
    private static final String DELEGATE_CALL_WHEN_FUNGIBLE_TOKEN_HAS_CONTRACT_ID =
            "FungibleTokenHasTheContractIdOnDelegateCall";
    private static final String DELEGATE_CALL_WHEN_NON_FUNGIBLE_TOKEN_HAS_CONTRACT_ID =
            "NonFungibleTokenHasTheContractIdOnDelegateCall";
    private static final String DELEGATE_CALL_WHEN_NON_FUNGIBLE_TOKEN_HAS_CONTRACT_ID_SIGNER_SIGNS =
            "NonFungibleTokenHasTheContractIdOnDelegateCall";
    private static final String DELEGATE_CALL_WHEN_FUNGIBLE_TOKEN_HAS_CONTRACT_ID_SIGNER_SIGNS =
            "FungibleTokenHasTheContractIdOnDelegateCall";

    public static void main(final String... args) {
        new ContractBurnHTSV2SecurityModelSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    public List<HapiSpec> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<HapiSpec> positiveSpecs() {
        return List.of(V2Security004FungibleTokenBurnPositive(), V2Security005NonFungibleTokenBurnPositive());
    }

    List<HapiSpec> negativeSpecs() {
        return List.of(
                V2Security004FungibleTokenBurnNegative(),
                V2Security004NonFungibleTokenBurnNegative(),
                V2Security039FungibleTokenWithDelegateContractKeyCanNotBurnFromDelegatecall(),
                V2Security039NonFungibleTokenWithDelegateContractKeyCanNotBurnFromDelegatecall());
    }

    @HapiTest
    final HapiSpec V2Security004FungibleTokenBurnPositive() {
        final var initialAmount = 20L;
        final var amountToBurn = 5L;
        final AtomicReference<TokenID> fungible = new AtomicReference<>();
        // sync
        return defaultHapiSpec("V2Security004FungibleTokenBurnPositive")
                .given(
                        //  overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                        // CONTRACTS_V2_SECURITY_MODEL_BLOCK_CUTOFF),
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
                        uploadInitCode(MIXED_BURN_TOKEN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                MIXED_BURN_TOKEN,
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)))),
                        // Create a key with shape contract and the contractId of MIXED_BURN_TOKEN contract
                        newKeyNamed(CONTRACT_KEY).shape(CONTRACT.signedWith(MIXED_BURN_TOKEN)),
                        // Update the token supply key to with the created key
                        tokenUpdate(FUNGIBLE_TOKEN).supplyKey(CONTRACT_KEY),
                        // Test Case 1: Signer paying and signing a token burn transaction
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        // The signer will have a key with the contractId (key type CONTRACT)
                        contractCall(MIXED_BURN_TOKEN, "burnToken", BigInteger.valueOf(amountToBurn), new long[0])
                                .via(SIGNER_BURNS_WITH_CONTRACT_ID)
                                .gas(GAS_TO_OFFER)
                                .hasRetryPrecheckFrom(BUSY)
                                .payingWith(SIGNER)
                                .signedBy(SIGNER),
                        // Assert that the token is burdned - total supply should be decreased with the amount that was
                        // burned
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount - amountToBurn),
                        // Test Case 2: the Treasury account is paying and signing a token burn transaction,
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        contractCall(MIXED_BURN_TOKEN, "burnToken", BigInteger.valueOf(amountToBurn), new long[0])
                                .via(SIGNER_HAS_KEY_WITH_CORRECT_CONTRACT_ID)
                                .gas(GAS_TO_OFFER)
                                .hasRetryPrecheckFrom(BUSY)
                                .payingWith(TOKEN_TREASURY)
                                .signedBy(TOKEN_TREASURY),
                        // Assert that the token is burned - total supply should be increased with the amount to burn.
                        // NOTE: it is multiplied by 2 because of the burned amount in the previous test
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount - 2 * amountToBurn),
                        // Test Case 3: one account  paying and another one signing a token burn transaction
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        contractCall(MIXED_BURN_TOKEN, "burnToken", BigInteger.valueOf(amountToBurn), new long[0])
                                .via(SIGNER_AND_PAYER_ARE_DIFFERENT)
                                .gas(GAS_TO_OFFER)
                                .hasRetryPrecheckFrom(BUSY)
                                .payingWith(SIGNER2)
                                .signedBy(SIGNER2, SIGNER),
                        // Assert that the token is burned - total supply should be increased with the amount to burn.
                        // NOTE: it is multiplied by 3 because of the burned amount in the previous tests
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount - 3 * amountToBurn),
                        // Create a key with thresh 1/2 with sigs: new ed25519 key, contractId of burnToken contract
                        newKeyNamed(TRESHOLD_KEY_CORRECT_CONTRACT_ID)
                                .shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, MIXED_BURN_TOKEN))),
                        // Update the token supply key to with the created key
                        tokenUpdate(FUNGIBLE_TOKEN).supplyKey(TRESHOLD_KEY_CORRECT_CONTRACT_ID),
                        // Test Case 4: Signer paying and signing a token burn transaction.
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        // The signer will have a key with the contractId (key type TRESHOLD_KEY)
                        contractCall(MIXED_BURN_TOKEN, "burnToken", BigInteger.valueOf(amountToBurn), new long[0])
                                .via(SIGNER_BURNS_WITH_TRESHOLD_KEY)
                                .gas(GAS_TO_OFFER)
                                .hasRetryPrecheckFrom(BUSY)
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
                        getTxnRecord(SIGNER_HAS_KEY_WITH_CORRECT_CONTRACT_ID)
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

        return defaultHapiSpec("V2Security005NonFungibleTokenBurnPositive")
                .given(
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
                        uploadInitCode(MIXED_BURN_TOKEN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                MIXED_BURN_TOKEN,
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)))),
                        // Create a key with shape contract and the contractId of burnToken contract
                        newKeyNamed(DELEGATE_CONTRACT_KEY_NAME)
                                .shape(DELEGATE_CONTRACT_KEY_SHAPE.signedWith(sigs(ON, MIXED_BURN_TOKEN))),
                        tokenUpdate(NON_FUNGIBLE_TOKEN).supplyKey(DELEGATE_CONTRACT_KEY_NAME),
                        // Test Case 1: Treasury account is paying and signing a token burn transaction, where the token
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        // The signer will have a key with the contractId (key type CONTRACT)
                        contractCall(MIXED_BURN_TOKEN, "burnToken", BigInteger.valueOf(0), serialNumber1)
                                .via(SIGNER_BURNS_WITH_CONTRACT_ID)
                                .gas(GAS_TO_OFFER)
                                .hasRetryPrecheckFrom(BUSY)
                                .payingWith(TOKEN_TREASURY)
                                .signedBy(TOKEN_TREASURY),
                        getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(4 - amountToBurn),
                        // Test Case 2: Signer account is paying and signing a token burn transaction, where the token
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        // The signer will have a key with the contractId (key type CONTRACT)
                        contractCall(MIXED_BURN_TOKEN, "burnToken", BigInteger.valueOf(0), serialNumber2)
                                .via(SIGNER_HAS_KEY_WITH_CORRECT_CONTRACT_ID)
                                .gas(GAS_TO_OFFER)
                                .hasRetryPrecheckFrom(BUSY)
                                .payingWith(SIGNER)
                                .signedBy(SIGNER),
                        getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(3 - amountToBurn),
                        // Test Case 3: one account  paying and another one signing a token burn transaction,
                        // SIGNER → call → CONTRACT → call →PRECOMPILE
                        contractCall(MIXED_BURN_TOKEN, "burnToken", BigInteger.valueOf(0), serialNumber3)
                                .via(SIGNER_AND_PAYER_ARE_DIFFERENT)
                                .gas(GAS_TO_OFFER)
                                .hasRetryPrecheckFrom(BUSY)
                                .payingWith(SIGNER2)
                                .signedBy(SIGNER2, TOKEN_TREASURY),
                        getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(2 - amountToBurn))))
                .then(
                        // Verify that each test case has 1 successful child record
                        getTxnRecord(SIGNER_BURNS_WITH_CONTRACT_ID)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS)),
                        getTxnRecord(SIGNER_HAS_KEY_WITH_CORRECT_CONTRACT_ID)
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

        return defaultHapiSpec("V2Security004FungibleTokenBurnNegative")
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(SIGNER).balance(ONE_MILLION_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(initialAmount)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY)
                                .exposingCreatedIdTo(idLit -> fungible.set(asToken(idLit))),
                        uploadInitCode(MIXED_BURN_TOKEN),
                        uploadInitCode(MINT_CONTRACT),
                        sourcing(() -> contractCreate(
                                MINT_CONTRACT, HapiParserUtil.asHeadlongAddress(asAddress(fungible.get())))))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(MIXED_BURN_TOKEN, HapiParserUtil.asHeadlongAddress(asAddress(fungible.get()))),
                        // Test Case 1: Signer paying and signing a token burn transaction,
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        // The signer and the token don't have updated keys
                        contractCall(MIXED_BURN_TOKEN, "burnToken", BigInteger.valueOf(amountToBurn), new long[0])
                                .via(SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS)
                                .gas(GAS_TO_OFFER)
                                .hasRetryPrecheckFrom(BUSY)
                                .payingWith(SIGNER)
                                .signedBy(SIGNER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
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
                        contractCall(MIXED_BURN_TOKEN, "burnToken", BigInteger.valueOf(amountToBurn), new long[0])
                                .via(SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY_AND_WRONG_CONTRACT_ID)
                                .gas(GAS_TO_OFFER)
                                .hasRetryPrecheckFrom(BUSY)
                                .signedBy(SIGNER)
                                .payingWith(SIGNER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount),
                        getTxnRecord(SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY_AND_WRONG_CONTRACT_ID)
                                .andAllChildRecords()
                                .logged(),
                        // Create a key with thresh 1/2 with sigs: new ed25519 key, contractId of MIXED_BURN_TOKEN
                        // contract
                        // Here the key has the contract`id of the correct contract
                        newKeyNamed(THRESHOLD_KEY).shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, MIXED_BURN_TOKEN))),
                        // Set the token's supply key to the initial one
                        tokenUpdate(FUNGIBLE_TOKEN).supplyKey(TOKEN_TREASURY),
                        // Update the Signer with the correct threshold key
                        cryptoUpdate(SIGNER).key(THRESHOLD_KEY),
                        // Test Case 3: Signer paying and signing a token burn transaction, when the token
                        // is expected to  be burned by the token treasury account
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        // The token has no updated supply key. The signer has the correct threshold key
                        contractCall(MIXED_BURN_TOKEN, "burnToken", BigInteger.valueOf(amountToBurn), new long[0])
                                .via(TOKEN_HAS_NO_UPDATED_KEY)
                                .gas(GAS_TO_OFFER)
                                .hasRetryPrecheckFrom(BUSY)
                                .signedBy(SIGNER)
                                .payingWith(SIGNER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount),
                        getTxnRecord(TOKEN_HAS_NO_UPDATED_KEY)
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
        final AtomicReference<TokenID> nonFungible = new AtomicReference<>();
        final var serialNumber1 = new long[] {1L};

        return defaultHapiSpec("V2Security004NonFungibleTokenBurnNegative")
                .given(
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
                        uploadInitCode(MIXED_BURN_TOKEN),
                        // contractCreate(MIXED_BURN_TOKEN),
                        uploadInitCode(MINT_CONTRACT),
                        sourcing(() -> contractCreate(
                                MINT_CONTRACT, HapiParserUtil.asHeadlongAddress(asAddress(nonFungible.get())))))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                MIXED_BURN_TOKEN, HapiParserUtil.asHeadlongAddress(asAddress(nonFungible.get()))),
                        // Test Case 1: Signer paying and signing a token burn transaction,
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        // The signer and the token don't have updated keys
                        contractCall(MIXED_BURN_TOKEN, "burnToken", BigInteger.valueOf(0), serialNumber1)
                                .via(SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS)
                                .gas(GAS_TO_OFFER)
                                .hasRetryPrecheckFrom(BUSY)
                                .payingWith(SIGNER)
                                .signedBy(SIGNER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
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
                        contractCall(MIXED_BURN_TOKEN, "burnToken", BigInteger.valueOf(0), serialNumber1)
                                .via(SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY_AND_WRONG_CONTRACT_ID)
                                .gas(GAS_TO_OFFER)
                                .hasRetryPrecheckFrom(BUSY)
                                .signedBy(SIGNER)
                                .payingWith(SIGNER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(1L),
                        getTxnRecord(SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY_AND_WRONG_CONTRACT_ID)
                                .andAllChildRecords()
                                .logged(),
                        // Create a key with thresh 1/2 with sigs: new ed25519 key, contractId of MIXED_BURN_TOKEN
                        // contract
                        // Here the key has the contract`id of the correct contract
                        newKeyNamed(THRESHOLD_KEY).shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, MIXED_BURN_TOKEN))),
                        // Set the token's supply key to the initial one
                        tokenUpdate(NON_FUNGIBLE_TOKEN).supplyKey(TOKEN_TREASURY),
                        // Update the Signer with the correct threshold key
                        cryptoUpdate(SIGNER).key(THRESHOLD_KEY),
                        // Test Case 3: Signer paying and signing a token burn transaction, when the token
                        // is expected to  be burned by the token treasury account
                        // SIGNER → call → CONTRACT → call → PRECOMPILE
                        // The token has no updated supply key. The signer has the correct threshold key
                        contractCall(MIXED_BURN_TOKEN, "burnToken", BigInteger.valueOf(0), serialNumber1)
                                .via(TOKEN_HAS_NO_UPDATED_KEY)
                                .gas(GAS_TO_OFFER)
                                .hasRetryPrecheckFrom(BUSY)
                                .signedBy(SIGNER)
                                .payingWith(SIGNER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(1L),
                        getTxnRecord(TOKEN_HAS_NO_UPDATED_KEY)
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
    final HapiSpec V2Security039NonFungibleTokenWithDelegateContractKeyCanNotBurnFromDelegatecall() {
        final var serialNumber1 = new long[] {1L};
        return defaultHapiSpec("V2Security035NonFungibleTokenWithDelegateContractKeyCanNotBurnFromDelegatecall")
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(SIGNER).balance(ONE_MILLION_HBARS),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY),
                        mintToken(NON_FUNGIBLE_TOKEN, List.of(copyFromUtf8(FIRST))),
                        uploadInitCode(MIXED_BURN_TOKEN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                MIXED_BURN_TOKEN,
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)))),
                        newKeyNamed(CONTRACT_KEY).shape(CONTRACT.signedWith(MIXED_BURN_TOKEN)),
                        tokenUpdate(NON_FUNGIBLE_TOKEN).supplyKey(CONTRACT_KEY),
                        // Test Case 1: Treasury account paying and signing a NON FUNGIBLE token burn transaction
                        // SIGNER → call → CONTRACT → delegatecall → PRECOMPILE
                        // The token has updated key
                        contractCall(
                                        MIXED_BURN_TOKEN,
                                        "burnTokenDelegateCall",
                                        BigInteger.valueOf(0),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        serialNumber1)
                                .via(DELEGATE_CALL_WHEN_NON_FUNGIBLE_TOKEN_HAS_CONTRACT_ID)
                                .gas(GAS_TO_OFFER)
                                .hasRetryPrecheckFrom(BUSY)
                                .signedBy(TOKEN_TREASURY)
                                .payingWith(TOKEN_TREASURY)
                                // Verify that the top level status of the transaction is CONTRACT_REVERT_EXECUTED
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        // Assert that the token is NOT burned
                        getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(1L),
                        // Assert the token is NOT burned from the token treasury account
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(NON_FUNGIBLE_TOKEN, 1L),
                        // Create a key with thresh 1/2 with sigs:  new ed25519 key, contractId of
                        // BURN_TOKEN_VIA_DELEGATE_CALL contract
                        newKeyNamed(TRESHOLD_KEY_CORRECT_CONTRACT_ID)
                                .shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, MIXED_BURN_TOKEN))),
                        // Update the token's supply to have the threshold key wit the wrong contract id
                        tokenUpdate(NON_FUNGIBLE_TOKEN).supplyKey(TRESHOLD_KEY_CORRECT_CONTRACT_ID),
                        // Update the signer of the transaction to have the threshold key with the wrong contract id
                        cryptoUpdate(SIGNER).key(TRESHOLD_KEY_CORRECT_CONTRACT_ID),
                        // Test Case 2: A Signer paying and signing a NON FUNGIBLE token burn transaction,
                        // SIGNER → call → CONTRACT → delegatecall → PRECOMPILE
                        // The token and the signer have updated keys
                        contractCall(
                                        MIXED_BURN_TOKEN,
                                        "burnTokenDelegateCall",
                                        BigInteger.valueOf(0),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        serialNumber1)
                                .via(DELEGATE_CALL_WHEN_NON_FUNGIBLE_TOKEN_HAS_CONTRACT_ID_SIGNER_SIGNS)
                                .gas(GAS_TO_OFFER)
                                .hasRetryPrecheckFrom(BUSY)
                                .signedBy(SIGNER)
                                .payingWith(SIGNER)
                                // Verify that the top level status of the transaction is CONTRACT_REVERT_EXECUTED
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        // Assert that the token is NOT burned
                        getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(1L),
                        // Assert the token is NOT burned from the token treasury account
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(NON_FUNGIBLE_TOKEN, 1L))))
                .then(withOpContext((spec, opLog) -> {
                    allRunFor(
                            spec,
                            // Verify that each test case has 1 top level call with the correct status
                            // NOTE: the used contract will revert when the token is not burned.
                            // The receipt has the revert error message.
                            emptyChildRecordsCheck(
                                    DELEGATE_CALL_WHEN_NON_FUNGIBLE_TOKEN_HAS_CONTRACT_ID, CONTRACT_REVERT_EXECUTED),
                            emptyChildRecordsCheck(
                                    DELEGATE_CALL_WHEN_NON_FUNGIBLE_TOKEN_HAS_CONTRACT_ID_SIGNER_SIGNS,
                                    CONTRACT_REVERT_EXECUTED));
                }));
    }

    @HapiTest
    final HapiSpec V2Security039FungibleTokenWithDelegateContractKeyCanNotBurnFromDelegatecall() {
        final var initialAmount = 20L;
        return defaultHapiSpec("V2Security035FungibleTokenWithDelegateContractKeyCanNotBurnFromDelegatecall")
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(SIGNER).balance(ONE_MILLION_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(initialAmount)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY),
                        uploadInitCode(MIXED_BURN_TOKEN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCreate(
                                MIXED_BURN_TOKEN,
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)))),
                        newKeyNamed(CONTRACT_KEY).shape(CONTRACT.signedWith(MIXED_BURN_TOKEN)),
                        tokenUpdate(FUNGIBLE_TOKEN).supplyKey(CONTRACT_KEY),
                        // Test Case 1: Treasury account paying and signing a FUNGIBLE token burn transaction
                        // SIGNER → call → CONTRACT → delegatecall → PRECOMPILE
                        // The token has updated key
                        contractCall(
                                        MIXED_BURN_TOKEN,
                                        "burnTokenDelegateCall",
                                        BigInteger.valueOf(1L),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        new long[0])
                                .via(DELEGATE_CALL_WHEN_FUNGIBLE_TOKEN_HAS_CONTRACT_ID)
                                .gas(GAS_TO_OFFER)
                                .hasRetryPrecheckFrom(BUSY)
                                .signedBy(TOKEN_TREASURY)
                                .payingWith(TOKEN_TREASURY)
                                // Verify that the top level status of the transaction is CONTRACT_REVERT_EXECUTED
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        // Assert that the token is NOT burned
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount),
                        // Assert the token is NOT burned from the token treasury account
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(FUNGIBLE_TOKEN, initialAmount),
                        // Test Case 2: A Signer paying and signing a FUNGIBLE token burn transaction
                        // SIGNER → call → CONTRACT → delegatecall → PRECOMPILE
                        // The token and the signer have updated keys
                        contractCall(
                                        MIXED_BURN_TOKEN,
                                        "burnTokenDelegateCall",
                                        BigInteger.valueOf(1L),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        new long[0])
                                .via(DELEGATE_CALL_WHEN_FUNGIBLE_TOKEN_HAS_CONTRACT_ID_SIGNER_SIGNS)
                                .gas(GAS_TO_OFFER)
                                .hasRetryPrecheckFrom(BUSY)
                                .signedBy(SIGNER)
                                .payingWith(SIGNER)
                                // Verify that the top level status of the transaction is CONTRACT_REVERT_EXECUTED
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        // Assert that the token is NOT burned
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(initialAmount),
                        // Assert the token is NOT burned from the token treasury account
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(FUNGIBLE_TOKEN, initialAmount))))
                .then(withOpContext((spec, opLog) -> {
                    allRunFor(
                            spec,
                            // Verify that each test case has 1 top level call with the correct status
                            // NOTE: the used contract will revert when the token is not burned.
                            // The receipt has the revert error message.
                            emptyChildRecordsCheck(
                                    DELEGATE_CALL_WHEN_FUNGIBLE_TOKEN_HAS_CONTRACT_ID, CONTRACT_REVERT_EXECUTED),
                            emptyChildRecordsCheck(
                                    DELEGATE_CALL_WHEN_FUNGIBLE_TOKEN_HAS_CONTRACT_ID_SIGNER_SIGNS,
                                    CONTRACT_REVERT_EXECUTED));
                }));
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
