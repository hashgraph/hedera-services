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

package contract;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static contract.AssociationsXTestConstants.A_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.A_TOKEN_ID;
import static contract.AssociationsXTestConstants.B_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.B_TOKEN_ID;
import static contract.AssociationsXTestConstants.C_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.C_TOKEN_ID;
import static contract.AssociationsXTestConstants.D_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.D_TOKEN_ID;
import static contract.HtsErc721TransferXTestConstants.APPROVED_ID;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID;
import static contract.MiscClassicTransfersXTestConstants.NEXT_ENTITY_NUM;
import static contract.WipeXTest.NUMBER_OWNED_NFTS;
import static contract.WipeXTest.TOKEN_BALANCE;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.ERC20_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC20_TOKEN_ID;
import static contract.XTestConstants.ERC721_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC721_TOKEN_ID;
import static contract.XTestConstants.INVALID_TOKEN_ADDRESS;
import static contract.XTestConstants.OWNER_ADDRESS;
import static contract.XTestConstants.OWNER_BESU_ADDRESS;
import static contract.XTestConstants.OWNER_ID;
import static contract.XTestConstants.SENDER_CONTRACT_ID_KEY;
import static contract.XTestConstants.SN_1234;
import static contract.XTestConstants.SN_1234_METADATA;
import static contract.XTestConstants.addErc20Relation;
import static contract.XTestConstants.addErc721Relation;
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
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint.MintTranslator;
import com.swirlds.state.spi.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

/**
 * Exercises mint on a fungible and non-fungible token via the following steps relative to an {@code OWNER} account:
 * <ol>
 *     <li>Mints {@code ERC20_TOKEN} via MINT operation</li>
 *     <li>Mints {@code ERC20_TOKEN} via MINT_V2 operation</li>
 *     <li>Mints {@code ERC721_TOKEN} via MINT operation</li>
 *     <li>Mints {@code ERC721_TOKEN} via MINT_V2 operation</li>
 *     <li>Mints {@code ERC20_TOKEN} without supply key via MINT operation. This should fail with TOKEN_HAS_NO_SUPPLY_KEY</li>
 *     <li>Mints {@code ERC20_TOKEN} without supply key via MINT_V2 operation. This should fail with TOKEN_HAS_NO_SUPPLY_KEY</li>
 *     <li>Mints {@code ERC721_TOKEN} without supply key via MINT operation. This should fail with TOKEN_HAS_NO_SUPPLY_KEY</li>
 *     <li>Mints {@code ERC721_TOKEN} without supply key via MINT_V2 operation. This should fail with TOKEN_HAS_NO_SUPPLY_KEY</li>
 *     <li>Mints {@code ERC20_TOKEN} with wrong supply key via MINT operation. This should fail with INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE</li>
 *     <li>Mints {@code ERC20_TOKEN} with wrong supply key via MINT_V2 operation. This should fail with INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE</li>
 *     <li>Mints {@code ERC721_TOKEN} with wrong supply key via MINT operation. This should fail with INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE</li>
 *     <li>Mints {@code ERC721_TOKEN} with wrong supply key via MINT_V2 operation. This should fail with INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE</li>
 *     <li>Mints token for invalid token address via MINT operation. This should fail with INVALID_TOKEN_ID</li>
 *     <li>Mints token for invalid token address via MINT_V2 operation. This should fail with INVALID_TOKEN_ID</li>
 * </ol>
 */
public class MintsXTest extends AbstractContractXTest {
    static final long INITIAL_SUPPLY = 1000;
    static final long MINT_AMOUNT = 10;
    static final byte[][] METADATA = {"data".getBytes()};
    static final byte[][] EMPTY_METADATA = new byte[][] {};

    @Override
    protected void doScenarioOperations() {
        // Mint 10 Tokens via mintV1
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(MintTranslator.MINT
                        .encodeCallWithArgs(ERC20_TOKEN_ADDRESS, BigInteger.valueOf(MINT_AMOUNT), EMPTY_METADATA)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(MintTranslator.MINT
                                .getOutputs()
                                .encodeElements(
                                        (long) SUCCESS.protoOrdinal(), INITIAL_SUPPLY + MINT_AMOUNT, new long[] {})
                                .array()),
                        output));

