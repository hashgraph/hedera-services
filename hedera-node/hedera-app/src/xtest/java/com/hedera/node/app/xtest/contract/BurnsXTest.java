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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn.BurnTranslator;
import com.hedera.node.app.spi.fixtures.Scenarios;
import com.hedera.node.app.spi.state.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;

/**
 * Exercises burnToken on a fungible and non-fungible token via the following steps relative to an {@code OWNER} account:
 * <ol>
 *     <li>Burns {@code ERC20_TOKEN} via BURN_TOKEN_V1 operation</li>
 *     <li>Burns {@code ERC20_TOKEN} via BURN_TOKEN_V2 operation</li>
 *     <li>Burns {@code ERC20_TOKEN} without supplyKey via BURN_TOKEN_V1 operation. This should fail with TOKEN_HAS_NO_SUPPLY_KEY</li>
 *     <li>Burns {@code ERC20_TOKEN} without supplyKey via BURN_TOKEN_V2 operation. This should fail with TOKEN_HAS_NO_SUPPLY_KEY</li>
 *     <li>Burns {@code ERC20_TOKEN} token which is not associated to account via BURN_TOKEN_V1 operation. This should fail with TOKEN_NOT_ASSOCIATED_TO_ACCOUNT</li>
 *     <li>Burns {@code ERC20_TOKEN} token which is not associated to account via BURN_TOKEN_V2 operation. This should fail with TOKEN_NOT_ASSOCIATED_TO_ACCOUNT</li>
 *     <li>Burns {@code ERC20_TOKEN} token when totalSupply < amountToBurn via BURN_TOKEN_V1 operation. This should fail with INVALID_TOKEN_BURN_AMOUNT</li>
 *     <li>Burns {@code ERC20_TOKEN} token when totalSupply < amountToBurn via BURN_TOKEN_V2 operation. This should fail with INVALID_TOKEN_BURN_AMOUNT</li>
 *     <li>Burns {@code ERC20_TOKEN} token with invalid id via BURN_TOKEN_V1 operation. This should fail with INVALID_TOKEN_ID</li>
 *     <li>Burns {@code ERC20_TOKEN} token with invalid id via BURN_TOKEN_V2 operation. This should fail with INVALID_TOKEN_ID</li>
 *     <li>Burns {@code ERC20_TOKEN} with invalid supplyKey via BURN_TOKEN_V1 operation. This should fail with INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE</li>
 *     <li>Burns {@code ERC20_TOKEN} with invalid supplyKey via BURN_TOKEN_V2 operation. This should fail with INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE</li>
 *     <li>Burns {@code ERC721_TOKEN} via BURN_TOKEN_V1 operation</li>
 *     <li>Burns {@code ERC721_TOKEN} via BURN_TOKEN_V2 operation</li>
 *     <li>Burns {@code ERC721_TOKEN} with invalid supplyKey via BURN_TOKEN_V1 operation. This should fail with INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE</li>
 *     <li>Burns {@code ERC721_TOKEN} with invalid supplyKey via BURN_TOKEN_V2 operation. This should fail with INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE</li>
 * </ol>
 */
public class BurnsXTest extends AbstractContractXTest {

    private static final long TOKEN_BALANCE = 9L;
    private static final long TOKENS_TO_BURN = 1L;

