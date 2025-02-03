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
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.FEE_SCHEDULE_KEY;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFeeScheduleUpdate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFeeNetOfTransfers;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.precompile.airdrops.SystemContractAirdropHelper.checkForBalances;
import static com.hedera.services.bdd.suites.contract.precompile.airdrops.SystemContractAirdropHelper.checkForEmptyBalance;
import static com.hedera.services.bdd.suites.contract.precompile.airdrops.SystemContractAirdropHelper.prepareAccountAddresses;
import static com.hedera.services.bdd.suites.contract.precompile.airdrops.SystemContractAirdropHelper.prepareContractAddresses;
import static com.hedera.services.bdd.suites.contract.precompile.airdrops.SystemContractAirdropHelper.prepareTokenAddresses;

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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.OptionalLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
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
}
