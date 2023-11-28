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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY;
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
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.wipe.WipeTranslator;
import com.hedera.node.app.spi.fixtures.Scenarios;
import com.hedera.node.app.spi.state.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

/**
 * Exercises wipeTokenAccount and wipeTokenAccountNFT via the following steps relative to an {@code OWNER} account:
 * <ol>
 *     <li>Wipe {@code ERC721_TOKEN} serial 1234 from Owner's account and verify successful operation</li>*
 *     <li>Wipe 10 {@code ERC20_TOKEN} Owner's account via wipeTokenAccountV1 and verify successful operation</li>*
 *     <li>Wipe 10 {@code ERC20_TOKEN} Owner's account via wipeTokenAccountV2 and verify successful operation</li>*
 *     <li>Wipe {@code ERC721_TOKEN} serial 1234 from Owner's account. This should fail with INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE</li>*
 *     <li>Wipe {@code ERC721_TOKEN} serial 1234 from Owner's account. This should fail with TOKEN_HAS_NO_WIPE_KEY</li>*
 *     <li>Wipe 10 {@code ERC20_TOKEN} Owner's account via wipeTokenAccountV1. This should fail with INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE</li>*
 *     <li>Wipe 10 {@code ERC20_TOKEN} Owner's account via wipeTokenAccountV1. This should fail with TOKEN_HAS_NO_WIPE_KEY</li>*
 *     <li>Wipe 10 {@code ERC20_TOKEN} Owner's account via wipeTokenAccountV2. This should fail with INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE</li>*
 *     <li>Wipe 10 {@code ERC20_TOKEN} Owner's account via wipeTokenAccountV2. This should fail with TOKEN_HAS_NO_WIPE_KEY</li>*
 *     <li>Via {@code assertExpectedAccounts} verify that owner's nft supply was decreased by 1.</li>*
 *     <li>Via {@code assertExpectedTokenRelations} verify that owner's token balance was decreased by 20.</li>*
 * </ol>
 */
public class WipeXTest extends AbstractContractXTest {

    public static final int NUMBER_OWNED_NFTS = 3;
    public static final long TOKEN_BALANCE = 50L;

    @Override
    protected void doScenarioOperations() {
        // WIPE NFT from OWNER's account
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(WipeTranslator.WIPE_NFT
                        .encodeCallWithArgs(
                                XTestConstants.ERC721_TOKEN_ADDRESS,
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                new long[] {XTestConstants.SN_1234.serialNumber()})
                        .array()),
                XTestConstants.assertSuccess("Failed to wipe NFT from Owner's account"));

