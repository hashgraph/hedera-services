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
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static contract.AssociationsXTestConstants.A_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.A_TOKEN_ID;
import static contract.AssociationsXTestConstants.B_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.B_TOKEN_ID;
import static contract.AssociationsXTestConstants.C_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.C_TOKEN_ID;
import static contract.AssociationsXTestConstants.D_TOKEN_ADDRESS;
import static contract.AssociationsXTestConstants.D_TOKEN_ID;
import static contract.HtsErc721TransferXTestConstants.APPROVED_ID;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.ERC20_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC20_TOKEN_ID;
import static contract.XTestConstants.ERC721_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC721_TOKEN_ID;
import static contract.XTestConstants.INVALID_TOKEN_ADDRESS;
import static contract.XTestConstants.OWNER_ADDRESS;
import static contract.XTestConstants.OWNER_ID;
import static contract.XTestConstants.SENDER_ADDRESS;
import static contract.XTestConstants.SENDER_BESU_ADDRESS;
import static contract.XTestConstants.SENDER_CONTRACT_ID_KEY;
import static contract.XTestConstants.SENDER_ID;
import static contract.XTestConstants.SN_1234;
import static contract.XTestConstants.SN_1234_METADATA;
import static contract.XTestConstants.SN_2345;
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
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.burn.BurnTranslator;
import com.hedera.node.app.spi.fixtures.Scenarios;
import com.swirlds.state.spi.ReadableKVState;
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
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(ERC20_TOKEN_ADDRESS, BigInteger.valueOf(TOKENS_TO_BURN), new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                                .getOutputs()
                                .encodeElements((long) SUCCESS.protoOrdinal(), TOKEN_BALANCE - TOKENS_TO_BURN)
                                .array()),
                        output));

        // should successfully burn fungible token via V2
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                        .encodeCallWithArgs(ERC20_TOKEN_ADDRESS, TOKENS_TO_BURN, new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                                .getOutputs()
                                .encodeElements((long) SUCCESS.protoOrdinal(), TOKEN_BALANCE - 2L)
                                .array()),
                        output));

        // should fail when token has no supplyKey via V1
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(A_TOKEN_ADDRESS, BigInteger.valueOf(TOKENS_TO_BURN), new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                                .getOutputs()
                                .encodeElements((long) TOKEN_HAS_NO_SUPPLY_KEY.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail when token has no supplyKey via V2
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                        .encodeCallWithArgs(A_TOKEN_ADDRESS, TOKENS_TO_BURN, new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                                .getOutputs()
                                .encodeElements((long) TOKEN_HAS_NO_SUPPLY_KEY.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail when token is not associated to account via V1
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(B_TOKEN_ADDRESS, BigInteger.valueOf(TOKENS_TO_BURN), new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                                .getOutputs()
                                .encodeElements((long) TOKEN_NOT_ASSOCIATED_TO_ACCOUNT.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail when token is not associated to account via V2
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                        .encodeCallWithArgs(B_TOKEN_ADDRESS, TOKENS_TO_BURN, new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                                .getOutputs()
                                .encodeElements((long) TOKEN_NOT_ASSOCIATED_TO_ACCOUNT.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail on totalSupply < amountToBurn via V1
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(ERC20_TOKEN_ADDRESS, BigInteger.valueOf(TOKEN_BALANCE + 1), new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                                .getOutputs()
                                .encodeElements((long) INVALID_TOKEN_BURN_AMOUNT.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail on totalSupply < amountToBurn via V2
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                        .encodeCallWithArgs(ERC20_TOKEN_ADDRESS, TOKEN_BALANCE + 1, new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                                .getOutputs()
                                .encodeElements((long) INVALID_TOKEN_BURN_AMOUNT.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail on invalid token id via V2
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(INVALID_TOKEN_ADDRESS, BigInteger.valueOf(TOKEN_BALANCE + 1), new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                                .getOutputs()
                                .encodeElements((long) INVALID_TOKEN_ID.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail on invalid token id via V2
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                        .encodeCallWithArgs(INVALID_TOKEN_ADDRESS, TOKEN_BALANCE + 1, new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                                .getOutputs()
                                .encodeElements((long) INVALID_TOKEN_ID.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail when token has wrong supplyKey via V1
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(C_TOKEN_ADDRESS, BigInteger.valueOf(TOKENS_TO_BURN), new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                                .getOutputs()
                                .encodeElements((long) INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail when token has wrong supplyKey via V2
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                        .encodeCallWithArgs(C_TOKEN_ADDRESS, TOKENS_TO_BURN, new long[] {})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                                .getOutputs()
                                .encodeElements((long) INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should successfully burn NFT via V1
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(
                                ERC721_TOKEN_ADDRESS, BigInteger.valueOf(0L), new long[] {SN_1234.serialNumber()})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                                .getOutputs()
                                .encodeElements((long) SUCCESS.protoOrdinal(), TOKEN_BALANCE - TOKENS_TO_BURN)
                                .array()),
                        output));

        // should successfully burn NFT via V2
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                        .encodeCallWithArgs(ERC721_TOKEN_ADDRESS, 0L, new long[] {SN_2345.serialNumber()})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                                .getOutputs()
                                .encodeElements((long) SUCCESS.protoOrdinal(), TOKEN_BALANCE - 2)
                                .array()),
                        output));

        // should fail when NFT has wrong supplyKey via V1
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                        .encodeCallWithArgs(
                                D_TOKEN_ADDRESS, BigInteger.valueOf(0L), new long[] {SN_1234.serialNumber()})
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(BurnTranslator.BURN_TOKEN_V1
                                .getOutputs()
                                .encodeElements((long) INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE.protoOrdinal(), 0L)
                                .array()),
                        output));

        // should fail when NFT has wrong supplyKey via V2
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(BurnTranslator.BURN_TOKEN_V2
                        .encodeCallWithArgs(D_TOKEN_ADDRESS, 0L, new long[] {SN_1234.serialNumber()})
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
                .tokenId(ERC20_TOKEN_ID)
                .accountId(OWNER_ID)
                .build());
        assertNotNull(tokenRelation);
        // one token burnt from V1 and one token burnt from V2
        assertEquals(TOKEN_BALANCE - (TOKENS_TO_BURN + TOKENS_TO_BURN), tokenRelation.balance());

        // asserts one NFT is burnt form V1 and one from V2
        final var receiverRelation = Objects.requireNonNull(tokenRelationships.get(EntityIDPair.newBuilder()
                .tokenId(ERC721_TOKEN_ID)
                .accountId(UNAUTHORIZED_SPENDER_ID)
                .build()));
        assertEquals(TOKEN_BALANCE - 2L, receiverRelation.balance());
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = withSenderAlias(new HashMap<>());
        aliases.put(ProtoBytes.newBuilder().value(OWNER_ADDRESS).build(), OWNER_ID);
        aliases.put(ProtoBytes.newBuilder().value(SENDER_ADDRESS).build(), SENDER_ID);
        return aliases;
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = new HashMap<TokenID, Token>();
        tokens.put(
                ERC20_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC20_TOKEN_ID)
                        .treasuryAccountId(OWNER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyKey(AN_ED25519_KEY)
                        .totalSupply(TOKEN_BALANCE)
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
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyKey(AN_ED25519_KEY)
                        .totalSupply(TOKEN_BALANCE)
                        .build());
        tokens.put(
                C_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(C_TOKEN_ID)
                        .treasuryAccountId(OWNER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyKey(Scenarios.ALICE.account().key())
                        .totalSupply(TOKEN_BALANCE)
                        .build());
        tokens.put(
                ERC721_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC721_TOKEN_ID)
                        .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .supplyKey(AN_ED25519_KEY)
                        .totalSupply(TOKEN_BALANCE)
                        .build());
        tokens.put(
                D_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(D_TOKEN_ID)
                        .treasuryAccountId(OWNER_ID)
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
                SN_1234,
                Nft.newBuilder()
                        .nftId(SN_1234)
                        .spenderId(APPROVED_ID)
                        .metadata(SN_1234_METADATA)
                        .build());
        nfts.put(
                SN_2345,
                Nft.newBuilder()
                        .nftId(SN_2345)
                        .spenderId(APPROVED_ID)
                        .metadata(SN_1234_METADATA)
                        .build());
        return nfts;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        addErc20Relation(tokenRelationships, OWNER_ID, TOKEN_BALANCE);
        addErc721Relation(tokenRelationships, UNAUTHORIZED_SPENDER_ID, TOKEN_BALANCE);
        return tokenRelationships;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = withSenderAccount(new HashMap<>());
        accounts.put(
                OWNER_ID,
                Account.newBuilder()
                        .accountId(OWNER_ID)
                        .alias(OWNER_ADDRESS)
                        .key(SENDER_CONTRACT_ID_KEY)
                        .build());
        accounts.put(
                UNAUTHORIZED_SPENDER_ID,
                Account.newBuilder()
                        .accountId(UNAUTHORIZED_SPENDER_ID)
                        .alias(UNAUTHORIZED_SPENDER_ADDRESS)
                        .build());
        return accounts;
    }
}
