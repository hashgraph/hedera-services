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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static contract.HtsErc721TransferXTestConstants.APPROVED_ID;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID;
import static contract.MiscClassicTransfersXTestConstants.INITIAL_RECEIVER_AUTO_ASSOCIATIONS;
import static contract.MiscClassicTransfersXTestConstants.NEXT_ENTITY_NUM;
import static contract.MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.ERC721_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC721_TOKEN_ID;
import static contract.XTestConstants.INVALID_SENDER_HEADLONG_ADDRESS;
import static contract.XTestConstants.OWNER_ADDRESS;
import static contract.XTestConstants.OWNER_BESU_ADDRESS;
import static contract.XTestConstants.OWNER_HEADLONG_ADDRESS;
import static contract.XTestConstants.OWNER_ID;
import static contract.XTestConstants.RECEIVER_HEADLONG_ADDRESS;
import static contract.XTestConstants.RECEIVER_ID;
import static contract.XTestConstants.SENDER_ADDRESS;
import static contract.XTestConstants.SENDER_BESU_ADDRESS;
import static contract.XTestConstants.SENDER_HEADLONG_ADDRESS;
import static contract.XTestConstants.SENDER_ID;
import static contract.XTestConstants.SN_1234;
import static contract.XTestConstants.SN_1234_METADATA;
import static contract.XTestConstants.SN_2345;
import static contract.XTestConstants.SN_2345_METADATA;
import static contract.XTestConstants.addErc721Relation;
import static contract.XTestConstants.assertSuccess;
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
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.setapproval.SetApprovalForAllTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersTranslator;
import com.swirlds.state.spi.ReadableKVState;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;

/**
 * Exercises setApprovalForAll via the following steps relative to an {@code OWNER} account:
 * <ol>
 *     <li>Transfer {@code ERC721_TOKEN} serialN 1234 from Owner's account. Should fail with SPENDER_DOES_NOT_HAVE_ALLOWANCE</li>
 *     <li>SetApprovalForAll to true and verify successful operation</li>
 *     <li>Transfer {@code ERC721_TOKEN} serialN 1234 from Owner's account and verify successful operation</li>
 *     <li>SetApprovalForAll to false and verify successful operation</li>
 *     <li>Transfer {@code ERC721_TOKEN} serialN 2345 from Owner's account. Should fail with SPENDER_DOES_NOT_HAVE_ALLOWANCE</li>
 *     <li>SetApprovalForAll with ERC call to true and verify successful operation</li>
 *     <li>Transfer {@code ERC721_TOKEN} serialN 2345 from Owner's account and verify successful operation</li>
 *     <li>SetApprovalForAll with ERC call to false and verify successful operation</li>
 *     <li>Via {@code assertExpectedAccounts} verify that 2 NFTs have been transferred from the OWNER to the RECEIVER</li>
 * </ol>
 */
public class SetApprovalForAllXTest extends AbstractContractXTest {

    private static final long NUMBER_OWNED_NFT = 3L;

