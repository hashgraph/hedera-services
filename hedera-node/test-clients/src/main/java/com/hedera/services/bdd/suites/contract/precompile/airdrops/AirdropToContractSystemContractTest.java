/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.includingFungiblePendingAirdrop;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.ADMIN_KEY;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.FEE_SCHEDULE_KEY;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFeeScheduleUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFeeNetOfTransfers;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.precompile.airdrops.SystemContractAirdropHelper.checkForBalances;
import static com.hedera.services.bdd.suites.contract.precompile.airdrops.SystemContractAirdropHelper.checkForEmptyBalance;
import static com.hedera.services.bdd.suites.contract.precompile.airdrops.SystemContractAirdropHelper.prepareAccountAddresses;
import static com.hedera.services.bdd.suites.contract.precompile.airdrops.SystemContractAirdropHelper.prepareFTAirdrops;
import static com.hedera.services.bdd.suites.contract.precompile.airdrops.SystemContractAirdropHelper.prepareTokenAddresses;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.OrderedInIsolation;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.dsl.annotations.Account;
import com.hedera.services.bdd.spec.dsl.annotations.Contract;
import com.hedera.services.bdd.spec.dsl.annotations.FungibleToken;
import com.hedera.services.bdd.spec.dsl.annotations.NonFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecNonFungibleToken;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;

@HapiTestLifecycle
@OrderedInIsolation
@Tag(SMART_CONTRACT)
public class AirdropToContractSystemContractTest {

    @Contract(contract = "Airdrop")
    static SpecContract airdropContract;

    @Account(tinybarBalance = 100_000_000_000_000_000L)
    static SpecAccount sender;

    @BeforeAll
    public static void setup(@NonNull final TestLifecycle lifecycle) {
        lifecycle.doAdhoc(sender.authorizeContract(airdropContract));
    }

    @Nested
    @DisplayName("With the Receiver already associated")
    class AirdropToAssociatedReceiverContract {

        @Order(0)
        @HapiTest
        @DisplayName("Can airdrop fungible token to a contract that is already associated to it")
        public Stream<DynamicTest> airdropToContract(
                @Contract(contract = "AssociateContract", isImmutable = true) SpecContract receiverContract,
                @FungibleToken(initialSupply = 1000L) SpecFungibleToken token) {
            return hapiTest(
                    receiverContract.call("associateToken", token).gas(1_000_000L),
                    sender.associateTokens(token),
                    token.treasury().transferUnitsTo(sender, 1000L, token),
                    airdropContract
                            .call("tokenAirdrop", token, sender, receiverContract, 10L)
                            .gas(1500000),
                    receiverContract.getBalance().andAssert(balance -> balance.hasTokenBalance(token.name(), 10L)));
        }

