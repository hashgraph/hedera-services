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

package com.hedera.services.yahcli.suites;

import static com.hedera.services.bdd.spec.HapiSpec.CostSnapshotMode.TAKE;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountRecords;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.deleteTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.getTransactionFee;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    HapiSpec.CostSnapshotMode costSnapshotMode = TAKE;
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
    public List<HapiSpec> getSpecsInSuite() {
        return Stream.of(
                        ofNullable(ServiceTypes.contains(Utils.ServiceType.CRYPTO) ? canonicalCryptoOps() : null),
                        ofNullable(ServiceTypes.contains(Utils.ServiceType.CONSENSUS) ? canonicalTopicOps() : null),
                        ofNullable(ServiceTypes.contains(Utils.ServiceType.TOKEN) ? canonicalTokenOps() : null),
                        ofNullable(ServiceTypes.contains(Utils.ServiceType.FILE) ? canonicalFileOps() : null),
                        ofNullable(ServiceTypes.contains(Utils.ServiceType.CONTRACT) ? canonicalContractOps() : null),
                        ofNullable(ServiceTypes.contains(Utils.ServiceType.SCHEDULED) ? canonicalScheduleOps() : null))
                .flatMap(Optional::stream)
                .collect(toList());
    }

    HapiSpec canonicalContractOps() {
        return HapiSpec.customHapiSpec(String.format("canonicalContractOps"))
                .withProperties(specConfig, Map.of(COST_SNAPSHOT_MODE, costSnapshotMode.toString()))
                .given(
                        newKeyNamed("key").shape(SIMPLE),
                        cryptoCreate(PAYER).key("key").balance(10_000_000_000L),
                        fileCreate("contractFile")
                                .payingWith(PAYER)
                                .fromResource("contract/contracts/CreateTrivial/CreateTrivial.bin"))
                .when(
                        uploadInitCode(contract),
                        contractCreate(contract)
                                .blankMemo()
                                .entityMemo("")
                                .bytecode("contractFile")
                                .adminKey("key")
                                .autoRenewSecs(THREE_MONTHS_IN_SECONDS - 1)
                                .gas(30000)
                                .payingWith(PAYER)
                                .hasKnownStatus(SUCCESS)
                                .via("canonicalContractCreate"),
                        contractUpdate(contract)
                                .newMemo("")
                                .blankMemo()
                                .payingWith(PAYER)
                                .newKey("key")
                                .newExpirySecs(THREE_MONTHS_IN_SECONDS)
                                .via("canonicalContractUpdate"),
                        contractCall(contract, "create")
                                .blankMemo()
                                .payingWith(PAYER)
                                .gas(100000)
                                .via("canonicalContractCall"),
                        getContractInfo(contract).payingWith(PAYER).via("canonicalGetContractInfo"),
                        contractCallLocal(contract, "getIndirect")
                                .payingWith(PAYER)
                                .nodePayment(100_000_000)
                                .gas(50000)
                                .via("canonicalContractCallLocal"),
                        getContractBytecode(contract).payingWith(PAYER).via("canonicalGetContractByteCode"),
                        contractDelete(contract).blankMemo().payingWith(PAYER).via("canonicalContractDelete"))
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

    HapiSpec canonicalFileOps() {
        int fileSize = 1000;
        final byte[] first = randomUtf8Bytes(fileSize);
        final byte[] next = randomUtf8Bytes(fileSize);

        return HapiSpec.customHapiSpec(String.format("canonicalFileOps"))
                .withProperties(specConfig, Map.of(COST_SNAPSHOT_MODE, costSnapshotMode.toString()))
                .given(
                        newKeyNamed("key").shape(SIMPLE),
                        cryptoCreate(PAYER).key("key").balance(1_000_000_000L),
                        newKeyListNamed("WACL", List.of(PAYER)),
                        fileCreate(MEMORABLE)
                                .blankMemo()
                                .entityMemo("")
                                .payingWith(PAYER)
                                .key("WACL")
                                .expiry(Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS - 1)
                                .contents(first)
                                .via("canonicalFileCreate"))
                .when(
                        fileAppend(MEMORABLE)
                                .blankMemo()
                                .payingWith(PAYER)
                                .content(next)
                                .via("canonicalFileAppend"),
                        fileUpdate(MEMORABLE)
                                .blankMemo()
                                .entityMemo("")
                                .payingWith(PAYER)
                                .contents(next)
                                .extendingExpiryBy(1L)
                                .via("canonicalFileUpdate"),
                        getFileContents(MEMORABLE).via("canonicalGetFileContents"),
                        getFileInfo(MEMORABLE).via("canonicalGetFileInfo"),
                        fileDelete(MEMORABLE).blankMemo().payingWith(PAYER).via("canonicalFileDelete"))
                .then(
                        withOpContext((spec, log) -> appendServiceName("File Service")),
                        getTransactionFee("canonicalFileCreate", feeTableBuilder, "fileCreate"),
                        getTransactionFee("canonicalFileAppend", feeTableBuilder, "fileAppend"),
                        getTransactionFee("canonicalFileUpdate", feeTableBuilder, "fileUpdate"),
                        getTransactionFee("canonicalGetFileContents", feeTableBuilder, "getFileContents"),
                        getTransactionFee("canonicalGetFileInfo", feeTableBuilder, "getFileInfo"),
                        getTransactionFee("canonicalFileDelete", feeTableBuilder, "fileDelete"));
    }

    HapiSpec canonicalTopicOps() {
        return HapiSpec.customHapiSpec(String.format("canonicalTopicOps"))
                .withProperties(specConfig, Map.of(COST_SNAPSHOT_MODE, costSnapshotMode.toString()))
                .given(
                        newKeyNamed("key").shape(SIMPLE),
                        cryptoCreate(PAYER).key("key").balance(100_000_000L))
                .when(
                        createTopic(TEST_TOPIC)
                                .blankMemo()
                                .topicMemo("")
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS - 1)
                                .adminKeyName(PAYER)
                                .payingWith(PAYER)
                                .via("canonicalTopicCreate"),
                        updateTopic(TEST_TOPIC)
                                .blankMemo()
                                .topicMemo("")
                                .payingWith(PAYER)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .via("canonicalTopicUpdate"),
                        submitMessageTo(TEST_TOPIC)
                                .message("testMessage")
                                .payingWith(PAYER)
                                .hasKnownStatus(SUCCESS)
                                .via("canonicalSubmitMessage"),
                        getTopicInfo(TEST_TOPIC).payingWith(PAYER).via("canonicalGetTopicInfo"),
                        deleteTopic(TEST_TOPIC).payingWith(PAYER).via("canonicalTopicDelete"))
                .then(
                        withOpContext((spec, log) -> appendServiceName("Consensus Service")),
                        getTransactionFee("canonicalTopicCreate", feeTableBuilder, "consensusCreateTopic"),
                        getTransactionFee("canonicalTopicUpdate", feeTableBuilder, "consensusUpdateTopic"),
                        getTransactionFee("canonicalSubmitMessage", feeTableBuilder, "consensusSubmitMessage"),
                        getTransactionFee("canonicalGetTopicInfo", feeTableBuilder, "consensusGetInfo"),
                        getTransactionFee("canonicalTopicDelete", feeTableBuilder, "consensusDeleteTopic"));
    }

    HapiSpec canonicalTokenOps() {
        return HapiSpec.customHapiSpec(String.format("canonicalTokenOps"))
                .withProperties(specConfig, Map.of(COST_SNAPSHOT_MODE, costSnapshotMode.toString()))
                .given(
                        newKeyNamed("key").shape(SIMPLE),
                        newKeyNamed(ADMIN_KEY).shape(listOf(3)),
                        newKeyNamed("freezeKey"),
                        newKeyNamed("kycKey"),
                        newKeyNamed("supplyKey"),
                        newKeyNamed("wipeKey"),
                        cryptoCreate(TOKEN_TREASURY).key("key").balance(1_000 * ONE_HBAR),
                        cryptoCreate(AUTO_RENEW_ACCOUNT).key(ADMIN_KEY).balance(0L),
                        cryptoCreate(TEST_ACCOUNT_A).key(ADMIN_KEY))
                .when(
                        tokenCreate("primary")
                                .entityMemo("")
                                .blankMemo()
                                .name(new String(randomUtf8Bytes(12), StandardCharsets.UTF_8))
                                .symbol(new String(randomUtf8Bytes(4), StandardCharsets.UTF_8))
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS - 1)
                                .adminKey(ADMIN_KEY)
                                .payingWith(TOKEN_TREASURY)
                                .via("canonicalTokenCreate"),
                        tokenUpdate("primary")
                                .entityMemo("")
                                .blankMemo()
                                .payingWith(TOKEN_TREASURY)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .via("canonicalTokenUpdate"),
                        tokenCreate(TEST_TOKEN)
                                .entityMemo("")
                                .name("testCoin")
                                .treasury(TOKEN_TREASURY)
                                .autoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                                .initialSupply(500)
                                .decimals(1)
                                .adminKey(ADMIN_KEY)
                                .freezeKey("freezeKey")
                                .kycKey("kycKey")
                                .supplyKey("supplyKey")
                                .wipeKey("wipeKey"),
                        mintToken(TEST_TOKEN, 1)
                                .blankMemo()
                                .payingWith(TOKEN_TREASURY)
                                .via("cannonicalMintToken"),
                        burnToken(TEST_TOKEN, 1)
                                .blankMemo()
                                .payingWith(TOKEN_TREASURY)
                                .via("canonicalBurnToken"),
                        tokenAssociate(TEST_ACCOUNT_A, TEST_TOKEN)
                                .blankMemo()
                                .payingWith(TOKEN_TREASURY)
                                .via("canonicalTokenAssociation"),
                        revokeTokenKyc(TEST_TOKEN, TEST_ACCOUNT_A)
                                .blankMemo()
                                .payingWith(TOKEN_TREASURY)
                                .via("canonicalTokenRevokeKyc"),
                        grantTokenKyc(TEST_TOKEN, TEST_ACCOUNT_A)
                                .blankMemo()
                                .payingWith(TOKEN_TREASURY)
                                .via("canonicalTokenGrantKyc"),
                        tokenFreeze(TEST_TOKEN, TEST_ACCOUNT_A)
                                .blankMemo()
                                .payingWith(TOKEN_TREASURY)
                                .via("canonicalTokenFreeze"),
                        tokenUnfreeze(TEST_TOKEN, TEST_ACCOUNT_A)
                                .blankMemo()
                                .payingWith(TOKEN_TREASURY)
                                .via("canonicalTokenUnFreeze"),
                        cryptoTransfer(moving(1, TEST_TOKEN).between(TOKEN_TREASURY, TEST_ACCOUNT_A))
                                .blankMemo()
                                .payingWith(TOKEN_TREASURY)
                                .via("canonicalTokenTransfer"),
                        wipeTokenAccount(TEST_TOKEN, TEST_ACCOUNT_A, 1)
                                .payingWith(TOKEN_TREASURY)
                                .blankMemo()
                                .via("canonicalTokenWipe"),
                        tokenDissociate(TEST_ACCOUNT_A, TEST_TOKEN)
                                .blankMemo()
                                .payingWith(TOKEN_TREASURY)
                                .via("canonicalTokenDissociation"),
                        getTokenInfo(TEST_TOKEN).payingWith(TOKEN_TREASURY).via("canonicalTokenGetInfo"),
                        tokenDelete(TEST_TOKEN)
                                .blankMemo()
                                .payingWith(TOKEN_TREASURY)
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

    HapiSpec canonicalCryptoOps() {

        return HapiSpec.customHapiSpec(String.format("canonicalCryptoOps"))
                .withProperties(specConfig, Map.of(COST_SNAPSHOT_MODE, costSnapshotMode.toString()))
                .given(
                        newKeyNamed("key").shape(SIMPLE),
                        cryptoCreate(PAYER).key("key").balance(1_000 * ONE_HBAR))
                .when(
                        cryptoCreate(CANONICAL_ACCOUNT)
                                .key("key")
                                .blankMemo()
                                .balance(100 * ONE_HBAR)
                                .entityMemo("")
                                .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                                .payingWith(PAYER)
                                .via("canonicalCryptoCreation"),
                        cryptoUpdate(CANONICAL_ACCOUNT)
                                .payingWith(CANONICAL_ACCOUNT)
                                .blankMemo()
                                .expiring(Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS)
                                .entityMemo("")
                                .via("canonicalCryptoUpdate"),
                        cryptoCreate("testAccount").key("key"),
                        cryptoTransfer(tinyBarsFromTo(CANONICAL_ACCOUNT, "testAccount", 1L))
                                .payingWith(CANONICAL_ACCOUNT)
                                .blankMemo()
                                .via("canonicalCryptoTransfer"),
                        getAccountRecords(CANONICAL_ACCOUNT).via("canonicalGetRecords"),
                        getAccountInfo(CANONICAL_ACCOUNT).via("canonicalGetAccountInfo"),
                        cryptoCreate("canonicalAccountTBD")
                                .blankMemo()
                                .entityMemo("")
                                .payingWith(PAYER),
                        cryptoDelete("canonicalAccountTBD")
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

    HapiSpec canonicalScheduleOps() {
        return HapiSpec.customHapiSpec(String.format("canonicalScheduleOps"))
                .withProperties(specConfig, Map.of(COST_SNAPSHOT_MODE, costSnapshotMode.toString()))
                .given(
                        cryptoCreate(PAYING_SENDER).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(RECEIVER).balance(0L).receiverSigRequired(true))
                .when(
                        scheduleCreate(
                                CANONICAL_SCHEDULE,
                                cryptoTransfer(tinyBarsFromTo(PAYING_SENDER, RECEIVER, 1L))
                                        .memo("")
                                        .fee(ONE_HBAR))
                                .via("canonicalScheduleCreation")
                                .payingWith(PAYING_SENDER)
                                .adminKey(PAYING_SENDER),
                        getScheduleInfo(CANONICAL_SCHEDULE).payingWith(PAYING_SENDER),
                        scheduleSign(CANONICAL_SCHEDULE)
                                .via("canonicalScheduleSigning")
                                .payingWith(PAYING_SENDER)
                                .alsoSigningWith(RECEIVER),
                        scheduleCreate(
                                "tbd",
                                cryptoTransfer(tinyBarsFromTo(PAYING_SENDER, RECEIVER, 1L))
                                        .memo("")
                                        .fee(ONE_HBAR)
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
        feeTableBuilder.append(String.format("%30s | Fees \t\t |\n", serviceName));
        feeTableBuilder.append(serviceBorder);
    }
}
