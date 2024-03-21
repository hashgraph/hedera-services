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

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static contract.HtsErc721TransferXTestConstants.APPROVED_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.APPROVED_HEADLONG_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.APPROVED_ID;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID;
import static contract.MiscClassicTransfersXTestConstants.INITIAL_OWNER_FUNGIBLE_BALANCE;
import static contract.MiscClassicTransfersXTestConstants.INITIAL_RECEIVER_AUTO_ASSOCIATIONS;
import static contract.MiscClassicTransfersXTestConstants.LAZY_CREATED_ID;
import static contract.MiscClassicTransfersXTestConstants.NEXT_ENTITY_NUM;
import static contract.XTestConstants.ERC20_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC20_TOKEN_ID;
import static contract.XTestConstants.ERC721_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC721_TOKEN_ID;
import static contract.XTestConstants.OWNER_ADDRESS;
import static contract.XTestConstants.OWNER_HEADLONG_ADDRESS;
import static contract.XTestConstants.OWNER_ID;
import static contract.XTestConstants.RECEIVER_HEADLONG_ADDRESS;
import static contract.XTestConstants.RECEIVER_ID;
import static contract.XTestConstants.SENDER_ADDRESS;
import static contract.XTestConstants.SENDER_BESU_ADDRESS;
import static contract.XTestConstants.SENDER_CONTRACT_ID_KEY;
import static contract.XTestConstants.SENDER_ID;
import static contract.XTestConstants.SN_1234;
import static contract.XTestConstants.SN_1234_METADATA;
import static contract.XTestConstants.SN_2345;
import static contract.XTestConstants.SN_2345_METADATA;
import static contract.XTestConstants.SN_3456;
import static contract.XTestConstants.SN_3456_METADATA;
import static contract.XTestConstants.addErc20Relation;
import static contract.XTestConstants.addErc721Relation;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.esaulpaugh.headlong.abi.Tuple;
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
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersTranslator;
import com.swirlds.platform.state.spi.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;

/**
 * Exercises some miscellaneous classic transfers, emphasizing slightly more exotic behaviors. These include,
 * <ul>
 *     <li>A NFT transfer to a previously unassociated account with open auto-association slots.</li>
 *     <li>A lazy-creation accomplished by transferring fungible tokens to an available alias.</li>
 * </ul>
 */
public class MiscClassicTransfersXTest extends AbstractContractXTest {
    @Override
    protected void doScenarioOperations() {
        // Unassociated account can receive a NFT since it has open auto-association slots
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(ClassicTransfersTranslator.TRANSFER_NFT
                        .encodeCallWithArgs(
                                XTestConstants.ERC721_TOKEN_ADDRESS,
                                OWNER_HEADLONG_ADDRESS,
                                RECEIVER_HEADLONG_ADDRESS,
                                SN_1234.serialNumber())
                        .array()),
                output -> assertEquals(Bytes.wrap(ReturnTypes.encodedRc(SUCCESS).array()), output));
        // Fungible transfer to an available alias can auto-create an account, while also
        // transferring a second NFT to same receiver as in the first call
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(ClassicTransfersTranslator.CRYPTO_TRANSFER
                        .encodeCallWithArgs(new Object[] {
                            new Tuple[] {
                                Tuple.of(
                                        ERC20_TOKEN_ADDRESS,
                                        new Tuple[] {
                                            Tuple.of(OWNER_HEADLONG_ADDRESS, -100L),
                                            Tuple.of(APPROVED_HEADLONG_ADDRESS, 100L)
                                        },
                                        new Tuple[] {}),
                                Tuple.of(ERC721_TOKEN_ADDRESS, new Tuple[] {}, new Tuple[] {
                                    Tuple.of(OWNER_HEADLONG_ADDRESS, RECEIVER_HEADLONG_ADDRESS, 2345L)
                                }),
                            }
                        })
                        .array()),
                output -> assertEquals(Bytes.wrap(ReturnTypes.encodedRc(SUCCESS).array()), output));
    }

    @Override
    protected void assertExpectedTokenRelations(@NonNull final ReadableKVState<EntityIDPair, TokenRelation> tokenRels) {
        final var receiverErc721Relation = requireNonNull(tokenRels.get(EntityIDPair.newBuilder()
                .tokenId(ERC721_TOKEN_ID)
                .accountId(RECEIVER_ID)
                .build()));
        assertEquals(2L, receiverErc721Relation.balance());
        final var ownerErc721Relation = requireNonNull(tokenRels.get(EntityIDPair.newBuilder()
                .tokenId(ERC721_TOKEN_ID)
                .accountId(OWNER_ID)
                .build()));
        assertEquals(1L, ownerErc721Relation.balance());
        final var lazyCreatedRelation = requireNonNull(tokenRels.get(EntityIDPair.newBuilder()
                .tokenId(ERC20_TOKEN_ID)
                .accountId(LAZY_CREATED_ID)
                .build()));
        assertEquals(100L, lazyCreatedRelation.balance());
    }

    @Override
    protected void assertExpectedAccounts(@NotNull ReadableKVState<AccountID, Account> accounts) {
        final var lazyCreation = requireNonNull(accounts.get(LAZY_CREATED_ID));
        assertEquals(1, lazyCreation.maxAutoAssociations());
        assertEquals(1, lazyCreation.usedAutoAssociations());
        assertEquals(APPROVED_ADDRESS, lazyCreation.alias());
    }

    @Override
    protected long initialEntityNum() {
        return NEXT_ENTITY_NUM - 1;
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(SENDER_ADDRESS).build(), SENDER_ID);
        aliases.put(ProtoBytes.newBuilder().value(OWNER_ADDRESS).build(), OWNER_ID);
        return aliases;
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
        tokens.put(
                ERC20_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC20_TOKEN_ID)
                        .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .build());
        return tokens;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        addErc721Relation(tokenRelationships, OWNER_ID, 3L);
        addErc721Relation(tokenRelationships, UNAUTHORIZED_SPENDER_ID, 3L);
        addErc20Relation(tokenRelationships, OWNER_ID, INITIAL_OWNER_FUNGIBLE_BALANCE);
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
        nfts.put(
                SN_2345,
                Nft.newBuilder()
                        .nftId(SN_2345)
                        .ownerId(OWNER_ID)
                        .metadata(SN_2345_METADATA)
                        .build());
        nfts.put(
                SN_3456,
                Nft.newBuilder()
                        .nftId(SN_3456)
                        .ownerId(OWNER_ID)
                        .metadata(SN_3456_METADATA)
                        .build());
        return nfts;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = withSenderContractAccount(new HashMap<>());
        accounts.put(
                OWNER_ID,
                Account.newBuilder()
                        .accountId(OWNER_ID)
                        .alias(OWNER_ADDRESS)
                        .key(SENDER_CONTRACT_ID_KEY)
                        .build());
        accounts.put(
                RECEIVER_ID,
                Account.newBuilder()
                        .accountId(RECEIVER_ID)
                        .maxAutoAssociations(INITIAL_RECEIVER_AUTO_ASSOCIATIONS)
                        .build());
        accounts.put(
                UNAUTHORIZED_SPENDER_ID,
                Account.newBuilder().accountId(UNAUTHORIZED_SPENDER_ID).build());
        return accounts;
    }
}
