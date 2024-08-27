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

package com.hedera.services.bdd.suites.hip904;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFeeNetOfTransfers;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeWithFallback;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.SECP_256K1_SHAPE;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.createHollowAccountFrom;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.updateSpecFor;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenCreate;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.stream.IntStream;

public class TokenAirdropBase {

    protected static final String OWNER = "owner";
    // receivers
    protected static final String RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS = "receiverWithUnlimitedAutoAssociations";
    protected static final String RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS = "receiverWithFreeAutoAssociations";
    protected static final String RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS = "receiverWithoutFreeAutoAssociations";
    protected static final String RECEIVER_WITH_0_AUTO_ASSOCIATIONS = "receiverWith0AutoAssociations";
    protected static final String ASSOCIATED_RECEIVER = "associatedReceiver";
    // tokens
    protected static final String FUNGIBLE_TOKEN = "fungibleToken";
    protected static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    protected static final String NFT_FOR_CONTRACT_TESTS = "nonFungibleTokens";
    // tokens with custom fees
    protected static final String FT_WITH_HBAR_FIXED_FEE = "fungibleTokenWithHbarCustomFee";
    protected static final String FT_WITH_HTS_FIXED_FEE = "fungibleTokenWithHtsCustomFee";
    protected static final String FT_WITH_FRACTIONAL_FEE = "fungibleTokenWithFractionalFee";
    protected static final String FT_WITH_FRACTIONAL_FEE_2 = "fungibleTokenWithFractionalFee2";
    protected static final String FT_WITH_FRACTIONAL_FEE_NET_OF_TRANSFERS = "ftWithFractionalFeeNetOfTransfers";
    protected static final String NFT_WITH_HTS_FIXED_FEE = "NftWithHtsFixedFee";
    protected static final String NFT_WITH_ROYALTY_FEE = "NftWithRoyaltyFee";
    protected static final String DENOM_TOKEN = "denomToken";
    protected static final String HTS_COLLECTOR = "htsCollector";
    protected static final String HBAR_COLLECTOR = "hbarCollector";
    protected static final String TREASURY_FOR_CUSTOM_FEE_TOKENS = "treasuryForCustomFeeTokens";
    protected static final String OWNER_OF_TOKENS_WITH_CUSTOM_FEES = "ownerOfTokensWithCustomFees";

    protected TokenMovement defaultMovementOfToken(String token) {
        return moving(10, token).between(OWNER, RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS);
    }

    protected TokenMovement moveFungibleTokensTo(String receiver) {
        return moving(10, FUNGIBLE_TOKEN).between(OWNER, receiver);
    }

