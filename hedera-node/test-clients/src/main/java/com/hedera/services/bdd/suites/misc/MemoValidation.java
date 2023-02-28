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

package com.hedera.services.bdd.suites.misc;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiSuite;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MemoValidation extends HapiSuite {
    private static final Logger log = LogManager.getLogger(MemoValidation.class);

    private static final char SINGLE_BYTE_CHAR = 'a';
    private static final char MULTI_BYTE_CHAR = 'ф';
    private static final String primary = "primary";
    private static final String secondary = "secondary";

    private static String longMemo;
    private static String validMemoWithMultiByteChars;
    private static String inValidMemoWithMultiByteChars;
    private static String stringOf49Bytes;

    public static void main(String... args) {
        new MemoValidation().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        setUpByteArrays();
        return List.of(
                //				cryptoOps(),
                //				topicOps(),
                //				scheduleOps(),
                //				tokenOps(),
                contractOps());
    }

    private HapiSpec contractOps() {
        final var contract = "CreateTrivial";
        return defaultHapiSpec("MemoValidationsOnContractOps")
                .given(uploadInitCode(contract), contractCreate(contract).omitAdminKey())
                .when(
                        contractCall(contract, "create").memo(longMemo).hasPrecheck(MEMO_TOO_LONG),
                        contractCall(contract, "create").memo(ZERO_BYTE_MEMO).hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
                        contractCall(contract, "create")
                                .memo(inValidMemoWithMultiByteChars)
                                .hasPrecheck(MEMO_TOO_LONG),
                        contractCall(contract, "create")
                                .memo(stringOf49Bytes + SINGLE_BYTE_CHAR + MULTI_BYTE_CHAR + stringOf49Bytes)
                                .hasPrecheck(MEMO_TOO_LONG))
                .then(
                        contractCreate(secondary)
                                .entityMemo(TxnUtils.nAscii(101))
                                .hasPrecheck(MEMO_TOO_LONG),
                        contractCreate(secondary).entityMemo(ZERO_BYTE_MEMO).hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
                        contractCreate(secondary)
                                .entityMemo(inValidMemoWithMultiByteChars)
                                .hasPrecheck(MEMO_TOO_LONG),
                        contractCreate(secondary)
                                .entityMemo(stringOf49Bytes + SINGLE_BYTE_CHAR + MULTI_BYTE_CHAR + stringOf49Bytes)
                                .hasPrecheck(MEMO_TOO_LONG));
    }

    private HapiSpec tokenOps() {
        return defaultHapiSpec("MemoValidationsOnTokenOps")
                .given(
                        cryptoCreate("firstUser"),
                        newKeyNamed("adminKey"),
                        tokenCreate(primary).blankMemo().adminKey("adminKey"))
                .when(
                        tokenUpdate(primary).memo(longMemo).hasPrecheck(MEMO_TOO_LONG),
                        tokenUpdate(primary).entityMemo(ZERO_BYTE_MEMO).hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
                        tokenUpdate(primary)
                                .entityMemo(inValidMemoWithMultiByteChars)
                                .hasPrecheck(MEMO_TOO_LONG),
                        tokenUpdate(primary).entityMemo(stringOf49Bytes + MULTI_BYTE_CHAR + stringOf49Bytes),
                        tokenUpdate(primary)
                                .entityMemo(stringOf49Bytes + SINGLE_BYTE_CHAR + MULTI_BYTE_CHAR + stringOf49Bytes)
                                .hasPrecheck(MEMO_TOO_LONG))
                .then(
                        tokenCreate(secondary).entityMemo(longMemo).hasPrecheck(MEMO_TOO_LONG),
                        tokenCreate(secondary).entityMemo(ZERO_BYTE_MEMO).hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
                        tokenCreate("inValidMemoWithMultiByteChars")
                                .entityMemo(inValidMemoWithMultiByteChars)
                                .hasPrecheck(MEMO_TOO_LONG),
                        tokenCreate("inValidMemoWithMultiByteChars")
                                .entityMemo(stringOf49Bytes + SINGLE_BYTE_CHAR + MULTI_BYTE_CHAR + stringOf49Bytes)
                                .hasPrecheck(MEMO_TOO_LONG),
                        tokenCreate(secondary).entityMemo(stringOf49Bytes + MULTI_BYTE_CHAR + stringOf49Bytes),
                        tokenAssociate("firstUser", primary)
                                .memo(inValidMemoWithMultiByteChars)
                                .hasPrecheck(MEMO_TOO_LONG));
    }

    private HapiSpec scheduleOps() {
        final String defaultWhitelist = HapiSpecSetup.getDefaultNodeProps().get("scheduling.whitelist");
        final var toScheduleOp1 = cryptoCreate("test");
        final var toScheduleOp2 = cryptoCreate("test").balance(1L);
        return defaultHapiSpec("MemoValidationsOnScheduleOps")
                .given(
                        overriding("scheduling.whitelist", "CryptoCreate"),
                        scheduleCreate(primary, toScheduleOp1).blankMemo())
                .when(
                        scheduleSign(primary).memo(longMemo).hasPrecheck(MEMO_TOO_LONG),
                        scheduleSign(primary).memo(ZERO_BYTE_MEMO).hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
                        scheduleSign(primary)
                                .memo(inValidMemoWithMultiByteChars)
                                .hasPrecheck(MEMO_TOO_LONG),
                        scheduleSign(primary)
                                .memo(stringOf49Bytes + SINGLE_BYTE_CHAR + MULTI_BYTE_CHAR + stringOf49Bytes)
                                .hasPrecheck(MEMO_TOO_LONG))
                .then(
                        scheduleCreate(secondary, toScheduleOp2)
                                .withEntityMemo(longMemo)
                                .hasPrecheck(MEMO_TOO_LONG),
                        scheduleCreate(secondary, toScheduleOp2)
                                .withEntityMemo(ZERO_BYTE_MEMO)
                                .hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
                        scheduleCreate("inValidMemoWithMultiByteChars", toScheduleOp2)
                                .withEntityMemo(inValidMemoWithMultiByteChars)
                                .hasPrecheck(MEMO_TOO_LONG),
                        scheduleCreate(secondary, toScheduleOp2).withEntityMemo(validMemoWithMultiByteChars),
                        scheduleCreate("validMemo1", toScheduleOp1.balance(100L))
                                .withEntityMemo(stringOf49Bytes + MULTI_BYTE_CHAR + stringOf49Bytes),
                        scheduleCreate("validMemo2", toScheduleOp2.entityMemo(validMemoWithMultiByteChars))
                                .withEntityMemo(
                                        stringOf49Bytes + SINGLE_BYTE_CHAR + SINGLE_BYTE_CHAR + stringOf49Bytes),
                        scheduleCreate("invalidMemo", toScheduleOp2.balance(200L))
                                .withEntityMemo(stringOf49Bytes + SINGLE_BYTE_CHAR + MULTI_BYTE_CHAR + stringOf49Bytes)
                                .hasPrecheck(MEMO_TOO_LONG),
                        overriding("scheduling.whitelist", defaultWhitelist));
    }

    private HapiSpec topicOps() {
        return defaultHapiSpec("MemoValidationsOnTopicOps")
                .given(
                        newKeyNamed("adminKey"),
                        createTopic(primary).adminKeyName("adminKey").blankMemo())
                .when(
                        updateTopic(primary).topicMemo(longMemo).hasKnownStatus(MEMO_TOO_LONG),
                        updateTopic(primary).topicMemo(ZERO_BYTE_MEMO).hasKnownStatus(INVALID_ZERO_BYTE_IN_STRING),
                        updateTopic(primary)
                                .topicMemo(inValidMemoWithMultiByteChars)
                                .hasKnownStatus(MEMO_TOO_LONG),
                        updateTopic(primary).topicMemo(stringOf49Bytes + MULTI_BYTE_CHAR + stringOf49Bytes),
                        updateTopic(primary)
                                .topicMemo(stringOf49Bytes + SINGLE_BYTE_CHAR + MULTI_BYTE_CHAR + stringOf49Bytes)
                                .hasKnownStatus(MEMO_TOO_LONG))
                .then(
                        createTopic(secondary).topicMemo(longMemo).hasKnownStatus(MEMO_TOO_LONG),
                        createTopic(secondary).topicMemo(ZERO_BYTE_MEMO).hasKnownStatus(INVALID_ZERO_BYTE_IN_STRING),
                        createTopic(secondary).topicMemo(validMemoWithMultiByteChars),
                        createTopic("inValidMemoWithMultiByteChars")
                                .topicMemo(inValidMemoWithMultiByteChars)
                                .hasKnownStatus(MEMO_TOO_LONG),
                        createTopic("validMemo1").topicMemo(stringOf49Bytes + MULTI_BYTE_CHAR + stringOf49Bytes),
                        createTopic("validMemo2")
                                .topicMemo(stringOf49Bytes + SINGLE_BYTE_CHAR + SINGLE_BYTE_CHAR + stringOf49Bytes),
                        createTopic("invalidMemo")
                                .topicMemo(stringOf49Bytes + SINGLE_BYTE_CHAR + MULTI_BYTE_CHAR + stringOf49Bytes)
                                .hasKnownStatus(MEMO_TOO_LONG));
    }

    private HapiSpec cryptoOps() {
        return defaultHapiSpec("MemoValidationsOnCryptoOps")
                .given(cryptoCreate(primary).blankMemo())
                .when(
                        cryptoUpdate(primary).entityMemo(longMemo).hasPrecheck(MEMO_TOO_LONG),
                        cryptoUpdate(primary).entityMemo(ZERO_BYTE_MEMO).hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
                        cryptoUpdate(primary)
                                .entityMemo(inValidMemoWithMultiByteChars)
                                .hasPrecheck(MEMO_TOO_LONG),
                        cryptoUpdate(primary).entityMemo(stringOf49Bytes + MULTI_BYTE_CHAR + stringOf49Bytes),
                        cryptoUpdate(primary)
                                .entityMemo(stringOf49Bytes + SINGLE_BYTE_CHAR + MULTI_BYTE_CHAR + stringOf49Bytes)
                                .hasPrecheck(MEMO_TOO_LONG),
                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, primary, 1000L))
                                .memo(stringOf49Bytes + SINGLE_BYTE_CHAR + MULTI_BYTE_CHAR + stringOf49Bytes)
                                .hasPrecheck(MEMO_TOO_LONG))
                .then(
                        cryptoCreate(secondary).entityMemo(longMemo).hasPrecheck(MEMO_TOO_LONG),
                        cryptoCreate(secondary).entityMemo(ZERO_BYTE_MEMO).hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
                        cryptoCreate(secondary)
                                .entityMemo(inValidMemoWithMultiByteChars)
                                .hasPrecheck(MEMO_TOO_LONG),
                        cryptoCreate(secondary)
                                .entityMemo(stringOf49Bytes + SINGLE_BYTE_CHAR + MULTI_BYTE_CHAR + stringOf49Bytes)
                                .hasPrecheck(MEMO_TOO_LONG),
                        cryptoCreate(secondary).entityMemo(stringOf49Bytes + MULTI_BYTE_CHAR + stringOf49Bytes));
    }

    private void setUpByteArrays() {
        final var LONG_BYTES = new byte[1000];
        final var VALID_BYTES = new byte[100];
        final var INVALID_BYTES = new byte[102];
        final var BYTES_49 = new byte[49];

        Arrays.fill(LONG_BYTES, (byte) 33);
        Arrays.fill(VALID_BYTES, (byte) MULTI_BYTE_CHAR);
        Arrays.fill(INVALID_BYTES, (byte) MULTI_BYTE_CHAR);
        Arrays.fill(BYTES_49, (byte) SINGLE_BYTE_CHAR);
        longMemo = new String(LONG_BYTES, StandardCharsets.UTF_8);
        validMemoWithMultiByteChars = new String(VALID_BYTES, StandardCharsets.UTF_8);
        inValidMemoWithMultiByteChars = new String(INVALID_BYTES, StandardCharsets.UTF_8);
        stringOf49Bytes = new String(BYTES_49, StandardCharsets.UTF_8);
    }
}
