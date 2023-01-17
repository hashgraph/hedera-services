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

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.approxChangeFromSnapshot;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.including;
import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
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
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CryptoDeleteSuite extends HapiSuite {

    static final Logger log = LogManager.getLogger(CryptoDeleteSuite.class);
    private static final long TOKEN_INITIAL_SUPPLY = 500;
    public static final String ACCOUNT_TO_BE_DELETED = "ACCOUNT_TO_BE_DELETED";

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
                new HapiSpec[] {
                    //                                        fundsTransferOnDelete(),
                    //
                    // cannotDeleteAccountsWithNonzeroTokenBalances(),
                    //                                        cannotDeleteAlreadyDeletedAccount(),
                    //
                    // cannotDeleteAccountWithSameBeneficiary(),
                    //                                        cannotDeleteTreasuryAccount(),
                    //                                        deletedAccountCannotBePayer(),
                    //                    deleteAccountWithAliasAndCreateNewAccountWithSameAlias(),
                    deleteHollowAccountAndTryToCreateNewOneWithSameEVMAddress()
                });
    }

    private HapiSpec deletedAccountCannotBePayer() {
        // Account Names
        String SUBMITTING_NODE_ACCOUNT = "0.0.3";
        String ACCOUNT_TO_BE_DELETED = "toBeDeleted";
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
                        cryptoTransfer(
                                tinyBarsFromTo(GENESIS, SUBMITTING_NODE_ACCOUNT, 1000000000)),
                        balanceSnapshot(
                                SUBMITTING_NODE_AFTER_BALANCE_LOAD, SUBMITTING_NODE_ACCOUNT),
                        cryptoDelete(ACCOUNT_TO_BE_DELETED)
                                .transfer(BENEFICIARY_ACCOUNT)
                                .deferStatusResolution(),
                        cryptoTransfer(tinyBarsFromTo(BENEFICIARY_ACCOUNT, GENESIS, 1))
                                .payingWith(ACCOUNT_TO_BE_DELETED)
                                .hasKnownStatus(PAYER_ACCOUNT_DELETED),
                        getAccountBalance(SUBMITTING_NODE_ACCOUNT)
                                .hasTinyBars(
                                        approxChangeFromSnapshot(
                                                SUBMITTING_NODE_AFTER_BALANCE_LOAD, -100000, 50000))
                                .logged());
    }

    private HapiSpec fundsTransferOnDelete() {
        long B = HapiSpecSetup.getDefaultInstance().defaultBalance();

        return defaultHapiSpec("FundsTransferOnDelete")
                .given(cryptoCreate("toBeDeleted"), cryptoCreate("transferAccount").balance(0L))
                .when(cryptoDelete("toBeDeleted").transfer("transferAccount").via("deleteTxn"))
                .then(
                        getAccountInfo("transferAccount").has(accountWith().balance(B)),
                        getTxnRecord("deleteTxn")
                                .hasPriority(
                                        recordWith()
                                                .transfers(
                                                        including(
                                                                tinyBarsFromTo(
                                                                        "toBeDeleted",
                                                                        "transferAccount",
                                                                        B)))));
    }

    private HapiSpec cannotDeleteAccountsWithNonzeroTokenBalances() {
        return defaultHapiSpec("CannotDeleteAccountsWithNonzeroTokenBalances")
                .given(
                        newKeyNamed("admin"),
                        cryptoCreate("toBeDeleted").maxAutomaticTokenAssociations(1),
                        cryptoCreate("transferAccount"),
                        cryptoCreate(TOKEN_TREASURY))
                .when(
                        tokenCreate("misc")
                                .adminKey("admin")
                                .initialSupply(TOKEN_INITIAL_SUPPLY)
                                .treasury(TOKEN_TREASURY),
                        tokenAssociate("toBeDeleted", "misc"),
                        cryptoTransfer(
                                moving(TOKEN_INITIAL_SUPPLY, "misc")
                                        .between(TOKEN_TREASURY, "toBeDeleted")),
                        cryptoDelete("toBeDeleted")
                                .transfer("transferAccount")
                                .hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES),
                        cryptoTransfer(
                                moving(TOKEN_INITIAL_SUPPLY, "misc")
                                        .between("toBeDeleted", TOKEN_TREASURY)),
                        tokenDissociate("toBeDeleted", "misc"),
                        cryptoTransfer(
                                moving(TOKEN_INITIAL_SUPPLY, "misc")
                                        .between(TOKEN_TREASURY, "toBeDeleted")),
                        cryptoDelete("toBeDeleted")
                                .transfer("transferAccount")
                                .hasKnownStatus(TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES))
                .then(
                        cryptoDelete(TOKEN_TREASURY).hasKnownStatus(ACCOUNT_IS_TREASURY),
                        cryptoTransfer(
                                moving(TOKEN_INITIAL_SUPPLY, "misc")
                                        .between("toBeDeleted", TOKEN_TREASURY)),
                        cryptoDelete("toBeDeleted"),
                        cryptoDelete("toBeDeleted").hasKnownStatus(ACCOUNT_DELETED),
                        tokenDelete("misc"),
                        tokenDissociate(TOKEN_TREASURY, "misc"),
                        cryptoDelete(TOKEN_TREASURY));
    }

    private HapiSpec cannotDeleteAlreadyDeletedAccount() {
        return defaultHapiSpec("CannotDeleteAlreadyDeletedAccount")
                .given(cryptoCreate("toBeDeleted"), cryptoCreate("transferAccount"))
                .when(
                        cryptoDelete("toBeDeleted")
                                .transfer("transferAccount")
                                .hasKnownStatus(SUCCESS))
                .then(
                        cryptoDelete("toBeDeleted")
                                .transfer("transferAccount")
                                .hasKnownStatus(ACCOUNT_DELETED));
    }

    private HapiSpec cannotDeleteAccountWithSameBeneficiary() {
        return defaultHapiSpec("CannotDeleteAccountWithSameBeneficiary")
                .given(cryptoCreate("toBeDeleted"))
                .when()
                .then(
                        cryptoDelete("toBeDeleted")
                                .transfer("toBeDeleted")
                                .hasPrecheck(TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT));
    }

    private HapiSpec cannotDeleteTreasuryAccount() {
        return defaultHapiSpec("CannotDeleteTreasuryAccount")
                .given(cryptoCreate("treasury"), cryptoCreate("transferAccount"))
                .when(
                        tokenCreate("toBeTransferred")
                                .initialSupply(TOKEN_INITIAL_SUPPLY)
                                .treasury("treasury"))
                .then(
                        cryptoDelete("treasury")
                                .transfer("transferAccount")
                                .hasKnownStatus(ACCOUNT_IS_TREASURY));
    }

    private HapiSpec deleteAccountWithAliasAndCreateNewAccountWithSameAlias() {
        return defaultHapiSpec("DeleteAccountWithAliasAndCreateNewAccountWithSameAlias")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate("transferAccount"))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    final var op =
                                            cryptoCreate(ACCOUNT_TO_BE_DELETED)
                                                    .alias(ecdsaKey.toByteString())
                                                    .balance(100 * ONE_HBAR);
                                    final var op2 =
                                            cryptoCreate(ACCOUNT)
                                                    .alias(ecdsaKey.toByteString())
                                                    .hasPrecheck(INVALID_ALIAS_KEY)
                                                    .balance(100 * ONE_HBAR);
                                    var accountToBeDeletedInfo =
                                            getAccountInfo(ACCOUNT_TO_BE_DELETED)
                                                    .has(
                                                            accountWith()
                                                                    .key(ecdsaKey)
                                                                    .alias(SECP_256K1_SOURCE_KEY)
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false));
                                    final var op3 =
                                            cryptoDelete(ACCOUNT_TO_BE_DELETED)
                                                    .payingWith(ACCOUNT_TO_BE_DELETED)
                                                    .signedBy(SECP_256K1_SOURCE_KEY);
                                    final var op4 =
                                            cryptoCreate(ACCOUNT)
                                                    .alias(ecdsaKey.toByteString())
                                                    .balance(100 * ONE_HBAR);
                                    var hapiGetAccountInfo =
                                            getAccountInfo(ACCOUNT)
                                                    .has(
                                                            accountWith()
                                                                    .key(ecdsaKey)
                                                                    .alias(SECP_256K1_SOURCE_KEY)
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false));
                                    allRunFor(
                                            spec,
                                            op,
                                            op2,
                                            accountToBeDeletedInfo,
                                            op3,
                                            op4,
                                            hapiGetAccountInfo);
                                }))
                .then();
    }

    private HapiSpec deleteHollowAccountAndTryToCreateNewOneWithSameEVMAddress() {
        return defaultHapiSpec("DeleteHollowAccountAndTryToCreateNewOneWithSameEVMAddress")
                .given(newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry().getKey(SECP_256K1_SOURCE_KEY);
                                    final var addressBytes =
                                            recoverAddressFromPubKey(
                                                    ecdsaKey.getECDSASecp256K1().toByteArray());
                                    assert addressBytes.length > 0;
                                    final var evmAddressBytes = ByteString.copyFrom(addressBytes);
                                    final var op =
                                            cryptoCreate(ACCOUNT_TO_BE_DELETED)
                                                    .evmAddress(evmAddressBytes)
                                                    .balance(100 * ONE_HBAR);
                                    final var op2 =
                                            cryptoCreate(ACCOUNT)
                                                    .evmAddress(evmAddressBytes)
                                                    .hasPrecheck(INVALID_ALIAS_KEY)
                                                    .balance(100 * ONE_HBAR);
                                    var accountToBeDeletedInfo =
                                            getAccountInfo(ACCOUNT_TO_BE_DELETED)
                                                    .has(
                                                            accountWith()
                                                                    .hasEmptyKey()
                                                                    .evmAddress(evmAddressBytes)
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false));
                                    final var op3 =
                                            cryptoDelete(ACCOUNT_TO_BE_DELETED)
                                                    .payingWith(ACCOUNT_TO_BE_DELETED)
                                                    .sigMapPrefixes(
                                                            uniqueWithFullPrefixesFor(
                                                                    SECP_256K1_SOURCE_KEY))
                                                    .signedBy(SECP_256K1_SOURCE_KEY);

                                    final var op4 =
                                            cryptoCreate(ACCOUNT)
                                                    .evmAddress(evmAddressBytes)
                                                    .balance(100 * ONE_HBAR);
                                    var hapiGetAccountInfo =
                                            getAccountInfo(ACCOUNT)
                                                    .has(
                                                            accountWith()
                                                                    .hasEmptyKey()
                                                                    .evmAddress(evmAddressBytes)
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false));
                                    allRunFor(
                                            spec,
                                            op,
                                            op2,
                                            accountToBeDeletedInfo,
                                            op3,
                                            op4,
                                            hapiGetAccountInfo);
                                }))
                .then();
    }
}
