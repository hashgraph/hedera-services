package token;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Fraction;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.RoyaltyFee;
import com.hedera.pbj.runtime.io.buffer.Bytes;

import java.util.Map;

public class RoyaltyCollectorAutoAssociationXTest extends AbstractTokenXTest {

    @Override
    protected void doScenarioOperations() {
        System.out.println(namedTokenIds);
        // Transfer some initial balance to the party and counterparty accounts
        handleAndCommitSingleTransaction(
                component.cryptoTransferHandler(),
                transfer(
                        movingFungibleUnits(FIRST_FUNGIBLE, TOKEN_TREASURY, PARTY, INITIAL_BALANCE),
                        movingFungibleUnits(SECOND_FUNGIBLE, TOKEN_TREASURY, COUNTERPARTY, INITIAL_BALANCE)),
                ResponseCodeEnum.OK);
        handleAndCommitSingleTransaction(
                component.tokenMintHandler(),
                nftMint(Bytes.wrap("HOLD"), NON_FUNGIBLE_UNIQUE),
                ResponseCodeEnum.OK);
        handleAndCommitSingleTransaction(
                component.cryptoTransferHandler(),
                transfer(movingNft(NON_FUNGIBLE_UNIQUE, TOKEN_TREASURY, PARTY, 1)),
                ResponseCodeEnum.OK);
        handleAndCommitSingleTransaction(
                component.cryptoTransferHandler(),
                transfer(
                        movingNft(NON_FUNGIBLE_UNIQUE, PARTY, COUNTERPARTY, 1),
                        movingFungibleUnits(FIRST_FUNGIBLE, COUNTERPARTY, PARTY, EXCHANGE_AMOUNT),
                        movingFungibleUnits(SECOND_FUNGIBLE, COUNTERPARTY, PARTY, EXCHANGE_AMOUNT)),
                ResponseCodeEnum.OK);
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
        addNamedFungibleToken(FIRST_FUNGIBLE, b -> b
                .treasuryAccountId(idOfNamedAccount(TOKEN_TREASURY))
                .totalSupply(INITIAL_SUPPLY), tokens);
        addNamedFungibleToken(SECOND_FUNGIBLE, b -> b
                .treasuryAccountId(idOfNamedAccount(TOKEN_TREASURY))
                .totalSupply(INITIAL_SUPPLY), tokens);
        addNamedNonFungibleToken(NON_FUNGIBLE_UNIQUE, b -> b
                        .treasuryAccountId(idOfNamedAccount(TOKEN_TREASURY))
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
