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

package com.hedera.services.bdd.suites.crypto;

import static com.hedera.services.bdd.junit.TestTags.CRYPTO;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.approxChangeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.including;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.submitModified;
import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withSuccessivelyVariedBodyIds;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.TOKEN_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;

import com.hedera.services.bdd.junit.ContextRequirement;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(CRYPTO)
public class CryptoDeleteSuite {
    private static final long TOKEN_INITIAL_SUPPLY = 500;
    private static final String TRANSFER_ACCOUNT = "transferAccount";
    public static final String TREASURY = "treasury";
    private static final String ACCOUNT_TO_BE_DELETED = "toBeDeleted";

    @HapiTest
    final Stream<DynamicTest> accountIdVariantsTreatedAsExpected() {
        return defaultHapiSpec("accountIdVariantsTreatedAsExpected")
                .given(cryptoCreate(TRANSFER_ACCOUNT), cryptoCreate(ACCOUNT_TO_BE_DELETED))
                .when()
                .then(submitModified(withSuccessivelyVariedBodyIds(), () -> cryptoDelete(ACCOUNT_TO_BE_DELETED)
                        .transfer(TRANSFER_ACCOUNT)));
    }

    @LeakyHapiTest(requirement = ContextRequirement.SYSTEM_ACCOUNT_BALANCES)
    final Stream<DynamicTest> deletedAccountCannotBePayer() {
        final var submittingNodeAccount = "0.0.3";
        final var beneficiaryAccount = "beneficiaryAccountForDeletedAccount";
        final var submittingNodePreTransfer = "submittingNodePreTransfer";
        final var submittingNodeAfterBalanceLoad = "submittingNodeAfterBalanceLoad";
        return hapiTest(
                cryptoCreate(ACCOUNT_TO_BE_DELETED),
                cryptoCreate(beneficiaryAccount).balance(0L),
                balanceSnapshot(submittingNodePreTransfer, submittingNodeAccount),
                cryptoTransfer(tinyBarsFromTo(GENESIS, submittingNodeAccount, 1000000000)),
                balanceSnapshot(submittingNodeAfterBalanceLoad, submittingNodeAccount),
                cryptoDelete(ACCOUNT_TO_BE_DELETED).transfer(beneficiaryAccount).deferStatusResolution(),
                cryptoTransfer(tinyBarsFromTo(beneficiaryAccount, GENESIS, 1))
                        .payingWith(ACCOUNT_TO_BE_DELETED)
                        .hasKnownStatus(PAYER_ACCOUNT_DELETED),
                getAccountBalance(submittingNodeAccount)
                        .hasTinyBars(approxChangeFromSnapshot(submittingNodeAfterBalanceLoad, -100000, 50000))
                        .logged());
    }

    @HapiTest
    final Stream<DynamicTest> canQueryForRecordsWithDeletedPayers() {
        final var stillQueryableTxn = "stillQueryableTxn";
        return defaultHapiSpec("CanQueryForRecordsWithDeletedPayers")
                .given(cryptoCreate(ACCOUNT_TO_BE_DELETED))
                .when(
                        cryptoTransfer(tinyBarsFromTo(ACCOUNT_TO_BE_DELETED, FUNDING, 1))
                                .payingWith(ACCOUNT_TO_BE_DELETED)
                                .via(stillQueryableTxn),
                        cryptoDelete(ACCOUNT_TO_BE_DELETED))
                .then(getTxnRecord(stillQueryableTxn).hasPriority(recordWith().payer(ACCOUNT_TO_BE_DELETED)));
    }

    @HapiTest
    final Stream<DynamicTest> fundsTransferOnDelete() {
        long B = HapiSpecSetup.getDefaultInstance().defaultBalance();

        return defaultHapiSpec("FundsTransferOnDelete")
                .given(
                        cryptoCreate(ACCOUNT_TO_BE_DELETED),
                        cryptoCreate(TRANSFER_ACCOUNT).balance(0L))
                .when(cryptoDelete(ACCOUNT_TO_BE_DELETED)
                        .transfer(TRANSFER_ACCOUNT)
                        .via("deleteTxn"))
                .then(
                        getAccountInfo(TRANSFER_ACCOUNT).has(accountWith().balance(B)),
                        getTxnRecord("deleteTxn")
                                .hasPriority(recordWith()
                                        .transfers(including(
                                                tinyBarsFromTo(ACCOUNT_TO_BE_DELETED, TRANSFER_ACCOUNT, B)))));
    }

