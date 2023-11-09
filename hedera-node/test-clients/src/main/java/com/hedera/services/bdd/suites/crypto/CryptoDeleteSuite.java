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

package com.hedera.services.bdd.suites.crypto;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
public class CryptoDeleteSuite extends HapiSuite {
    static final Logger log = LogManager.getLogger(CryptoDeleteSuite.class);
    private static final long TOKEN_INITIAL_SUPPLY = 500;
    private static final String TRANSFER_ACCOUNT = "transferAccount";
    private static final String TREASURY = "treasury";
    private static final String ACCOUNT_TO_BE_DELETED = "toBeDeleted";

    public static void main(String... args) {
        new CryptoDeleteSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                fundsTransferOnDelete(),
                cannotDeleteAccountsWithNonzeroTokenBalances(),
                cannotDeleteAlreadyDeletedAccount(),
                cannotDeleteAccountWithSameBeneficiary(),
                cannotDeleteTreasuryAccount(),
                deletedAccountCannotBePayer(),
                canQueryForRecordsWithDeletedPayers());
    }

    @HapiTest
    private HapiSpec deletedAccountCannotBePayer() {
        // Account Names
        String SUBMITTING_NODE_ACCOUNT = "0.0.3";
        String BENEFICIARY_ACCOUNT = "beneficiaryAccountForDeletedAccount";

        // Snapshot Names
        String SUBMITTING_NODE_PRE_TRANSFER = "submittingNodePreTransfer";
        String SUBMITTING_NODE_AFTER_BALANCE_LOAD = "submittingNodeAfterBalanceLoad";

        return defaultHapiSpec("DeletedAccountCannotBePayer")
                .given(
                        cryptoCreate(ACCOUNT_TO_BE_DELETED),
                        cryptoCreate(BENEFICIARY_ACCOUNT).balance(0L))
                .when()
                .then(
                        balanceSnapshot(SUBMITTING_NODE_PRE_TRANSFER, SUBMITTING_NODE_ACCOUNT),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, SUBMITTING_NODE_ACCOUNT, 1000000000)),
                        balanceSnapshot(SUBMITTING_NODE_AFTER_BALANCE_LOAD, SUBMITTING_NODE_ACCOUNT),
                        cryptoDelete(ACCOUNT_TO_BE_DELETED)
                                .transfer(BENEFICIARY_ACCOUNT)
                                .deferStatusResolution(),
                        cryptoTransfer(tinyBarsFromTo(BENEFICIARY_ACCOUNT, GENESIS, 1))
                                .payingWith(ACCOUNT_TO_BE_DELETED)
                                .hasKnownStatus(PAYER_ACCOUNT_DELETED),
                        getAccountBalance(SUBMITTING_NODE_ACCOUNT)
                                .hasTinyBars(
                                        approxChangeFromSnapshot(SUBMITTING_NODE_AFTER_BALANCE_LOAD, -100000, 50000))
                                .logged());
    }

    @HapiTest
    private HapiSpec canQueryForRecordsWithDeletedPayers() {
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
    private HapiSpec fundsTransferOnDelete() {
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
    private HapiSpec cannotDeleteAccountsWithNonzeroTokenBalances() {
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
    private HapiSpec cannotDeleteAlreadyDeletedAccount() {
        return defaultHapiSpec("CannotDeleteAlreadyDeletedAccount")
                .given(cryptoCreate(ACCOUNT_TO_BE_DELETED), cryptoCreate(TRANSFER_ACCOUNT))
                .when(cryptoDelete(ACCOUNT_TO_BE_DELETED)
                        .transfer(TRANSFER_ACCOUNT)
                        .hasKnownStatus(SUCCESS))
                .then(cryptoDelete(ACCOUNT_TO_BE_DELETED)
                        .transfer(TRANSFER_ACCOUNT)
                        .hasKnownStatus(ACCOUNT_DELETED));
    }

    @HapiTest
    private HapiSpec cannotDeleteAccountWithSameBeneficiary() {
        return defaultHapiSpec("CannotDeleteAccountWithSameBeneficiary")
                .given(cryptoCreate(ACCOUNT_TO_BE_DELETED))
                .when()
                .then(cryptoDelete(ACCOUNT_TO_BE_DELETED)
                        .transfer(ACCOUNT_TO_BE_DELETED)
                        .hasPrecheck(TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT));
    }

    @HapiTest
    private HapiSpec cannotDeleteTreasuryAccount() {
        return defaultHapiSpec("CannotDeleteTreasuryAccount")
                .given(cryptoCreate(TREASURY), cryptoCreate(TRANSFER_ACCOUNT))
                .when(tokenCreate("toBeTransferred")
                        .initialSupply(TOKEN_INITIAL_SUPPLY)
                        .treasury(TREASURY))
                .then(cryptoDelete(TREASURY).transfer(TRANSFER_ACCOUNT).hasKnownStatus(ACCOUNT_IS_TREASURY));
    }
}
