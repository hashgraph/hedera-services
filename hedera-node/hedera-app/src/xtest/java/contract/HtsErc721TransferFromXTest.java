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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc721TransferFromTranslator.ERC_721_TRANSFER_FROM;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmAddress;
import static contract.HtsErc721TransferXTestConstants.APPROVED_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.APPROVED_BESU_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.APPROVED_ID;
import static contract.HtsErc721TransferXTestConstants.OPERATOR_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.OPERATOR_BESU_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.OPERATOR_ID;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_BESU_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.ERC721_TOKEN_ID;
import static contract.XTestConstants.OWNER_ADDRESS;
import static contract.XTestConstants.OWNER_BESU_ADDRESS;
import static contract.XTestConstants.OWNER_HEADLONG_ADDRESS;
import static contract.XTestConstants.OWNER_ID;
import static contract.XTestConstants.RECEIVER_HEADLONG_ADDRESS;
import static contract.XTestConstants.RECEIVER_ID;
import static contract.XTestConstants.SN_1234;
import static contract.XTestConstants.SN_1234_METADATA;
import static contract.XTestConstants.SN_2345;
import static contract.XTestConstants.SN_2345_METADATA;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.swirlds.platform.state.spi.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;

/**
 * Exercises the ERC-721 {@code transferFrom()} call by setting up an owner that has two NFTs, one of which is
 * approved for transfer by a specific account. The owner has also approved an operator to transfer any of its
 * NFTs.
 *
 * <p>The test validates that the approved account can transfer the NFT that it is approved for, and that
 * the operator can transfer even an NFT with no specific approval.
 *
 * <p>It also validates that an unauthorized account cannot transfer an NFT, and that an approved account cannot
 * transfer an NFT that it is not approved for.
 *
 * <p>The test includes two negative scenarios where we expect a revert. First, the current owner must be the first
 * argument to the call; and second, the owner must be referenced by its priority address, not its long-zero address.
 */