    @HapiTest
    final Stream<DynamicTest> cannotDeleteAccountsWithNonzeroTokenBalances() {
        return defaultHapiSpec("CannotDeleteAccountsWithNonzeroTokenBalances")
                .given(
                        newKeyNamed("admin"),
                        cryptoCreate(ACCOUNT_TO_BE_DELETED).maxAutomaticTokenAssociations(1),
                        cryptoCreate(TRANSFER_ACCOUNT),
                        cryptoCreate(TOKEN_TREASURY))
                .when(
                        tokenCreate("misc")
                                .adminKey("admin")
                                .initialSupply(TOKEN_INITIAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate(ACCOUNT_TO_BE_DELETED, "misc"),
                        cryptoTransfer(
                                moving(TOKEN_INITIAL_SUPPLY, "misc").between(TOKEN_TREASURY, ACCOUNT_TO_BE_DELETED)),
                        cryptoDelete(ACCOUNT_TO_BE_DELETED)
                                .transfer(TRANSFER_ACCOUNT)
                                .hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES),
                        cryptoTransfer(
                                moving(TOKEN_INITIAL_SUPPLY, "misc").between(ACCOUNT_TO_BE_DELETED, TOKEN_TREASURY)),
                        tokenDissociate(ACCOUNT_TO_BE_DELETED, "misc"),
                        cryptoTransfer(
                                moving(TOKEN_INITIAL_SUPPLY, "misc").between(TOKEN_TREASURY, ACCOUNT_TO_BE_DELETED)),
                        cryptoDelete(ACCOUNT_TO_BE_DELETED)
                                .transfer(TRANSFER_ACCOUNT)
                                .hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES))
                .then(
                        cryptoDelete(TOKEN_TREASURY).hasKnownStatus(ACCOUNT_IS_TREASURY),
                        cryptoTransfer(
                                moving(TOKEN_INITIAL_SUPPLY, "misc").between(ACCOUNT_TO_BE_DELETED, TOKEN_TREASURY)),
                        cryptoDelete(ACCOUNT_TO_BE_DELETED),
                        cryptoDelete(ACCOUNT_TO_BE_DELETED).hasKnownStatus(ACCOUNT_DELETED),
                        tokenDelete("misc"),
                        tokenDissociate(TOKEN_TREASURY, "misc"),
                        cryptoDelete(TOKEN_TREASURY));
    }

    @HapiTest
    final Stream<DynamicTest> cannotDeleteAlreadyDeletedAccount() {
        return hapiTest(
                cryptoCreate(ACCOUNT_TO_BE_DELETED),
                cryptoCreate(TRANSFER_ACCOUNT),
                cryptoDelete(ACCOUNT_TO_BE_DELETED).transfer(TRANSFER_ACCOUNT).hasKnownStatus(SUCCESS),
                cryptoDelete(ACCOUNT_TO_BE_DELETED).transfer(TRANSFER_ACCOUNT).hasKnownStatus(ACCOUNT_DELETED));
    }

    @HapiTest
    final Stream<DynamicTest> mustSpecifyTargetId() {
        return defaultHapiSpec("mustSpecifyTargetId")
                .given()
                .when()
                .then(cryptoDelete("0.0.0")
                        .sansTargetId()
                        .signedBy(DEFAULT_PAYER)
                        .hasPrecheck(ACCOUNT_ID_DOES_NOT_EXIST));
    }

    @HapiTest
    final Stream<DynamicTest> cannotDeleteAccountWithSameBeneficiary() {
        return defaultHapiSpec("CannotDeleteAccountWithSameBeneficiary")
                .given(cryptoCreate(ACCOUNT_TO_BE_DELETED))
                .when()
                .then(cryptoDelete(ACCOUNT_TO_BE_DELETED)
                        .transfer(ACCOUNT_TO_BE_DELETED)
                        .hasPrecheck(TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT));
    }

    @HapiTest
    final Stream<DynamicTest> cannotDeleteTreasuryAccount() {
        return hapiTest(
                cryptoCreate(TREASURY),
                cryptoCreate(TRANSFER_ACCOUNT),
                tokenCreate("toBeTransferred")
                        .initialSupply(TOKEN_INITIAL_SUPPLY)
                        .treasury(TREASURY),
                cryptoDelete(TREASURY).transfer(TRANSFER_ACCOUNT).hasKnownStatus(ACCOUNT_IS_TREASURY));
    }
}
