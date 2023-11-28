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
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_PAUSE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.pauses.PausesTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersTranslator;
import com.hedera.node.app.spi.fixtures.Scenarios;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

/**
 * Exercises pause and unpause on a token via the following steps relative to an {@code OWNER} account:
 * <ol>
 *     <li>Transfer {@code ERC721_TOKEN} serial 1234 from SENDER to RECEIVER.</li>*
 *     <li>Pause {@code ERC721_TOKEN} via {@link PausesTranslator#PAUSE}.</li>
 *     <li>Transfer {@code ERC721_TOKEN} serial 2345 from SENDER to RECEIVER. This should fail with code TOKEN_IS_PAUSED.</li>*
 *     <li>Unpause {@code ERC721_TOKEN} via {@link PausesTranslator#UNPAUSE}.</li>
 *     <li>Transfer {@code ERC721_TOKEN} serial 2345 from SENDER to RECEIVER. This should now succeed.</li>*
 *     <li>Pause {@code ERC721_TOKEN} via {@link PausesTranslator#PAUSE}. This should fail with code INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE.</li>
 *     <li>Pause {@code ERC721_TOKEN} via {@link PausesTranslator#PAUSE}. This should fail with code TOKEN_HAS_NO_PAUSE_KEY.</li>
 *     <li>Unpause {@code ERC721_TOKEN} via {@link PausesTranslator#UNPAUSE}. This should fail with code INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE.</li>
 *     <li>Unpause {@code ERC721_TOKEN} via {@link PausesTranslator#UNPAUSE}. This should fail with code TOKEN_HAS_NO_PAUSE_KEY.</li>
 * </ol>
 */
public class PausesXTest extends AbstractContractXTest {
    @Override
    protected void doScenarioOperations() {
        // Transfer series 1234 of ERC721_TOKEN to RECEIVER
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(ClassicTransfersTranslator.TRANSFER_NFT
                        .encodeCallWithArgs(
                                XTestConstants.ERC721_TOKEN_ADDRESS,
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                XTestConstants.RECEIVER_HEADLONG_ADDRESS,
                                XTestConstants.SN_1234.serialNumber())
                        .array()),
                XTestConstants.assertSuccess("Pre-pause transfer failed"));

        // PAUSE
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(PausesTranslator.PAUSE
                        .encodeCallWithArgs(XTestConstants.ERC721_TOKEN_ADDRESS)
                        .array()),
                XTestConstants.assertSuccess("Pause failed"));

        // Transfer series 2345 of ERC721_TOKEN to RECEIVER - should fail with TOKEN_IS_PAUSED
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(ClassicTransfersTranslator.TRANSFER_NFT
                        .encodeCallWithArgs(
                                XTestConstants.ERC721_TOKEN_ADDRESS,
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                XTestConstants.RECEIVER_HEADLONG_ADDRESS,
                                XTestConstants.SN_2345.serialNumber())
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(TOKEN_IS_PAUSED).array()),
                        output,
                        "Token should have been paused"));

        // UNPAUSE
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(PausesTranslator.UNPAUSE
                        .encodeCallWithArgs(XTestConstants.ERC721_TOKEN_ADDRESS)
                        .array()),
                XTestConstants.assertSuccess("Unpause failed"));

        // Transfer series 2345 of ERC721_TOKEN to RECEIVER - should succeed now.
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(ClassicTransfersTranslator.TRANSFER_NFT
                        .encodeCallWithArgs(
                                XTestConstants.ERC721_TOKEN_ADDRESS,
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                XTestConstants.RECEIVER_HEADLONG_ADDRESS,
                                XTestConstants.SN_2345.serialNumber())
                        .array()),
                XTestConstants.assertSuccess("Post-unpause transfer failed"));

        // PAUSE wrong pause key
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(PausesTranslator.PAUSE
                        .encodeCallWithArgs(AssociationsXTestConstants.A_TOKEN_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                .array()),
                        output,
                        "Token should have been paused"));

        // PAUSE no pause key
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(PausesTranslator.PAUSE
                        .encodeCallWithArgs(AssociationsXTestConstants.B_TOKEN_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(TOKEN_HAS_NO_PAUSE_KEY).array()),
                        output,
                        "Token should have been paused"));

        // UNPAUSE wrong pause key
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(PausesTranslator.UNPAUSE
                        .encodeCallWithArgs(AssociationsXTestConstants.A_TOKEN_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)
                                .array()),
                        output,
                        "Token should have been paused"));

        // UNPAUSE no pause key
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(PausesTranslator.UNPAUSE
                        .encodeCallWithArgs(AssociationsXTestConstants.B_TOKEN_ADDRESS)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(TOKEN_HAS_NO_PAUSE_KEY).array()),
                        output,
                        "Token should have been paused"));
    }

    @Override
    protected long initialEntityNum() {
        return MiscClassicTransfersXTestConstants.NEXT_ENTITY_NUM - 1;
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(XTestConstants.SENDER_ADDRESS).build(), XTestConstants.SENDER_ID);
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
                        .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .pauseKey(XTestConstants.SENDER_CONTRACT_ID_KEY)
                        .build());
        tokens.put(
                AssociationsXTestConstants.A_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(AssociationsXTestConstants.A_TOKEN_ID)
                        .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .pauseKey(Scenarios.ALICE.account().key())
                        .build());
        tokens.put(
                AssociationsXTestConstants.B_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(AssociationsXTestConstants.B_TOKEN_ID)
                        .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .build());
        return tokens;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        XTestConstants.addErc721Relation(tokenRelationships, XTestConstants.OWNER_ID, 3L);
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
        nfts.put(
                XTestConstants.SN_2345,
                Nft.newBuilder()
                        .nftId(XTestConstants.SN_2345)
                        .ownerId(XTestConstants.OWNER_ID)
                        .metadata(XTestConstants.SN_2345_METADATA)
                        .build());
        return nfts;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = withSenderContractAccount(new HashMap<>());
        accounts.put(
                XTestConstants.OWNER_ID,
                Account.newBuilder()
                        .accountId(XTestConstants.OWNER_ID)
                        .alias(XTestConstants.OWNER_ADDRESS)
                        .key(XTestConstants.SENDER_CONTRACT_ID_KEY)
                        .build());
        accounts.put(
                XTestConstants.RECEIVER_ID,
                Account.newBuilder()
                        .accountId(XTestConstants.RECEIVER_ID)
                        .maxAutoAssociations(MiscClassicTransfersXTestConstants.INITIAL_RECEIVER_AUTO_ASSOCIATIONS)
                        .build());
        accounts.put(
                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID,
                Account.newBuilder()
                        .accountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .build());
        return accounts;
    }
}