public class HtsErc721TransferFromXTest extends AbstractContractXTest {
    @Override
    protected void doScenarioOperations() {
        // Referencing the owner via their long-zero address doesn't work (their account won't be found)
        runHtsCallAndExpectRevert(
                OWNER_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_721_TRANSFER_FROM.encodeCallWithArgs(
                                asHeadlongAddress(asEvmAddress(OWNER_ID.accountNumOrThrow())),
                                RECEIVER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(SN_1234.serialNumber())),
                        ERC721_TOKEN_ID),
                INVALID_ACCOUNT_ID,
                "Owner priority address must be used");
        // Unauthorized spender cannot transfer owner's SN1234 NFT
        runHtsCallAndExpectRevert(
                UNAUTHORIZED_SPENDER_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_721_TRANSFER_FROM.encodeCallWithArgs(
                                OWNER_HEADLONG_ADDRESS,
                                RECEIVER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(SN_1234.serialNumber())),
                        ERC721_TOKEN_ID),
                SPENDER_DOES_NOT_HAVE_ALLOWANCE,
                "Spender does not have allowance for SN1234");
        // Approved spender for owner's SN1234 NFT cannot transfer the SN2345 NFT
        runHtsCallAndExpectRevert(
                APPROVED_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_721_TRANSFER_FROM.encodeCallWithArgs(
                                OWNER_HEADLONG_ADDRESS,
                                RECEIVER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(SN_2345.serialNumber())),
                        ERC721_TOKEN_ID),
                SPENDER_DOES_NOT_HAVE_ALLOWANCE,
                "SN1234 spender does not have allowance for SN2345");
        // Approved spender can spend owner's SN1234 NFT
        runHtsCallAndExpectOnSuccess(
                APPROVED_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_721_TRANSFER_FROM.encodeCallWithArgs(
                                OWNER_HEADLONG_ADDRESS,
                                RECEIVER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(SN_1234.serialNumber())),
                        ERC721_TOKEN_ID),
                output -> assertEquals(Bytes.EMPTY, output, "Approved spender should succeed"));
        // Operator can spend owner's SN2345 NFT
        runHtsCallAndExpectOnSuccess(
                OPERATOR_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_721_TRANSFER_FROM.encodeCallWithArgs(
                                OWNER_HEADLONG_ADDRESS,
                                RECEIVER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(SN_2345.serialNumber())),
                        ERC721_TOKEN_ID),
                output -> assertEquals(Bytes.EMPTY, output, "Operator should succeed"));
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = new HashMap<TokenID, Token>();
        tokens.put(
                ERC721_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC721_TOKEN_ID)
                        .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
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
                        .ownerId(OWNER_ID)
                        .spenderId(APPROVED_ID)
                        .metadata(SN_1234_METADATA)
                        .build());
        nfts.put(
                SN_2345,
                Nft.newBuilder()
                        .nftId(SN_2345)
                        .ownerId(OWNER_ID)
                        .metadata(SN_2345_METADATA)
                        .build());
        return nfts;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        XTestConstants.addErc721Relation(tokenRelationships, OWNER_ID, 2L);
        XTestConstants.addErc721Relation(tokenRelationships, UNAUTHORIZED_SPENDER_ID, 3L);
        XTestConstants.addErc721Relation(tokenRelationships, APPROVED_ID, 1L);
        XTestConstants.addErc721Relation(tokenRelationships, OPERATOR_ID, 1L);
        XTestConstants.addErc721Relation(tokenRelationships, RECEIVER_ID, 0L);
        return tokenRelationships;
    }

    @Override
    protected void assertExpectedTokenRelations(@NotNull ReadableKVState<EntityIDPair, TokenRelation> tokenRels) {
        final var ownerRelation = Objects.requireNonNull(tokenRels.get(EntityIDPair.newBuilder()
                .tokenId(ERC721_TOKEN_ID)
                .accountId(OWNER_ID)
                .build()));
        assertEquals(0L, ownerRelation.balance());
        final var receiverRelation = Objects.requireNonNull(tokenRels.get(EntityIDPair.newBuilder()
                .tokenId(ERC721_TOKEN_ID)
                .accountId(RECEIVER_ID)
                .build()));
        assertEquals(2L, receiverRelation.balance());
    }

    @Override
    protected void assertExpectedNfts(@NonNull final ReadableKVState<NftID, Nft> nfts) {
        final var sn1234 = Objects.requireNonNull(nfts.get(SN_1234));
        assertEquals(RECEIVER_ID, sn1234.ownerId());
        final var sn2345 = Objects.requireNonNull(nfts.get(SN_1234));
        assertEquals(RECEIVER_ID, sn2345.ownerId());
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(OWNER_ADDRESS).build(), OWNER_ID);
        aliases.put(ProtoBytes.newBuilder().value(UNAUTHORIZED_SPENDER_ADDRESS).build(), UNAUTHORIZED_SPENDER_ID);
        aliases.put(ProtoBytes.newBuilder().value(APPROVED_ADDRESS).build(), APPROVED_ID);
        aliases.put(ProtoBytes.newBuilder().value(OPERATOR_ADDRESS).build(), OPERATOR_ID);
        return aliases;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                OWNER_ID,
                Account.newBuilder()
                        .accountId(OWNER_ID)
                        .alias(OWNER_ADDRESS)
                        .key(AN_ED25519_KEY)
                        .approveForAllNftAllowances(List.of(AccountApprovalForAllAllowance.newBuilder()
                                .spenderId(OPERATOR_ID)
                                .tokenId(ERC721_TOKEN_ID)
                                .build()))
                        .build());
        accounts.put(
                UNAUTHORIZED_SPENDER_ID,
                Account.newBuilder()
                        .accountId(UNAUTHORIZED_SPENDER_ID)
                        .key(AN_ED25519_KEY)
                        .alias(UNAUTHORIZED_SPENDER_ADDRESS)
                        .build());
        accounts.put(
                APPROVED_ID,
                Account.newBuilder()
                        .accountId(APPROVED_ID)
                        .key(AN_ED25519_KEY)
                        .alias(APPROVED_ADDRESS)
                        .build());
        accounts.put(
                OPERATOR_ID,
                Account.newBuilder()
                        .accountId(OPERATOR_ID)
                        .key(AN_ED25519_KEY)
                        .alias(OPERATOR_ADDRESS)
                        .build());
        accounts.put(
                RECEIVER_ID,
                Account.newBuilder().accountId(RECEIVER_ID).key(AN_ED25519_KEY).build());
        return accounts;
    }
}
