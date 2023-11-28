/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.xtest.contract;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc20TransfersTranslator.ERC_20_TRANSFER;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc20TransfersTranslator.ERC_20_TRANSFER_FROM;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.spi.state.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Assertions;

/**
 * Exercises the ERC-20 {@code transferFrom()} and {@code transfer()} call by setting up an owner that has a
 * balance of a fungible token, along with an account that has been granted an allowance of the token.
 *
 * <p>The test validates that the owner account can transfer some of its balance as the sender; and that the
 * approved account can transfer some of its allowance as the sender.
 *
 * <p>The test includes a negative scenario where we expect a revert, in which an unauthorized spender tries
 * to transfer some of the owner's balance.
 */
public class HtsErc20TransfersXTest extends AbstractContractXTest {
    @Override
    protected void doScenarioOperations() {
        // The owner can transfer() their own balance
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_20_TRANSFER.encodeCallWithArgs(
                                XTestConstants.RECEIVER_HEADLONG_ADDRESS, BigInteger.valueOf(100L)),
                        XTestConstants.ERC20_TOKEN_ID),
                output ->
                        assertEquals(asBytesResult(ERC_20_TRANSFER.getOutputs().encodeElements(true)), output),
                "Owner can transfer their own balance");
        // The approved spender can spend the owner's balance
        runHtsCallAndExpectOnSuccess(
                HtsErc721TransferXTestConstants.APPROVED_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_20_TRANSFER_FROM.encodeCallWithArgs(
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                XTestConstants.RECEIVER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(200L)),
                        XTestConstants.ERC20_TOKEN_ID),
                output -> assertEquals(
                        asBytesResult(ERC_20_TRANSFER_FROM.getOutputs().encodeElements(true)), output),
                "Approved spender can spend the owner's balance");
        // Unauthorized spender cannot spend the owner's balance
        runHtsCallAndExpectRevert(
                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_20_TRANSFER_FROM.encodeCallWithArgs(
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                XTestConstants.RECEIVER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(300L)),
                        XTestConstants.ERC20_TOKEN_ID),
                SPENDER_DOES_NOT_HAVE_ALLOWANCE,
                "Unauthorized spender cannot spend the owner's balance");
    }

    @Override
    protected void assertExpectedAccounts(@NonNull ReadableKVState<AccountID, Account> accounts) {
        final var owner = Objects.requireNonNull(accounts.get(XTestConstants.OWNER_ID));
        final var allowance = Objects.requireNonNull(owner.tokenAllowances()).get(0);
        Assertions.assertEquals(XTestConstants.ERC20_TOKEN_ID, allowance.tokenId());
        Assertions.assertEquals(HtsErc721TransferXTestConstants.APPROVED_ID, allowance.spenderId());
        assertEquals(Long.MAX_VALUE - 200L, allowance.amount());
    }

    @Override
    protected void assertExpectedTokenRelations(@NonNull ReadableKVState<EntityIDPair, TokenRelation> tokenRels) {
        // The owner's balance should have been reduced by 300
        assertTokenBalance(tokenRels, XTestConstants.OWNER_ID, 700L);
        // The receiver's balance should have been increased by 300
        assertTokenBalance(tokenRels, XTestConstants.RECEIVER_ID, 300L);
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(XTestConstants.OWNER_ADDRESS).build(), XTestConstants.OWNER_ID);
        aliases.put(
                ProtoBytes.newBuilder()
                        .value(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ADDRESS)
                        .build(),
                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID);
        aliases.put(
                ProtoBytes.newBuilder()
                        .value(HtsErc721TransferXTestConstants.APPROVED_ADDRESS)
                        .build(),
                HtsErc721TransferXTestConstants.APPROVED_ID);
        return aliases;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                XTestConstants.OWNER_ID,
                Account.newBuilder()
                        .accountId(XTestConstants.OWNER_ID)
                        .alias(XTestConstants.OWNER_ADDRESS)
                        .tokenAllowances(List.of(AccountFungibleTokenAllowance.newBuilder()
                                .spenderId(HtsErc721TransferXTestConstants.APPROVED_ID)
                                .tokenId(XTestConstants.ERC20_TOKEN_ID)
                                .amount(Long.MAX_VALUE)
                                .build()))
                        .key(XTestConstants.AN_ED25519_KEY)
                        .build());
        accounts.put(
                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID,
                Account.newBuilder()
                        .accountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .key(XTestConstants.AN_ED25519_KEY)
                        .alias(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ADDRESS)
                        .build());
        accounts.put(
                HtsErc721TransferXTestConstants.APPROVED_ID,
                Account.newBuilder()
                        .accountId(HtsErc721TransferXTestConstants.APPROVED_ID)
                        .key(XTestConstants.AN_ED25519_KEY)
                        .alias(HtsErc721TransferXTestConstants.APPROVED_ADDRESS)
                        .build());
        accounts.put(
                XTestConstants.RECEIVER_ID,
                Account.newBuilder().accountId(XTestConstants.RECEIVER_ID).build());
        return accounts;
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = new HashMap<TokenID, Token>();
        tokens.put(
                XTestConstants.ERC20_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(XTestConstants.ERC20_TOKEN_ID)
                        .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .build());
        return tokens;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        addErc20Relation(tokenRelationships, XTestConstants.OWNER_ID, 1000L);
        addErc20Relation(tokenRelationships, HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID, 1234L);
        addErc20Relation(tokenRelationships, HtsErc721TransferXTestConstants.APPROVED_ID, 0L);
        addErc20Relation(tokenRelationships, XTestConstants.RECEIVER_ID, 0L);
        return tokenRelationships;
    }

    private void addErc20Relation(
            final Map<EntityIDPair, TokenRelation> tokenRelationships, final AccountID accountID, final long balance) {
        tokenRelationships.put(
                EntityIDPair.newBuilder()
                        .tokenId(XTestConstants.ERC20_TOKEN_ID)
                        .accountId(accountID)
                        .build(),
                TokenRelation.newBuilder()
                        .tokenId(XTestConstants.ERC20_TOKEN_ID)
                        .accountId(accountID)
                        .balance(balance)
                        .kycGranted(true)
                        .build());
    }

    private void assertTokenBalance(
            final ReadableKVState<EntityIDPair, TokenRelation> tokenRels,
            final AccountID accountID,
            final long expectedBalance) {
        assertEquals(
                expectedBalance,
                Objects.requireNonNull(tokenRels.get(EntityIDPair.newBuilder()
                                .tokenId(XTestConstants.ERC20_TOKEN_ID)
                                .accountId(accountID)
                                .build()))
                        .balance());
    }
}
