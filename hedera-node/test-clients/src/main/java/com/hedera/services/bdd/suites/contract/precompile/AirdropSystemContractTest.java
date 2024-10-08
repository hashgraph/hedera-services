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

package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;

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
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
@HapiTestLifecycle
@OrderedInIsolation
public class AirdropSystemContractTest {

    @Contract(contract = "Airdrop", creationGas = 2_000_000L)
    static SpecContract airdropContract;

    @FungibleToken(name = "token", initialSupply = 1_000_000L)
    static SpecFungibleToken token;

    @FungibleToken(name = "token2", initialSupply = 1_000_000L)
    static SpecFungibleToken token2;

    @FungibleToken(name = "token3", initialSupply = 1_000_000L)
    static SpecFungibleToken token3;

    @FungibleToken(name = "token4", initialSupply = 1_000_000L)
    static SpecFungibleToken token4;

    @FungibleToken(name = "token5", initialSupply = 1_000_000L)
    static SpecFungibleToken token5;

    @NonFungibleToken(name = "nft", numPreMints = 10)
    static SpecNonFungibleToken nft;

    @NonFungibleToken(name = "nft2", numPreMints = 10)
    static SpecNonFungibleToken nft2;

    @NonFungibleToken(name = "nft3", numPreMints = 10)
    static SpecNonFungibleToken nft3;

    @NonFungibleToken(name = "nft4", numPreMints = 10)
    static SpecNonFungibleToken nft4;

    @NonFungibleToken(name = "nft5", numPreMints = 10)
    static SpecNonFungibleToken nft5;

    @NonFungibleToken(name = "nft6", numPreMints = 10)
    static SpecNonFungibleToken nft6;

    @Account(name = "sender", centBalance = 10_000_000L)
    static SpecAccount sender;

    @Account(name = "receiver1", tinybarBalance = 10_000_000_000L)
    static SpecAccount receiver1;

    @Account(name = "receiver2", tinybarBalance = 10_000_000_000L)
    static SpecAccount receiver2;

    @Account(name = "receiver3", tinybarBalance = 10_000_000_000L)
    static SpecAccount receiver3;

    @Account(name = "receiver4", tinybarBalance = 10_000_000_000L)
    static SpecAccount receiver4;

    @Account(name = "receiver5", tinybarBalance = 10_000_000_000L)
    static SpecAccount receiver5;

    @Account(name = "receiver6", tinybarBalance = 10_000_000_000L)
    static SpecAccount receiver6;

    @Account(name = "receiver7", tinybarBalance = 10_000_000_000L)
    static SpecAccount receiver7;

    @Account(name = "receiver8", tinybarBalance = 10_000_000_000L)
    static SpecAccount receiver8;

    @Account(name = "receiver9", tinybarBalance = 10_000_000_000L)
    static SpecAccount receiver9;

    @Account(name = "receiver10", tinybarBalance = 10_000_000_000L)
    static SpecAccount receiver10;

    @Account(name = "receiver11", tinybarBalance = 10_000_000_000L)
    static SpecAccount receiver11;

    @BeforeAll
    public static void beforeAll(final @NonNull TestLifecycle lifecycle) {
        lifecycle.doAdhoc(
                receiver1.transferHBarsTo(airdropContract, 1_000_000_000L),
                receiver2.getBalance(),
                receiver3.getBalance(),
                receiver4.getBalance(),
                receiver5.getBalance(),
                receiver6.getBalance(),
                receiver7.getBalance(),
                receiver8.getBalance(),
                receiver9.getBalance(),
                receiver10.getBalance(),
                receiver11.getBalance(),
                sender.authorizeContract(airdropContract),
                sender.associateTokens(token, token2, token3, token4, token5, nft, nft2, nft3, nft4, nft5, nft6),
                nft.treasury().transferNFTsTo(sender, nft, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L),
                nft2.treasury().transferNFTsTo(sender, nft2, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L),
                nft3.treasury().transferNFTsTo(sender, nft3, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L),
                nft4.treasury().transferNFTsTo(sender, nft4, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L),
                nft5.treasury().transferNFTsTo(sender, nft5, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L),
                nft6.treasury().transferNFTsTo(sender, nft6, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L),
                token.treasury().transferUnitsTo(sender, 500_000L, token),
                token2.treasury().transferUnitsTo(sender, 500_000L, token2),
                token3.treasury().transferUnitsTo(sender, 500_000L, token3),
                token4.treasury().transferUnitsTo(sender, 500_000L, token4),
                token5.treasury().transferUnitsTo(sender, 500_000L, token5));
    }

    @HapiTest
    @Order(1)
    @DisplayName("Airdrop token")
    public Stream<DynamicTest> airdropToken() {
        return hapiTest(airdropContract
                .call("tokenAirdrop", token, sender, receiver1, 10L)
                .gas(1500000));
    }

