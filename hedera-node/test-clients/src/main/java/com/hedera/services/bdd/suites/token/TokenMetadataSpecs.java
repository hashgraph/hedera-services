/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.token;

import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AutoAssocAsserts.accountTokenPairsInAnyOrder;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_TOKEN_NAMES;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.METADATA_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

/**
 * Validates the {@code TokenCreate} and {@code TokenUpdate} transactions, specifically its:
 * <ul>
 *     <li>Metadata and MetadataKey values and behaviours.</li>
 * </ul>
 */
@HapiTestSuite
@Tag(TOKEN)
public class TokenMetadataSpecs extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TokenMetadataSpecs.class);
    private static final String PRIMARY = "primary";
    private static final String NON_FUNGIBLE_UNIQUE_FINITE = "non-fungible-unique-finite";
    private static final String AUTO_RENEW_ACCOUNT = "autoRenewAccount";
    private static final String ADMIN_KEY = "adminKey";
    private static final String SUPPLY_KEY = "supplyKey";
    private static final String CREATE_TXN = "createTxn";
    private static final String PAYER = "payer";
    private static final String METADATA_KEY = "metadataKey";
    private static final String PAUSE_KEY = "pauseKey";
    private static final String FREEZE_KEY = "freezeKey";
    private static final String KYC_KEY = "kycKey";
    private static final String FEE_SCHEDULE_KEY = "feeScheduleKey";

    private static String TOKEN_TREASURY = "treasury";

    public static void main(String... args) {
        new TokenMetadataSpecs().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                rejectsMetadataTooLong(),
                creationRequiresAppropriateSigsHappyPath(),
                creationDoesNotHaveRequiredSigs(),
                fungibleCreationHappyPath());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @HapiTest
    public HapiSpec rejectsMetadataTooLong() {
        String metadataStringTooLong = TxnUtils.nAscii(101);
        return defaultHapiSpec("validatesMetadataLength")
                .given()
                .when()
                .then(tokenCreate(PRIMARY).metaData(metadataStringTooLong).hasPrecheck(METADATA_TOO_LONG));
    }

    @HapiTest
    public HapiSpec creationDoesNotHaveRequiredSigs() {
        return defaultHapiSpec("CreationRequiresAppropriateSigs")
                .given(
                        cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        newKeyNamed(ADMIN_KEY))
                .when()
                .then(
                        tokenCreate("shouldntWork")
                                .treasury(TOKEN_TREASURY)
                                .payingWith(PAYER)
                                .adminKey(ADMIN_KEY)
                                .signedBy(PAYER)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        /* treasury must sign */
                        tokenCreate("shouldntWorkEither")
                                .treasury(TOKEN_TREASURY)
                                .payingWith(PAYER)
                                .adminKey(ADMIN_KEY)
                                .signedBy(PAYER, ADMIN_KEY)
                                .hasKnownStatus(INVALID_SIGNATURE));
    }

    @HapiTest
    public HapiSpec creationRequiresAppropriateSigsHappyPath() {
        return defaultHapiSpec("CreationRequiresAppropriateSigsHappyPath", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(cryptoCreate(PAYER), cryptoCreate(TOKEN_TREASURY).balance(0L), newKeyNamed(ADMIN_KEY))
                .when()
                .then(tokenCreate("shouldWork")
                        .treasury(TOKEN_TREASURY)
                        .payingWith(PAYER)
                        .adminKey(ADMIN_KEY)
                        .signedBy(TOKEN_TREASURY, PAYER, ADMIN_KEY)
                        .hasKnownStatus(SUCCESS));
    }

    @HapiTest
    public HapiSpec fungibleCreationHappyPath() {
        String memo = "JUMP";
        String metadata = "metadata";
        String saltedName = salted(PRIMARY);

        return defaultHapiSpec("FungibleCreationHappyPath", NONDETERMINISTIC_TOKEN_NAMES)
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(METADATA_KEY),
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(KYC_KEY))
                .when(tokenCreate(PRIMARY)
                        .supplyType(TokenSupplyType.FINITE)
                        .entityMemo(memo)
                        .name(saltedName)
                        .treasury(TOKEN_TREASURY)
                        .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                        .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                        .maxSupply(1000)
                        .initialSupply(500)
                        .decimals(1)
                        .adminKey(ADMIN_KEY)
                        .supplyKey(SUPPLY_KEY)
                        .metadataKey(METADATA_KEY)
                        .kycKey(KYC_KEY)
                        .freezeKey(FREEZE_KEY)
                        .metaData(metadata)
                        .via(CREATE_TXN))
                .then(
                        withOpContext((spec, opLog) -> {
                            var createTxn = getTxnRecord(CREATE_TXN);
                            allRunFor(spec, createTxn);
                            var timestamp = createTxn
                                    .getResponseRecord()
                                    .getConsensusTimestamp()
                                    .getSeconds();
                            spec.registry().saveExpiry(PRIMARY, timestamp + THREE_MONTHS_IN_SECONDS);
                        }),
                        getTokenInfo(PRIMARY)
                                .logged()
                                .hasRegisteredId(PRIMARY)
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSupplyType(TokenSupplyType.FINITE)
                                .hasEntityMemo(memo)
                                .hasName(saltedName)
                                .hasTreasury(TOKEN_TREASURY)
                                .hasAutoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .hasValidExpiry()
                                .hasDecimals(1)
                                .hasAdminKey(PRIMARY)
                                .hasSupplyKey(PRIMARY)
                                .hasMetadataKey(PRIMARY)
                                .hasMetadata(metadata)
                                .hasMaxSupply(1000)
                                .hasTotalSupply(500)
                                .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT),
                        getAccountInfo(TOKEN_TREASURY)
                                .hasToken(relationshipWith(PRIMARY)
                                        .balance(500)
                                        .kyc(TokenKycStatus.Granted)
                                        .freeze(TokenFreezeStatus.Unfrozen)));
    }

    @HapiTest
    public HapiSpec nonFungibleCreationHappyPath() {
        String metadata = "metadata";
        return defaultHapiSpec("NonFungibleCreationHappyPath", NONDETERMINISTIC_TOKEN_NAMES)
                .given(
                        cryptoCreate(TOKEN_TREASURY).balance(0L),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).balance(0L),
                        newKeyNamed(ADMIN_KEY),
                        newKeyNamed(SUPPLY_KEY),
                        newKeyNamed(METADATA_KEY),
                        newKeyNamed(KYC_KEY))
                .when(
                        tokenCreate(NON_FUNGIBLE_UNIQUE_FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyType(TokenSupplyType.FINITE)
                                .initialSupply(0)
                                .maxSupply(100)
                                .treasury(TOKEN_TREASURY)
                                .supplyKey(GENESIS)
                                .metadataKey(METADATA_KEY)
                                .kycKey(KYC_KEY)
                                .metaData(metadata)
                                .via(CREATE_TXN),
                        getTxnRecord(CREATE_TXN)
                                .logged()
                                .hasPriority(recordWith()
                                        .autoAssociated(accountTokenPairsInAnyOrder(
                                                List.of(Pair.of(TOKEN_TREASURY, NON_FUNGIBLE_UNIQUE_FINITE))))))
                .then(
                        withOpContext((spec, opLog) -> {
                            var createTxn = getTxnRecord(CREATE_TXN);
                            allRunFor(spec, createTxn);
                            var timestamp = createTxn
                                    .getResponseRecord()
                                    .getConsensusTimestamp()
                                    .getSeconds();
                            spec.registry().saveExpiry(NON_FUNGIBLE_UNIQUE_FINITE, timestamp + THREE_MONTHS_IN_SECONDS);
                        }),
                        getTokenInfo(NON_FUNGIBLE_UNIQUE_FINITE)
                                .logged()
                                .hasRegisteredId(NON_FUNGIBLE_UNIQUE_FINITE)
                                .hasTokenType(NON_FUNGIBLE_UNIQUE)
                                .hasSupplyType(TokenSupplyType.FINITE)
                                .hasTotalSupply(0)
                                .hasMaxSupply(100),
                        getAccountInfo(TOKEN_TREASURY)
                                .hasToken(relationshipWith(NON_FUNGIBLE_UNIQUE_FINITE)
                                        .balance(0)
                                        .kyc(TokenKycStatus.Granted)));
    }
}