        // Mint 10 Tokens via mintV2
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(MintTranslator.MINT_V2
                        .encodeCallWithArgs(ERC20_TOKEN_ADDRESS, MINT_AMOUNT, EMPTY_METADATA)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(MintTranslator.MINT
                                .getOutputs()
                                .encodeElements(
                                        (long) SUCCESS.protoOrdinal(),
                                        INITIAL_SUPPLY + MINT_AMOUNT + MINT_AMOUNT,
                                        new long[] {})
                                .array()),
                        output));

        // Mint NFT via mintV1
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(MintTranslator.MINT
                        .encodeCallWithArgs(ERC721_TOKEN_ADDRESS, BigInteger.ZERO, METADATA)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(MintTranslator.MINT
                                .getOutputs()
                                .encodeElements((long) SUCCESS.protoOrdinal(), 1L, new long[] {1})
                                .array()),
                        output));

        // Mint NFT via mintV2
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(MintTranslator.MINT_V2
                        .encodeCallWithArgs(ERC721_TOKEN_ADDRESS, 0L, METADATA)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(MintTranslator.MINT
                                .getOutputs()
                                .encodeElements((long) SUCCESS.protoOrdinal(), 2L, new long[] {2})
                                .array()),
                        output));

        // should fail when token has no supplyKey
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(MintTranslator.MINT
                        .encodeCallWithArgs(A_TOKEN_ADDRESS, BigInteger.valueOf(MINT_AMOUNT), EMPTY_METADATA)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(MintTranslator.MINT
                                .getOutputs()
                                .encodeElements((long) TOKEN_HAS_NO_SUPPLY_KEY.protoOrdinal(), 0L, new long[] {})
                                .array()),
                        output));

        // should fail when token has no supplyKey
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(MintTranslator.MINT_V2
                        .encodeCallWithArgs(A_TOKEN_ADDRESS, MINT_AMOUNT, EMPTY_METADATA)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(MintTranslator.MINT
                                .getOutputs()
                                .encodeElements((long) TOKEN_HAS_NO_SUPPLY_KEY.protoOrdinal(), 0L, new long[] {})
                                .array()),
                        output));

        // should fail when token has no supplyKey
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(MintTranslator.MINT
                        .encodeCallWithArgs(B_TOKEN_ADDRESS, BigInteger.valueOf(0), METADATA)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(MintTranslator.MINT
                                .getOutputs()
                                .encodeElements((long) TOKEN_HAS_NO_SUPPLY_KEY.protoOrdinal(), 0L, new long[] {})
                                .array()),
                        output));

        // should fail when token has no supplyKey
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(MintTranslator.MINT_V2
                        .encodeCallWithArgs(B_TOKEN_ADDRESS, 0L, METADATA)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(MintTranslator.MINT
                                .getOutputs()
                                .encodeElements((long) TOKEN_HAS_NO_SUPPLY_KEY.protoOrdinal(), 0L, new long[] {})
                                .array()),
                        output));

        // should fail when token has wrong supplyKey
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(MintTranslator.MINT
                        .encodeCallWithArgs(C_TOKEN_ADDRESS, BigInteger.valueOf(MINT_AMOUNT), EMPTY_METADATA)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(MintTranslator.MINT
                                .getOutputs()
                                .encodeElements(
                                        (long) INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE.protoOrdinal(),
                                        0L,
                                        new long[] {})
                                .array()),
                        output));

        // should fail when token has wrong supplyKey
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(MintTranslator.MINT_V2
                        .encodeCallWithArgs(C_TOKEN_ADDRESS, MINT_AMOUNT, EMPTY_METADATA)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(MintTranslator.MINT
                                .getOutputs()
                                .encodeElements(
                                        (long) INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE.protoOrdinal(),
                                        0L,
                                        new long[] {})
                                .array()),
                        output));

        // should fail when token has wrong supplyKey
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(MintTranslator.MINT
                        .encodeCallWithArgs(D_TOKEN_ADDRESS, BigInteger.valueOf(0), METADATA)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(MintTranslator.MINT
                                .getOutputs()
                                .encodeElements(
                                        (long) INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE.protoOrdinal(),
                                        0L,
                                        new long[] {})
                                .array()),
                        output));

        // should fail when token has wrong supplyKey
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(MintTranslator.MINT_V2
                        .encodeCallWithArgs(D_TOKEN_ADDRESS, 0L, METADATA)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(MintTranslator.MINT
                                .getOutputs()
                                .encodeElements(
                                        (long) INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE.protoOrdinal(),
                                        0L,
                                        new long[] {})
                                .array()),
                        output));

        // should fail when token has invalid address
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(MintTranslator.MINT
                        .encodeCallWithArgs(INVALID_TOKEN_ADDRESS, BigInteger.valueOf(MINT_AMOUNT), EMPTY_METADATA)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(MintTranslator.MINT
                                .getOutputs()
                                .encodeElements((long) INVALID_TOKEN_ID.protoOrdinal(), 0L, new long[] {})
                                .array()),
                        output));

        // should fail when token has invalid address
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(MintTranslator.MINT_V2
                        .encodeCallWithArgs(INVALID_TOKEN_ADDRESS, MINT_AMOUNT, EMPTY_METADATA)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(MintTranslator.MINT
                                .getOutputs()
                                .encodeElements((long) INVALID_TOKEN_ID.protoOrdinal(), 0L, new long[] {})
                                .array()),
                        output));
    }

    @Override
    protected long initialEntityNum() {
        return NEXT_ENTITY_NUM - 1;
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(OWNER_ADDRESS).build(), OWNER_ID);
        return aliases;
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = new HashMap<TokenID, Token>();
        tokens.put(
                ERC20_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC20_TOKEN_ID)
                        .supplyKey(AN_ED25519_KEY)
                        .totalSupply(INITIAL_SUPPLY)
                        .treasuryAccountId(OWNER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .build());
        tokens.put(
                ERC721_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC721_TOKEN_ID)
                        .supplyKey(AN_ED25519_KEY)
                        .treasuryAccountId(OWNER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .build());
        tokens.put(
                A_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(A_TOKEN_ID)
                        .treasuryAccountId(OWNER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .totalSupply(TOKEN_BALANCE)
                        .build());
        tokens.put(
                B_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(B_TOKEN_ID)
                        .treasuryAccountId(OWNER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .build());
        tokens.put(
                C_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(C_TOKEN_ID)
                        .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyKey(SENDER_CONTRACT_ID_KEY)
                        .build());
        tokens.put(
                D_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(D_TOKEN_ID)
                        .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .supplyKey(SENDER_CONTRACT_ID_KEY)
                        .build());
        return tokens;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        addErc20Relation(tokenRelationships, OWNER_ID, TOKEN_BALANCE);
        addErc721Relation(tokenRelationships, OWNER_ID, NUMBER_OWNED_NFTS);
        tokenRelationships.put(
                EntityIDPair.newBuilder()
                        .tokenId(A_TOKEN_ID)
                        .accountId(OWNER_ID)
                        .build(),
                TokenRelation.newBuilder()
                        .tokenId(A_TOKEN_ID)
                        .accountId(OWNER_ID)
                        .balance(TOKEN_BALANCE)
                        .kycGranted(true)
                        .build());
        tokenRelationships.put(
                EntityIDPair.newBuilder()
                        .tokenId(B_TOKEN_ID)
                        .accountId(OWNER_ID)
                        .build(),
                TokenRelation.newBuilder()
                        .tokenId(B_TOKEN_ID)
                        .accountId(OWNER_ID)
                        .balance(TOKEN_BALANCE)
                        .kycGranted(true)
                        .build());
        return tokenRelationships;
    }

    @Override
    protected Map<NftID, Nft> initialNfts() {
        final var nfts = new HashMap<NftID, Nft>();
        nfts.put(
                SN_1234,
                Nft.newBuilder()
                        .nftId(SN_1234)
                        .ownerId(OWNER_ID)
                        .spenderId(APPROVED_ID)
                        .metadata(SN_1234_METADATA)
                        .build());
        return nfts;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                OWNER_ID,
                Account.newBuilder()
                        .key(AN_ED25519_KEY)
                        .accountId(OWNER_ID)
                        .numberOwnedNfts(NUMBER_OWNED_NFTS)
                        .alias(OWNER_ADDRESS)
                        .build());
        return accounts;
    }

    @Override
    protected void assertExpectedTokenRelations(
            @NonNull final ReadableKVState<EntityIDPair, TokenRelation> tokenRelationships) {
        final var tokenRelation = tokenRelationships.get(EntityIDPair.newBuilder()
                .tokenId(ERC20_TOKEN_ID)
                .accountId(OWNER_ID)
                .build());
        assertNotNull(tokenRelation);

        final var nftRelation = tokenRelationships.get(EntityIDPair.newBuilder()
                .tokenId(ERC721_TOKEN_ID)
                .accountId(OWNER_ID)
                .build());
        assertNotNull(nftRelation);

        // Token balance should be increased by 20, the sum of the two mint operations (10 + 10)
        assertEquals(TOKEN_BALANCE + (MINT_AMOUNT + MINT_AMOUNT), tokenRelation.balance());

        assertEquals(NUMBER_OWNED_NFTS + 2, nftRelation.balance());
    }
}
