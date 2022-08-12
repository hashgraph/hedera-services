/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.PropertySource.asAccountString;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAutoCreatedAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createDefaultContract;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDeleteAliased;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.sortedCryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithAlias;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeWithFallback;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.hapi.ContractUpdateSuite.ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AutoAccountCreationSuite extends HapiApiSuite {
    private static final Logger LOG = LogManager.getLogger(AutoAccountCreationSuite.class);
    private static final long INITIAL_BALANCE = 1000L;
    private static final ByteString ALIAS_CONTENT =
            ByteString.copyFromUtf8(
                    "a479462fba67674b5a41acfb16cb6828626b61d3f389fa611005a45754130e5c749073c0b1b791596430f4a54649cc8a3f6d28147dd4099070a5c3c4811d1771");
    private static final Key VALID_ED_25519_KEY =
            Key.newBuilder().setEd25519(ALIAS_CONTENT).build();
    private static final ByteString VALID_25519_ALIAS = VALID_ED_25519_KEY.toByteString();
    private static final String AUTO_MEMO = "auto-created account";
    private static final String VALID_ALIAS = "validAlias";
    private static final String PAYER = "payer";
    private static final String TRANSFER_TXN = "transferTxn";
    private static final String ALIAS = "alias";
    private static final String PAYER_1 = "payer1";
    private static final String ALIAS_2 = "alias2";
    private static final String PAYER_4 = "payer4";
    private static final String TRANSFER_TXN_2 = "transferTxn2";
    private static final String TRANSFER_ALIAS = "transferAlias";
    private static final String A_TOKEN = "tokenA";
    private static final String B_TOKEN = "tokenB";
    private static final String NFT_INFINITE_SUPPLY_TOKEN = "nftA";
    private static final String NFT_FINITE_SUPPLY_TOKEN = "nftB";
    private static final String MULTI_KEY = "multi";

    public static void main(String... args) {
        new AutoAccountCreationSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(
                autoAccountCreationsHappyPath(),
                autoAccountCreationBadAlias(),
                autoAccountCreationUnsupportedAlias(),
                transferToAccountAutoCreatedUsingAlias(),
                transferToAccountAutoCreatedUsingAccount(),
                transferFromAliasToAlias(),
                transferFromAliasToAccount(),
                multipleAutoAccountCreations(),
                accountCreatedIfAliasUsedAsPubKey(),
                aliasCanBeUsedOnManyAccountsNotAsAlias(),
                autoAccountCreationWorksWhenUsingAliasOfDeletedAccount(),
                canGetBalanceAndInfoViaAlias(),
                noStakePeriodStartIfNotStakingToNode(),
                canAutoCreateWithFungibleTokenTransfersToAlias(),
                canAutoCreateWithNftTransfersToAlias(),
                autoCreateWithNftFallBackFeeFails(),
                repeatedAliasInSameTransferListFails()
        );
    }

    private HapiApiSpec repeatedAliasInSameTransferListFails() {
        final var civilian = "somebody";
        final var autoCreateSponsor = "autoCreateSponsor";

        return defaultHapiSpec("repeatedAliasInSameTransferListFails")
                .given(
                        newKeyNamed(VALID_ALIAS),
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(VALID_ALIAS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(2),
                        tokenCreate(NFT_INFINITE_SUPPLY_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .via("nftCreateTxn"),
                        tokenCreate(NFT_FINITE_SUPPLY_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(12L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .initialSupply(0L),
                        mintToken(
                                NFT_INFINITE_SUPPLY_TOKEN,
                                List.of(
                                        ByteString.copyFromUtf8("a"),
                                        ByteString.copyFromUtf8("b"),
                                        ByteString.copyFromUtf8("c"),
                                        ByteString.copyFromUtf8("d"),
                                        ByteString.copyFromUtf8("e"))),
                        mintToken(
                                NFT_FINITE_SUPPLY_TOKEN,
                                List.of(
                                        ByteString.copyFromUtf8("a"),
                                        ByteString.copyFromUtf8("b"),
                                        ByteString.copyFromUtf8("c"),
                                        ByteString.copyFromUtf8("d"),
                                        ByteString.copyFromUtf8("e"))),
                        cryptoCreate(civilian).balance(10 * ONE_HBAR),
                        cryptoCreate(autoCreateSponsor)
                                .balance(INITIAL_BALANCE * ONE_HBAR)
                                .maxAutomaticTokenAssociations(10))
                .when(
                        tokenAssociate(
                                autoCreateSponsor,
                                NFT_FINITE_SUPPLY_TOKEN,
                                NFT_INFINITE_SUPPLY_TOKEN),
                        cryptoTransfer(
                                movingUnique(NFT_FINITE_SUPPLY_TOKEN, 3L, 4L)
                                        .between(TOKEN_TREASURY, autoCreateSponsor),
                                movingUnique(NFT_INFINITE_SUPPLY_TOKEN, 1L, 2L)
                                        .between(TOKEN_TREASURY, autoCreateSponsor)),
                        getAccountInfo(autoCreateSponsor).logged(),
                        getAccountInfo(TOKEN_TREASURY).logged(),
                        cryptoTransfer(
                                movingUnique(NFT_FINITE_SUPPLY_TOKEN, 3, 4)
                                        .between(autoCreateSponsor, VALID_ALIAS),
                                movingUnique(NFT_INFINITE_SUPPLY_TOKEN, 1, 2)
                                        .between(autoCreateSponsor, VALID_ALIAS))
                                .via("multiTransfer")
                                .payingWith(civilian)
                                .signedBy(civilian, autoCreateSponsor, VALID_ALIAS)
                                .logged(),
                        getTxnRecord("multiTransfer").andAllChildRecords().logged(),
                        getAliasedAccountInfo(VALID_ALIAS).logged(),
                        getAccountInfo(autoCreateSponsor).logged(),
                        getAccountInfo(TOKEN_TREASURY).logged())
                .then();
    }

    private HapiApiSpec autoCreateWithNftFallBackFeeFails() {
        final var civilian = "somebody";
        final var autoCreateSponsor = "autoCreateSponsor";

        return defaultHapiSpec("autoCreateWithNftFallBackFeeFails")
                .given(
                        newKeyNamed(VALID_ALIAS),
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(VALID_ALIAS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(2),
                        tokenCreate(NFT_INFINITE_SUPPLY_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .withCustom(
                                        royaltyFeeWithFallback(
                                                1,
                                                2,
                                                fixedHbarFeeInheritingRoyaltyCollector(10),
                                                TOKEN_TREASURY))
                                .via("nftCreateTxn"),
                        mintToken(
                                NFT_INFINITE_SUPPLY_TOKEN,
                                List.of(
                                        ByteString.copyFromUtf8("a"),
                                        ByteString.copyFromUtf8("b"),
                                        ByteString.copyFromUtf8("c"),
                                        ByteString.copyFromUtf8("d"),
                                        ByteString.copyFromUtf8("e"))),
                        cryptoCreate(civilian).balance(100 * ONE_HBAR),
                        cryptoCreate("dummy").balance(10 * ONE_HBAR),
                        cryptoCreate(autoCreateSponsor)
                                .balance(INITIAL_BALANCE * ONE_HBAR)
                                .maxAutomaticTokenAssociations(10))
                .when(
                        tokenAssociate(
                                autoCreateSponsor,
                                NFT_INFINITE_SUPPLY_TOKEN),
                        cryptoTransfer(
                                movingUnique(NFT_INFINITE_SUPPLY_TOKEN, 1L, 2L)
                                        .between(TOKEN_TREASURY, autoCreateSponsor)),
                        getAccountInfo(autoCreateSponsor).logged(),
                        getAccountInfo(TOKEN_TREASURY).logged(),
                        cryptoTransfer(
                                movingUnique(NFT_INFINITE_SUPPLY_TOKEN, 1, 2)
                                        .between(autoCreateSponsor, VALID_ALIAS))
                                .via("multiTransfer")
                                .payingWith(civilian)
                                .signedBy(civilian, autoCreateSponsor, VALID_ALIAS)
                                .hasKnownStatus(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE)
                                .logged(),
                        getAccountInfo(autoCreateSponsor).logged(),
                        getAccountInfo(TOKEN_TREASURY).logged()
                )
                .then();
    }

    private HapiApiSpec canAutoCreateWithNftTransfersToAlias() {
        final var civilian = "somebody";
        final var autoCreateSponsor = "autoCreateSponsor";

        return defaultHapiSpec("canAutoCreateWithNftTransfersToAlias")
                .given(
                        newKeyNamed(VALID_ALIAS),
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(VALID_ALIAS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(2),
                        tokenCreate(NFT_INFINITE_SUPPLY_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .via("nftCreateTxn"),
                        tokenCreate(NFT_FINITE_SUPPLY_TOKEN)
                                .supplyType(TokenSupplyType.FINITE)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .maxSupply(12L)
                                .supplyKey(MULTI_KEY)
                                .adminKey(MULTI_KEY)
                                .initialSupply(0L),
                        mintToken(
                                NFT_INFINITE_SUPPLY_TOKEN,
                                List.of(
                                        ByteString.copyFromUtf8("a"),
                                        ByteString.copyFromUtf8("b"),
                                        ByteString.copyFromUtf8("c"),
                                        ByteString.copyFromUtf8("d"),
                                        ByteString.copyFromUtf8("e"))),
                        mintToken(
                                NFT_FINITE_SUPPLY_TOKEN,
                                List.of(
                                        ByteString.copyFromUtf8("a"),
                                        ByteString.copyFromUtf8("b"),
                                        ByteString.copyFromUtf8("c"),
                                        ByteString.copyFromUtf8("d"),
                                        ByteString.copyFromUtf8("e"))),
                        cryptoCreate(civilian).balance(10 * ONE_HBAR),
                        cryptoCreate(autoCreateSponsor)
                                .balance(INITIAL_BALANCE * ONE_HBAR)
                                .maxAutomaticTokenAssociations(10))
                .when(
                        tokenAssociate(
                                autoCreateSponsor,
                                NFT_FINITE_SUPPLY_TOKEN,
                                NFT_INFINITE_SUPPLY_TOKEN),
                        cryptoTransfer(
                                movingUnique(NFT_FINITE_SUPPLY_TOKEN, 3L, 4L)
                                        .between(TOKEN_TREASURY, autoCreateSponsor),
                                movingUnique(NFT_INFINITE_SUPPLY_TOKEN, 1L, 2L)
                                        .between(TOKEN_TREASURY, autoCreateSponsor)),
                        getAccountInfo(autoCreateSponsor).logged(),
                        getAccountInfo(TOKEN_TREASURY).logged(),
                        cryptoTransfer(
                                        movingUnique(NFT_FINITE_SUPPLY_TOKEN, 3, 4)
                                                .between(autoCreateSponsor, VALID_ALIAS),
                                        movingUnique(NFT_INFINITE_SUPPLY_TOKEN, 1, 2)
                                                .between(autoCreateSponsor, VALID_ALIAS))
                                .via("multiTransfer")
                                .payingWith(civilian)
                                .signedBy(civilian, autoCreateSponsor, VALID_ALIAS)
                                .logged(),
                        getTxnRecord("multiTransfer").andAllChildRecords().logged(),
                        getAliasedAccountInfo(VALID_ALIAS).logged(),
                        getAccountInfo(autoCreateSponsor).logged(),
                        getAccountInfo(TOKEN_TREASURY).logged())
                .then();
    }

    private HapiApiSpec canAutoCreateWithFungibleTokenTransfersToAlias() {
        final var civilian = "somebody";
        final var autoCreateSponsor = "autoCreateSponsor";
        final String alias1 = "alias1";

        return defaultHapiSpec("canAutoCreateWithFungibleTokenTransfersToAlias")
                .given(
                        newKeyNamed(VALID_ALIAS),
                        newKeyNamed(alias1),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                        tokenCreate(A_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(Long.MAX_VALUE)
                                .treasury(TOKEN_TREASURY)
                                .via("tokenACreateTxn"),
                        tokenCreate(B_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(Long.MAX_VALUE)
                                .treasury(TOKEN_TREASURY)
                                .via("tokenBCreateTxn"),
                        getTxnRecord("tokenACreateTxn")
                                .hasNewTokenAssociation(A_TOKEN, TOKEN_TREASURY),
                        getTxnRecord("tokenBCreateTxn")
                                .hasNewTokenAssociation(B_TOKEN, TOKEN_TREASURY),
                        cryptoCreate(civilian).balance(10 * ONE_HBAR),
                        cryptoCreate(autoCreateSponsor)
                                .balance(INITIAL_BALANCE * ONE_HBAR)
                                .maxAutomaticTokenAssociations(1))
                .when(
                        cryptoTransfer(
                                        moving(100, A_TOKEN)
                                                .between(TOKEN_TREASURY, autoCreateSponsor))
                                .via("transferAToSponsor"),
                        getAccountInfo(autoCreateSponsor).logged(),
                        getAccountInfo(TOKEN_TREASURY).logged(),
                        cryptoTransfer(
                                        moving(10, A_TOKEN).between(autoCreateSponsor, VALID_ALIAS),
                                        moving(10, A_TOKEN).between(TOKEN_TREASURY, VALID_ALIAS))
                                .via("failedTransfer")
                                .payingWith(civilian)
                                .signedBy(civilian, autoCreateSponsor, VALID_ALIAS, TOKEN_TREASURY)
                                .logged(),
                        getTxnRecord("failedTransfer").andAllChildRecords().logged(),
                        getAliasedAccountInfo(VALID_ALIAS).logged(),
                        getAccountInfo(autoCreateSponsor).logged(),
                        getAccountInfo(TOKEN_TREASURY).logged(),
                        cryptoTransfer(
                                        moving(10, A_TOKEN).between(autoCreateSponsor, alias1),
                                        moving(10, B_TOKEN).between(TOKEN_TREASURY, alias1))
                                .via(TRANSFER_TXN)
                                .payingWith(civilian)
                                .signedBy(civilian, autoCreateSponsor, alias1, TOKEN_TREASURY)
                                .logged())
                .then(
                        getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged(),
                        getAccountInfo(autoCreateSponsor).logged(),
                        getAliasedAccountInfo(alias1).logged());
    }

    private HapiApiSpec noStakePeriodStartIfNotStakingToNode() {
        final var user = "user";
        final var contract = "contract";
        return defaultHapiSpec("NoStakePeriodStartIfNotStakingToNode")
                .given(
                        newKeyNamed(ADMIN_KEY),
                        cryptoCreate(user).key(ADMIN_KEY).stakedNodeId(0L),
                        createDefaultContract(contract).adminKey(ADMIN_KEY).stakedNodeId(0L),
                        getAccountInfo(user).has(accountWith().someStakePeriodStart()),
                        getContractInfo(contract).has(contractWith().someStakePeriodStart()))
                .when(
                        cryptoUpdate(user).newStakedAccountId(contract),
                        contractUpdate(contract).newStakedAccountId(user))
                .then(
                        getAccountInfo(user).has(accountWith().noStakePeriodStart()),
                        getContractInfo(contract).has(contractWith().noStakePeriodStart()));
    }

    private HapiApiSpec canGetBalanceAndInfoViaAlias() {
        final var ed25519SourceKey = "ed25519Alias";
        final var secp256k1SourceKey = "secp256k1Alias";
        final var secp256k1Shape = KeyShape.SECP256K1;
        final var ed25519Shape = KeyShape.ED25519;
        final var autoCreation = "autoCreation";

        return defaultHapiSpec("CanGetBalanceAndInfoViaAlias")
                .given(
                        cryptoCreate("civilian").balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(ed25519SourceKey).shape(ed25519Shape),
                        newKeyNamed(secp256k1SourceKey).shape(secp256k1Shape))
                .when(
                        sortedCryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                "civilian", ed25519SourceKey, ONE_HUNDRED_HBARS),
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, secp256k1SourceKey, ONE_HUNDRED_HBARS))
                                /* Sort the transfer list so the accounts are created in a predictable order (the
                                 * serialized bytes of an Ed25519 are always lexicographically prior to the serialized
                                 * bytes of a secp256k1 key, so now the first child record will _always_ be for the
                                 * ed25519 auto-creation). */
                                .payingWith(GENESIS)
                                .via(autoCreation))
                .then(
                        getTxnRecord(autoCreation)
                                .andAllChildRecords()
                                .hasAliasInChildRecord(ed25519SourceKey, 0)
                                .hasAliasInChildRecord(secp256k1SourceKey, 1)
                                .logged(),
                        getAutoCreatedAccountBalance(ed25519SourceKey)
                                .hasExpectedAccountID()
                                .logged(),
                        getAutoCreatedAccountBalance(secp256k1SourceKey)
                                .hasExpectedAccountID()
                                .logged(),
                        getAliasedAccountInfo(ed25519SourceKey)
                                .hasExpectedAliasKey()
                                .hasExpectedAccountID()
                                .has(
                                        accountWith()
                                                .expectedBalanceWithChargedUsd(
                                                        ONE_HUNDRED_HBARS, 0, 0))
                                .logged(),
                        getAliasedAccountInfo(secp256k1SourceKey)
                                .hasExpectedAliasKey()
                                .hasExpectedAccountID()
                                .has(
                                        accountWith()
                                                .expectedBalanceWithChargedUsd(
                                                        ONE_HUNDRED_HBARS, 0, 0))
                                .logged());
    }

    private HapiApiSpec aliasCanBeUsedOnManyAccountsNotAsAlias() {
        return defaultHapiSpec("AliasCanBeUsedOnManyAccountsNotAsAlias")
                .given(
                        /* have alias key on other accounts and tokens not as alias */
                        newKeyNamed(VALID_ALIAS),
                        cryptoCreate(PAYER).key(VALID_ALIAS).balance(INITIAL_BALANCE * ONE_HBAR),
                        tokenCreate(PAYER).adminKey(VALID_ALIAS),
                        tokenCreate(PAYER).supplyKey(VALID_ALIAS),
                        tokenCreate("a").treasury(PAYER))
                .when(
                        /* auto account is created */
                        cryptoTransfer(
                                        tinyBarsFromToWithAlias(
                                                PAYER, VALID_ALIAS, ONE_HUNDRED_HBARS))
                                .via(TRANSFER_TXN))
                .then(
                        /* get transaction record and validate the child record has alias bytes as expected */
                        getTxnRecord(TRANSFER_TXN)
                                .andAllChildRecords()
                                .hasNonStakingChildRecordCount(1)
                                .hasAliasInChildRecord(VALID_ALIAS, 0),
                        getAccountInfo(PAYER)
                                .has(
                                        accountWith()
                                                .balance(
                                                        (INITIAL_BALANCE * ONE_HBAR)
                                                                - ONE_HUNDRED_HBARS)
                                                .noAlias()),
                        getAliasedAccountInfo(VALID_ALIAS)
                                .has(
                                        accountWith()
                                                .key(VALID_ALIAS)
                                                .expectedBalanceWithChargedUsd(
                                                        ONE_HUNDRED_HBARS, 0, 0)
                                                .alias(VALID_ALIAS)
                                                .autoRenew(THREE_MONTHS_IN_SECONDS)
                                                .receiverSigReq(false)
                                                .memo(AUTO_MEMO)));
    }

    private HapiApiSpec accountCreatedIfAliasUsedAsPubKey() {
        return defaultHapiSpec("AccountCreatedIfAliasUsedAsPubKey")
                .given(
                        newKeyNamed(ALIAS),
                        cryptoCreate(PAYER_1)
                                .balance(INITIAL_BALANCE * ONE_HBAR)
                                .key(ALIAS)
                                .signedBy(ALIAS, DEFAULT_PAYER))
                .when(
                        cryptoTransfer(tinyBarsFromToWithAlias(PAYER_1, ALIAS, ONE_HUNDRED_HBARS))
                                .via(TRANSFER_TXN))
                .then(
                        getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged(),
                        getAccountInfo(PAYER_1)
                                .has(
                                        accountWith()
                                                .balance(
                                                        (INITIAL_BALANCE * ONE_HBAR)
                                                                - ONE_HUNDRED_HBARS)
                                                .noAlias()),
                        getAliasedAccountInfo(ALIAS)
                                .has(
                                        accountWith()
                                                .key(ALIAS)
                                                .expectedBalanceWithChargedUsd(
                                                        ONE_HUNDRED_HBARS, 0, 0)
                                                .alias(ALIAS)
                                                .autoRenew(THREE_MONTHS_IN_SECONDS)
                                                .receiverSigReq(false))
                                .logged());
    }

    private HapiApiSpec autoAccountCreationWorksWhenUsingAliasOfDeletedAccount() {
        return defaultHapiSpec("AutoAccountCreationWorksWhenUsingAliasOfDeletedAccount")
                .given(
                        newKeyNamed(ALIAS),
                        newKeyNamed(ALIAS_2),
                        cryptoCreate(PAYER).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(
                        cryptoTransfer(tinyBarsFromToWithAlias(PAYER, ALIAS, ONE_HUNDRED_HBARS))
                                .via("txn"),
                        getTxnRecord("txn").hasNonStakingChildRecordCount(1).logged())
                .then(
                        cryptoDeleteAliased(ALIAS)
                                .transfer(PAYER)
                                .hasKnownStatus(SUCCESS)
                                .signedBy(ALIAS, PAYER, DEFAULT_PAYER)
                                .purging(),
                        cryptoTransfer(tinyBarsFromToWithAlias(PAYER, ALIAS, ONE_HUNDRED_HBARS))
                                .via("txn2")
                                .hasKnownStatus(ACCOUNT_DELETED)

                        /* need to validate it creates after expiration */
                        //						sleepFor(60000L),
                        //						cryptoTransfer(
                        //								HapiCryptoTransfer.tinyBarsFromToWithAlias("payer", "alias",
                        // ONE_HUNDRED_HBARS)).via(
                        //								"txn2"),
                        //						getTxnRecord("txn2").hasChildRecordCount(1).logged()
                        );
    }

    private HapiApiSpec transferFromAliasToAlias() {
        return defaultHapiSpec("transferFromAliasToAlias")
                .given(
                        newKeyNamed(ALIAS),
                        newKeyNamed(ALIAS_2),
                        cryptoCreate(PAYER_4).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(
                        cryptoTransfer(
                                        tinyBarsFromToWithAlias(
                                                PAYER_4, ALIAS, 2 * ONE_HUNDRED_HBARS))
                                .via("txn"),
                        getTxnRecord("txn").andAllChildRecords().logged(),
                        getAliasedAccountInfo(ALIAS)
                                .has(
                                        accountWith()
                                                .expectedBalanceWithChargedUsd(
                                                        (2 * ONE_HUNDRED_HBARS), 0, 0)))
                .then(
                        /* transfer from an alias that was auto created to a new alias, validate account is created */
                        cryptoTransfer(tinyBarsFromToWithAlias(ALIAS, ALIAS_2, ONE_HUNDRED_HBARS))
                                .via(TRANSFER_TXN_2),
                        getTxnRecord(TRANSFER_TXN_2).andAllChildRecords().logged(),
                        getAliasedAccountInfo(ALIAS)
                                .has(
                                        accountWith()
                                                .expectedBalanceWithChargedUsd(
                                                        ONE_HUNDRED_HBARS, 0, 0)),
                        getAliasedAccountInfo(ALIAS_2)
                                .has(
                                        accountWith()
                                                .expectedBalanceWithChargedUsd(
                                                        ONE_HUNDRED_HBARS, 0, 0)));
    }

    private HapiApiSpec transferFromAliasToAccount() {
        final var payer = PAYER_4;
        final var alias = ALIAS;
        return defaultHapiSpec("transferFromAliasToAccount")
                .given(
                        newKeyNamed(alias),
                        cryptoCreate(payer).balance(INITIAL_BALANCE * ONE_HBAR),
                        cryptoCreate("randomAccount").balance(0L).payingWith(payer))
                .when(
                        cryptoTransfer(tinyBarsFromToWithAlias(payer, alias, 2 * ONE_HUNDRED_HBARS))
                                .via("txn"),
                        getTxnRecord("txn").andAllChildRecords().logged(),
                        getAliasedAccountInfo(alias)
                                .has(
                                        accountWith()
                                                .expectedBalanceWithChargedUsd(
                                                        (2 * ONE_HUNDRED_HBARS), 0, 0)))
                .then(
                        /* transfer from an alias that was auto created to a new alias, validate account is created */
                        cryptoTransfer(
                                        tinyBarsFromToWithAlias(
                                                alias, "randomAccount", ONE_HUNDRED_HBARS))
                                .via(TRANSFER_TXN_2),
                        getTxnRecord(TRANSFER_TXN_2)
                                .andAllChildRecords()
                                .hasNonStakingChildRecordCount(0),
                        getAliasedAccountInfo(alias)
                                .has(
                                        accountWith()
                                                .expectedBalanceWithChargedUsd(
                                                        ONE_HUNDRED_HBARS, 0, 0)));
    }

    private HapiApiSpec transferToAccountAutoCreatedUsingAccount() {
        return defaultHapiSpec("transferToAccountAutoCreatedUsingAccount")
                .given(
                        newKeyNamed(TRANSFER_ALIAS),
                        cryptoCreate(PAYER).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(
                        cryptoTransfer(
                                        tinyBarsFromToWithAlias(
                                                PAYER, TRANSFER_ALIAS, ONE_HUNDRED_HBARS))
                                .via("txn"),
                        getTxnRecord("txn").andAllChildRecords().logged())
                .then(
                        /* get the account associated with alias and transfer */
                        withOpContext(
                                (spec, opLog) -> {
                                    final var aliasAccount =
                                            spec.registry()
                                                    .getAccountID(
                                                            spec.registry()
                                                                    .getKey(TRANSFER_ALIAS)
                                                                    .toByteString()
                                                                    .toStringUtf8());

                                    final var op =
                                            cryptoTransfer(
                                                            tinyBarsFromTo(
                                                                    PAYER,
                                                                    asAccountString(aliasAccount),
                                                                    ONE_HUNDRED_HBARS))
                                                    .via(TRANSFER_TXN_2);
                                    final var op2 =
                                            getTxnRecord(TRANSFER_TXN_2)
                                                    .andAllChildRecords()
                                                    .logged();
                                    final var op3 =
                                            getAccountInfo(PAYER)
                                                    .has(
                                                            accountWith()
                                                                    .balance(
                                                                            (INITIAL_BALANCE
                                                                                            * ONE_HBAR)
                                                                                    - (2
                                                                                            * ONE_HUNDRED_HBARS)));
                                    final var op4 =
                                            getAliasedAccountInfo(TRANSFER_ALIAS)
                                                    .has(
                                                            accountWith()
                                                                    .expectedBalanceWithChargedUsd(
                                                                            (2 * ONE_HUNDRED_HBARS),
                                                                            0,
                                                                            0));
                                    allRunFor(spec, op, op2, op3, op4);
                                }));
    }

    private HapiApiSpec transferToAccountAutoCreatedUsingAlias() {
        return defaultHapiSpec("transferToAccountAutoCreatedUsingAlias")
                .given(newKeyNamed(ALIAS), cryptoCreate(PAYER).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(
                        cryptoTransfer(tinyBarsFromToWithAlias(PAYER, ALIAS, ONE_HUNDRED_HBARS))
                                .via(TRANSFER_TXN),
                        getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged(),
                        getAccountInfo(PAYER)
                                .has(
                                        accountWith()
                                                .balance(
                                                        (INITIAL_BALANCE * ONE_HBAR)
                                                                - ONE_HUNDRED_HBARS)),
                        getAliasedAccountInfo(ALIAS)
                                .has(
                                        accountWith()
                                                .expectedBalanceWithChargedUsd(
                                                        ONE_HUNDRED_HBARS, 0, 0)))
                .then(
                        /* transfer using alias and not account number */
                        cryptoTransfer(tinyBarsFromToWithAlias(PAYER, ALIAS, ONE_HUNDRED_HBARS))
                                .via(TRANSFER_TXN_2),
                        getTxnRecord(TRANSFER_TXN_2)
                                .andAllChildRecords()
                                .hasNonStakingChildRecordCount(0)
                                .logged(),
                        getAccountInfo(PAYER)
                                .has(
                                        accountWith()
                                                .balance(
                                                        (INITIAL_BALANCE * ONE_HBAR)
                                                                - (2 * ONE_HUNDRED_HBARS))),
                        getAliasedAccountInfo(ALIAS)
                                .has(
                                        accountWith()
                                                .expectedBalanceWithChargedUsd(
                                                        (2 * ONE_HUNDRED_HBARS), 0, 0)));
    }

    private HapiApiSpec autoAccountCreationUnsupportedAlias() {
        final var threshKeyAlias =
                Key.newBuilder()
                        .setThresholdKey(
                                ThresholdKey.newBuilder()
                                        .setThreshold(2)
                                        .setKeys(
                                                KeyList.newBuilder()
                                                        .addKeys(
                                                                Key.newBuilder()
                                                                        .setEd25519(
                                                                                ByteString.copyFrom(
                                                                                        "aaa"
                                                                                                .getBytes())))
                                                        .addKeys(
                                                                Key.newBuilder()
                                                                        .setECDSASecp256K1(
                                                                                ByteString.copyFrom(
                                                                                        "bbbb"
                                                                                                .getBytes())))
                                                        .addKeys(
                                                                Key.newBuilder()
                                                                        .setEd25519(
                                                                                ByteString.copyFrom(
                                                                                        "cccccc"
                                                                                                .getBytes())))))
                        .build()
                        .toByteString();
        final var keyListAlias =
                Key.newBuilder()
                        .setKeyList(
                                KeyList.newBuilder()
                                        .addKeys(
                                                Key.newBuilder()
                                                        .setEd25519(
                                                                ByteString.copyFrom(
                                                                        "aaaaaa".getBytes())))
                                        .addKeys(
                                                Key.newBuilder()
                                                        .setECDSASecp256K1(
                                                                ByteString.copyFrom(
                                                                        "bbbbbbb".getBytes()))))
                        .build()
                        .toByteString();
        final var contractKeyAlias =
                Key.newBuilder()
                        .setContractID(ContractID.newBuilder().setContractNum(100L))
                        .build()
                        .toByteString();
        final var delegateContractKeyAlias =
                Key.newBuilder()
                        .setDelegatableContractId(ContractID.newBuilder().setContractNum(100L))
                        .build()
                        .toByteString();

        return defaultHapiSpec("autoAccountCreationUnsupportedAlias")
                .given(cryptoCreate(PAYER).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(
                        cryptoTransfer(tinyBarsFromTo(PAYER, threshKeyAlias, ONE_HUNDRED_HBARS))
                                .hasKnownStatus(ResponseCodeEnum.INVALID_ALIAS_KEY)
                                .via("transferTxnThreshKey"),
                        cryptoTransfer(tinyBarsFromTo(PAYER, keyListAlias, ONE_HUNDRED_HBARS))
                                .hasKnownStatus(ResponseCodeEnum.INVALID_ALIAS_KEY)
                                .via("transferTxnKeyList"),
                        cryptoTransfer(tinyBarsFromTo(PAYER, contractKeyAlias, ONE_HUNDRED_HBARS))
                                .hasKnownStatus(ResponseCodeEnum.INVALID_ALIAS_KEY)
                                .via("transferTxnContract"),
                        cryptoTransfer(
                                        tinyBarsFromTo(
                                                PAYER, delegateContractKeyAlias, ONE_HUNDRED_HBARS))
                                .hasKnownStatus(ResponseCodeEnum.INVALID_ALIAS_KEY)
                                .via("transferTxnKeyDelegate"))
                .then();
    }

    private HapiApiSpec autoAccountCreationBadAlias() {
        final var invalidAlias = VALID_25519_ALIAS.substring(0, 10);

        return defaultHapiSpec("AutoAccountCreationBadAlias")
                .given(cryptoCreate(PAYER).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(
                        cryptoTransfer(tinyBarsFromTo(PAYER, invalidAlias, ONE_HUNDRED_HBARS))
                                .hasKnownStatus(ResponseCodeEnum.INVALID_ALIAS_KEY)
                                .via("transferTxnBad"))
                .then();
    }

    private HapiApiSpec autoAccountCreationsHappyPath() {
        final var civilian = "somebody";
        final var autoCreateSponsor = "autoCreateSponsor";
        final var creationTime = new AtomicLong();
        return defaultHapiSpec("AutoAccountCreationsHappyPath")
                .given(
                        newKeyNamed(VALID_ALIAS),
                        cryptoCreate(civilian).balance(10 * ONE_HBAR),
                        cryptoCreate(autoCreateSponsor).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(
                        cryptoTransfer(
                                        tinyBarsFromToWithAlias(
                                                autoCreateSponsor, VALID_ALIAS, ONE_HUNDRED_HBARS))
                                .via(TRANSFER_TXN)
                                .payingWith(civilian))
                .then(
                        getReceipt(TRANSFER_TXN)
                                .andAnyChildReceipts()
                                .hasChildAutoAccountCreations(1),
                        getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged(),
                        getAccountInfo(autoCreateSponsor)
                                .has(
                                        accountWith()
                                                .balance(
                                                        (INITIAL_BALANCE * ONE_HBAR)
                                                                - ONE_HUNDRED_HBARS)
                                                .noAlias()),
                        assertionsHold(
                                (spec, opLog) -> {
                                    final var lookup =
                                            getTxnRecord(TRANSFER_TXN)
                                                    .andAllChildRecords()
                                                    .hasNonStakingChildRecordCount(1)
                                                    .hasAliasInChildRecord(VALID_ALIAS, 0)
                                                    .logged();
                                    allRunFor(spec, lookup);
                                    final var sponsor =
                                            spec.registry().getAccountID(autoCreateSponsor);
                                    final var payer = spec.registry().getAccountID(civilian);
                                    final var parent = lookup.getResponseRecord();
                                    final var child = lookup.getChildRecord(0);
                                    assertAliasBalanceAndFeeInChildRecord(
                                            parent,
                                            child,
                                            sponsor,
                                            payer,
                                            ONE_HUNDRED_HBARS,
                                            10 * ONE_HBAR);
                                    creationTime.set(child.getConsensusTimestamp().getSeconds());
                                }),
                        sourcing(
                                () ->
                                        getAliasedAccountInfo(VALID_ALIAS)
                                                .has(
                                                        accountWith()
                                                                .key(VALID_ALIAS)
                                                                .expectedBalanceWithChargedUsd(
                                                                        ONE_HUNDRED_HBARS, 0, 0)
                                                                .alias(VALID_ALIAS)
                                                                .autoRenew(THREE_MONTHS_IN_SECONDS)
                                                                .receiverSigReq(false)
                                                                .expiry(
                                                                        creationTime.get()
                                                                                + THREE_MONTHS_IN_SECONDS,
                                                                        0)
                                                                .memo(AUTO_MEMO))
                                                .logged()));
    }

    @SuppressWarnings("java:S5960")
    private void assertAliasBalanceAndFeeInChildRecord(
            final TransactionRecord parent,
            final TransactionRecord child,
            final AccountID sponsor,
            final AccountID defaultPayer,
            final long newAccountFunding,
            final long payerInitialBalance) {
        long receivedBalance = 0;
        long fundingAccountBalance = 0;
        long payerBalWithoutAutoCreationFee = 0;
        for (final var adjust : parent.getTransferList().getAccountAmountsList()) {
            final var id = adjust.getAccountID();
            if (!(id.getAccountNum() < 100
                    || id.equals(sponsor)
                    || id.equals(defaultPayer)
                    || id.getAccountNum() == 800
                    || id.getAccountNum() == 801)) {
                receivedBalance = adjust.getAmount();
            }

            // auto-creation fee is transferred to 0.0.98 (funding account) from payer
            if (id.getAccountNum() == 98) {
                fundingAccountBalance = adjust.getAmount();
            }
            // sum of all deductions from the payer without auto creation fee
            if ((id.getAccountNum() < 98
                    || id.equals(defaultPayer)
                    || id.getAccountNum() == 800
                    || id.getAccountNum() == 801)) {
                payerBalWithoutAutoCreationFee += adjust.getAmount();
            }
        }
        assertEquals(newAccountFunding, receivedBalance, "Transferred incorrect amount to alias");
        assertEquals(
                fundingAccountBalance,
                child.getTransactionFee(),
                "Child record did not specify deducted fee");
        assertEquals(
                -child.getTransactionFee(),
                payerBalWithoutAutoCreationFee,
                "Auto creation fee is not deducted from payer");
    }

    private HapiApiSpec multipleAutoAccountCreations() {
        return defaultHapiSpec("MultipleAutoAccountCreations")
                .given(cryptoCreate(PAYER).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(
                        newKeyNamed("alias1"),
                        newKeyNamed(ALIAS_2),
                        newKeyNamed("alias3"),
                        newKeyNamed("alias4"),
                        newKeyNamed("alias5"),
                        cryptoTransfer(
                                        tinyBarsFromToWithAlias(PAYER, "alias1", ONE_HUNDRED_HBARS),
                                        tinyBarsFromToWithAlias(PAYER, ALIAS_2, ONE_HUNDRED_HBARS),
                                        tinyBarsFromToWithAlias(PAYER, "alias3", ONE_HUNDRED_HBARS))
                                .via("multipleAutoAccountCreates"),
                        getTxnRecord("multipleAutoAccountCreates")
                                .hasNonStakingChildRecordCount(3)
                                .logged(),
                        getAccountInfo(PAYER)
                                .has(
                                        accountWith()
                                                .balance(
                                                        (INITIAL_BALANCE * ONE_HBAR)
                                                                - 3 * ONE_HUNDRED_HBARS)))
                .then(
                        cryptoTransfer(
                                        tinyBarsFromToWithAlias(
                                                PAYER, "alias4", 7 * ONE_HUNDRED_HBARS),
                                        tinyBarsFromToWithAlias(PAYER, "alias5", 100))
                                .via("failedAutoCreate")
                                .hasKnownStatus(INSUFFICIENT_ACCOUNT_BALANCE),
                        getTxnRecord("failedAutoCreate").hasNonStakingChildRecordCount(0).logged(),
                        getAccountInfo(PAYER)
                                .has(
                                        accountWith()
                                                .balance(
                                                        (INITIAL_BALANCE * ONE_HBAR)
                                                                - 3 * ONE_HUNDRED_HBARS)));
    }
}
