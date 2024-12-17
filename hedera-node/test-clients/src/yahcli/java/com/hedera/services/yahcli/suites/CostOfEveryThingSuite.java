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

package com.hedera.services.yahcli.suites;

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleDelete;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.getTransactionFee;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

public class CostOfEveryThingSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CostOfEveryThingSuite.class);
    private static final String COST_SNAPSHOT_MODE = "cost.snapshot.mode";
    private static final String PAYER = "payer";
    private static final String AUTO_RENEW_ACCOUNT = "autoRenewAccount";
    private static final String PAYING_SENDER = "payingSender";
    private static final String RECEIVER = "receiver";
    private static final String CANONICAL_SCHEDULE = "canonicalSchedule";
    private static final String ADMIN_KEY = "adminKey";
    private static final String MEMORABLE = "memorable";
    private static final String TEST_TOPIC = "testTopic";
    private static final String TEST_ACCOUNT_A = "testAccountA";
    private static final String TEST_TOKEN = "testToken";
    private static final String CANONICAL_ACCOUNT = "canonicalAccount";

    private final Map<String, String> specConfig;
    private final EnumSet<Utils.ServiceType> ServiceTypes;
    private StringBuilder feeTableBuilder;
    private String serviceBorder;
    private String contract = "CreateTrivial";

    public CostOfEveryThingSuite(
            final Map<String, String> specConfig,
            final StringBuilder feeTableBuilder,
            final String serviceBorder,
            final String[] services) {
        this.specConfig = specConfig;
        this.feeTableBuilder = feeTableBuilder;
        this.serviceBorder = serviceBorder;
        this.ServiceTypes = Utils.rationalizedServices(services);
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<Stream<DynamicTest>> getSpecsInSuite() {
        return Stream.of(
                        Optional.ofNullable(
                                ServiceTypes.contains(Utils.ServiceType.CRYPTO) ? canonicalCryptoOps() : null),
                        Optional.ofNullable(
                                ServiceTypes.contains(Utils.ServiceType.CONSENSUS) ? canonicalTopicOps() : null),
                        Optional.ofNullable(
                                ServiceTypes.contains(Utils.ServiceType.TOKEN) ? canonicalTokenOps() : null),
                        Optional.ofNullable(ServiceTypes.contains(Utils.ServiceType.FILE) ? canonicalFileOps() : null),
                        Optional.ofNullable(
                                ServiceTypes.contains(Utils.ServiceType.CONTRACT) ? canonicalContractOps() : null),
                        Optional.ofNullable(
                                ServiceTypes.contains(Utils.ServiceType.SCHEDULED) ? canonicalScheduleOps() : null))
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }

    final Stream<DynamicTest> canonicalContractOps() {
        return customHapiSpec("canonicalContractOps")
                .withProperties(specConfig)
                .given(
                        UtilVerbs.newKeyNamed("key").shape(KeyShape.SIMPLE),
                        TxnVerbs.cryptoCreate(PAYER).key("key").balance(10_000_000_000L),
                        TxnVerbs.fileCreate("contractFile")
                                .payingWith(PAYER)
                                .fromResource("contract/contracts/CreateTrivial/CreateTrivial.bin"))
                .when(
                        TxnVerbs.uploadInitCode(contract),
                        TxnVerbs.contractCreate(contract)
                                .blankMemo()
                                .entityMemo("")
                                .bytecode("contractFile")
                                .adminKey("key")
                                .autoRenewSecs(HapiSuite.THREE_MONTHS_IN_SECONDS - 1)
                                .gas(30000)
                                .payingWith(PAYER)
                                .hasKnownStatus(SUCCESS)
                                .via("canonicalContractCreate"),
                        TxnVerbs.contractUpdate(contract)
                                .newMemo("")
                                .blankMemo()
                                .payingWith(PAYER)
                                .newKey("key")
                                .newExpirySecs(HapiSuite.THREE_MONTHS_IN_SECONDS)
                                .via("canonicalContractUpdate"),
                        TxnVerbs.contractCall(contract, "create")
                                .blankMemo()
                                .payingWith(PAYER)
                                .gas(100000)
                                .via("canonicalContractCall"),
                        QueryVerbs.getContractInfo(contract).payingWith(PAYER).via("canonicalGetContractInfo"),
                        QueryVerbs.contractCallLocal(contract, "getIndirect")
                                .payingWith(PAYER)
                                .nodePayment(100_000_000)
                                .gas(50000)
                                .via("canonicalContractCallLocal"),
                        QueryVerbs.getContractBytecode(contract)
                                .payingWith(PAYER)
                                .via("canonicalGetContractByteCode"),
                        TxnVerbs.contractDelete(contract)
                                .blankMemo()
                                .payingWith(PAYER)
                                .via("canonicalContractDelete"))
                .then(
                        withOpContext((spec, log) -> appendServiceName("Smart Contract Service")),
                        getTransactionFee("canonicalContractCreate", feeTableBuilder, "contractCreate"),
                        getTransactionFee("canonicalContractUpdate", feeTableBuilder, "contractUpdate"),
                        getTransactionFee("canonicalContractCall", feeTableBuilder, "contractCall"),
                        getTransactionFee("canonicalGetContractInfo", feeTableBuilder, "getContractInfo"),
                        getTransactionFee("canonicalContractCallLocal", feeTableBuilder, "contractCallLocal"),
                        getTransactionFee("canonicalGetContractByteCode", feeTableBuilder, "getContractByteCode"),
                        getTransactionFee("canonicalContractDelete", feeTableBuilder, "contractDelete"));
    }

    final Stream<DynamicTest> canonicalFileOps() {
        int fileSize = 1000;
        final byte[] first = TxnUtils.randomUtf8Bytes(fileSize);
        final byte[] next = TxnUtils.randomUtf8Bytes(fileSize);

        return customHapiSpec("canonicalFileOps")
                .withProperties(specConfig)
                .given(
                        UtilVerbs.newKeyNamed("key").shape(KeyShape.SIMPLE),
                        TxnVerbs.cryptoCreate(PAYER).key("key").balance(1_000_000_000L),
                        UtilVerbs.newKeyListNamed("WACL", List.of(PAYER)),
                        TxnVerbs.fileCreate(MEMORABLE)
                                .blankMemo()
                                .entityMemo("")
                                .payingWith(PAYER)
                                .key("WACL")
                                .expiry(Instant.now().getEpochSecond() + HapiSuite.THREE_MONTHS_IN_SECONDS - 1)
                                .contents(first)
                                .via("canonicalFileCreate"))
                .when(
                        TxnVerbs.fileAppend(MEMORABLE)
                                .blankMemo()
                                .payingWith(PAYER)
                                .content(next)
                                .via("canonicalFileAppend"),
                        TxnVerbs.fileUpdate(MEMORABLE)
                                .blankMemo()
                                .entityMemo("")
                                .payingWith(PAYER)
                                .contents(next)
                                .extendingExpiryBy(1L)
                                .via("canonicalFileUpdate"),
                        QueryVerbs.getFileContents(MEMORABLE).via("canonicalGetFileContents"),
                        QueryVerbs.getFileInfo(MEMORABLE).via("canonicalGetFileInfo"),
                        TxnVerbs.fileDelete(MEMORABLE)
                                .blankMemo()
                                .payingWith(PAYER)
                                .via("canonicalFileDelete"))
                .then(
                        withOpContext((spec, log) -> appendServiceName("File Service")),
                        getTransactionFee("canonicalFileCreate", feeTableBuilder, "fileCreate"),
                        getTransactionFee("canonicalFileAppend", feeTableBuilder, "fileAppend"),
                        getTransactionFee("canonicalFileUpdate", feeTableBuilder, "fileUpdate"),
                        getTransactionFee("canonicalGetFileContents", feeTableBuilder, "getFileContents"),
                        getTransactionFee("canonicalGetFileInfo", feeTableBuilder, "getFileInfo"),
                        getTransactionFee("canonicalFileDelete", feeTableBuilder, "fileDelete"));
    }

    final Stream<DynamicTest> canonicalTopicOps() {
        return customHapiSpec("canonicalTopicOps")
                .withProperties(specConfig)
                .given(
                        UtilVerbs.newKeyNamed("key").shape(KeyShape.SIMPLE),
                        TxnVerbs.cryptoCreate(PAYER).key("key").balance(100_000_000L))
                .when(
                        TxnVerbs.createTopic(TEST_TOPIC)
                                .blankMemo()
                                .topicMemo("")
                                .autoRenewPeriod(HapiSuite.THREE_MONTHS_IN_SECONDS - 1)
                                .adminKeyName(PAYER)
                                .payingWith(PAYER)
                                .via("canonicalTopicCreate"),
                        TxnVerbs.updateTopic(TEST_TOPIC)
                                .blankMemo()
                                .topicMemo("")
                                .payingWith(PAYER)
                                .autoRenewPeriod(HapiSuite.THREE_MONTHS_IN_SECONDS)
                                .via("canonicalTopicUpdate"),
                        TxnVerbs.submitMessageTo(TEST_TOPIC)
                                .message("testMessage")
                                .payingWith(PAYER)
                                .hasKnownStatus(SUCCESS)
                                .via("canonicalSubmitMessage"),
                        QueryVerbs.getTopicInfo(TEST_TOPIC).payingWith(PAYER).via("canonicalGetTopicInfo"),
                        TxnVerbs.deleteTopic(TEST_TOPIC).payingWith(PAYER).via("canonicalTopicDelete"))
                .then(
                        withOpContext((spec, log) -> appendServiceName("Consensus Service")),
                        getTransactionFee("canonicalTopicCreate", feeTableBuilder, "consensusCreateTopic"),
                        getTransactionFee("canonicalTopicUpdate", feeTableBuilder, "consensusUpdateTopic"),
                        getTransactionFee("canonicalSubmitMessage", feeTableBuilder, "consensusSubmitMessage"),
                        getTransactionFee("canonicalGetTopicInfo", feeTableBuilder, "consensusGetInfo"),
                        getTransactionFee("canonicalTopicDelete", feeTableBuilder, "consensusDeleteTopic"));
    }

    final Stream<DynamicTest> canonicalTokenOps() {
        return customHapiSpec("canonicalTokenOps")
                .withProperties(specConfig)
                .given(
                        UtilVerbs.newKeyNamed("key").shape(KeyShape.SIMPLE),
                        UtilVerbs.newKeyNamed(ADMIN_KEY).shape(KeyShape.listOf(3)),
                        UtilVerbs.newKeyNamed("freezeKey"),
                        UtilVerbs.newKeyNamed("kycKey"),
                        UtilVerbs.newKeyNamed("supplyKey"),
                        UtilVerbs.newKeyNamed("wipeKey"),
                        TxnVerbs.cryptoCreate(HapiSuite.TOKEN_TREASURY)
                                .key("key")
                                .balance(1_000 * HapiSuite.ONE_HBAR),
                        TxnVerbs.cryptoCreate(AUTO_RENEW_ACCOUNT).key(ADMIN_KEY).balance(0L),
                        TxnVerbs.cryptoCreate(TEST_ACCOUNT_A).key(ADMIN_KEY))
                .when(
                        TxnVerbs.tokenCreate("primary")
                                .entityMemo("")
                                .blankMemo()
                                .name(new String(TxnUtils.randomUtf8Bytes(12), StandardCharsets.UTF_8))
                                .symbol(new String(TxnUtils.randomUtf8Bytes(4), StandardCharsets.UTF_8))
                                .treasury(HapiSuite.TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(HapiSuite.THREE_MONTHS_IN_SECONDS - 1)
                                .adminKey(ADMIN_KEY)
                                .payingWith(HapiSuite.TOKEN_TREASURY)
                                .via("canonicalTokenCreate"),
                        TxnVerbs.tokenUpdate("primary")
                                .entityMemo("")
                                .blankMemo()
                                .payingWith(HapiSuite.TOKEN_TREASURY)
                                .autoRenewPeriod(HapiSuite.THREE_MONTHS_IN_SECONDS)
                                .via("canonicalTokenUpdate"),
                        TxnVerbs.tokenCreate(TEST_TOKEN)
                                .entityMemo("")
                                .name("testCoin")
                                .treasury(HapiSuite.TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(HapiSuite.THREE_MONTHS_IN_SECONDS)
                                .initialSupply(500)
                                .decimals(1)
                                .adminKey(ADMIN_KEY)
                                .freezeKey("freezeKey")
                                .kycKey("kycKey")
                                .supplyKey("supplyKey")
                                .wipeKey("wipeKey"),
                        TxnVerbs.mintToken(TEST_TOKEN, 1)
                                .blankMemo()
                                .payingWith(HapiSuite.TOKEN_TREASURY)
                                .via("cannonicalMintToken"),
                        TxnVerbs.burnToken(TEST_TOKEN, 1)
                                .blankMemo()
                                .payingWith(HapiSuite.TOKEN_TREASURY)
                                .via("canonicalBurnToken"),
                        TxnVerbs.tokenAssociate(TEST_ACCOUNT_A, TEST_TOKEN)
                                .blankMemo()
                                .payingWith(HapiSuite.TOKEN_TREASURY)
                                .via("canonicalTokenAssociation"),
                        TxnVerbs.revokeTokenKyc(TEST_TOKEN, TEST_ACCOUNT_A)
                                .blankMemo()
                                .payingWith(HapiSuite.TOKEN_TREASURY)
                                .via("canonicalTokenRevokeKyc"),
                        TxnVerbs.grantTokenKyc(TEST_TOKEN, TEST_ACCOUNT_A)
                                .blankMemo()
                                .payingWith(HapiSuite.TOKEN_TREASURY)
                                .via("canonicalTokenGrantKyc"),
                        TxnVerbs.tokenFreeze(TEST_TOKEN, TEST_ACCOUNT_A)
                                .blankMemo()
                                .payingWith(HapiSuite.TOKEN_TREASURY)
                                .via("canonicalTokenFreeze"),
                        TxnVerbs.tokenUnfreeze(TEST_TOKEN, TEST_ACCOUNT_A)
                                .blankMemo()
                                .payingWith(HapiSuite.TOKEN_TREASURY)
                                .via("canonicalTokenUnFreeze"),
                        TxnVerbs.cryptoTransfer(TokenMovement.moving(1, TEST_TOKEN)
                                        .between(HapiSuite.TOKEN_TREASURY, TEST_ACCOUNT_A))
                                .blankMemo()
                                .payingWith(HapiSuite.TOKEN_TREASURY)
                                .via("canonicalTokenTransfer"),
                        TxnVerbs.wipeTokenAccount(TEST_TOKEN, TEST_ACCOUNT_A, 1)
                                .payingWith(HapiSuite.TOKEN_TREASURY)
                                .blankMemo()
                                .via("canonicalTokenWipe"),
                        TxnVerbs.tokenDissociate(TEST_ACCOUNT_A, TEST_TOKEN)
                                .blankMemo()
                                .payingWith(HapiSuite.TOKEN_TREASURY)
                                .via("canonicalTokenDissociation"),
                        QueryVerbs.getTokenInfo(TEST_TOKEN)
                                .payingWith(HapiSuite.TOKEN_TREASURY)
                                .via("canonicalTokenGetInfo"),
                        TxnVerbs.tokenDelete(TEST_TOKEN)
                                .blankMemo()
                                .payingWith(HapiSuite.TOKEN_TREASURY)
                                .via("canonicalTokenDelete"))
                .then(
                        withOpContext((spec, log) -> appendServiceName("Token Service")),
                        getTransactionFee("canonicalTokenCreate", feeTableBuilder, "tokenCreate"),
                        getTransactionFee("canonicalTokenUpdate", feeTableBuilder, "tokenUpdate"),
                        getTransactionFee("cannonicalMintToken", feeTableBuilder, "tokenMintSingle"),
                        getTransactionFee("canonicalBurnToken", feeTableBuilder, "tokenBurnSingle"),
                        getTransactionFee("canonicalTokenAssociation", feeTableBuilder, "tokenAssociate"),
                        getTransactionFee("canonicalTokenGrantKyc", feeTableBuilder, "tokenGrantKyc"),
                        getTransactionFee("canonicalTokenRevokeKyc", feeTableBuilder, "tokenRevokeKyc"),
                        getTransactionFee("canonicalTokenFreeze", feeTableBuilder, "tokenFreeze"),
                        getTransactionFee("canonicalTokenUnFreeze", feeTableBuilder, "tokenUnFreeze"),
                        getTransactionFee("canonicalTokenTransfer", feeTableBuilder, "tokenTransfer"),
                        getTransactionFee("canonicalTokenWipe", feeTableBuilder, "tokenWipe"),
                        getTransactionFee("canonicalTokenDissociation", feeTableBuilder, "tokenDissociate"),
                        getTransactionFee("canonicalTokenGetInfo", feeTableBuilder, "getTokenInfo"),
                        getTransactionFee("canonicalTokenDelete", feeTableBuilder, "tokenDelete"));
    }

    final Stream<DynamicTest> canonicalCryptoOps() {

        return customHapiSpec("canonicalCryptoOps")
                .withProperties(specConfig)
                .given(
                        UtilVerbs.newKeyNamed("key").shape(KeyShape.SIMPLE),
                        TxnVerbs.cryptoCreate(PAYER).key("key").balance(1_000 * HapiSuite.ONE_HBAR))
                .when(
                        TxnVerbs.cryptoCreate(CANONICAL_ACCOUNT)
                                .key("key")
                                .blankMemo()
                                .balance(100 * HapiSuite.ONE_HBAR)
                                .entityMemo("")
                                .autoRenewSecs(HapiSuite.THREE_MONTHS_IN_SECONDS)
                                .payingWith(PAYER)
                                .via("canonicalCryptoCreation"),
                        TxnVerbs.cryptoUpdate(CANONICAL_ACCOUNT)
                                .payingWith(CANONICAL_ACCOUNT)
                                .blankMemo()
                                .expiring(Instant.now().getEpochSecond() + HapiSuite.THREE_MONTHS_IN_SECONDS)
                                .entityMemo("")
                                .via("canonicalCryptoUpdate"),
                        TxnVerbs.cryptoCreate("testAccount").key("key"),
                        TxnVerbs.cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(CANONICAL_ACCOUNT, "testAccount", 1L))
                                .payingWith(CANONICAL_ACCOUNT)
                                .blankMemo()
                                .via("canonicalCryptoTransfer"),
                        QueryVerbs.getAccountRecords(CANONICAL_ACCOUNT).via("canonicalGetRecords"),
                        QueryVerbs.getAccountInfo(CANONICAL_ACCOUNT).via("canonicalGetAccountInfo"),
                        TxnVerbs.cryptoCreate("canonicalAccountTBD")
                                .blankMemo()
                                .entityMemo("")
                                .payingWith(PAYER),
                        TxnVerbs.cryptoDelete("canonicalAccountTBD")
                                .blankMemo()
                                .payingWith(PAYER)
                                .via("canonicalCryptoDeletion"))
                .then(
                        withOpContext((spec, log) -> appendServiceName("Cryptocurrency Service")),
                        getTransactionFee("canonicalCryptoCreation", feeTableBuilder, "cryptoCreate"),
                        getTransactionFee("canonicalCryptoUpdate", feeTableBuilder, "cryptoUpdate"),
                        getTransactionFee("canonicalCryptoTransfer", feeTableBuilder, "cryptoTransfer"),
                        getTransactionFee("canonicalGetRecords", feeTableBuilder, "cryptoGetAccountRecords"),
                        getTransactionFee("canonicalGetAccountInfo", feeTableBuilder, "cryptoGetAccountInfo"),
                        getTransactionFee("canonicalCryptoDeletion", feeTableBuilder, "cryptoDelete"));
    }

    final Stream<DynamicTest> canonicalScheduleOps() {
        return customHapiSpec("canonicalScheduleOps")
                .withProperties(specConfig)
                .given(
                        TxnVerbs.cryptoCreate(PAYING_SENDER).balance(HapiSuite.ONE_HUNDRED_HBARS),
                        TxnVerbs.cryptoCreate(RECEIVER).balance(0L).receiverSigRequired(true))
                .when(
                        TxnVerbs.scheduleCreate(
                                        CANONICAL_SCHEDULE,
                                        TxnVerbs.cryptoTransfer(
                                                        HapiCryptoTransfer.tinyBarsFromTo(PAYING_SENDER, RECEIVER, 1L))
                                                .memo("")
                                                .fee(HapiSuite.ONE_HBAR))
                                .via("canonicalScheduleCreation")
                                .payingWith(PAYING_SENDER)
                                .adminKey(PAYING_SENDER),
                        QueryVerbs.getScheduleInfo(CANONICAL_SCHEDULE).payingWith(PAYING_SENDER),
                        TxnVerbs.scheduleSign(CANONICAL_SCHEDULE)
                                .via("canonicalScheduleSigning")
                                .payingWith(PAYING_SENDER)
                                .alsoSigningWith(RECEIVER),
                        TxnVerbs.scheduleCreate(
                                        "tbd",
                                        TxnVerbs.cryptoTransfer(
                                                        HapiCryptoTransfer.tinyBarsFromTo(PAYING_SENDER, RECEIVER, 1L))
                                                .memo("")
                                                .fee(HapiSuite.ONE_HBAR)
                                                .signedBy(PAYING_SENDER))
                                .payingWith(PAYING_SENDER)
                                .adminKey(PAYING_SENDER),
                        scheduleDelete("tbd").via("canonicalScheduleDeletion").payingWith(PAYING_SENDER))
                .then(
                        withOpContext((spec, log) -> appendServiceName("Schedule Transaction Service")),
                        getTransactionFee("canonicalScheduleCreation", feeTableBuilder, "scheduleCreate"),
                        getTransactionFee("canonicalScheduleSigning", feeTableBuilder, "scheduleSign"),
                        getTransactionFee("canonicalScheduleDeletion", feeTableBuilder, "scheduleDelete"));
    }

    private void appendServiceName(final String serviceName) {
        feeTableBuilder.append(serviceBorder);
        feeTableBuilder.append(String.format("%30s | Fees \t\t |%n", serviceName));
        feeTableBuilder.append(serviceBorder);
    }
}
