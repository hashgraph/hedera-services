/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.contract.precompile.airdrops;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingFungiblePendingAirdrop;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingNftPendingAirdrop;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.ADMIN_KEY;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.FEE_SCHEDULE_KEY;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFeeScheduleUpdate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFeeNetOfTransfers;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.contract.precompile.airdrops.SystemContractAirdropHelper.checkForBalances;
import static com.hedera.services.bdd.suites.contract.precompile.airdrops.SystemContractAirdropHelper.checkForEmptyBalance;
import static com.hedera.services.bdd.suites.contract.precompile.airdrops.SystemContractAirdropHelper.prepareAccountAddresses;
import static com.hedera.services.bdd.suites.contract.precompile.airdrops.SystemContractAirdropHelper.prepareContractAddresses;
import static com.hedera.services.bdd.suites.contract.precompile.airdrops.SystemContractAirdropHelper.prepareTokenAddresses;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_HAS_PENDING_AIRDROPS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PENDING_NFT_AIRDROP_ALREADY_EXISTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.OrderedInIsolation;
import com.hedera.services.bdd.spec.dsl.annotations.Account;
import com.hedera.services.bdd.spec.dsl.annotations.Contract;
import com.hedera.services.bdd.spec.dsl.annotations.FungibleToken;
import com.hedera.services.bdd.spec.dsl.annotations.NonFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecNonFungibleToken;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.OptionalLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;

@HapiTestLifecycle
@OrderedInIsolation
@Tag(SMART_CONTRACT)
public class AirdropFromContractTest {

    @Contract(contract = "Airdrop")
    static SpecContract airdropContract;

    @Order(0)
    @HapiTest
    @DisplayName("Contract Airdrops a token to a receiver who is associated to the token")
    public Stream<DynamicTest> airdropTokenToAccount(
            @NonNull @Account(maxAutoAssociations = 10, tinybarBalance = 100L) final SpecAccount receiver,
            @Contract(contract = "EmptyOne", creationGas = 10_000_000L) final SpecContract sender,
            @NonNull @FungibleToken(initialSupply = 1_000_000L) final SpecFungibleToken token) {
        return hapiTest(
                sender.authorizeContract(airdropContract),
                sender.associateTokens(token),
                airdropContract.associateTokens(token),
                receiver.getBalance().andAssert(balance -> balance.hasTokenBalance(token.name(), 0L)),
                token.treasury().transferUnitsTo(sender, 1_000L, token),
                airdropContract
                        .call("tokenAirdrop", token, sender, receiver, 10L)
                        .sending(85_000_000L)
                        .gas(1_500_000L)
                        .via("AirdropTxn"),
                getTxnRecord("AirdropTxn").hasPriority(recordWith().pendingAirdropsCount(0)),
                receiver.getBalance()
                        .andAssert(balance -> balance.hasTokenBalance(token.name(), 10L))
                        .andAssert(balance -> balance.hasTinyBars(100L)),
                receiver.getInfo().andAssert(info -> info.hasAlreadyUsedAutomaticAssociations(1)));
    }

