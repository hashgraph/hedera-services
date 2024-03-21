/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package token;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.spi.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

public class RoyaltyCollectorAutoAssociationXTest extends AbstractTokenXTest {

    @Override
    protected void doScenarioOperations() {
        // Transfer some initial balance to the party and counterparty accounts
        handleAndCommitSingleTransaction(
                component.cryptoTransferHandler(),
                transfer(
                        movingFungibleUnits(FIRST_FUNGIBLE, TOKEN_TREASURY, COUNTERPARTY, INITIAL_BALANCE),
                        movingFungibleUnits(SECOND_FUNGIBLE, TOKEN_TREASURY, COUNTERPARTY, INITIAL_BALANCE)));
        handleAndCommitSingleTransaction(
                component.tokenMintHandler(), nftMint(Bytes.wrap("HOLD"), NON_FUNGIBLE_UNIQUE));
        handleAndCommitSingleTransaction(
                component.cryptoTransferHandler(), transfer(movingNft(NON_FUNGIBLE_UNIQUE, TOKEN_TREASURY, PARTY, 1)));
        handleAndCommitSingleTransaction(
                component.cryptoTransferHandler(),
                transfer(
                        movingNft(NON_FUNGIBLE_UNIQUE, PARTY, COUNTERPARTY, 1),
                        movingFungibleUnits(FIRST_FUNGIBLE, COUNTERPARTY, PARTY, EXCHANGE_AMOUNT),
                        movingFungibleUnits(SECOND_FUNGIBLE, COUNTERPARTY, PARTY, EXCHANGE_AMOUNT)));
    }

    @Override
    protected void assertExpectedNfts(@NonNull ReadableKVState<NftID, Nft> nfts) {
        // Here is where we can e.g. assert the NFT has been transferred to the counterparty
    }

    @Override
    protected void assertExpectedTokenRelations(@NonNull ReadableKVState<EntityIDPair, TokenRelation> tokenRels) {
        // Here is where we can e.g. assert the expected royalties have been collected from the
        // fungible value exchanged for the NFT
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = super.initialAccounts();
        addNamedAccount(TOKEN_TREASURY, accounts);
        addNamedAccount(FIRST_ROYALTY_COLLECTOR, b -> b.maxAutoAssociations(PLENTY_OF_SLOTS), accounts);
        addNamedAccount(SECOND_ROYALTY_COLLECTOR, b -> b.maxAutoAssociations(PLENTY_OF_SLOTS), accounts);
        addNamedAccount(PARTY, b -> b.maxAutoAssociations(PLENTY_OF_SLOTS), accounts);
        addNamedAccount(COUNTERPARTY, b -> b.maxAutoAssociations(PLENTY_OF_SLOTS), accounts);
        return accounts;
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = super.initialTokens();
        addNamedFungibleToken(
                FIRST_FUNGIBLE,
                b -> b.treasuryAccountId(idOfNamedAccount(TOKEN_TREASURY)).totalSupply(INITIAL_SUPPLY),
                tokens);
        addNamedFungibleToken(
                SECOND_FUNGIBLE,
                b -> b.treasuryAccountId(idOfNamedAccount(TOKEN_TREASURY)).totalSupply(INITIAL_SUPPLY),
                tokens);
        addNamedNonFungibleToken(
                NON_FUNGIBLE_UNIQUE,
                b -> b.treasuryAccountId(idOfNamedAccount(TOKEN_TREASURY))
                        .customFees(royaltyFeeNoFallback(1, 12, FIRST_ROYALTY_COLLECTOR))
                        .customFees(royaltyFeeNoFallback(1, 15, SECOND_ROYALTY_COLLECTOR)),
                tokens);
        return tokens;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRels = super.initialTokenRelationships();
        addNewRelation(TOKEN_TREASURY, FIRST_FUNGIBLE, b -> b.balance(INITIAL_SUPPLY), tokenRels);
        addNewRelation(TOKEN_TREASURY, SECOND_FUNGIBLE, b -> b.balance(INITIAL_SUPPLY), tokenRels);
        addNewRelation(TOKEN_TREASURY, NON_FUNGIBLE_UNIQUE, b -> b.balance(0), tokenRels);
        return tokenRels;
    }

    private static final String PARTY = "party";
    private static final String COUNTERPARTY = "counterparty";
    private static final String TOKEN_TREASURY = "tokenTreasury";
    private static final String UNIQUE_WITH_ROYALTY = "uniqueWithRoyalty";
    private static final String FIRST_FUNGIBLE = "firstFungible";
    private static final String SECOND_FUNGIBLE = "secondFungible";
    private static final String NON_FUNGIBLE_UNIQUE = "nonFungibleUnique";
    private static final String FIRST_ROYALTY_COLLECTOR = "firstRoyaltyCollector";
    private static final String SECOND_ROYALTY_COLLECTOR = "secondRoyaltyCollector";
    private static final int PLENTY_OF_SLOTS = 10;
    private static final long INITIAL_SUPPLY = 123456789;
    private static final long INITIAL_BALANCE = 1000;
    private static final long EXCHANGE_AMOUNT = 12 * 15;
    private static final long FIRST_ROYALTY_AMOUNT = EXCHANGE_AMOUNT / 12;
    private static final long SECOND_ROYALTY_AMOUNT = EXCHANGE_AMOUNT / 15;
}