    /**
     * Create Fungible token and set up all scenario receivers
     * - receiver with unlimited auto associations
     * - associated receiver
     * - receiver with 0 auto associations
     * - receiver with free auto associations
     * - receiver with positive number associations, but without fee ones
     *
     * @return array of operations
     */
    protected static SpecOperation[] setUpTokensAndAllReceivers() {
        var nftSupplyKey = "nftSupplyKey";
        final var t = new ArrayList<SpecOperation>(List.of(
                cryptoCreate(OWNER).balance(ONE_HUNDRED_HBARS),
                // base tokens
                tokenCreate(FUNGIBLE_TOKEN)
                        .treasury(OWNER)
                        .tokenType(FUNGIBLE_COMMON)
                        .initialSupply(1000L),
                tokenCreate("dummy").treasury(OWNER).tokenType(FUNGIBLE_COMMON).initialSupply(100L),
                newKeyNamed(nftSupplyKey),
                tokenCreate(NON_FUNGIBLE_TOKEN)
                        .treasury(OWNER)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .initialSupply(0L)
                        .name(NON_FUNGIBLE_TOKEN)
                        .supplyKey(nftSupplyKey),
                mintToken(
                        NON_FUNGIBLE_TOKEN,
                        IntStream.range(0, 10)
                                .mapToObj(a -> ByteString.copyFromUtf8(String.valueOf(a)))
                                .toList()),
                mintToken(
                        NON_FUNGIBLE_TOKEN,
                        IntStream.range(10, 20)
                                .mapToObj(a -> ByteString.copyFromUtf8(String.valueOf(a)))
                                .toList()),
                tokenCreate(NFT_FOR_CONTRACT_TESTS)
                        .treasury(OWNER)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .initialSupply(0L)
                        .name(NON_FUNGIBLE_TOKEN)
                        .supplyKey(nftSupplyKey),
                mintToken(
                        NFT_FOR_CONTRACT_TESTS,
                        IntStream.range(1, 10)
                                .mapToObj(a -> ByteString.copyFromUtf8(String.valueOf(a)))
                                .toList()),
                // all kind of receivers
                cryptoCreate(RECEIVER_WITH_UNLIMITED_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(-1),
                cryptoCreate(RECEIVER_WITH_0_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(0),
                cryptoCreate(RECEIVER_WITH_FREE_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(100),
                cryptoCreate(RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS).maxAutomaticTokenAssociations(1),
                // fill the auto associate slot
                cryptoTransfer(moving(10, "dummy").between(OWNER, RECEIVER_WITHOUT_FREE_AUTO_ASSOCIATIONS)),
                cryptoCreate(ASSOCIATED_RECEIVER),
                tokenAssociate(ASSOCIATED_RECEIVER, FUNGIBLE_TOKEN),
                tokenAssociate(ASSOCIATED_RECEIVER, NON_FUNGIBLE_TOKEN)));

        return t.toArray(new SpecOperation[0]);
    }

    protected static SpecOperation[] setUpTokensWithCustomFees(long tokenTotal, long hbarFee, long htsFee) {
        var nftWithCustomFeeSupplyKey = "nftWithCustomFeeSupplyKey";
        final var t = new ArrayList<SpecOperation>(List.of(
                // tokens with custom fees
                cryptoCreate(TREASURY_FOR_CUSTOM_FEE_TOKENS),
                cryptoCreate(HBAR_COLLECTOR).balance(0L),
                tokenCreate(FT_WITH_HBAR_FIXED_FEE)
                        .treasury(TREASURY_FOR_CUSTOM_FEE_TOKENS)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .initialSupply(tokenTotal)
                        .withCustom(fixedHbarFee(hbarFee, HBAR_COLLECTOR)),
                cryptoCreate(HTS_COLLECTOR).balance(ONE_HUNDRED_HBARS),
                tokenCreate(DENOM_TOKEN)
                        .treasury(TREASURY_FOR_CUSTOM_FEE_TOKENS)
                        .initialSupply(tokenTotal),
                tokenAssociate(HTS_COLLECTOR, DENOM_TOKEN),
                tokenCreate(FT_WITH_HTS_FIXED_FEE)
                        .treasury(TREASURY_FOR_CUSTOM_FEE_TOKENS)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .initialSupply(tokenTotal)
                        .withCustom(fixedHtsFee(htsFee, DENOM_TOKEN, HTS_COLLECTOR)),
                tokenAssociate(HTS_COLLECTOR, FT_WITH_HTS_FIXED_FEE),
                newKeyNamed(nftWithCustomFeeSupplyKey),
                tokenCreate(NFT_WITH_HTS_FIXED_FEE)
                        .treasury(TREASURY_FOR_CUSTOM_FEE_TOKENS)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .supplyKey(nftWithCustomFeeSupplyKey)
                        .supplyType(TokenSupplyType.INFINITE)
                        .initialSupply(0)
                        .withCustom(fixedHtsFee(htsFee, FT_WITH_HTS_FIXED_FEE, HTS_COLLECTOR)),
                mintToken(NFT_WITH_HTS_FIXED_FEE, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes()))),
                tokenCreate(FT_WITH_FRACTIONAL_FEE)
                        .treasury(TREASURY_FOR_CUSTOM_FEE_TOKENS)
                        .tokenType(FUNGIBLE_COMMON)
                        .withCustom(fractionalFee(1, 10L, 1L, OptionalLong.empty(), HTS_COLLECTOR))
                        .initialSupply(Long.MAX_VALUE)
                        .payingWith(HTS_COLLECTOR),
                tokenCreate(FT_WITH_FRACTIONAL_FEE_2)
                        .treasury(TREASURY_FOR_CUSTOM_FEE_TOKENS)
                        .tokenType(FUNGIBLE_COMMON)
                        .withCustom(fractionalFee(1, 10L, 1L, OptionalLong.empty(), HTS_COLLECTOR))
                        .initialSupply(Long.MAX_VALUE)
                        .payingWith(HTS_COLLECTOR),
                tokenCreate(FT_WITH_FRACTIONAL_FEE_NET_OF_TRANSFERS)
                        .treasury(TREASURY_FOR_CUSTOM_FEE_TOKENS)
                        .tokenType(FUNGIBLE_COMMON)
                        .withCustom(fractionalFeeNetOfTransfers(1, 10L, 1L, OptionalLong.of(100), HTS_COLLECTOR))
                        .initialSupply(Long.MAX_VALUE)
                        .payingWith(HTS_COLLECTOR),
                tokenCreate(NFT_WITH_ROYALTY_FEE)
                        .maxSupply(10L)
                        .initialSupply(0)
                        .supplyType(TokenSupplyType.FINITE)
                        .tokenType(NON_FUNGIBLE_UNIQUE)
                        .supplyKey(nftWithCustomFeeSupplyKey)
                        .treasury(TREASURY_FOR_CUSTOM_FEE_TOKENS)
                        .withCustom(
                                royaltyFeeWithFallback(1, 2, fixedHbarFeeInheritingRoyaltyCollector(1), HTS_COLLECTOR)),
                tokenAssociate(HTS_COLLECTOR, NFT_WITH_ROYALTY_FEE),
                mintToken(NFT_WITH_ROYALTY_FEE, List.of(ByteStringUtils.wrapUnsafely("meta1".getBytes())))));

        return t.toArray(new SpecOperation[0]);
    }

    protected static SpecOperation[] setUpEntitiesPreHIP904() {
        final var validAlias = "validAlias";
        final var aliasTwo = "alias2.0";
        final var sponsor = "sponsor";
        final var t = new ArrayList<SpecOperation>(List.of(
                // create hollow account with 0 auto associations
                cryptoCreate(sponsor).balance(ONE_HUNDRED_HBARS),
                newKeyNamed(validAlias).shape(SECP_256K1_SHAPE),
                newKeyNamed(aliasTwo).shape(SECP_256K1_SHAPE)));
        t.addAll(Arrays.stream(createHollowAccountFrom(validAlias)).toList());
        t.addAll(Arrays.stream(createHollowAccountFrom(aliasTwo)).toList());
        t.add(withOpContext((spec, opLog) -> updateSpecFor(spec, validAlias)));
        t.add(withOpContext((spec, opLog) -> updateSpecFor(spec, aliasTwo)));
        return t.toArray(new SpecOperation[0]);
    }

    /**
     * Create and mint NFT and set up all scenario receivers
     * - receiver with unlimited auto associations
     * - associated receiver
     * - receiver with 0 auto associations
     * - receiver with free auto associations
     * - receiver with positive number associations, but without fee ones
     *
     * @return array of operations
     */
    protected HapiTokenCreate createTokenWithName(String tokenName) {
        return tokenCreate(tokenName).tokenType(TokenType.FUNGIBLE_COMMON).treasury(OWNER);
    }

    protected SpecOperation[] deployMutableContract(String name, int maxAutoAssociations) {
        var t = List.of(
                newKeyNamed(name),
                uploadInitCode(name),
                contractCreate(name)
                        .maxAutomaticTokenAssociations(maxAutoAssociations)
                        .adminKey(name)
                        .gas(500_000L));

        return t.toArray(new SpecOperation[0]);
    }
}
