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

import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.HapiPropertySource.asTokenString;
import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyShape.SECP256K1;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hedera.services.bdd.suites.file.FileUpdateSuite.CIVILIAN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoCreate;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

// Some of the test cases cannot be converted to use eth calls,
// since they use admin keys, which are held by the txn payer.
// In the case of an eth txn, we revoke the payers keys and the txn would fail.
// The only way an eth account to create a token is the admin key to be of a contractId type.
@SuppressWarnings("java:S1192") // "string literal should not be duplicated" - this rule makes test suites worse
public class SigningReqsV1SecurityModelSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(SigningReqsV1SecurityModelSuite.class);

    private static final String FIRST_CREATE_TXN = "firstCreateTxn";
    private static final String SECOND_CREATE_TXN = "secondCreateTxn";
    private static final long DEFAULT_AMOUNT_TO_SEND = 20 * ONE_HBAR;
    private static final String MINIMAL_CREATIONS_CONTRACT = "MinimalTokenCreations";

    public static final String AUTO_RENEW = "autoRenew";
    public static final String AR_KEY = "arKey";
    public static final int GAS_TO_OFFER = 1_000_000;

    public static void main(String... args) {
        new SigningReqsV1SecurityModelSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return false;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return List.of(
                newAutoRenewAccountMustSignUpdate(),
                newTreasuryAccountMustSignUpdate(),
                autoRenewAccountMustSignCreation(),
                fractionalFeeCollectorMustSign(),
                selfDenominatedFixedCollectorMustSign());
    }

    @SuppressWarnings("java:S5960") // "assertions should not be used in production code" - not production
    final Stream<DynamicTest> selfDenominatedFixedCollectorMustSign() {
        final var fcKey = "fcKey";
        final var arKey = AR_KEY;
        final var feeCollector = "feeCollector";
        final var autoRenew = AUTO_RENEW;
        final AtomicLong contractId = new AtomicLong();
        final AtomicReference<Address> autoRenewAlias = new AtomicReference<>();
        final AtomicReference<Address> feeCollectorAlias = new AtomicReference<>();
        final AtomicReference<TokenID> createdToken = new AtomicReference<>();

        return propertyPreservingHapiSpec("SelfDenominatedFixedCollectorMustSign")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenCreate",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(arKey).shape(SECP256K1),
                        newKeyNamed(fcKey).shape(SECP256K1),
                        cryptoCreate(CIVILIAN).balance(10L * ONE_HUNDRED_HBARS),
                        cryptoCreateWithExposingId(autoRenew, arKey, autoRenewAlias),
                        cryptoCreateWithExposingId(feeCollector, fcKey, feeCollectorAlias),
                        uploadInitCode(MINIMAL_CREATIONS_CONTRACT),
                        contractCreate(MINIMAL_CREATIONS_CONTRACT)
                                .gas(GAS_TO_OFFER)
                                .exposingNumTo(contractId::set))
                .when(
                        sourcing(() -> contractCall(
                                        MINIMAL_CREATIONS_CONTRACT,
                                        "makeRenewableTokenWithSelfDenominatedFixedFee",
                                        autoRenewAlias.get(),
                                        THREE_MONTHS_IN_SECONDS,
                                        feeCollectorAlias.get())
                                .via(FIRST_CREATE_TXN)
                                .gas(10L * GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(CIVILIAN)
                                .alsoSigningWithFullPrefix(autoRenew)
                                .refusingEthConversion()
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing(() -> contractCall(
                                        MINIMAL_CREATIONS_CONTRACT,
                                        "makeRenewableTokenWithSelfDenominatedFixedFee",
                                        autoRenewAlias.get(),
                                        THREE_MONTHS_IN_SECONDS,
                                        feeCollectorAlias.get())
                                .via(FIRST_CREATE_TXN)
                                .gas(10L * GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(CIVILIAN)
                                .alsoSigningWithFullPrefix(autoRenew, feeCollector)
                                .refusingEthConversion()))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN)
                                .andAllChildRecords()
                                .exposingTokenCreationsTo(creations -> createdToken.set(creations.get(0))),
                        sourcing(() -> getTokenInfo(asTokenString(createdToken.get()))
                                .hasAutoRenewAccount(autoRenew)
                                .logged()
                                .hasCustom((spec, fees) -> {
                                    assertEquals(1, fees.size());
                                    final var fee = fees.get(0);
                                    assertTrue(fee.hasFixedFee());
                                    assertEquals(
                                            createdToken.get(),
                                            fee.getFixedFee().getDenominatingTokenId());
                                    assertEquals(
                                            spec.registry().getAccountID(feeCollector), fee.getFeeCollectorAccountId());
                                })));
    }

    @SuppressWarnings("java:S5960") // "assertions should not be used in production code" - not production
    final Stream<DynamicTest> fractionalFeeCollectorMustSign() {
        final var fcKey = "fcKey";
        final var arKey = AR_KEY;
        final var feeCollector = "feeCollector";
        final var autoRenew = AUTO_RENEW;
        final AtomicLong contractId = new AtomicLong();
        final AtomicReference<Address> autoRenewAlias = new AtomicReference<>();
        final AtomicReference<Address> feeCollectorAlias = new AtomicReference<>();
        final AtomicReference<TokenID> createdToken = new AtomicReference<>();

        return propertyPreservingHapiSpec("FractionalFeeCollectorMustSign")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenCreate",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(arKey).shape(SECP256K1),
                        newKeyNamed(fcKey).shape(SECP256K1),
                        cryptoCreate(CIVILIAN).balance(10L * ONE_HUNDRED_HBARS),
                        cryptoCreateWithExposingId(autoRenew, arKey, autoRenewAlias),
                        cryptoCreateWithExposingId(feeCollector, fcKey, feeCollectorAlias),
                        uploadInitCode(MINIMAL_CREATIONS_CONTRACT),
                        contractCreate(MINIMAL_CREATIONS_CONTRACT)
                                .gas(GAS_TO_OFFER)
                                .exposingNumTo(contractId::set))
                .when(
                        sourcing(() -> contractCall(
                                        MINIMAL_CREATIONS_CONTRACT,
                                        "makeRenewableTokenWithFractionalFee",
                                        autoRenewAlias.get(),
                                        THREE_MONTHS_IN_SECONDS,
                                        feeCollectorAlias.get())
                                .via(FIRST_CREATE_TXN)
                                .gas(10L * GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(CIVILIAN)
                                .alsoSigningWithFullPrefix(autoRenew)
                                .refusingEthConversion()
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        sourcing(() -> contractCall(
                                        MINIMAL_CREATIONS_CONTRACT,
                                        "makeRenewableTokenWithFractionalFee",
                                        autoRenewAlias.get(),
                                        THREE_MONTHS_IN_SECONDS,
                                        feeCollectorAlias.get())
                                .via(FIRST_CREATE_TXN)
                                .gas(10L * GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(CIVILIAN)
                                .alsoSigningWithFullPrefix(autoRenew, feeCollector)
                                .refusingEthConversion()))
                .then(
                        getTxnRecord(FIRST_CREATE_TXN)
                                .andAllChildRecords()
                                .exposingTokenCreationsTo(creations -> createdToken.set(creations.get(0))),
                        sourcing(() -> getTokenInfo(asTokenString(createdToken.get()))
                                .hasAutoRenewAccount(autoRenew)
                                .logged()
                                .hasCustom((spec, fees) -> {
                                    assertEquals(1, fees.size());
                                    final var fee = fees.get(0);
                                    assertTrue(fee.hasFractionalFee());
                                    assertEquals(
                                            spec.registry().getAccountID(feeCollector), fee.getFeeCollectorAccountId());
                                })));
    }

    final Stream<DynamicTest> autoRenewAccountMustSignCreation() {
        final var arKey = AR_KEY;
        final var autoRenew = AUTO_RENEW;
        final AtomicReference<Address> autoRenewAlias = new AtomicReference<>();
        final AtomicLong contractId = new AtomicLong();
        final AtomicReference<TokenID> createdToken = new AtomicReference<>();

        return propertyPreservingHapiSpec("AutoRenewAccountMustSignCreation")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenCreate",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(arKey).shape(SECP256K1),
                        cryptoCreate(CIVILIAN).balance(10L * ONE_HUNDRED_HBARS),
                        cryptoCreateWithExposingId(autoRenew, arKey, autoRenewAlias),
                        uploadInitCode(MINIMAL_CREATIONS_CONTRACT),
                        contractCreate(MINIMAL_CREATIONS_CONTRACT)
                                .exposingNumTo(contractId::set)
                                .gas(GAS_TO_OFFER))
                .when(
                        // Fails without the auto-renew account's full-prefix signature
                        sourcing(() -> contractCall(
                                        MINIMAL_CREATIONS_CONTRACT,
                                        "makeRenewableToken",
                                        autoRenewAlias.get(),
                                        THREE_MONTHS_IN_SECONDS)
                                .via(FIRST_CREATE_TXN)
                                .gas(10L * GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(CIVILIAN)
                                .refusingEthConversion()
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)),
                        // Succeeds with the full-prefix signature
                        sourcing(() -> contractCall(
                                        MINIMAL_CREATIONS_CONTRACT,
                                        "makeRenewableToken",
                                        autoRenewAlias.get(),
                                        THREE_MONTHS_IN_SECONDS)
                                .via(SECOND_CREATE_TXN)
                                .gas(10L * GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .payingWith(CIVILIAN)
                                .alsoSigningWithFullPrefix(arKey)
                                .refusingEthConversion()))
                .then(
                        getTxnRecord(SECOND_CREATE_TXN)
                                .andAllChildRecords()
                                .exposingTokenCreationsTo(creations -> createdToken.set(creations.get(0))),
                        childRecordsCheck(
                                FIRST_CREATE_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        sourcing(() ->
                                getTokenInfo(asTokenString(createdToken.get())).hasAutoRenewAccount(autoRenew)));
    }

    final Stream<DynamicTest> newTreasuryAccountMustSignUpdate() {
        final var ft = "fungibleToken";
        final var ntKey = "ntKey";
        final var updateTxn = "updateTxn";
        final var newTreasury = "newTreasury";
        final AtomicReference<Address> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<Address> newTreasuryAliasAddr = new AtomicReference<>();

        return propertyPreservingHapiSpec("NewTreasuryAccountMustSignUpdate")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenCreate,TokenUpdate",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(ntKey).shape(SECP256K1),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(newTreasury)
                                // The new treasury must either already be associated or
                                // have open auto-association slots; it's therefore a bit
                                // odd that we require it to also sign, but this is the
                                // HAPI behavior, so we should be consistent for now
                                .maxAutomaticTokenAssociations(1)
                                .key(ntKey)
                                .exposingCreatedIdTo(id -> newTreasuryAliasAddr.set(idAsHeadlongAddress(id))),
                        cryptoCreate(CIVILIAN).balance(10L * ONE_HUNDRED_HBARS),
                        uploadInitCode(MINIMAL_CREATIONS_CONTRACT),
                        contractCreate(MINIMAL_CREATIONS_CONTRACT).gas(GAS_TO_OFFER),
                        tokenCreate(ft)
                                .adminKey(CIVILIAN)
                                .treasury(TOKEN_TREASURY)
                                .exposingCreatedIdTo(idLit -> tokenMirrorAddr.set(idAsHeadlongAddress(asToken(idLit)))))
                .when(sourcing(() -> contractCall(
                                MINIMAL_CREATIONS_CONTRACT,
                                "updateTokenWithNewTreasury",
                                tokenMirrorAddr.get(),
                                newTreasuryAliasAddr.get())
                        .via(updateTxn)
                        .gas(10L * GAS_TO_OFFER)
                        .sending(DEFAULT_AMOUNT_TO_SEND)
                        .payingWith(CIVILIAN)
                        .refusingEthConversion()
                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .then(
                        childRecordsCheck(
                                updateTxn,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        // Treasury account is unchanged
                        getTokenInfo(ft).hasTreasury(TOKEN_TREASURY));
    }

    final Stream<DynamicTest> newAutoRenewAccountMustSignUpdate() {
        final var ft = "fungibleToken";
        final var narKey = "narKey";
        final var adminKey = "adminKey";
        final var updateTxn = "updateTxn";
        final var newAutoRenewAccount = "newAutoRenewAccount";
        final AtomicReference<Address> tokenMirrorAddr = new AtomicReference<>();
        final AtomicReference<Address> newAutoRenewAliasAddr = new AtomicReference<>();

        return propertyPreservingHapiSpec("newAutoRenewAccountMustSignUpdate")
                .preserving(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overridingTwo(
                                CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
                                "ContractCall,CryptoTransfer,TokenCreate,TokenUpdate",
                                CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
                                CONTRACTS_V1_SECURITY_MODEL_BLOCK_CUTOFF),
                        newKeyNamed(adminKey),
                        newKeyNamed(narKey).shape(SECP256K1),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(newAutoRenewAccount)
                                .maxAutomaticTokenAssociations(2)
                                .key(narKey)
                                .exposingCreatedIdTo(id -> newAutoRenewAliasAddr.set(idAsHeadlongAddress(id))),
                        cryptoCreate(CIVILIAN).balance(10L * ONE_HUNDRED_HBARS),
                        uploadInitCode(MINIMAL_CREATIONS_CONTRACT),
                        contractCreate(MINIMAL_CREATIONS_CONTRACT).gas(GAS_TO_OFFER),
                        tokenCreate(ft)
                                .autoRenewAccount(TOKEN_TREASURY)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS - 3600L)
                                .adminKey(CIVILIAN)
                                .treasury(TOKEN_TREASURY)
                                .exposingCreatedIdTo(idLit -> tokenMirrorAddr.set(idAsHeadlongAddress(asToken(idLit)))))
                .when(sourcing(() -> contractCall(
                                MINIMAL_CREATIONS_CONTRACT,
                                "updateTokenWithNewAutoRenewInfo",
                                tokenMirrorAddr.get(),
                                newAutoRenewAliasAddr.get(),
                                THREE_MONTHS_IN_SECONDS + 3600)
                        .via(updateTxn)
                        .gas(10L * GAS_TO_OFFER)
                        .sending(DEFAULT_AMOUNT_TO_SEND)
                        .payingWith(CIVILIAN)
                        .refusingEthConversion()
                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)))
                .then(
                        childRecordsCheck(
                                updateTxn,
                                CONTRACT_REVERT_EXECUTED,
                                TransactionRecordAsserts.recordWith()
                                        .status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        // Auto-renew account is unchanged
                        getTokenInfo(ft).hasAutoRenewAccount(TOKEN_TREASURY));
    }

    private static HapiCryptoCreate cryptoCreateWithExposingId(
            String accountName, String keyName, AtomicReference<Address> addressReference) {
        return cryptoCreate(accountName)
                .key(keyName)
                .exposingCreatedIdTo(id -> addressReference.set(idAsHeadlongAddress(id)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