    @Order(1)
    @HapiTest
    @DisplayName("Airdrop token from contact with custom fee")
    public Stream<DynamicTest> airdropTokenWithCustomFee(
            @NonNull @Account(maxAutoAssociations = 10, tinybarBalance = 100L) final SpecAccount receiver,
            @NonNull @Contract(contract = "EmptyOne", creationGas = 10_000_000L) final SpecContract sender,
            @NonNull
                    @FungibleToken(
                            initialSupply = 1_000_000L,
                            keys = {FEE_SCHEDULE_KEY})
                    final SpecFungibleToken token) {
        return hapiTest(withOpContext((spec, opLog) -> {
            allRunFor(
                    spec,
                    sender.associateTokens(token),
                    airdropContract.associateTokens(token),
                    sender.authorizeContract(airdropContract),
                    token.treasury().transferUnitsTo(sender, 500_000L, token),
                    receiver.getBalance().andAssert(balance -> balance.hasTinyBars(100L)),
                    token.treasury().transferUnitsTo(sender, 1_000L, token),
                    tokenFeeScheduleUpdate(token.name())
                            .withCustom(fractionalFeeNetOfTransfers(
                                    1L, 10L, 1L, OptionalLong.of(100L), airdropContract.name())));
            allRunFor(
                    spec,
                    airdropContract
                            .call("tokenAirdrop", token, sender, receiver, 10L)
                            .sending(85_000_000L)
                            .gas(1_500_000L)
                            .via("AirdropTxn"),
                    getTxnRecord("AirdropTxn").hasPriority(recordWith().pendingAirdropsCount(0)),
                    sender.getBalance().andAssert(balance -> balance.hasTokenBalance(token.name(), 500989)),
                    receiver.getBalance().andAssert(balance -> balance.hasTokenBalance(token.name(), 10)),
                    airdropContract.getBalance().andAssert(balance -> balance.hasTokenBalance(token.name(), 1L)),
                    receiver.getInfo().andAssert(info -> info.hasAlreadyUsedAutomaticAssociations(1)));
        }));
    }

    @Order(2)
    @HapiTest
    @DisplayName("Airdrop multiple tokens from contract that is already associated with them")
    public Stream<DynamicTest> airdropMultipleTokens(
            @NonNull @Account(maxAutoAssociations = 5, tinybarBalance = 100L) final SpecAccount receiver,
            @NonNull @Contract(contract = "EmptyOne", creationGas = 10_000_000L) final SpecContract sender,
            @NonNull @FungibleToken(initialSupply = 1_000_000L) final SpecFungibleToken token1,
            @NonNull @FungibleToken(initialSupply = 1_000_000L) final SpecFungibleToken token2,
            @NonNull @FungibleToken(initialSupply = 1_000_000L) final SpecFungibleToken token3,
            @NonNull @NonFungibleToken(numPreMints = 1) final SpecNonFungibleToken nft1,
            @NonNull @NonFungibleToken(numPreMints = 1) final SpecNonFungibleToken nft2,
            @NonNull @NonFungibleToken(numPreMints = 1) final SpecNonFungibleToken nft3) {
        return hapiTest(withOpContext((spec, opLog) -> {
            allRunFor(
                    spec,
                    sender.associateTokens(token1, token2, token3, nft1, nft2, nft3),
                    sender.authorizeContract(airdropContract),
                    token1.treasury().transferUnitsTo(sender, 1_000L, token1),
                    token2.treasury().transferUnitsTo(sender, 1_000L, token2),
                    token3.treasury().transferUnitsTo(sender, 1_000L, token3));
            allRunFor(
                    spec,
                    nft1.treasury().transferNFTsTo(sender, nft1, 1L),
                    nft2.treasury().transferNFTsTo(sender, nft2, 1L),
                    nft3.treasury().transferNFTsTo(sender, nft3, 1L),
                    receiver.associateTokens(token1, token2, token3, nft1, nft2, nft3));
            allRunFor(spec, checkForEmptyBalance(receiver, List.of(token1, token2, token3), List.of()));
            final var serials = new long[] {1L, 1L, 1L};
            allRunFor(
                    spec,
                    airdropContract
                            .call(
                                    "mixedAirdrop",
                                    prepareTokenAddresses(spec, token1, token2, token3),
                                    prepareTokenAddresses(spec, nft1, nft2, nft3),
                                    prepareContractAddresses(spec, sender, sender, sender),
                                    prepareAccountAddresses(spec, receiver, receiver, receiver),
                                    prepareContractAddresses(spec, sender, sender, sender),
                                    prepareAccountAddresses(spec, receiver, receiver, receiver),
                                    10L,
                                    serials)
                            .gas(1750000),
                    checkForBalances(receiver, List.of(token1, token2, token3), List.of(nft1, nft2, nft3)));
        }));
    }