    @Override
    protected void doScenarioOperations() {
        // should successfully burn fungible token via V1
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(
                                XTestConstants.ERC20_TOKEN_ADDRESS, BigInteger.valueOf(TOKENS_TO_BURN), new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                                .getOutputs()
                                .encodeElements((long) SUCCESS.protoOrdinal(), TOKEN_BALANCE - TOKENS_TO_BURN)
                                .array()),
                        output));

        // should successfully burn fungible token via V2
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                        .encodeCallWithArgs(XTestConstants.ERC20_TOKEN_ADDRESS, TOKENS_TO_BURN, new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                                .getOutputs()
                                .encodeElements((long) SUCCESS.protoOrdinal(), TOKEN_BALANCE - 2L)
                                .array()),
                        output));

        // should fail when token has no supplyKey via V1
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.A_TOKEN_ADDRESS,
                                BigInteger.valueOf(TOKENS_TO_BURN),
                                new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                                .getOutputs()
                                .encodeElements((long) TOKEN_HAS_NO_SUPPLY_KEY.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail when token has no supplyKey via V2
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                        .encodeCallWithArgs(AssociationsXTestConstants.A_TOKEN_ADDRESS, TOKENS_TO_BURN, new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                                .getOutputs()
                                .encodeElements((long) TOKEN_HAS_NO_SUPPLY_KEY.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail when token is not associated to account via V1
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.B_TOKEN_ADDRESS,
                                BigInteger.valueOf(TOKENS_TO_BURN),
                                new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                                .getOutputs()
                                .encodeElements((long) TOKEN_NOT_ASSOCIATED_TO_ACCOUNT.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail when token is not associated to account via V2
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                        .encodeCallWithArgs(AssociationsXTestConstants.B_TOKEN_ADDRESS, TOKENS_TO_BURN, new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                                .getOutputs()
                                .encodeElements((long) TOKEN_NOT_ASSOCIATED_TO_ACCOUNT.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail on totalSupply < amountToBurn via V1
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(
                                XTestConstants.ERC20_TOKEN_ADDRESS,
                                BigInteger.valueOf(TOKEN_BALANCE + 1),
                                new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                                .getOutputs()
                                .encodeElements((long) INVALID_TOKEN_BURN_AMOUNT.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail on totalSupply < amountToBurn via V2
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                        .encodeCallWithArgs(XTestConstants.ERC20_TOKEN_ADDRESS, TOKEN_BALANCE + 1, new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                                .getOutputs()
                                .encodeElements((long) INVALID_TOKEN_BURN_AMOUNT.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail on invalid token id via V2
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(
                                XTestConstants.INVALID_TOKEN_ADDRESS,
                                BigInteger.valueOf(TOKEN_BALANCE + 1),
                                new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                                .getOutputs()
                                .encodeElements((long) INVALID_TOKEN_ID.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail on invalid token id via V2
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                        .encodeCallWithArgs(XTestConstants.INVALID_TOKEN_ADDRESS, TOKEN_BALANCE + 1, new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                                .getOutputs()
                                .encodeElements((long) INVALID_TOKEN_ID.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail when token has wrong supplyKey via V1
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.C_TOKEN_ADDRESS,
                                BigInteger.valueOf(TOKENS_TO_BURN),
                                new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                                .getOutputs()
                                .encodeElements((long) INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail when token has wrong supplyKey via V2
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                        .encodeCallWithArgs(AssociationsXTestConstants.C_TOKEN_ADDRESS, TOKENS_TO_BURN, new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                                .getOutputs()
                                .encodeElements((long) INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should successfully burn NFT via V1
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(XTestConstants.ERC721_TOKEN_ADDRESS, BigInteger.valueOf(0L), new long[] {
                            XTestConstants.SN_1234.serialNumber()
                        })
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                                .getOutputs()
                                .encodeElements((long) SUCCESS.protoOrdinal(), TOKEN_BALANCE - TOKENS_TO_BURN)
                                .array()),
                        output));

        // should successfully burn NFT via V2
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                        .encodeCallWithArgs(XTestConstants.ERC721_TOKEN_ADDRESS, 0L, new long[] {
                            XTestConstants.SN_2345.serialNumber()
                        })
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                                .getOutputs()
                                .encodeElements((long) SUCCESS.protoOrdinal(), TOKEN_BALANCE - 2)
                                .array()),
                        output));

        // should fail when NFT has wrong supplyKey via V1
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.D_TOKEN_ADDRESS,
                                BigInteger.valueOf(0L),
                                new long[] {XTestConstants.SN_1234.serialNumber()})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                                .getOutputs()
                                .encodeElements((long) INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail when NFT has wrong supplyKey via V2
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                        .encodeCallWithArgs(AssociationsXTestConstants.D_TOKEN_ADDRESS, 0L, new long[] {
                            XTestConstants.SN_1234.serialNumber()
                        })
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                                .getOutputs()
                                .encodeElements((long) INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE.protoOrdinal(), 0L)
                                .array()),
                        output));
    }

    @Override
    protected void assertExpectedTokenRelations(
            @NonNull final ReadableKVState<EntityIDPair, TokenRelation> tokenRelationships) {
        final var tokenRelation = tokenRelationships.get(EntityIDPair.newBuilder()
                .tokenId(XTestConstants.ERC20_TOKEN_ID)
                .accountId(XTestConstants.OWNER_ID)
                .build());
        assertNotNull(tokenRelation);
        // one token burnt from V1 and one token burnt from V2
        assertEquals(TOKEN_BALANCE - (TOKENS_TO_BURN + TOKENS_TO_BURN), tokenRelation.balance());

        // asserts one NFT is burnt form V1 and one from V2
        final var receiverRelation = Objects.requireNonNull(tokenRelationships.get(EntityIDPair.newBuilder()
                .tokenId(XTestConstants.ERC721_TOKEN_ID)
                .accountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                .build()));
        assertEquals(TOKEN_BALANCE - 2L, receiverRelation.balance());
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = withSenderAlias(new HashMap<>());
        aliases.put(ProtoBytes.newBuilder().value(XTestConstants.OWNER_ADDRESS).build(), XTestConstants.OWNER_ID);
        aliases.put(ProtoBytes.newBuilder().value(XTestConstants.SENDER_ADDRESS).build(), XTestConstants.SENDER_ID);
        return aliases;
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = new HashMap<TokenID, Token>();
        tokens.put(
                XTestConstants.ERC20_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(XTestConstants.ERC20_TOKEN_ID)
                        .treasuryAccountId(XTestConstants.OWNER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyKey(XTestConstants.AN_ED25519_KEY)
                        .totalSupply(TOKEN_BALANCE)
                        .build());
        tokens.put(
                AssociationsXTestConstants.A_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(AssociationsXTestConstants.A_TOKEN_ID)
                        .treasuryAccountId(XTestConstants.OWNER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .totalSupply(TOKEN_BALANCE)
                        .build());
        tokens.put(
                AssociationsXTestConstants.B_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(AssociationsXTestConstants.B_TOKEN_ID)
                        .treasuryAccountId(XTestConstants.OWNER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyKey(XTestConstants.AN_ED25519_KEY)
                        .totalSupply(TOKEN_BALANCE)
                        .build());
        tokens.put(
                AssociationsXTestConstants.C_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(AssociationsXTestConstants.C_TOKEN_ID)
                        .treasuryAccountId(XTestConstants.OWNER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyKey(Scenarios.ALICE.account().key())
                        .totalSupply(TOKEN_BALANCE)
                        .build());
        tokens.put(
                XTestConstants.ERC721_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(XTestConstants.ERC721_TOKEN_ID)
                        .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .supplyKey(XTestConstants.AN_ED25519_KEY)
                        .totalSupply(TOKEN_BALANCE)
                        .build());
        tokens.put(
                AssociationsXTestConstants.D_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(AssociationsXTestConstants.D_TOKEN_ID)
                        .treasuryAccountId(XTestConstants.OWNER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .supplyKey(Scenarios.ALICE.account().key())
                        .totalSupply(TOKEN_BALANCE)
                        .build());
        return tokens;
    }

    @Override
    protected Map<NftID, Nft> initialNfts() {
        final var nfts = new HashMap<NftID, Nft>();
        nfts.put(
                XTestConstants.SN_1234,
                Nft.newBuilder()
                        .nftId(XTestConstants.SN_1234)
                        .spenderId(HtsErc721TransferXTestConstants.APPROVED_ID)
                        .metadata(XTestConstants.SN_1234_METADATA)
                        .build());
        nfts.put(
                XTestConstants.SN_2345,
                Nft.newBuilder()
                        .nftId(XTestConstants.SN_2345)
                        .spenderId(HtsErc721TransferXTestConstants.APPROVED_ID)
                        .metadata(XTestConstants.SN_1234_METADATA)
                        .build());
        return nfts;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        XTestConstants.addErc20Relation(tokenRelationships, XTestConstants.OWNER_ID, TOKEN_BALANCE);
        XTestConstants.addErc721Relation(
                tokenRelationships, HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID, TOKEN_BALANCE);
        return tokenRelationships;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = withSenderAccount(new HashMap<>());
        accounts.put(
                XTestConstants.OWNER_ID,
                Account.newBuilder()
                        .accountId(XTestConstants.OWNER_ID)
                        .alias(XTestConstants.OWNER_ADDRESS)
                        .key(XTestConstants.SENDER_CONTRACT_ID_KEY)
                        .build());
        accounts.put(
                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID,
                Account.newBuilder()
                        .accountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .alias(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ADDRESS)
                        .build());
        return accounts;
    }
}