        // WIPE 10 Tokens via wipeTokenAccountV1
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(WipeTranslator.WIPE_FUNGIBLE_V1
                        .encodeCallWithArgs(
                                XTestConstants.ERC20_TOKEN_ADDRESS, XTestConstants.OWNER_HEADLONG_ADDRESS, 10L)
                        .array()),
                XTestConstants.assertSuccess("Failed to wipe 10 Tokens from Owner's account"));

        // WIPE 10 Tokens via wipeTokenAccountV2
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(WipeTranslator.WIPE_FUNGIBLE_V2
                        .encodeCallWithArgs(
                                XTestConstants.ERC20_TOKEN_ADDRESS, XTestConstants.OWNER_HEADLONG_ADDRESS, 10L)
                        .array()),
                XTestConstants.assertSuccess("Failed to wipe 10 Tokens from Owner's account (V2)"));

        // @Future remove to revert #9272 after modularization is completed
        // Try to WIPE NFT with Invalid Token address
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(WipeTranslator.WIPE_NFT
                        .encodeCallWithArgs(
                                XTestConstants.OTHER_TOKEN_ADDRESS,
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                new long[] {XTestConstants.SN_1234.serialNumber()})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_TOKEN_ID).array()),
                        output,
                        "Expected INVALID_TOKEN_ID when trying to WIPE NFT with Invalid Token address"));

        // Try to WIPE NFT with Invalid Account address
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(WipeTranslator.WIPE_NFT
                        .encodeCallWithArgs(
                                XTestConstants.ERC721_TOKEN_ADDRESS,
                                XTestConstants.INVALID_SENDER_HEADLONG_ADDRESS,
                                new long[] {XTestConstants.SN_1234.serialNumber()})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_ACCOUNT_ID).array()),
                        output,
                        "Expected INVALID_ACCOUNT_ID when trying to WIPE NFT with Invalid Account address"));

        // Try to WIPE NFT with Invalid serial numbers address
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(WipeTranslator.WIPE_NFT
                        .encodeCallWithArgs(
                                XTestConstants.ERC721_TOKEN_ADDRESS,
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                new long[] {-7511})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_NFT_ID).array()),
                        output,
                        "Expected INVALID_NFT_ID when trying to WIPE NFT with Invalid serial numbers address"));

        // Try to execute with token address
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(WipeTranslator.WIPE_FUNGIBLE_V1
                        .encodeCallWithArgs(
                                XTestConstants.OTHER_TOKEN_ADDRESS, XTestConstants.OWNER_HEADLONG_ADDRESS, 10L)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_TOKEN_ID).array()),
                        output,
                        "Expected INVALID_TOKEN_ID when trying to execute with other token address"));

        // Try to execute with invalid account address
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(WipeTranslator.WIPE_FUNGIBLE_V2
                        .encodeCallWithArgs(
                                XTestConstants.ERC20_TOKEN_ADDRESS, XTestConstants.INVALID_SENDER_HEADLONG_ADDRESS, 10L)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_ACCOUNT_ID).array()),
                        output,
                        "Expected INVALID_ACCOUNT_ID when trying to execute with invalid account address (V2)"));

        // WIPE NFT from OWNER's account  with invalid key
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(WipeTranslator.WIPE_NFT
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.A_TOKEN_ADDRESS,
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                new long[] {XTestConstants.SN_1234.serialNumber()})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                .array()),
                        output,
                        "Expected INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE when trying to execute with invalid wipe key"));

        // WIPE NFT from OWNER's account without wipe key
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(WipeTranslator.WIPE_NFT
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.B_TOKEN_ADDRESS,
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                new long[] {XTestConstants.SN_1234.serialNumber()})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(TOKEN_HAS_NO_WIPE_KEY).array()),
                        output,
                        "Expected TOKEN_HAS_NO_WIPE_KEY when trying to execute with invalid wipe key"));

        // WIPE 10 Tokens via wipeTokenAccountV1  with invalid key
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(WipeTranslator.WIPE_FUNGIBLE_V1
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.C_TOKEN_ADDRESS, XTestConstants.OWNER_HEADLONG_ADDRESS, 10L)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                .array()),
                        output,
                        "Expected INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE when trying to execute with invalid wipe key"));

        // WIPE 10 Tokens via wipeTokenAccountV1 without wipe key
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(WipeTranslator.WIPE_FUNGIBLE_V1
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.D_TOKEN_ADDRESS, XTestConstants.OWNER_HEADLONG_ADDRESS, 10L)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(TOKEN_HAS_NO_WIPE_KEY).array()),
                        output,
                        "Expected TOKEN_HAS_NO_WIPE_KEY when trying to execute with invalid wipe key"));

        // WIPE 10 Tokens via wipeTokenAccountV2 with invalid key
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(WipeTranslator.WIPE_FUNGIBLE_V2
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.C_TOKEN_ADDRESS, XTestConstants.OWNER_HEADLONG_ADDRESS, 10L)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                .array()),
                        output,
                        "Expected INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE when trying to execute with invalid wipe key"));

        // WIPE 10 Tokens via wipeTokenAccountV2 without wipe key
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(WipeTranslator.WIPE_FUNGIBLE_V2
                        .encodeCallWithArgs(
                                AssociationsXTestConstants.D_TOKEN_ADDRESS, XTestConstants.OWNER_HEADLONG_ADDRESS, 10L)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(TOKEN_HAS_NO_WIPE_KEY).array()),
                        output,
                        "Expected TOKEN_HAS_NO_WIPE_KEY when trying to execute with invalid wipe key"));
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(XTestConstants.OWNER_ADDRESS).build(), XTestConstants.OWNER_ID);
        return aliases;
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = new HashMap<TokenID, Token>();
        tokens.put(
                XTestConstants.ERC721_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(XTestConstants.ERC721_TOKEN_ID)
                        .wipeKey(XTestConstants.AN_ED25519_KEY)
                        .totalSupply(1000)
                        .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .build());
        tokens.put(
                XTestConstants.ERC20_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(XTestConstants.ERC20_TOKEN_ID)
                        .wipeKey(XTestConstants.AN_ED25519_KEY)
                        .totalSupply(1000)
                        .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .build());
        tokens.put(
                AssociationsXTestConstants.A_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(AssociationsXTestConstants.A_TOKEN_ID)
                        .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .totalSupply(1000L)
                        .wipeKey(Scenarios.ALICE.account().key())
                        .build());
        tokens.put(
                AssociationsXTestConstants.B_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(AssociationsXTestConstants.B_TOKEN_ID)
                        .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .totalSupply(1000L)
                        .build());
        tokens.put(
                AssociationsXTestConstants.C_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(AssociationsXTestConstants.C_TOKEN_ID)
                        .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .totalSupply(1000L)
                        .wipeKey(Scenarios.ALICE.account().key())
                        .build());
        tokens.put(
                AssociationsXTestConstants.D_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(AssociationsXTestConstants.D_TOKEN_ID)
                        .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .totalSupply(1000L)
                        .build());
        return tokens;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        XTestConstants.addErc721Relation(tokenRelationships, XTestConstants.OWNER_ID, NUMBER_OWNED_NFTS);
        XTestConstants.addErc20Relation(tokenRelationships, XTestConstants.OWNER_ID, TOKEN_BALANCE);
        return tokenRelationships;
    }

    @Override
    protected Map<NftID, Nft> initialNfts() {
        final var nfts = new HashMap<NftID, Nft>();
        nfts.put(
                XTestConstants.SN_1234,
                Nft.newBuilder()
                        .nftId(XTestConstants.SN_1234)
                        .ownerId(XTestConstants.OWNER_ID)
                        .spenderId(HtsErc721TransferXTestConstants.APPROVED_ID)
                        .metadata(XTestConstants.SN_1234_METADATA)
                        .build());
        return nfts;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                XTestConstants.OWNER_ID,
                Account.newBuilder()
                        .accountId(XTestConstants.OWNER_ID)
                        .numberOwnedNfts(NUMBER_OWNED_NFTS)
                        .alias(XTestConstants.OWNER_ADDRESS)
                        .key(XTestConstants.AN_ED25519_KEY)
                        .build());
        accounts.put(
                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID,
                Account.newBuilder()
                        .accountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .key(XTestConstants.AN_ED25519_KEY)
                        .build());
        return accounts;
    }

    @Override
    protected void assertExpectedAccounts(@NonNull final ReadableKVState<AccountID, Account> accounts) {
        final var account = accounts.get(XTestConstants.OWNER_ID);
        assertNotNull(account);
        // Number of owned NFTs should be decreased by 1
        assertEquals(NUMBER_OWNED_NFTS - 1, account.numberOwnedNfts());
    }

    @Override
    protected void assertExpectedTokenRelations(
            @NonNull final ReadableKVState<EntityIDPair, TokenRelation> tokenRelationships) {
        final var tokenRelation = tokenRelationships.get(EntityIDPair.newBuilder()
                .tokenId(XTestConstants.ERC20_TOKEN_ID)
                .accountId(XTestConstants.OWNER_ID)
                .build());
        assertNotNull(tokenRelation);
        // Token balance should be decreased by 20, the sum of the two wipe operations (10 + 10)
        assertEquals(TOKEN_BALANCE - 20L, tokenRelation.balance());
    }
}
