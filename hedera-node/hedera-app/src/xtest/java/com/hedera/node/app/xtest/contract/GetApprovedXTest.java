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

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved.GetApprovedTranslator.ERC_GET_APPROVED;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved.GetApprovedTranslator.HAPI_GET_APPROVED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

public class GetApprovedXTest extends AbstractContractXTest {

    @Override
    protected void doScenarioOperations() {
        // ErcGetApproved series 1234 of ERC721_TOKEN
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_GET_APPROVED.encodeCallWithArgs(BigInteger.valueOf(1234)), XTestConstants.ERC721_TOKEN_ID),
                output -> assertEquals(
                        asBytesResult(ERC_GET_APPROVED
                                .getOutputs()
                                .encodeElements(ConversionUtils.headlongAddressOf(
                                        HtsErc721TransferXTestConstants.spenderAccount))),
                        output));

        // HapiGetApproved series 1234 of ERC721_TOKEN
        runHtsCallAndExpectOnSuccess(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(HAPI_GET_APPROVED
                        .encodeCallWithArgs(XTestConstants.ERC721_TOKEN_ADDRESS, BigInteger.valueOf(1234))
                        .array()),
                output -> assertEquals(
                        asBytesResult(HAPI_GET_APPROVED
                                .getOutputs()
                                .encodeElements(
                                        SUCCESS.getNumber(),
                                        ConversionUtils.headlongAddressOf(
                                                HtsErc721TransferXTestConstants.spenderAccount))),
                        output));

        // HapiGetApproved for invalid series 3456
        runHtsCallAndExpectRevert(
                XTestConstants.SENDER_BESU_ADDRESS,
                Bytes.wrap(HAPI_GET_APPROVED
                        .encodeCallWithArgs(XTestConstants.ERC721_TOKEN_ADDRESS, BigInteger.valueOf(3456))
                        .array()),
                ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER);
    }

    @Override
    protected long initialEntityNum() {
        return MiscViewsXTestConstants.NEXT_ENTITY_NUM - 1;
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = withSenderAlias(new HashMap<>());
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
                HtsErc721TransferXTestConstants.APPROVED_ID,
                Account.newBuilder()
                        .accountId(HtsErc721TransferXTestConstants.APPROVED_ID)
                        .build());
        accounts.put(
                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID,
                Account.newBuilder()
                        .accountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                        .build());
        return accounts;
    }
}