    @Order(3)
    @HapiTest
    @DisplayName("Airdrop token from contact")
    public Stream<DynamicTest> airdropTokenToAccountWithFreeSlots(
            @NonNull @Account(maxAutoAssociations = 0, tinybarBalance = 100L) final SpecAccount receiver,
            @Contract(contract = "EmptyOne", creationGas = 10_000_000L) final SpecContract sender,
            @NonNull @FungibleToken(initialSupply = 1_000_000L) final SpecFungibleToken token) {
        return hapiTest(
                receiver.associateTokens(token),
                sender.associateTokens(token),
                sender.authorizeContract(airdropContract),
                token.treasury().transferUnitsTo(sender, 500_000L, token),
                airdropContract
                        .call("tokenAirdrop", token, sender, receiver, 10L)
                        .gas(10000000)
                        .sending(5L)
                        .via("AirdropTxn"),
                getTxnRecord("AirdropTxn").logged().andAllChildRecords(),
                receiver.getBalance()
                        .andAssert(balance -> balance.hasTokenBalance(token.name(), 10L))
                        .andAssert(balance -> balance.hasTinyBars(100L)),
                receiver.getInfo().andAssert(info -> info.hasAlreadyUsedAutomaticAssociations(0)));
    }

    @Order(5)
    @HapiTest
    @DisplayName("Contract account airdrops a single token to an ECDSA account")
    public Stream<DynamicTest> airdropTokenToECDSAAccount(
            @NonNull @Account(maxAutoAssociations = 10, tinybarBalance = 100L) final SpecAccount receiver,
            @Contract(contract = "EmptyOne", creationGas = 10_000_000L) final SpecContract sender,
            @NonNull @FungibleToken(initialSupply = 1_000_000L) final SpecFungibleToken token) {

        return hapiTest(withOpContext((spec, opLog) -> {
            allRunFor(
                    spec,
                    receiver.getBalance(),
                    newKeyNamed("key").shape(KeyShape.SECP256K1),
                    cryptoUpdate(receiver.name()).key("key"),
                    sender.authorizeContract(airdropContract),
                    sender.associateTokens(token),
                    airdropContract.associateTokens(token),
                    receiver.getBalance().andAssert(balance -> balance.hasTokenBalance(token.name(), 0L)),
                    token.treasury().transferUnitsTo(sender, 1_000L, token),
                    airdropContract
                            .call("tokenAirdrop", token, sender, receiver, 10L)
                            .sending(85_000_000L)
                            .gas(1_500_000L)
                            .via("AirdropTxn"),
                    getTxnRecord("AirdropTxn").hasPriority(recordWith().pendingAirdropsCount(0)),
                    receiver.getBalance()
                            .andAssert(balance -> balance.hasTokenBalance(token.name(), 10L))
                            .andAssert(balance -> balance.hasTinyBars(100L)),
                    receiver.getInfo().andAssert(info -> info.hasAlreadyUsedAutomaticAssociations(1)));
        }));
    }

    @Order(4)
    @HapiTest
    @DisplayName("Contract account airdrops a single token to an ED25519 account")
    public Stream<DynamicTest> airdropTokenToED25519Account(
            @NonNull @Account(maxAutoAssociations = 10, tinybarBalance = 100L) final SpecAccount receiver,
            @Contract(contract = "EmptyOne", creationGas = 10_000_000L) final SpecContract sender,
            @NonNull @FungibleToken(initialSupply = 1_000_000L) final SpecFungibleToken token) {

        return hapiTest(withOpContext((spec, opLog) -> {
            allRunFor(
                    spec,
                    receiver.getBalance(),
                    newKeyNamed("key").shape(KeyShape.ED25519),
                    cryptoUpdate(receiver.name()).key("key"),
                    sender.authorizeContract(airdropContract),
                    sender.associateTokens(token),
                    airdropContract.associateTokens(token),
                    receiver.getBalance().andAssert(balance -> balance.hasTokenBalance(token.name(), 0L)),
                    token.treasury().transferUnitsTo(sender, 1_000L, token),
                    airdropContract
                            .call("tokenAirdrop", token, sender, receiver, 10L)
                            .sending(85_000_000L)
                            .gas(1_500_000L)
                            .via("AirdropTxn"),
                    getTxnRecord("AirdropTxn").hasPriority(recordWith().pendingAirdropsCount(0)),
                    receiver.getBalance()
                            .andAssert(balance -> balance.hasTokenBalance(token.name(), 10L))
                            .andAssert(balance -> balance.hasTinyBars(100L)),
                    receiver.getInfo().andAssert(info -> info.hasAlreadyUsedAutomaticAssociations(1)));
        }));
    }

