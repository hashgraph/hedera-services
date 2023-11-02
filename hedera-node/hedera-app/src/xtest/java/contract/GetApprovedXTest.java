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

package contract;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved.GetApprovedTranslator.ERC_GET_APPROVED;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved.GetApprovedTranslator.HAPI_GET_APPROVED;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static contract.HtsErc721TransferXTestConstants.APPROVED_ID;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID;
import static contract.HtsErc721TransferXTestConstants.spenderAccount;
import static contract.MiscViewsXTestConstants.NEXT_ENTITY_NUM;
import static contract.XTestConstants.ERC721_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC721_TOKEN_ID;
import static contract.XTestConstants.OWNER_ADDRESS;
import static contract.XTestConstants.OWNER_ID;
import static contract.XTestConstants.SENDER_ADDRESS;
import static contract.XTestConstants.SENDER_BESU_ADDRESS;
import static contract.XTestConstants.SENDER_CONTRACT_ID_KEY;
import static contract.XTestConstants.SENDER_ID;
import static contract.XTestConstants.SN_1234;
import static contract.XTestConstants.SN_1234_METADATA;
import static contract.XTestConstants.SN_2345;
import static contract.XTestConstants.SN_2345_METADATA;
import static contract.XTestConstants.addErc721Relation;
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
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

public class GetApprovedXTest extends AbstractContractXTest {

    @Override
    protected void doScenarioOperations() {
        // ErcGetApproved series 1234 of ERC721_TOKEN
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                bytesForRedirect(ERC_GET_APPROVED.encodeCallWithArgs(BigInteger.valueOf(1234)), ERC721_TOKEN_ID),
                output -> assertEquals(
                        asBytesResult(ERC_GET_APPROVED.getOutputs().encodeElements(headlongAddressOf(spenderAccount))),
                        output));

        // HapiGetApproved series 1234 of ERC721_TOKEN
        runHtsCallAndExpectOnSuccess(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(HAPI_GET_APPROVED
                        .encodeCallWithArgs(ERC721_TOKEN_ADDRESS, BigInteger.valueOf(1234))
                        .array()),
                output -> assertEquals(
                        asBytesResult(HAPI_GET_APPROVED
                                .getOutputs()
                                .encodeElements(SUCCESS.getNumber(), headlongAddressOf(spenderAccount))),
                        output));

        // HapiGetApproved for invalid series 3456
        runHtsCallAndExpectRevert(
                SENDER_BESU_ADDRESS,
                Bytes.wrap(HAPI_GET_APPROVED
                        .encodeCallWithArgs(ERC721_TOKEN_ADDRESS, BigInteger.valueOf(3456))
                        .array()),
                ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER);
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
        addErc721Relation(tokenRelationships, OWNER_ID, 3L);
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
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                SENDER_ID,
                Account.newBuilder()
                        .accountId(OWNER_ID)
                        .alias(SENDER_ADDRESS)
                        .smartContract(true)
                        .build());
        accounts.put(
                OWNER_ID,
                Account.newBuilder()
                        .accountId(OWNER_ID)
                        .alias(OWNER_ADDRESS)
                        .key(SENDER_CONTRACT_ID_KEY)
                        .build());
        accounts.put(APPROVED_ID, Account.newBuilder().accountId(APPROVED_ID).build());
        accounts.put(
                UNAUTHORIZED_SPENDER_ID,
                Account.newBuilder().accountId(UNAUTHORIZED_SPENDER_ID).build());
        return accounts;
    }
}