    @Override
    protected void doScenarioOperations() {
        // Transfer series 1234 of ERC721_TOKEN to RECEIVER
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(ClassicTransfersTranslator.TRANSFER_NFT
                        .encodeCallWithArgs(
                                ERC721_TOKEN_ADDRESS,
                                OWNER_HEADLONG_ADDRESS,
                                RECEIVER_HEADLONG_ADDRESS,
                                SN_1234.serialNumber())
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(SPENDER_DOES_NOT_HAVE_ALLOWANCE)
                                .array()),
                        output));

        // Set approval for all to true
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(SetApprovalForAllTranslator.SET_APPROVAL_FOR_ALL
                        .encodeCallWithArgs(ERC721_TOKEN_ADDRESS, SENDER_HEADLONG_ADDRESS, true)
                        .array()),
                assertSuccess());

        // Transfer series 1234 of ERC721_TOKEN to RECEIVER
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(ClassicTransfersTranslator.TRANSFER_NFT
                        .encodeCallWithArgs(
                                ERC721_TOKEN_ADDRESS,
                                OWNER_HEADLONG_ADDRESS,
                                RECEIVER_HEADLONG_ADDRESS,
                                SN_1234.serialNumber())
                        .array()),
                assertSuccess());

        // Set approval for all to false
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(SetApprovalForAllTranslator.SET_APPROVAL_FOR_ALL
                        .encodeCallWithArgs(ERC721_TOKEN_ADDRESS, SENDER_HEADLONG_ADDRESS, false)
                        .array()),
                assertSuccess());

        // Attempt to transfer series 2345 of ERC721_TOKEN to RECEIVER
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(ClassicTransfersTranslator.TRANSFER_NFT
                        .encodeCallWithArgs(
                                ERC721_TOKEN_ADDRESS,
                                OWNER_HEADLONG_ADDRESS,
                                RECEIVER_HEADLONG_ADDRESS,
                                SN_2345.serialNumber())
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(SPENDER_DOES_NOT_HAVE_ALLOWANCE)
                                .array()),
                        output));

        // Set approval for all to true via ERC call
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                bytesForRedirect(
                        SetApprovalForAllTranslator.ERC721_SET_APPROVAL_FOR_ALL.encodeCallWithArgs(
                                SENDER_HEADLONG_ADDRESS, true),
                        ERC721_TOKEN_ID),
                assertSuccess());

        // Transfer series 2345 of ERC721_TOKEN to RECEIVER
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(ClassicTransfersTranslator.TRANSFER_NFT
                        .encodeCallWithArgs(
                                ERC721_TOKEN_ADDRESS,
                                OWNER_HEADLONG_ADDRESS,
                                RECEIVER_HEADLONG_ADDRESS,
                                SN_2345.serialNumber())
                        .array()),
                assertSuccess());

        // Set approval for all to false via ERC call
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                bytesForRedirect(
                        SetApprovalForAllTranslator.ERC721_SET_APPROVAL_FOR_ALL.encodeCallWithArgs(
                                SENDER_HEADLONG_ADDRESS, false),
                        ERC721_TOKEN_ID),
                assertSuccess());

        // @Future remove to revert #9214 after modularization is completed
        // Those tests ensure that the precompile matches mono behaviour
        // Try SetApproveForAll with Invalid Account address
        runHtsCallAndExpectRevert(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(SetApprovalForAllTranslator.SET_APPROVAL_FOR_ALL
                        .encodeCallWithArgs(ERC721_TOKEN_ADDRESS, INVALID_SENDER_HEADLONG_ADDRESS, true)
                        .array()),
                TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);

        // Try SetApproveForAll with Invalid Account address, ERC Call
        runHtsCallAndExpectRevert(
                OWNER_BESU_ADDRESS,
                bytesForRedirect(
                        SetApprovalForAllTranslator.ERC721_SET_APPROVAL_FOR_ALL.encodeCallWithArgs(
                                INVALID_SENDER_HEADLONG_ADDRESS, true),
                        ERC721_TOKEN_ID),
                TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);

        // Try SetApproveForAll with Invalid Token
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(SetApprovalForAllTranslator.SET_APPROVAL_FOR_ALL
                        .encodeCallWithArgs(ERC20_TOKEN_ADDRESS, SENDER_HEADLONG_ADDRESS, true)
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_TOKEN_ID).array()), output));
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
        return tokens;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        addErc721Relation(tokenRelationships, OWNER_ID, NUMBER_OWNED_NFT);
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
                        .key(AN_ED25519_KEY)
                        .numberOwnedNfts(NUMBER_OWNED_NFT)
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

    @Override
    protected void assertExpectedAccounts(@NotNull final ReadableKVState<AccountID, Account> accounts) {
        final var ownerAccount = accounts.get(OWNER_ID);
        final var receiverAccount = accounts.get(RECEIVER_ID);
        assertNotNull(ownerAccount);
        assertNotNull(receiverAccount);

        // Number of owned NFTs should be decreased by 2
        // Number of receiver NFTs should be 2
        assertEquals(NUMBER_OWNED_NFT - 2, ownerAccount.numberOwnedNfts());
        assertEquals(2, receiverAccount.numberOwnedNfts());
    }
}