    @Order(6)
    @HapiTest
    @DisplayName("Contract account airdrops a single token to an account alias with free association slots")
    public Stream<DynamicTest> airdropToAccountWithFreeAutoAssocSlots(
            @Contract(contract = "EmptyOne", creationGas = 10_000_000L) final SpecContract sender,
            @NonNull @Account(maxAutoAssociations = -1, tinybarBalance = 100L) final SpecAccount receiver,
            @FungibleToken(initialSupply = 1_000_000L) final SpecFungibleToken token,
            @NonFungibleToken(numPreMints = 1) final SpecNonFungibleToken nft) {
        return hapiTest(withOpContext((spec, opLog) -> {
            allRunFor(
                    spec,
                    sender.authorizeContract(airdropContract),
                    sender.associateTokens(token, nft),
                    airdropContract.associateTokens(token, nft),
                    receiver.getBalance().andAssert(balance -> balance.hasTokenBalance(token.name(), 0L)),
                    receiver.getBalance().andAssert(balance -> balance.hasTokenBalance(nft.name(), 0L)),
                    token.treasury().transferUnitsTo(sender, 1_000L, token),
                    nft.treasury().transferNFTsTo(sender, nft, 1L));
            allRunFor(
                    spec,
                    airdropContract
                            .call("tokenAirdrop", token, sender, receiver, 10L)
                            .sending(85_000_000L)
                            .gas(1_500_000L),
                    airdropContract
                            .call("nftAirdrop", nft, sender, receiver, 1L)
                            .sending(85_000_000L)
                            .gas(1_500_000L),
                    receiver.getInfo().andAssert(info -> info.hasAlreadyUsedAutomaticAssociations(2)),
                    receiver.getBalance().andAssert(balance -> balance.hasTokenBalance(token.name(), 10L)),
                    receiver.getBalance().andAssert(balance -> balance.hasTokenBalance(nft.name(), 1L)));
        }));
    }

    @Nested
    class AirdropFromContractNegativeCases {

        @HapiTest
        @DisplayName("Airdrop token with custom fees while the sender cannot pay the fees")
        public Stream<DynamicTest> airdropFromContractWhileTheSenderCannotPayTheCustomFees(
                @NonNull @Account(maxAutoAssociations = 10, tinybarBalance = 100L) final SpecAccount receiver,
                @Contract(contract = "EmptyOne", creationGas = 10_000_000L) final SpecContract sender,
                @NonNull
                        @FungibleToken(
                                initialSupply = 1_000_000L,
                                name = "airdropToken",
                                keys = {ADMIN_KEY, FEE_SCHEDULE_KEY})
                        final SpecFungibleToken token,
                @NonNull @FungibleToken(initialSupply = 1_000_000L) final SpecFungibleToken tokenForFee) {
            return hapiTest(withOpContext((spec, opLog) -> {
                allRunFor(
                        spec,
                        sender.associateTokens(token, tokenForFee),
                        tokenAssociate(GENESIS, tokenForFee.name()),
                        sender.authorizeContract(airdropContract),
                        token.treasury().transferUnitsTo(sender, 500_000L, token),
                        tokenFeeScheduleUpdate(token.name()).withCustom(fixedHtsFee(5L, tokenForFee.name(), GENESIS)));
                allRunFor(
                        spec,
                        airdropContract
                                .call("tokenAirdrop", token, sender, receiver, 10L)
                                .sending(85_000_000L)
                                .gas(1_500_000L)
                                .via("AirdropTxn")
                                .andAssert(txn -> txn.hasKnownStatuses(
                                        CONTRACT_REVERT_EXECUTED, INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE)));
            }));
        }