    @HapiTest
    @Order(2)
    @DisplayName("Airdrop NFT")
    public Stream<DynamicTest> airdropNft() {
        return hapiTest(
                airdropContract.call("nftAirdrop", nft, sender, receiver1, 1L).gas(1500000));
    }

    @HapiTest
    @Order(3)
    @DisplayName("Multiple Airdrop token transactions")
    public Stream<DynamicTest> airdropTokens() {
        return hapiTest(withOpContext((spec, opLog) -> {
            final var tokens = new Address[] {
                token.addressOn(spec.targetNetworkOrThrow()),
                token2.addressOn(spec.targetNetworkOrThrow()),
                token3.addressOn(spec.targetNetworkOrThrow())
            };
            final var senderAddress = sender.addressOn(spec.targetNetworkOrThrow());
            final var sendersArray = new Address[] {senderAddress, senderAddress, senderAddress};
            final var receiversArray = new Address[] {
                receiver1.addressOn(spec.targetNetworkOrThrow()),
                receiver2.addressOn(spec.targetNetworkOrThrow()),
                receiver3.addressOn(spec.targetNetworkOrThrow())
            };
            allRunFor(
                    spec,
                    airdropContract
                            .call("tokenNAmountAirdrops", tokens, sendersArray, receiversArray, 10L)
                            .gas(1500000));
        }));
    }

    @HapiTest
    @Order(4)
    @DisplayName("Multiple Airdrop NFT transactions")
    public Stream<DynamicTest> airdropNfts() {
        return hapiTest(withOpContext((spec, opLog) -> {
            final var nfts = new Address[] {
                nft.addressOn(spec.targetNetworkOrThrow()),
                nft2.addressOn(spec.targetNetworkOrThrow()),
                nft3.addressOn(spec.targetNetworkOrThrow())
            };
            final var senderAddress = sender.addressOn(spec.targetNetworkOrThrow());
            final var sendersArray = new Address[] {senderAddress, senderAddress, senderAddress};
            final var receiversArray = new Address[] {
                receiver1.addressOn(spec.targetNetworkOrThrow()),
                receiver2.addressOn(spec.targetNetworkOrThrow()),
                receiver3.addressOn(spec.targetNetworkOrThrow())
            };
            final var serials = new long[] {2L, 2L, 2L};
            allRunFor(
                    spec,
                    airdropContract
                            .call("nftNAmountAirdrops", nfts, sendersArray, receiversArray, serials)
                            .gas(1500000));
        }));
    }

    @HapiTest
    @Order(5)
    @DisplayName("Airdrop token and NFT")
    public Stream<DynamicTest> airdropTokenAndNft() {
        return hapiTest(withOpContext((spec, opLog) -> {
            final var tokens = new Address[] {
                token.addressOn(spec.targetNetworkOrThrow()),
                token2.addressOn(spec.targetNetworkOrThrow()),
                token3.addressOn(spec.targetNetworkOrThrow())
            };
            final var nfts = new Address[] {
                nft.addressOn(spec.targetNetworkOrThrow()),
                nft2.addressOn(spec.targetNetworkOrThrow()),
                nft3.addressOn(spec.targetNetworkOrThrow())
            };
            final var senderAddress = sender.addressOn(spec.targetNetworkOrThrow());
            final var sendersArray = new Address[] {senderAddress, senderAddress, senderAddress};
            final var tokenReceiversArray = new Address[] {
                receiver1.addressOn(spec.targetNetworkOrThrow()),
                receiver2.addressOn(spec.targetNetworkOrThrow()),
                receiver3.addressOn(spec.targetNetworkOrThrow())
            };
            final var nftReceiversArray = new Address[] {
                receiver4.addressOn(spec.targetNetworkOrThrow()),
                receiver5.addressOn(spec.targetNetworkOrThrow()),
                receiver6.addressOn(spec.targetNetworkOrThrow())
            };
            final var serials = new long[] {3L, 3L, 3L};
            allRunFor(
                    spec,
                    airdropContract
                            .call(
                                    "mixedAirdrop",
                                    tokens,
                                    nfts,
                                    sendersArray,
                                    tokenReceiversArray,
                                    sendersArray,
                                    nftReceiversArray,
                                    10L,
                                    serials)
                            .gas(1750000));
        }));
    }

