/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.records;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbar;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.takeBalanceSnapshots;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateRecordTransactionFees;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateTransferListForBalances;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CryptoRecordsSanityCheckSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(CryptoRecordsSanityCheckSuite.class);
    private static final String PAYER = "payer";
    private static final String RECEIVER = "receiver";
    private static final String NEW_KEY = "newKey";
    private static final String ORIG_KEY = "origKey";

    public static void main(String... args) {
        new CryptoRecordsSanityCheckSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[]{
                cryptoCreateRecordSanityChecks(),
                cryptoDeleteRecordSanityChecks(),
                cryptoTransferRecordSanityChecks(),
                cryptoUpdateRecordSanityChecks(),
                insufficientAccountBalanceRecordSanityChecks(),
                invalidPayerSigCryptoTransferRecordSanityChecks(),
                ownershipChangeShowsInRecord(),
        });
    }

    private HapiSpec ownershipChangeShowsInRecord() {
        final var firstOwner = "A";
        final var secondOwner = "B";
        final var uniqueToken = "DoubleVision";
        final var metadata = ByteString.copyFromUtf8("A sphinx, a buddha, and so on.");
        final var supplyKey = "minting";
        final var mintRecord = "mint";
        final var xferRecord = "xfer";

        return defaultHapiSpec("OwnershipChangeShowsInRecord")
                .given(
                        newKeyNamed(supplyKey),
                        cryptoCreate(firstOwner),
                        cryptoCreate(secondOwner),
                        tokenCreate(uniqueToken)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(firstOwner)
                                .supplyKey(supplyKey)
                                .initialSupply(0L))
                .when(
                        tokenAssociate(secondOwner, uniqueToken),
                        mintToken(uniqueToken, List.of(metadata)).via(mintRecord),
                        getAccountBalance(firstOwner).logged(),
                        cryptoTransfer(
                                        movingHbar(1_234_567L).between(secondOwner, firstOwner),
                                        movingUnique(uniqueToken, 1L).between(firstOwner, secondOwner))
                                .via(xferRecord))
                .then(
                        getTxnRecord(mintRecord).logged(),
                        getTxnRecord(xferRecord).logged());
    }

    private HapiSpec cryptoCreateRecordSanityChecks() {
        return defaultHapiSpec("CryptoCreateRecordSanityChecks")
                .given(takeBalanceSnapshots(FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER))
                .when(cryptoCreate("test").via("txn"))
                .then(
                        validateTransferListForBalances(
                                "txn", List.of("test", FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER)),
                        validateRecordTransactionFees("txn"));
    }

    private HapiSpec cryptoDeleteRecordSanityChecks() {
        return defaultHapiSpec("CryptoDeleteRecordSanityChecks")
                .given(flattened(
                        cryptoCreate("test"),
                        takeBalanceSnapshots(FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER, "test")))
                .when(cryptoDelete("test").via("txn").transfer(DEFAULT_PAYER))
                .then(
                        validateTransferListForBalances(
                                "txn",
                                List.of(FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER, "test"),
                                Set.of("test")),
                        validateRecordTransactionFees("txn"));
    }

    private HapiSpec cryptoTransferRecordSanityChecks() {
        return defaultHapiSpec("CryptoTransferRecordSanityChecks")
                .given(flattened(
                        cryptoCreate("a").balance(100_000L),
                        takeBalanceSnapshots(FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER, "a")))
                .when(cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "a", 1_234L)).via("txn"))
                .then(
                        validateTransferListForBalances(
                                "txn", List.of(FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER, "a")),
                        validateRecordTransactionFees("txn"));
    }

    private HapiSpec cryptoUpdateRecordSanityChecks() {
        return defaultHapiSpec("CryptoUpdateRecordSanityChecks")
                .given(flattened(
                        cryptoCreate("test"),
                        newKeyNamed(NEW_KEY).type(KeyFactory.KeyType.SIMPLE),
                        takeBalanceSnapshots(FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER, "test")))
                .when(cryptoUpdate("test").key(NEW_KEY).via("txn").fee(500_000L).payingWith("test"))
                .then(
                        validateTransferListForBalances(
                                "txn", List.of(FUNDING, NODE, STAKING_REWARD, NODE_REWARD, DEFAULT_PAYER, "test")),
                        validateRecordTransactionFees("txn"));
    }

    private HapiSpec insufficientAccountBalanceRecordSanityChecks() {
        final long BALANCE = 500_000_000L;
        return defaultHapiSpec("InsufficientAccountBalanceRecordSanityChecks")
                .given(flattened(
                        cryptoCreate(PAYER).balance(BALANCE),
                        cryptoCreate(RECEIVER),
                        takeBalanceSnapshots(FUNDING, NODE, STAKING_REWARD, NODE_REWARD, PAYER, RECEIVER)))
                .when(
                        cryptoTransfer(tinyBarsFromTo(PAYER, RECEIVER, BALANCE / 2))
                                .payingWith(PAYER)
                                .via("txn1")
                                .fee(ONE_HUNDRED_HBARS)
                                .deferStatusResolution(),
                        cryptoTransfer(tinyBarsFromTo(PAYER, RECEIVER, BALANCE / 2))
                                .payingWith(PAYER)
                                .via("txn2")
                                .fee(ONE_HUNDRED_HBARS)
                                .hasKnownStatus(INSUFFICIENT_ACCOUNT_BALANCE),
                        sleepFor(1_000L))
                .then(validateTransferListForBalances(
                        List.of("txn1", "txn2"), List.of(FUNDING, NODE, STAKING_REWARD, NODE_REWARD, PAYER, RECEIVER)));
    }

    private HapiSpec invalidPayerSigCryptoTransferRecordSanityChecks() {
        final long BALANCE = 10_000_000L;

        return defaultHapiSpec("InvalidPayerSigCryptoTransferSanityChecks")
                .given(
                        newKeyNamed(ORIG_KEY),
                        newKeyNamed(NEW_KEY),
                        cryptoCreate(PAYER).key(ORIG_KEY).balance(BALANCE),
                        cryptoCreate(RECEIVER))
                .when(cryptoUpdate(PAYER)
                        .key(NEW_KEY)
                        .payingWith(PAYER)
                        .fee(BALANCE / 2)
                        .via("updateTxn")
                        .deferStatusResolution())
                .then(cryptoTransfer(tinyBarsFromTo(PAYER, RECEIVER, 1_000L))
                        .payingWith(PAYER)
                        .via("transferTxn")
                        .signedBy(ORIG_KEY, RECEIVER)
                        .hasKnownStatus(INVALID_PAYER_SIGNATURE));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