        @HapiTest
        @DisplayName("Airdrop nft to the same account twice should fail with PENDING_NFT_AIRDROP_ALREADY_EXISTS")
        public Stream<DynamicTest> airdropNftToTheSameAccountTwice(
                @NonNull @Account(maxAutoAssociations = 10, tinybarBalance = 100L) final SpecAccount associatedReceiver,
                @NonNull @Account(maxAutoAssociations = 0, tinybarBalance = 100L)
                        final SpecAccount notAssociatedReceiver,
                @Contract(contract = "EmptyOne", creationGas = 10_000_000L) final SpecContract sender,
                @NonNull @NonFungibleToken(numPreMints = 1) final SpecNonFungibleToken nft) {
            return hapiTest(withOpContext((spec, opLog) -> {
                allRunFor(
                        spec,
                        sender.authorizeContract(airdropContract),
                        sender.associateTokens(nft),
                        nft.treasury().transferNFTsTo(sender, nft, 1L),
                        associatedReceiver.associateTokens(nft),
                        notAssociatedReceiver
                                .getBalance()
                                .andAssert(balance -> balance.hasTokenBalance(nft.name(), 0L)));
                allRunFor(
                        spec,
                        // Airdrop the same nft serial to the same account
                        // when the account is already associated with the nft we don't get a pending airdrop
                        // so when we try to do so with a single airdrop it will fail with
                        // TOKEN_ID_REPEATED_IN_TOKEN_LIST
                        airdropContract
                                .call(
                                        "nftNAmountAirdrops",
                                        prepareTokenAddresses(spec, nft, nft, nft),
                                        prepareContractAddresses(spec, sender, sender, sender),
                                        prepareAccountAddresses(
                                                spec, associatedReceiver, associatedReceiver, associatedReceiver),
                                        new long[] {1L, 1L, 1L})
                                .sending(85_000_000L)
                                .gas(1_500_000L)
                                .andAssert(txn -> txn.hasKnownStatuses(
                                        CONTRACT_REVERT_EXECUTED, TOKEN_ID_REPEATED_IN_TOKEN_LIST)));
                allRunFor(
                        spec,
                        // We validate the same case but with an account that is not associated with the nft
                        airdropContract
                                .call(
                                        "nftNAmountAirdrops",
                                        prepareTokenAddresses(spec, nft, nft, nft),
                                        prepareContractAddresses(spec, sender, sender, sender),
                                        prepareAccountAddresses(
                                                spec,
                                                notAssociatedReceiver,
                                                notAssociatedReceiver,
                                                notAssociatedReceiver),
                                        new long[] {1L, 1L, 1L})
                                .sending(85_000_000L)
                                .gas(1_500_000L)
                                .andAssert(txn -> txn.hasKnownStatuses(
                                        CONTRACT_REVERT_EXECUTED, TOKEN_ID_REPEATED_IN_TOKEN_LIST)));
                allRunFor(
                        spec,
                        // Now we airdrop a single to the same account to go to the pending airdrop list
                        airdropContract
                                .call("nftAirdrop", nft, sender, notAssociatedReceiver, 1L)
                                .sending(85_000_000L)
                                .gas(1_500_000L)
                                .via("AirdropToPendingState"),
                        getTxnRecord("AirdropToPendingState")
                                .hasChildRecords(recordWith()
                                        .pendingAirdrops(includingNftPendingAirdrop(movingUnique(nft.name(), 1L)
                                                .between(sender.name(), notAssociatedReceiver.name())))));
                allRunFor(
                        spec,
                        // Now we try to airdrop the same nft serial to the same account that should fail with
                        // PENDING_NFT_AIRDROP_ALREADY_EXISTS
                        airdropContract
                                .call("nftAirdrop", nft, sender, notAssociatedReceiver, 1L)
                                .sending(85_000_000L)
                                .gas(1_500_000L)
                                .andAssert(txn -> txn.hasKnownStatuses(
                                        CONTRACT_REVERT_EXECUTED, PENDING_NFT_AIRDROP_ALREADY_EXISTS)));
            }));
        }