    @HapiTest
    @Order(15)
    @DisplayName("Airdrop 10 token and NFT")
    public Stream<DynamicTest> airdrop10TokenAndNft() {
        return hapiTest(withOpContext((spec, opLog) -> {
            final var tokens = new Address[] {
                token.addressOn(spec.targetNetworkOrThrow()),
                token2.addressOn(spec.targetNetworkOrThrow()),
                token3.addressOn(spec.targetNetworkOrThrow()),
                token4.addressOn(spec.targetNetworkOrThrow()),
                token5.addressOn(spec.targetNetworkOrThrow())
            };
            final var nfts = new Address[] {
                nft.addressOn(spec.targetNetworkOrThrow()),
                nft2.addressOn(spec.targetNetworkOrThrow()),
                nft3.addressOn(spec.targetNetworkOrThrow()),
                nft4.addressOn(spec.targetNetworkOrThrow()),
                nft5.addressOn(spec.targetNetworkOrThrow())
            };
            final var senderAddress = sender.addressOn(spec.targetNetworkOrThrow());
            final var sendersArray =
                    new Address[] {senderAddress, senderAddress, senderAddress, senderAddress, senderAddress};
            final var tokenReceiversArray = new Address[] {
                receiver1.addressOn(spec.targetNetworkOrThrow()),
                receiver2.addressOn(spec.targetNetworkOrThrow()),
                receiver3.addressOn(spec.targetNetworkOrThrow()),
                receiver4.addressOn(spec.targetNetworkOrThrow()),
                receiver5.addressOn(spec.targetNetworkOrThrow())
            };
            final var nftReceiversArray = new Address[] {
                receiver6.addressOn(spec.targetNetworkOrThrow()),
                receiver7.addressOn(spec.targetNetworkOrThrow()),
                receiver8.addressOn(spec.targetNetworkOrThrow()),
                receiver9.addressOn(spec.targetNetworkOrThrow()),
                receiver10.addressOn(spec.targetNetworkOrThrow())
            };
            final var serials = new long[] {6L, 6L, 6L, 6L, 6L};
            allRunFor(
                    spec,
                    airdropContract
                            .call(
                                    "mixedAirdrop",
                                    tokens,
                                    nfts,
                                    sendersArray,
                                    tokenReceiversArray,
                                    sendersArray,
                                    nftReceiversArray,
                                    10L,
                                    serials)
                            //                    .andAssert(txn -> txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED))
                            .via("tenAirdrops")
                            .gas(3_500_000),
                    getTxnRecord("tenAirdrops").logged());
        }));
    }

    @HapiTest
    @Order(7)
    @DisplayName("Should fail Airdrop 11 token and NFT")
    public Stream<DynamicTest> airdrop11TokenAndNft() {
        return hapiTest(withOpContext((spec, opLog) -> {
            final var tokens = new Address[] {
                token.addressOn(spec.targetNetworkOrThrow()),
                token2.addressOn(spec.targetNetworkOrThrow()),
                token3.addressOn(spec.targetNetworkOrThrow()),
                token4.addressOn(spec.targetNetworkOrThrow()),
                token5.addressOn(spec.targetNetworkOrThrow())
            };
            final var nfts = new Address[] {
                nft.addressOn(spec.targetNetworkOrThrow()),
                nft2.addressOn(spec.targetNetworkOrThrow()),
                nft3.addressOn(spec.targetNetworkOrThrow()),
                nft4.addressOn(spec.targetNetworkOrThrow()),
                nft5.addressOn(spec.targetNetworkOrThrow()),
                nft6.addressOn(spec.targetNetworkOrThrow())
            };
            final var senderAddress = sender.addressOn(spec.targetNetworkOrThrow());
            final var tokenSendersArray =
                    new Address[] {senderAddress, senderAddress, senderAddress, senderAddress, senderAddress};
            final var nftSendersArray = new Address[] {
                senderAddress, senderAddress, senderAddress, senderAddress, senderAddress, senderAddress
            };
            final var tokenReceiversArray = new Address[] {
                receiver1.addressOn(spec.targetNetworkOrThrow()),
                receiver2.addressOn(spec.targetNetworkOrThrow()),
                receiver3.addressOn(spec.targetNetworkOrThrow()),
                receiver4.addressOn(spec.targetNetworkOrThrow()),
                receiver5.addressOn(spec.targetNetworkOrThrow())
            };
            final var nftReceiversArray = new Address[] {
                receiver6.addressOn(spec.targetNetworkOrThrow()),
                receiver7.addressOn(spec.targetNetworkOrThrow()),
                receiver8.addressOn(spec.targetNetworkOrThrow()),
                receiver9.addressOn(spec.targetNetworkOrThrow()),
                receiver10.addressOn(spec.targetNetworkOrThrow()),
                receiver11.addressOn(spec.targetNetworkOrThrow())
            };
            final var serials = new long[] {5L, 5L, 5L, 5L, 5L, 5L};
            allRunFor(
                    spec,
                    airdropContract
                            .call(
                                    "mixedAirdrop",
                                    tokens,
                                    nfts,
                                    tokenSendersArray,
                                    tokenReceiversArray,
                                    nftSendersArray,
                                    nftReceiversArray,
                                    10L,
                                    serials)
                            .gas(1750000)
                            .andAssert(txn -> {
                                txn.hasKnownStatus(CONTRACT_REVERT_EXECUTED);
                            }));
        }));
    }
}