        @Order(1)
        @HapiTest
        @DisplayName("Can airdorp multiple tokens to contract that is already associated with them")
        public Stream<DynamicTest> airdropTokensToContract(
                @Contract(contract = "AssociateContract", isImmutable = true) SpecContract receiverContract,
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
                        token1.treasury().transferUnitsTo(sender, 1_000L, token1),
                        token2.treasury().transferUnitsTo(sender, 1_000L, token2),
                        token3.treasury().transferUnitsTo(sender, 1_000L, token3));
                allRunFor(
                        spec,
                        nft1.treasury().transferNFTsTo(sender, nft1, 1L),
                        nft2.treasury().transferNFTsTo(sender, nft2, 1L),
                        nft3.treasury().transferNFTsTo(sender, nft3, 1L),
                        receiverContract
                                .call("associateTokens", (Object)
                                        prepareTokenAddresses(spec, token1, token2, token3, nft1, nft2, nft3))
                                .gas(5_000_000L));
                allRunFor(
                        spec,
                        checkForEmptyBalance(
                                receiverContract, List.of(token1, token2, token3), List.of(nft1, nft2, nft3)));
                final var serials = new long[] {1L, 1L, 1L};
                allRunFor(
                        spec,
                        airdropContract
                                .call(
                                        "mixedAirdrop",
                                        prepareTokenAddresses(spec, token1, token2, token3),
                                        prepareTokenAddresses(spec, nft1, nft2, nft3),
                                        prepareAccountAddresses(spec, sender, sender, sender),
                                        prepareAccountAddresses(
                                                spec, receiverContract, receiverContract, receiverContract),
                                        prepareAccountAddresses(spec, sender, sender, sender),
                                        prepareAccountAddresses(
                                                spec, receiverContract, receiverContract, receiverContract),
                                        10L,
                                        serials)
                                .gas(1750000),
                        checkForBalances(receiverContract, List.of(token1, token2, token3), List.of(nft1, nft2, nft3)));
            }));
        }

        @Order(2)
        @HapiTest
        @DisplayName("Can airdrop multiple tokens to a contract that is already associated to some of them")
        public Stream<DynamicTest> canAirdropTokensToContractWithSomeAssociations(
                @Contract(contract = "AssociateContract", isImmutable = true, maxAutoAssociations = 2)
                        SpecContract receiverContract,
                @NonNull @FungibleToken(initialSupply = 1_000_000L) final SpecFungibleToken token1,
                @NonNull @FungibleToken(initialSupply = 1_000_000L) final SpecFungibleToken token2,
                @NonNull @NonFungibleToken(numPreMints = 1) final SpecNonFungibleToken nft1,
                @NonNull @NonFungibleToken(numPreMints = 1) final SpecNonFungibleToken nft2) {
            return hapiTest(withOpContext((spec, opLog) -> {
                allRunFor(
                        spec,
                        sender.associateTokens(token1, token2, nft1, nft2),
                        token1.treasury().transferUnitsTo(sender, 1_000L, token1),
                        token2.treasury().transferUnitsTo(sender, 1_000L, token2));
                allRunFor(
                        spec,
                        nft1.treasury().transferNFTsTo(sender, nft1, 1L),
                        nft2.treasury().transferNFTsTo(sender, nft2, 1L),
                        receiverContract
                                .call("associateTokens", (Object) prepareTokenAddresses(spec, token1, nft1))
                                .gas(1_500_000L));
                allRunFor(spec, checkForEmptyBalance(receiverContract, List.of(token1, token2), List.of(nft1, nft2)));
                final var serials = new long[] {1L, 1L};
                allRunFor(
                        spec,
                        airdropContract
                                .call(
                                        "mixedAirdrop",
                                        prepareTokenAddresses(spec, token1, token2),
                                        prepareTokenAddresses(spec, nft1, nft2),
                                        prepareAccountAddresses(spec, sender, sender),
                                        prepareAccountAddresses(spec, receiverContract, receiverContract),
                                        prepareAccountAddresses(spec, sender, sender),
                                        prepareAccountAddresses(spec, receiverContract, receiverContract),
                                        10L,
                                        serials)
                                .gas(1_500_000L)
                                .sending(85_000_000L),
                        checkForBalances(receiverContract, List.of(token1, token2), List.of(nft1, nft2)));
            }));
        }

        @Order(3)
        @HapiTest
        @DisplayName(
                "Can airdrop two tokens to contract with no remaining auto assoc slot and already associated to one of the tokens")
        public Stream<DynamicTest> canAirdropTwoTokensToContractWithNoAutoAssocSlots(
                @Contract(contract = "AssociateContract", isImmutable = true, maxAutoAssociations = 0)
                        SpecContract receiverContract,
                @NonNull @FungibleToken(initialSupply = 1_000_000L) final SpecFungibleToken token1,
                @NonNull @FungibleToken(initialSupply = 1_000_000L) final SpecFungibleToken token2) {
            return hapiTest(withOpContext((spec, opLog) -> {
                allRunFor(
                        spec,
                        sender.associateTokens(token1, token2),
                        token1.treasury().transferUnitsTo(sender, 1_000L, token1),
                        token2.treasury().transferUnitsTo(sender, 1_000L, token2));
                allRunFor(spec, receiverContract.call("associateToken", token1).gas(1_000_000L));
                checkForEmptyBalance(receiverContract, List.of(token1, token2), List.of());
                allRunFor(
                        spec,
                        airdropContract
                                .call(
                                        "tokenNAmountAirdrops",
                                        prepareTokenAddresses(spec, token1, token2),
                                        prepareAccountAddresses(spec, sender, sender),
                                        prepareAccountAddresses(spec, receiverContract, receiverContract),
                                        10L)
                                .gas(1_500_000L)
                                .sending(85_000_000L)
                                .via("pendingAirdrop"),
                        checkForBalances(receiverContract, List.of(token1), List.of()),
                        checkForEmptyBalance(receiverContract, List.of(token2), List.of()),
                        validateChargedUsd("pendingAirdrop", 0.124),
                        getTxnRecord("pendingAirdrop")
                                .logged()
                                .hasChildRecords(recordWith()
                                        .pendingAirdrops(includingFungiblePendingAirdrop(
                                                prepareFTAirdrops(sender, receiverContract, List.of(token2))
                                                        .toArray(TokenMovement[]::new)))));
            }));
        }

        @Order(4)
        @HapiTest
        @DisplayName(
                "Airdropped token with custom fees to be paid by the contract receiver should be payed by the sender")
        public Stream<DynamicTest> airdropWithCustomFees(
                @Contract(contract = "AssociateContract", isImmutable = true) SpecContract receiverContract,
                @NonNull
                        @FungibleToken(
                                initialSupply = 1_000_000L,
                                keys = {ADMIN_KEY, FEE_SCHEDULE_KEY})
                        final SpecFungibleToken token) {
            return hapiTest(withOpContext((spec, opLog) -> {
                allRunFor(
                        spec,
                        sender.associateTokens(token),
                        airdropContract.associateTokens(token),
                        receiverContract.call("associateToken", token).gas(1_000_000L),
                        // Initial check for receiver balance
                        receiverContract.getBalance().andAssert(balance -> balance.hasTinyBars(0L)),
                        token.treasury().transferUnitsTo(sender, 1_000L, token),
                        tokenFeeScheduleUpdate(token.name())
                                .withCustom(fractionalFee(1L, 10L, 1L, OptionalLong.of(100L), airdropContract.name())));
                allRunFor(
                        spec,
                        airdropContract
                                .call("tokenAirdrop", token, sender, receiverContract, 10L)
                                .gas(1_500_000L),
                        // Fractional fee is paid by the transferred value
                        receiverContract.getBalance().andAssert(balance -> balance.hasTokenBalance(token.name(), 9)),
                        airdropContract.getBalance().andAssert(balance -> balance.hasTokenBalance(token.name(), 1L)));
            }));
        }

        @Order(5)
        @HapiTest
        @DisplayName(
                "Airdropped token with custom fees (net of transfers = true) to be paid by the contract receiver should be payed by the sender")
        public Stream<DynamicTest> airdropWithCustomFeesNetOfTransfersTrue(
                @Contract(contract = "AssociateContract", isImmutable = true) SpecContract receiverContract,
                @NonNull
                        @FungibleToken(
                                initialSupply = 1_000_000L,
                                keys = {ADMIN_KEY, FEE_SCHEDULE_KEY})
                        final SpecFungibleToken token) {
            return hapiTest(withOpContext((spec, opLog) -> {
                allRunFor(
                        spec,
                        sender.associateTokens(token),
                        airdropContract.associateTokens(token),
                        receiverContract.call("associateToken", token).gas(1_000_000L),
                        // Initial check for receiver balance
                        receiverContract.getBalance().andAssert(balance -> balance.hasTinyBars(0L)),
                        token.treasury().transferUnitsTo(sender, 1_000L, token),
                        tokenFeeScheduleUpdate(token.name())
                                .withCustom(fractionalFeeNetOfTransfers(
                                        1L, 10L, 1L, OptionalLong.of(100L), airdropContract.name())));
                allRunFor(
                        spec,
                        airdropContract
                                .call("tokenAirdrop", token, sender, receiverContract, 10L)
                                .gas(1_500_000L),
                        receiverContract.getBalance().andAssert(balance -> balance.hasTokenBalance(token.name(), 10)),
                        airdropContract.getBalance().andAssert(balance -> balance.hasTokenBalance(token.name(), 1L)));
            }));
        }

        @Order(6)
        @HapiTest
        @DisplayName(
                "Airdropped token with custom fees to be paid by the contract receiver that is a fee collector for another fee would not be paid")
        public Stream<DynamicTest> airdropWithCustomFeeWhereReceiverIsCollectorForAnotherFee(
                @Contract(contract = "AssociateContract", isImmutable = true) SpecContract receiverContract,
                @NonNull
                        @FungibleToken(
                                initialSupply = 1_000_000L,
                                keys = {ADMIN_KEY, FEE_SCHEDULE_KEY})
                        final SpecFungibleToken token) {
            return hapiTest(withOpContext((spec, opLog) -> {
                allRunFor(
                        spec,
                        sender.associateTokens(token),
                        airdropContract.associateTokens(token),
                        receiverContract.call("associateToken", token).gas(1_000_000L),
                        // Initial check for receiver balance
                        receiverContract.getBalance().andAssert(balance -> balance.hasTinyBars(0L)),
                        token.treasury().transferUnitsTo(sender, 1_000L, token),
                        tokenFeeScheduleUpdate(token.name())
                                .withCustom(
                                        fractionalFee(1L, 10L, 1L, OptionalLong.of(100L), airdropContract.name(), true))
                                .withCustom(fixedHbarFee(10L, receiverContract.name())));
                allRunFor(
                        spec,
                        airdropContract
                                .call("tokenAirdrop", token, sender, receiverContract, 10L)
                                .gas(1_500_000L),
                        receiverContract.getBalance().andAssert(balance -> balance.hasTokenBalance(token.name(), 10)),
                        airdropContract.getBalance().andAssert(balance -> balance.hasTokenBalance(token.name(), 0L)));
            }));
        }

        @Order(7)
        @HapiTest
        @DisplayName(
                "Airdropped token with custom fees to be paid by the contract receiver when the collector is contract should not be paid")
        public Stream<DynamicTest> airdropWithCustomFeesToContractCollector() {
            return hapiTest(withOpContext((spec, opLog) -> {
                final var token = "token";
                final var receiverContract = "receiverContract";
                final var tokenAddress = new AtomicReference<Address>();
                final var receiverContractAddress = new AtomicReference<String>();
                allRunFor(
                        spec,
                        uploadInitCode("AssociateContract"),
                        contractCreate(receiverContract)
                                .bytecode("AssociateContract")
                                .gas(5_000_000L)
                                .exposingNumTo(num -> receiverContractAddress.set(asHexedSolidityAddress(0, 0, num))),
                        newKeyNamed("adminKey"),
                        newKeyNamed("feeScheduleKey"),
                        tokenCreate(token)
                                .initialSupply(1_000_000L)
                                .treasury(receiverContract)
                                .adminKey("adminKey")
                                .feeScheduleKey("feeScheduleKey")
                                .exposingAddressTo(tokenAddress::set),
                        tokenAssociate(sender.name(), token),
                        // Initial check for receiver balance
                        getAccountBalance(receiverContract).hasTokenBalance(token, 1_000_000L),
                        cryptoTransfer(moving(1_000L, token).between(receiverContract, sender.name())),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fractionalFeeNetOfTransfers(
                                        1L, 10L, 1L, OptionalLong.of(100L), receiverContract)));
                allRunFor(
                        spec,
                        airdropContract
                                .call(
                                        "tokenAirdrop",
                                        tokenAddress.get(),
                                        sender,
                                        asHeadlongAddress(receiverContractAddress.get()),
                                        10L)
                                .gas(1_500_000L),
                        // New balance should be:
                        // 1_000_000(initial balance) - 1_000(transfer to sender) + 10 (airdropped amount) = 999_010
                        // with no fees paid.
                        getAccountBalance(receiverContract).hasTokenBalance(token, 999_010L));
            }));
        }
    }
}
