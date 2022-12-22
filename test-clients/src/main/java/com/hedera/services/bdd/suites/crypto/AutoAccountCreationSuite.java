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

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.PropertySource.asAccount;
import static com.hedera.services.bdd.spec.PropertySource.asAccountString;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.AutoAssocAsserts.accountTokenPairsInAnyOrder;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingFungibleMovement;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createDefaultContract;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDeleteAliased;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdateAliased;
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
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbar;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.suites.contract.Utils.aaWith;
import static com.hedera.services.bdd.suites.contract.Utils.accountId;
import static com.hedera.services.bdd.suites.contract.Utils.ocWith;
import static com.hedera.services.bdd.suites.contract.hapi.ContractUpdateSuite.ADMIN_KEY;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.updateSpecFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.TokenSupplyType.FINITE;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AutoAccountCreationSuite extends HapiSuite {
    private static final Logger LOG = LogManager.getLogger(AutoAccountCreationSuite.class);
    private static final long INITIAL_BALANCE = 1000L;
    private static final ByteString ALIAS_CONTENT =
            ByteString.copyFromUtf8(
                    "a479462fba67674b5a41acfb16cb6828626b61d3f389fa611005a45754130e5c749073c0b1b791596430f4a54649cc8a3f6d28147dd4099070a5c3c4811d1771");
    private static final Key VALID_ED_25519_KEY =
            Key.newBuilder().setEd25519(ALIAS_CONTENT).build();
    private static final ByteString VALID_25519_ALIAS = VALID_ED_25519_KEY.toByteString();
    private static final String AUTO_MEMO = "auto-created account";
    public static final String LAZY_MEMO = "lazy-created account";
    public static final String VALID_ALIAS = "validAlias";
    private static final String PAYER = "payer";
    private static final String TRANSFER_TXN = "transferTxn";
    private static final String ALIAS = "alias";
    private static final String PAYER_1 = "payer1";
    private static final String ALIAS_2 = "alias2";
    private static final String PAYER_4 = "payer4";
    private static final String TRANSFER_TXN_2 = "transferTxn2";
    private static final String TRANSFER_ALIAS = "transferAlias";
    public static final String A_TOKEN = "tokenA";
    private static final String B_TOKEN = "tokenB";
    public static final String NFT_INFINITE_SUPPLY_TOKEN = "nftA";
    private static final String NFT_FINITE_SUPPLY_TOKEN = "nftB";
    private static final String MULTI_KEY = "multi";
    private static final String PARTY = "party";
    private static final String COUNTERPARTY = "counterparty";

    private static final String CIVILIAN = "somebody";
    public static final String TOKEN_A_CREATE = "tokenACreateTxn";

    private static final String TOKEN_B_CREATE = "tokenBCreateTxn";
    public static final String NFT_CREATE = "nftCreateTxn";
    private static final String SPONSOR = "autoCreateSponsor";
    public static final String LAZY_CREATE_SPONSOR = "lazyCreateSponsor";

    private static final long EXPECTED_HBAR_TRANSFER_AUTO_CREATION_FEE = 39418863L;
    private static final long EXPECTED_MULTI_TOKEN_TRANSFER_AUTO_CREATION_FEE = 42427268L;
    private static final long EXPECTED_SINGLE_TOKEN_TRANSFER_AUTO_CREATE_FEE = 40927290L;
    private static final String HBAR_XFER = "hbarXfer";
    private static final String NFT_XFER = "nftXfer";
    private static final String FT_XFER = "ftXfer";

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
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                /* --- Hbar auto creates */
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
                hollowAccountCreationWithCryptoTransfer(),
                failureAfterHollowAccountCreationReclaimsAlias(),
                /* -- HTS auto creates -- */
                canAutoCreateWithFungibleTokenTransfersToAlias(),
                multipleTokenTransfersSucceed(),
                nftTransfersToAlias(),
                autoCreateWithNftFallBackFeeFails(),
                repeatedAliasInSameTransferListFails(),
                canAutoCreateWithHbarAndTokenTransfers(),
                transferHbarsToEVMAddressAlias(),
                transferFungibleToEVMAddressAlias(),
                transferNonFungibleToEVMAddressAlias(),
                payerBalanceIsReflectsAllChangesBeforeFeeCharging());
    }

    private HapiSpec canAutoCreateWithHbarAndTokenTransfers() {
        final var initialTokenSupply = 1000;
        return defaultHapiSpec("hbarAndTokenTransfers")
                .given(
                        newKeyNamed(VALID_ALIAS),
                        cryptoCreate(TOKEN_TREASURY).balance(10 * ONE_HUNDRED_HBARS),
                        cryptoCreate(CIVILIAN)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(2),
                        tokenCreate(A_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .supplyType(FINITE)
                                .initialSupply(initialTokenSupply)
                                .maxSupply(10L * initialTokenSupply)
                                .treasury(TOKEN_TREASURY)
                                .via(TOKEN_A_CREATE),
                        getTxnRecord(TOKEN_A_CREATE)
                                .hasNewTokenAssociation(A_TOKEN, TOKEN_TREASURY))
                .when(
                        tokenAssociate(CIVILIAN, A_TOKEN),
                        cryptoTransfer(moving(10, A_TOKEN).between(TOKEN_TREASURY, CIVILIAN)),
                        getAccountInfo(CIVILIAN).hasToken(relationshipWith(A_TOKEN).balance(10)))
                .then(
                        cryptoTransfer(
                                        movingHbar(10L).between(CIVILIAN, VALID_ALIAS),
                                        moving(1, A_TOKEN).between(CIVILIAN, VALID_ALIAS))
                                .signedBy(DEFAULT_PAYER, CIVILIAN)
                                .via(TRANSFER_TXN),
                        getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged(),
                        getAliasedAccountInfo(VALID_ALIAS)
                                .has(accountWith().balance(10L))
                                .hasToken(relationshipWith(A_TOKEN)));
    }

    private HapiSpec repeatedAliasInSameTransferListFails() {
        final AtomicReference<TokenID> ftId = new AtomicReference<>();
        final AtomicReference<TokenID> nftId = new AtomicReference<>();
        final AtomicReference<AccountID> partyId = new AtomicReference<>();
        final AtomicReference<AccountID> counterId = new AtomicReference<>();
        final AtomicReference<ByteString> partyAlias = new AtomicReference<>();
        final AtomicReference<ByteString> counterAlias = new AtomicReference<>();

        return defaultHapiSpec("repeatedAliasInSameTransferListFails")
                .given(
                        newKeyNamed(VALID_ALIAS),
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(VALID_ALIAS),
                        cryptoCreate(PARTY).maxAutomaticTokenAssociations(2),
                        cryptoCreate(COUNTERPARTY).maxAutomaticTokenAssociations(2),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(2),
                        tokenCreate(A_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(Long.MAX_VALUE)
                                .treasury(TOKEN_TREASURY)
                                .via(TOKEN_A_CREATE),
                        tokenCreate(NFT_INFINITE_SUPPLY_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .supplyType(TokenSupplyType.INFINITE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .via(NFT_CREATE),
                        mintToken(
                                NFT_INFINITE_SUPPLY_TOKEN,
                                List.of(
                                        ByteString.copyFromUtf8("a"),
                                        ByteString.copyFromUtf8("b"))),
                        cryptoCreate(CIVILIAN).balance(10 * ONE_HBAR),
                        cryptoCreate(SPONSOR)
                                .balance(INITIAL_BALANCE * ONE_HBAR)
                                .maxAutomaticTokenAssociations(10))
                .when(
                        tokenAssociate(SPONSOR, NFT_INFINITE_SUPPLY_TOKEN),
                        cryptoTransfer(
                                movingUnique(NFT_INFINITE_SUPPLY_TOKEN, 1L, 2L)
                                        .between(TOKEN_TREASURY, SPONSOR)),
                        getAccountInfo(SPONSOR).logged(),
                        getAccountInfo(TOKEN_TREASURY).logged(),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var registry = spec.registry();
                                    ftId.set(registry.getTokenID(A_TOKEN));
                                    nftId.set(registry.getTokenID(NFT_INFINITE_SUPPLY_TOKEN));
                                    partyId.set(registry.getAccountID(PARTY));
                                    counterId.set(registry.getAccountID(COUNTERPARTY));
                                    partyAlias.set(
                                            ByteString.copyFrom(asSolidityAddress(partyId.get())));
                                    counterAlias.set(
                                            ByteString.copyFrom(
                                                    asSolidityAddress(counterId.get())));

                                    cryptoTransfer(
                                                    (x, b) ->
                                                            b.addTokenTransfers(
                                                                    TokenTransferList.newBuilder()
                                                                            .addTransfers(
                                                                                    aaWith(
                                                                                            SPONSOR,
                                                                                            -1))
                                                                            .addTransfers(
                                                                                    aaWith(
                                                                                            asAccount(
                                                                                                    "0.0."
                                                                                                            + partyAlias
                                                                                                                    .get()),
                                                                                            +1))
                                                                            .addTransfers(
                                                                                    aaWith(
                                                                                            TOKEN_TREASURY,
                                                                                            -1))
                                                                            .addTransfers(
                                                                                    aaWith(
                                                                                            asAccount(
                                                                                                    "0.0."
                                                                                                            + partyAlias
                                                                                                                    .get()),
                                                                                            +1))))
                                            .signedBy(DEFAULT_PAYER, PARTY, SPONSOR)
                                            .hasKnownStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS);
                                }))
                .then();
    }

    private HapiSpec autoCreateWithNftFallBackFeeFails() {
        final var firstRoyaltyCollector = "firstRoyaltyCollector";
        return defaultHapiSpec("autoCreateWithNftFallBackFeeFails")
                .given(
                        newKeyNamed(VALID_ALIAS),
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(VALID_ALIAS),
                        cryptoCreate(TOKEN_TREASURY)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(2),
                        cryptoCreate(firstRoyaltyCollector).maxAutomaticTokenAssociations(100),
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
                                                20,
                                                fixedHbarFeeInheritingRoyaltyCollector(1),
                                                firstRoyaltyCollector))
                                .via(NFT_CREATE),
                        mintToken(
                                NFT_INFINITE_SUPPLY_TOKEN,
                                List.of(
                                        ByteString.copyFromUtf8("a"),
                                        ByteString.copyFromUtf8("b"),
                                        ByteString.copyFromUtf8("c"),
                                        ByteString.copyFromUtf8("d"),
                                        ByteString.copyFromUtf8("e"))),
                        cryptoCreate(CIVILIAN)
                                .balance(1000 * ONE_HBAR)
                                .maxAutomaticTokenAssociations(2),
                        cryptoCreate("dummy").balance(10 * ONE_HBAR),
                        cryptoCreate(SPONSOR)
                                .balance(ONE_MILLION_HBARS)
                                .maxAutomaticTokenAssociations(10))
                .when(
                        cryptoTransfer(
                                movingUnique(NFT_INFINITE_SUPPLY_TOKEN, 1L, 2L)
                                        .between(TOKEN_TREASURY, SPONSOR)),
                        getAccountInfo(SPONSOR)
                                .hasToken(relationshipWith(NFT_INFINITE_SUPPLY_TOKEN)),
                        // auto creating an account using a nft with fall back royalty fee fails
                        cryptoTransfer(
                                        movingUnique(NFT_INFINITE_SUPPLY_TOKEN, 1, 2)
                                                .between(SPONSOR, VALID_ALIAS))
                                .payingWith(CIVILIAN)
                                .signedBy(CIVILIAN, SPONSOR, VALID_ALIAS)
                                .hasKnownStatus(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE),
                        getAccountInfo(SPONSOR)
                                .hasOwnedNfts(2)
                                .hasToken(relationshipWith(NFT_INFINITE_SUPPLY_TOKEN)),
                        getAccountInfo(TOKEN_TREASURY).logged())
                .then(
                        // But transferring this NFT to a known alias with hbar in it works
                        cryptoTransfer(tinyBarsFromToWithAlias(SPONSOR, VALID_ALIAS, 10 * ONE_HBAR))
                                .payingWith(CIVILIAN)
                                .signedBy(CIVILIAN, SPONSOR, VALID_ALIAS)
                                .via(TRANSFER_TXN),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, VALID_ALIAS)),
                        getTxnRecord(TRANSFER_TXN)
                                .andAllChildRecords()
                                .hasNonStakingChildRecordCount(1),
                        cryptoUpdateAliased(VALID_ALIAS)
                                .maxAutomaticAssociations(10)
                                .signedBy(VALID_ALIAS, DEFAULT_PAYER),
                        cryptoTransfer(
                                        movingUnique(NFT_INFINITE_SUPPLY_TOKEN, 1, 2)
                                                .between(SPONSOR, VALID_ALIAS))
                                .payingWith(SPONSOR)
                                .fee(10 * ONE_HBAR)
                                .signedBy(SPONSOR, VALID_ALIAS)
                                .logged(),
                        getAliasedAccountInfo(VALID_ALIAS).hasOwnedNfts(2));
    }

    private HapiSpec nftTransfersToAlias() {
        final var civilianBal = 10 * ONE_HBAR;
        final var transferFee = 0.44012644 * ONE_HBAR;
        final var multiNftTransfer = "multiNftTransfer";

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
                                .via(NFT_CREATE),
                        tokenCreate(NFT_FINITE_SUPPLY_TOKEN)
                                .supplyType(FINITE)
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
                        cryptoCreate(CIVILIAN).balance(civilianBal))
                .when(
                        tokenAssociate(
                                CIVILIAN, NFT_FINITE_SUPPLY_TOKEN, NFT_INFINITE_SUPPLY_TOKEN),
                        cryptoTransfer(
                                movingUnique(NFT_FINITE_SUPPLY_TOKEN, 3L, 4L)
                                        .between(TOKEN_TREASURY, CIVILIAN),
                                movingUnique(NFT_INFINITE_SUPPLY_TOKEN, 1L, 2L)
                                        .between(TOKEN_TREASURY, CIVILIAN)),
                        getAccountInfo(CIVILIAN)
                                .hasToken(relationshipWith(NFT_FINITE_SUPPLY_TOKEN))
                                .hasToken(relationshipWith(NFT_INFINITE_SUPPLY_TOKEN))
                                .has(accountWith().balance(civilianBal)),
                        cryptoTransfer(
                                        movingUnique(NFT_FINITE_SUPPLY_TOKEN, 3, 4)
                                                .between(CIVILIAN, VALID_ALIAS),
                                        movingUnique(NFT_INFINITE_SUPPLY_TOKEN, 1, 2)
                                                .between(CIVILIAN, VALID_ALIAS))
                                .via(multiNftTransfer)
                                .payingWith(CIVILIAN)
                                .signedBy(CIVILIAN, VALID_ALIAS),
                        getTxnRecord(multiNftTransfer)
                                .andAllChildRecords()
                                .hasNonStakingChildRecordCount(1)
                                .logged(),
                        childRecordsCheck(
                                multiNftTransfer,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .fee(EXPECTED_MULTI_TOKEN_TRANSFER_AUTO_CREATION_FEE)),
                        getAliasedAccountInfo(VALID_ALIAS)
                                .has(accountWith().balance(0).maxAutoAssociations(2).ownedNfts(4))
                                .logged(),
                        getAccountInfo(CIVILIAN)
                                .has(accountWith().balance((long) (civilianBal - transferFee))))
                .then();
    }

    private HapiSpec multipleTokenTransfersSucceed() {
        final var initialTokenSupply = 1000;
        final var multiTokenXfer = "multiTokenXfer";

        return defaultHapiSpec("multipleTokenTransfersSucceed")
                .given(
                        newKeyNamed(VALID_ALIAS),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                        tokenCreate(A_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(initialTokenSupply)
                                .treasury(TOKEN_TREASURY)
                                .via(TOKEN_A_CREATE),
                        tokenCreate(B_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(initialTokenSupply)
                                .treasury(TOKEN_TREASURY)
                                .via(TOKEN_B_CREATE),
                        getTxnRecord(TOKEN_A_CREATE)
                                .hasNewTokenAssociation(A_TOKEN, TOKEN_TREASURY),
                        getTxnRecord(TOKEN_B_CREATE)
                                .hasNewTokenAssociation(B_TOKEN, TOKEN_TREASURY),
                        cryptoCreate(CIVILIAN)
                                .balance(10 * ONE_HBAR)
                                .maxAutomaticTokenAssociations(2))
                .when(
                        cryptoTransfer(
                                        moving(100, A_TOKEN).between(TOKEN_TREASURY, CIVILIAN),
                                        moving(100, B_TOKEN).between(TOKEN_TREASURY, CIVILIAN))
                                .via("transferAToSponsor"),
                        getAccountInfo(TOKEN_TREASURY)
                                .hasToken(relationshipWith(B_TOKEN).balance(900)),
                        getAccountInfo(TOKEN_TREASURY)
                                .hasToken(relationshipWith(A_TOKEN).balance(900)),
                        getAccountInfo(CIVILIAN).hasToken(relationshipWith(A_TOKEN).balance(100)),
                        getAccountInfo(CIVILIAN).hasToken(relationshipWith(B_TOKEN).balance(100)),

                        /* --- transfer same token type to alias --- */
                        cryptoTransfer(
                                        moving(10, A_TOKEN).between(CIVILIAN, VALID_ALIAS),
                                        moving(10, B_TOKEN).between(CIVILIAN, VALID_ALIAS))
                                .via(multiTokenXfer)
                                .payingWith(CIVILIAN)
                                .signedBy(CIVILIAN, VALID_ALIAS)
                                .logged(),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, VALID_ALIAS)),
                        getTxnRecord(multiTokenXfer)
                                .andAllChildRecords()
                                .hasNonStakingChildRecordCount(1)
                                .hasPriority(
                                        recordWith()
                                                .status(SUCCESS)
                                                .tokenTransfers(
                                                        includingFungibleMovement(
                                                                moving(10, A_TOKEN)
                                                                        .to(VALID_ALIAS)))
                                                .tokenTransfers(
                                                        includingFungibleMovement(
                                                                moving(10, B_TOKEN)
                                                                        .to(VALID_ALIAS)))
                                                .autoAssociated(
                                                        accountTokenPairsInAnyOrder(
                                                                List.of(
                                                                        Pair.of(
                                                                                VALID_ALIAS,
                                                                                B_TOKEN),
                                                                        Pair.of(
                                                                                VALID_ALIAS,
                                                                                A_TOKEN)))))
                                .logged(),
                        childRecordsCheck(
                                multiTokenXfer,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .fee(EXPECTED_MULTI_TOKEN_TRANSFER_AUTO_CREATION_FEE)),
                        getAliasedAccountInfo(VALID_ALIAS)
                                .hasToken(relationshipWith(A_TOKEN).balance(10))
                                .hasToken(relationshipWith(B_TOKEN).balance(10))
                                .has(accountWith().balance(0L).maxAutoAssociations(2)),
                        getAccountInfo(CIVILIAN)
                                .hasToken(relationshipWith(A_TOKEN).balance(90))
                                .hasToken(relationshipWith(B_TOKEN).balance(90))
                                .has(accountWith().balanceLessThan(10 * ONE_HBAR)))
                .then(
                        /* --- transfer token to created alias */
                        cryptoTransfer(moving(10, B_TOKEN).between(CIVILIAN, VALID_ALIAS))
                                .via("newXfer")
                                .payingWith(CIVILIAN)
                                .signedBy(CIVILIAN, VALID_ALIAS, TOKEN_TREASURY),
                        getTxnRecord("newXfer")
                                .andAllChildRecords()
                                .hasNonStakingChildRecordCount(0)
                                .logged(),
                        getAliasedAccountInfo(VALID_ALIAS)
                                .hasToken(relationshipWith(A_TOKEN).balance(10))
                                .hasToken(relationshipWith(B_TOKEN).balance(20)));
    }

    private HapiSpec payerBalanceIsReflectsAllChangesBeforeFeeCharging() {
        final var secondAliasKey = "secondAlias";
        final var secondPayer = "secondPayer";
        final AtomicLong totalAutoCreationFees = new AtomicLong();

        return defaultHapiSpec("PayerBalanceIsReflectsAllChangesBeforeFeeCharging")
                .given(
                        newKeyNamed(VALID_ALIAS),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(A_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(1000)
                                .treasury(TOKEN_TREASURY),
                        cryptoCreate(CIVILIAN)
                                .balance(ONE_HUNDRED_HBARS)
                                .maxAutomaticTokenAssociations(1),
                        cryptoTransfer(moving(100, A_TOKEN).between(TOKEN_TREASURY, CIVILIAN)),
                        cryptoTransfer(
                                        moving(10, A_TOKEN).between(CIVILIAN, VALID_ALIAS),
                                        movingHbar(1).between(CIVILIAN, FUNDING))
                                .fee(50 * ONE_HBAR)
                                .payingWith(CIVILIAN)
                                .signedBy(CIVILIAN),
                        getAccountBalance(CIVILIAN)
                                .exposingBalanceTo(
                                        balance ->
                                                totalAutoCreationFees.set(
                                                        ONE_HUNDRED_HBARS - balance - 1)))
                .when(
                        logIt(
                                spec ->
                                        String.format(
                                                "Total auto-creation fees: %d",
                                                totalAutoCreationFees.get())),
                        sourcing(
                                () ->
                                        cryptoCreate(secondPayer)
                                                .maxAutomaticTokenAssociations(1)
                                                .balance(totalAutoCreationFees.get())),
                        cryptoTransfer(moving(100, A_TOKEN).between(TOKEN_TREASURY, secondPayer)))
                .then(
                        newKeyNamed(secondAliasKey),
                        sourcing(
                                () ->
                                        cryptoTransfer(
                                                        moving(10, A_TOKEN)
                                                                .between(
                                                                        secondPayer,
                                                                        secondAliasKey),
                                                        movingHbar(1).between(secondPayer, FUNDING))
                                                .fee(totalAutoCreationFees.get() - 2)
                                                .payingWith(secondPayer)
                                                .signedBy(secondPayer)
                                                .hasKnownStatus(INSUFFICIENT_PAYER_BALANCE)),
                        getAccountBalance(secondPayer)
                                .hasTinyBars(
                                        spec ->
                                                // Should only be charged a few hundred thousand
                                                // tinybar at most
                                                balance ->
                                                        ((totalAutoCreationFees.get() - balance)
                                                                        > 500_000L)
                                                                ? Optional.empty()
                                                                : Optional.of(
                                                                        "Payer was"
                                                                            + " over-charged!")));
    }

    private HapiSpec canAutoCreateWithFungibleTokenTransfersToAlias() {
        final var initialTokenSupply = 1000;
        final var sameTokenXfer = "sameTokenXfer";
        final long transferFee = 1163019L;

        return defaultHapiSpec("canAutoCreateWithFungibleTokenTransfersToAlias")
                .given(
                        newKeyNamed(VALID_ALIAS),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
                        tokenCreate(A_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(initialTokenSupply)
                                .treasury(TOKEN_TREASURY)
                                .via(TOKEN_A_CREATE),
                        tokenCreate(B_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .initialSupply(initialTokenSupply)
                                .treasury(TOKEN_TREASURY)
                                .via(TOKEN_B_CREATE),
                        getTxnRecord(TOKEN_A_CREATE)
                                .hasNewTokenAssociation(A_TOKEN, TOKEN_TREASURY),
                        getTxnRecord(TOKEN_B_CREATE)
                                .hasNewTokenAssociation(B_TOKEN, TOKEN_TREASURY),
                        cryptoCreate(CIVILIAN)
                                .balance(10 * ONE_HBAR)
                                .maxAutomaticTokenAssociations(2))
                .when(
                        cryptoTransfer(
                                        moving(100, A_TOKEN).between(TOKEN_TREASURY, CIVILIAN),
                                        moving(100, B_TOKEN).between(TOKEN_TREASURY, CIVILIAN))
                                .via("transferAToSponsor"),
                        getAccountInfo(TOKEN_TREASURY)
                                .hasToken(relationshipWith(B_TOKEN).balance(900)),
                        getAccountInfo(TOKEN_TREASURY)
                                .hasToken(relationshipWith(A_TOKEN).balance(900)),
                        getAccountInfo(CIVILIAN).hasToken(relationshipWith(A_TOKEN).balance(100)),
                        getAccountInfo(CIVILIAN).hasToken(relationshipWith(B_TOKEN).balance(100)),

                        /* --- transfer same token type to alias --- */
                        cryptoTransfer(
                                        moving(10, A_TOKEN).between(CIVILIAN, VALID_ALIAS),
                                        moving(10, A_TOKEN).between(TOKEN_TREASURY, VALID_ALIAS))
                                .via(sameTokenXfer)
                                .payingWith(CIVILIAN)
                                .signedBy(CIVILIAN, VALID_ALIAS, TOKEN_TREASURY)
                                .logged(),
                        getTxnRecord(sameTokenXfer)
                                .andAllChildRecords()
                                .hasNonStakingChildRecordCount(1)
                                .logged(),
                        childRecordsCheck(
                                sameTokenXfer,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .fee(EXPECTED_SINGLE_TOKEN_TRANSFER_AUTO_CREATE_FEE)),
                        getAliasedAccountInfo(VALID_ALIAS)
                                .hasToken(relationshipWith(A_TOKEN).balance(20)),
                        getAccountInfo(CIVILIAN)
                                .hasToken(relationshipWith(A_TOKEN).balance(90))
                                .has(accountWith().balanceLessThan(10 * ONE_HBAR)),
                        assertionsHold(
                                (spec, opLog) -> {
                                    final var lookup =
                                            getTxnRecord(sameTokenXfer)
                                                    .andAllChildRecords()
                                                    .hasNonStakingChildRecordCount(1)
                                                    .hasNoAliasInChildRecord(0)
                                                    .logged();
                                    allRunFor(spec, lookup);
                                    final var sponsor = spec.registry().getAccountID(DEFAULT_PAYER);
                                    final var payer = spec.registry().getAccountID(CIVILIAN);
                                    final var parent = lookup.getResponseRecord();
                                    final var child = lookup.getChildRecord(0);
                                    assertAliasBalanceAndFeeInChildRecord(
                                            parent, child, sponsor, payer, 0L, transferFee);
                                }))
                .then(
                        /* --- transfer another token to created alias */
                        cryptoTransfer(moving(10, B_TOKEN).between(CIVILIAN, VALID_ALIAS))
                                .via("failedXfer")
                                .payingWith(CIVILIAN)
                                .signedBy(CIVILIAN, VALID_ALIAS, TOKEN_TREASURY)
                                .hasKnownStatus(NO_REMAINING_AUTOMATIC_ASSOCIATIONS));
    }

    private HapiSpec noStakePeriodStartIfNotStakingToNode() {
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

    private HapiSpec hollowAccountCreationWithCryptoTransfer() {
        return defaultHapiSpec("HollowAccountCreationWithCryptoTransfer")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR))
                .when()
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry()
                                                    .getKey(SECP_256K1_SOURCE_KEY)
                                                    .getECDSASecp256K1()
                                                    .toByteArray();
                                    final var evmAddress =
                                            ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                                    final var op =
                                            cryptoTransfer(
                                                            tinyBarsFromTo(
                                                                    LAZY_CREATE_SPONSOR,
                                                                    evmAddress,
                                                                    ONE_HUNDRED_HBARS))
                                                    .hasKnownStatus(SUCCESS)
                                                    .via(TRANSFER_TXN);

                                    final var op2 =
                                            getAliasedAccountInfo(evmAddress)
                                                    .has(
                                                            accountWith()
                                                                    .hasEmptyKey()
                                                                    .expectedBalanceWithChargedUsd(
                                                                            ONE_HUNDRED_HBARS, 0, 0)
                                                                    .autoRenew(
                                                                            THREE_MONTHS_IN_SECONDS)
                                                                    .receiverSigReq(false)
                                                                    .memo(LAZY_MEMO));

                                    allRunFor(spec, op, op2);
                                }));
    }

    private HapiSpec failureAfterHollowAccountCreationReclaimsAlias() {
        final var underfunded = "underfunded";
        final var secondTransferTxn = "SecondTransferTxn";
        final AtomicReference<ByteString> targetAddress = new AtomicReference<>();
        return defaultHapiSpec("FailureAfterHollowAccountCreationReclaimsAlias")
                .given(
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(LAZY_CREATE_SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(cryptoCreate(underfunded).balance(10 * ONE_HBAR))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var ecdsaKey =
                                            spec.registry()
                                                    .getKey(SECP_256K1_SOURCE_KEY)
                                                    .getECDSASecp256K1()
                                                    .toByteArray();
                                    final var evmAddress =
                                            ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                                    targetAddress.set(evmAddress);
                                    final var controlledOp =
                                            cryptoTransfer(
                                                            (sameSpec, b) -> {
                                                                final var sponsorId =
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        LAZY_CREATE_SPONSOR);
                                                                final var underfundedId =
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        underfunded);
                                                                final var funding =
                                                                        spec.registry()
                                                                                .getAccountID(
                                                                                        FUNDING);
                                                                b.setTransfers(
                                                                        TransferList.newBuilder()
                                                                                .addAccountAmounts(
                                                                                        aaWith(
                                                                                                sponsorId,
                                                                                                -ONE_HUNDRED_HBARS))
                                                                                .addAccountAmounts(
                                                                                        aaWith(
                                                                                                evmAddress,
                                                                                                +ONE_HUNDRED_HBARS))
                                                                                .addAccountAmounts(
                                                                                        aaWith(
                                                                                                underfundedId,
                                                                                                -ONE_HUNDRED_HBARS))
                                                                                .addAccountAmounts(
                                                                                        aaWith(
                                                                                                funding,
                                                                                                +ONE_HUNDRED_HBARS))
                                                                                .build());
                                                            })
                                                    .hasKnownStatus(SUCCESS)
                                                    .memo("QUESTIONABLE")
                                                    .signedBy(
                                                            DEFAULT_PAYER,
                                                            LAZY_CREATE_SPONSOR,
                                                            underfunded)
                                                    .hasKnownStatus(INSUFFICIENT_ACCOUNT_BALANCE)
                                                    .via(TRANSFER_TXN);
                                    allRunFor(spec, controlledOp);
                                }),
                        getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged(),
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .nodePayment(123)
                                .hasAnswerOnlyPrecheck(INVALID_ACCOUNT_ID),
                        sourcing(
                                () ->
                                        cryptoTransfer(
                                                        tinyBarsFromTo(
                                                                LAZY_CREATE_SPONSOR,
                                                                targetAddress.get(),
                                                                ONE_HUNDRED_HBARS))
                                                .signedBy(DEFAULT_PAYER, LAZY_CREATE_SPONSOR)
                                                .via(secondTransferTxn)),
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY).logged());
    }

    private HapiSpec canGetBalanceAndInfoViaAlias() {
        final var ed25519SourceKey = "ed25519Alias";
        final var secp256k1SourceKey = "secp256k1Alias";
        final var secp256k1Shape = KeyShape.SECP256K1;
        final var ed25519Shape = KeyShape.ED25519;
        final var autoCreation = "autoCreation";

        return defaultHapiSpec("CanGetBalanceAndInfoViaAlias")
                .given(
                        cryptoCreate(CIVILIAN).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(ed25519SourceKey).shape(ed25519Shape),
                        newKeyNamed(secp256k1SourceKey).shape(secp256k1Shape))
                .when(
                        sortedCryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                CIVILIAN, ed25519SourceKey, ONE_HUNDRED_HBARS),
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, secp256k1SourceKey, ONE_HUNDRED_HBARS))
                                /* Sort the transfer list so the accounts are created in a predictable order (the
                                 * serialized bytes of an Ed25519 are always lexicographically prior to the serialized
                                 * bytes of a secp256k1 key, so now the first child record will _always_ be for the
                                 * ed25519 auto-creation). */
                                .payingWith(GENESIS)
                                .via(autoCreation),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, ed25519SourceKey)),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, secp256k1SourceKey)))
                .then(
                        getTxnRecord(autoCreation)
                                .andAllChildRecords()
                                .hasNoAliasInChildRecord(0)
                                .hasNoAliasInChildRecord(1)
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

    private HapiSpec aliasCanBeUsedOnManyAccountsNotAsAlias() {
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
                                .hasNoAliasInChildRecord(0),
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

    private HapiSpec accountCreatedIfAliasUsedAsPubKey() {
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

    private HapiSpec autoAccountCreationWorksWhenUsingAliasOfDeletedAccount() {
        return defaultHapiSpec("AutoAccountCreationWorksWhenUsingAliasOfDeletedAccount")
                .given(
                        newKeyNamed(ALIAS),
                        newKeyNamed(ALIAS_2),
                        cryptoCreate(PAYER).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(
                        cryptoTransfer(tinyBarsFromToWithAlias(PAYER, ALIAS, ONE_HUNDRED_HBARS))
                                .via("txn"),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, ALIAS)),
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

    private HapiSpec transferFromAliasToAlias() {
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
                        withOpContext((spec, opLog) -> updateSpecFor(spec, ALIAS)),
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

    private HapiSpec transferFromAliasToAccount() {
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
                        withOpContext((spec, opLog) -> updateSpecFor(spec, alias)),
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

    private HapiSpec transferToAccountAutoCreatedUsingAccount() {
        return defaultHapiSpec("transferToAccountAutoCreatedUsingAccount")
                .given(
                        newKeyNamed(TRANSFER_ALIAS),
                        cryptoCreate(PAYER).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(
                        cryptoTransfer(
                                        tinyBarsFromToWithAlias(
                                                PAYER, TRANSFER_ALIAS, ONE_HUNDRED_HBARS))
                                .via("txn"),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, TRANSFER_ALIAS)),
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

    private HapiSpec transferToAccountAutoCreatedUsingAlias() {
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

    private HapiSpec autoAccountCreationUnsupportedAlias() {
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

    private HapiSpec autoAccountCreationBadAlias() {
        final var invalidAlias = VALID_25519_ALIAS.substring(0, 10);

        return defaultHapiSpec("AutoAccountCreationBadAlias")
                .given(cryptoCreate(PAYER).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(
                        cryptoTransfer(tinyBarsFromTo(PAYER, invalidAlias, ONE_HUNDRED_HBARS))
                                .hasKnownStatus(ResponseCodeEnum.INVALID_ALIAS_KEY)
                                .via("transferTxnBad"))
                .then();
    }

    private HapiSpec autoAccountCreationsHappyPath() {
        final var creationTime = new AtomicLong();
        final long transferFee = 185030L;
        return defaultHapiSpec("AutoAccountCreationsHappyPath")
                .given(
                        newKeyNamed(VALID_ALIAS),
                        cryptoCreate(CIVILIAN).balance(10 * ONE_HBAR),
                        cryptoCreate(PAYER).balance(10 * ONE_HBAR),
                        cryptoCreate(SPONSOR).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(
                        cryptoTransfer(
                                        tinyBarsFromToWithAlias(
                                                SPONSOR, VALID_ALIAS, ONE_HUNDRED_HBARS),
                                        tinyBarsFromToWithAlias(CIVILIAN, VALID_ALIAS, ONE_HBAR))
                                .via(TRANSFER_TXN)
                                .payingWith(PAYER))
                .then(
                        getReceipt(TRANSFER_TXN)
                                .andAnyChildReceipts()
                                .hasChildAutoAccountCreations(1),
                        getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged(),
                        getAccountInfo(SPONSOR)
                                .has(
                                        accountWith()
                                                .balance(
                                                        (INITIAL_BALANCE * ONE_HBAR)
                                                                - ONE_HUNDRED_HBARS)
                                                .noAlias()),
                        childRecordsCheck(
                                TRANSFER_TXN,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .fee(EXPECTED_HBAR_TRANSFER_AUTO_CREATION_FEE)),
                        assertionsHold(
                                (spec, opLog) -> {
                                    final var lookup =
                                            getTxnRecord(TRANSFER_TXN)
                                                    .andAllChildRecords()
                                                    .hasNonStakingChildRecordCount(1)
                                                    .hasNoAliasInChildRecord(0)
                                                    .logged();
                                    allRunFor(spec, lookup);
                                    final var sponsor = spec.registry().getAccountID(SPONSOR);
                                    final var payer = spec.registry().getAccountID(PAYER);
                                    final var parent = lookup.getResponseRecord();
                                    final var child = lookup.getChildRecord(0);
                                    assertAliasBalanceAndFeeInChildRecord(
                                            parent,
                                            child,
                                            sponsor,
                                            payer,
                                            ONE_HUNDRED_HBARS + ONE_HBAR,
                                            transferFee);
                                    creationTime.set(child.getConsensusTimestamp().getSeconds());
                                }),
                        sourcing(
                                () ->
                                        getAliasedAccountInfo(VALID_ALIAS)
                                                .has(
                                                        accountWith()
                                                                .key(VALID_ALIAS)
                                                                .expectedBalanceWithChargedUsd(
                                                                        ONE_HUNDRED_HBARS
                                                                                + ONE_HBAR,
                                                                        0,
                                                                        0)
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
            final long transferFee) {
        long receivedBalance = 0;
        long creationFeeSplit = 0;
        long payerBalWithAutoCreationFee = 0;
        for (final var adjust : parent.getTransferList().getAccountAmountsList()) {
            final var id = adjust.getAccountID();
            if (!(id.getAccountNum() < 100
                    || id.equals(sponsor)
                    || id.equals(defaultPayer)
                    || id.getAccountNum() == 800
                    || id.getAccountNum() == 801)) {
                receivedBalance = adjust.getAmount();
            }

            // auto-creation fee is transferred to 0.0.98 (funding account) and 0.0.800, 0.0.801 (if
            // staking is active)
            // from payer
            if (id.getAccountNum() == 98
                    || id.getAccountNum() == 800
                    || id.getAccountNum() == 801) {
                creationFeeSplit += adjust.getAmount();
            }
            // sum of all deductions from the payer along with auto creation fee
            if ((id.getAccountNum() <= 98
                    || id.equals(defaultPayer)
                    || id.getAccountNum() == 800
                    || id.getAccountNum() == 801)) {
                payerBalWithAutoCreationFee += adjust.getAmount();
            }
        }
        assertEquals(newAccountFunding, receivedBalance, "Transferred incorrect amount to alias");
        assertEquals(
                creationFeeSplit - transferFee,
                child.getTransactionFee(),
                "Child record did not specify deducted fee");
        assertEquals(0, payerBalWithAutoCreationFee, "Auto creation fee is deducted from payer");
    }

    private HapiSpec multipleAutoAccountCreations() {
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

    private HapiSpec transferHbarsToEVMAddressAlias() {

        final AtomicReference<AccountID> partyId = new AtomicReference<>();
        final AtomicReference<ByteString> partyAlias = new AtomicReference<>();
        final AtomicReference<ByteString> counterAlias = new AtomicReference<>();

        return defaultHapiSpec("TransferHbarsToEVMAddressAlias")
                .given(
                        cryptoCreate(PARTY).maxAutomaticTokenAssociations(2),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var registry = spec.registry();
                                    final var ecdsaKey = registry.getKey(SECP_256K1_SOURCE_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    assert addressBytes != null;
                                    final var evmAddressBytes = ByteString.copyFrom(addressBytes);
                                    partyId.set(registry.getAccountID(PARTY));
                                    partyAlias.set(
                                            ByteString.copyFrom(asSolidityAddress(partyId.get())));
                                    counterAlias.set(evmAddressBytes);
                                }))
                .when(
                        cryptoTransfer(
                                        (spec, b) ->
                                                b.setTransfers(
                                                        TransferList.newBuilder()
                                                                .addAccountAmounts(
                                                                        aaWith(
                                                                                partyAlias.get(),
                                                                                -2))
                                                                .addAccountAmounts(
                                                                        aaWith(
                                                                                counterAlias.get(),
                                                                                +2))))
                                .signedBy(DEFAULT_PAYER, PARTY)
                                .via(HBAR_XFER))
                .then(
                        getTxnRecord(HBAR_XFER)
                                .hasChildRecordCount(1)
                                .hasChildRecords(recordWith().status(SUCCESS).memo(LAZY_MEMO)));
    }

    private HapiSpec transferFungibleToEVMAddressAlias() {

        final var fungibleToken = "fungibleToken";
        final AtomicReference<TokenID> ftId = new AtomicReference<>();
        final AtomicReference<AccountID> partyId = new AtomicReference<>();
        final AtomicReference<ByteString> partyAlias = new AtomicReference<>();
        final AtomicReference<ByteString> counterAlias = new AtomicReference<>();

        return defaultHapiSpec("TransferFungibleToEVMAddressAlias")
                .given(
                        cryptoCreate(PARTY).maxAutomaticTokenAssociations(2),
                        tokenCreate(fungibleToken).treasury(PARTY).initialSupply(1_000_000),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var registry = spec.registry();
                                    final var ecdsaKey = registry.getKey(SECP_256K1_SOURCE_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    assert addressBytes != null;
                                    final var evmAddressBytes = ByteString.copyFrom(addressBytes);
                                    ftId.set(registry.getTokenID(fungibleToken));
                                    partyId.set(registry.getAccountID(PARTY));
                                    partyAlias.set(
                                            ByteString.copyFrom(asSolidityAddress(partyId.get())));
                                    counterAlias.set(evmAddressBytes);
                                }))
                .when(
                        cryptoTransfer(
                                        (spec, b) ->
                                                b.addTokenTransfers(
                                                        TokenTransferList.newBuilder()
                                                                .setToken(ftId.get())
                                                                .addTransfers(
                                                                        aaWith(
                                                                                partyAlias.get(),
                                                                                -500))
                                                                .addTransfers(
                                                                        aaWith(
                                                                                counterAlias.get(),
                                                                                +500))))
                                .signedBy(DEFAULT_PAYER, PARTY)
                                .via(FT_XFER))
                .then(
                        getTxnRecord(FT_XFER)
                                .hasChildRecordCount(1)
                                .hasChildRecords(recordWith().status(SUCCESS).memo(LAZY_MEMO)));
    }

    private HapiSpec transferNonFungibleToEVMAddressAlias() {

        final var nonFungibleToken = "nonFungibleToken";
        final AtomicReference<TokenID> nftId = new AtomicReference<>();
        final AtomicReference<AccountID> partyId = new AtomicReference<>();
        final AtomicReference<ByteString> partyAlias = new AtomicReference<>();
        final AtomicReference<ByteString> counterAlias = new AtomicReference<>();

        return defaultHapiSpec("TransferNonFungibleToEVMAddressAlias")
                .given(
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(PARTY).maxAutomaticTokenAssociations(2),
                        tokenCreate(nonFungibleToken)
                                .initialSupply(0)
                                .treasury(PARTY)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .supplyKey(MULTI_KEY),
                        mintToken(
                                nonFungibleToken,
                                List.of(copyFromUtf8("Test transfer nft to EVM address alias."))),
                        withOpContext(
                                (spec, opLog) -> {
                                    final var registry = spec.registry();
                                    final var ecdsaKey = registry.getKey(SECP_256K1_SOURCE_KEY);
                                    final var tmp = ecdsaKey.getECDSASecp256K1().toByteArray();
                                    final var addressBytes = recoverAddressFromPubKey(tmp);
                                    assert addressBytes != null;
                                    final var evmAddressBytes = ByteString.copyFrom(addressBytes);
                                    nftId.set(registry.getTokenID(nonFungibleToken));
                                    partyId.set(registry.getAccountID(PARTY));
                                    partyAlias.set(
                                            ByteString.copyFrom(asSolidityAddress(partyId.get())));
                                    counterAlias.set(evmAddressBytes);
                                }))
                .when(
                        cryptoTransfer(
                                        (spec, b) ->
                                                b.addTokenTransfers(
                                                        TokenTransferList.newBuilder()
                                                                .setToken(nftId.get())
                                                                .addNftTransfers(
                                                                        ocWith(
                                                                                accountId(
                                                                                        partyAlias
                                                                                                .get()),
                                                                                accountId(
                                                                                        counterAlias
                                                                                                .get()),
                                                                                1L))))
                                .signedBy(DEFAULT_PAYER, PARTY)
                                .via(NFT_XFER))
                .then(
                        getTxnRecord(NFT_XFER)
                                .hasChildRecordCount(1)
                                .hasChildRecords(recordWith().status(SUCCESS).memo(LAZY_MEMO)));
    }
}