        @HapiTest
        @DisplayName(
                "Airdrop token amount of Long.MAX_VALUE then try to airdrop 1 more token to the same receiver should fail")
        public Stream<DynamicTest> airdropMaxLongPlusOneShouldFail(
                @NonNull @FungibleToken(initialSupply = Long.MAX_VALUE) final SpecFungibleToken token,
                @NonNull @Contract(contract = "EmptyOne", creationGas = 100_000_000L) final SpecContract sender,
                @NonNull @Account(maxAutoAssociations = 1, tinybarBalance = 100L) final SpecAccount receiver) {
            return hapiTest(withOpContext((spec, opLog) -> {
                allRunFor(
                        spec,
                        sender.authorizeContract(airdropContract),
                        sender.associateTokens(token),
                        token.treasury().transferUnitsTo(sender, Long.MAX_VALUE, token));
                allRunFor(
                        spec,
                        airdropContract
                                .call("tokenAirdrop", token, sender, receiver, Long.MAX_VALUE)
                                .sending(85_000_000L)
                                .gas(1_500_000L));
                allRunFor(
                        spec,
                        receiver.getBalance()
                                .andAssert(balance -> balance.hasTokenBalance(token.name(), Long.MAX_VALUE)));
                allRunFor(
                        spec,
                        airdropContract
                                .call("tokenAirdrop", token, sender, receiver, 1L)
                                .sending(85_000_000L)
                                .gas(1_500_000L)
                                .andAssert(txn ->
                                        txn.hasKnownStatuses(CONTRACT_REVERT_EXECUTED, INSUFFICIENT_TOKEN_BALANCE)));
            }));
        }

        @HapiTest
        @DisplayName("Contract tries to airdrop a token to itself")
        public Stream<DynamicTest> airdropTokenToItself(
                @NonNull @FungibleToken(initialSupply = 1_000_000L) final SpecFungibleToken token,
                @NonNull @Contract(contract = "EmptyOne", creationGas = 10_000_000L) final SpecContract sender) {
            return hapiTest(withOpContext((spec, opLog) -> {
                allRunFor(
                        spec,
                        sender.authorizeContract(airdropContract),
                        sender.associateTokens(token),
                        token.treasury().transferUnitsTo(sender, 1_000L, token));
                allRunFor(
                        spec,
                        airdropContract
                                .call("tokenAirdrop", token, sender, sender, 10L)
                                .sending(85_000_000L)
                                .gas(1_500_000L)
                                .andAssert(txn -> txn.hasKnownStatuses(
                                        CONTRACT_REVERT_EXECUTED, ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS)));
            }));
        }

        @HapiTest
        @DisplayName("Contract airdrops to a pending state then tries to SELFDESTRUCT")
        public Stream<DynamicTest> contractAirdropsThenSelfdestructs(
                @NonNull @FungibleToken(initialSupply = 100) final SpecFungibleToken token,
                @NonNull @Contract(contract = "EmptyOne", creationGas = 100_000_000L) final SpecContract contract,
                @NonNull @Account(maxAutoAssociations = 0, tinybarBalance = 100L) final SpecAccount receiver) {
            return hapiTest(withOpContext((spec, opLog) -> {
                allRunFor(
                        spec,
                        contract.authorizeContract(airdropContract),
                        contract.associateTokens(token),
                        token.treasury().transferUnitsTo(contract, 100, token));
                allRunFor(
                        spec,
                        airdropContract
                                .call("tokenAirdrop", token, contract, receiver, 10L)
                                .sending(85_000_000L)
                                .gas(1_500_000L)
                                .via("pendingAirdropTxn"),
                        getTxnRecord("pendingAirdropTxn")
                                .hasChildRecords(recordWith()
                                        .pendingAirdrops(
                                                includingFungiblePendingAirdrop(TokenMovement.moving(10L, token.name())
                                                        .between(contract.name(), receiver.name())))));
                allRunFor(
                        spec,
                        contractDelete(contract.name())
                                .hasKnownStatusFrom(CONTRACT_REVERT_EXECUTED, ACCOUNT_HAS_PENDING_AIRDROPS));
            }));
        }
    }
}
