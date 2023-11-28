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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.allowance.GetAllowanceTranslator;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exercises allowance for a token via the following steps relative to an {@code OWNER} and {@code SENDER} accounts:
 * <ol>
 *     <li>Get Allowance for {@code ERC20_TOKEN} for SENDER via {@link com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.allowance.GetAllowanceTranslator#GET_ALLOWANCE}.</li>
 *     <li>Get Allowance for {@code ERC20_TOKEN} for SENDER via {@link com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.allowance.GetAllowanceTranslator#ERC_GET_ALLOWANCE}.</li>
 * </ol>
 */
public class GetAllowanceXTest extends AbstractContractXTest {
    @Override
    protected void doScenarioOperations() {
        runHtsCallAndExpectOnSuccess(
                HtsErc721TransferXTestConstants.APPROVED_BESU_ADDRESS,
                bytesForRedirect(
                        GetAllowanceTranslator.GET_ALLOWANCE.encodeCallWithArgs(
                                XTestConstants.ERC20_TOKEN_ADDRESS,
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                HtsErc721TransferXTestConstants.APPROVED_HEADLONG_ADDRESS),
                        XTestConstants.ERC20_TOKEN_ID),
                output -> assertEquals(
                        asBytesResult(GetAllowanceTranslator.GET_ALLOWANCE
                                .getOutputs()
                                .encodeElements((long) SUCCESS.getNumber(), BigInteger.valueOf(1_000L))),
                        output));
        runHtsCallAndExpectOnSuccess(
                HtsErc721TransferXTestConstants.APPROVED_BESU_ADDRESS,
                bytesForRedirect(
                        GetAllowanceTranslator.ERC_GET_ALLOWANCE.encodeCallWithArgs(
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                HtsErc721TransferXTestConstants.APPROVED_HEADLONG_ADDRESS),
                        XTestConstants.ERC20_TOKEN_ID),
                output -> assertEquals(
                        asBytesResult(GetAllowanceTranslator.ERC_GET_ALLOWANCE
                                .getOutputs()
                                .encodeElements(BigInteger.valueOf(1_000L))),
                        output));
        runHtsCallAndExpectOnSuccess(
                HtsErc721TransferXTestConstants.APPROVED_BESU_ADDRESS,
                bytesForRedirect(
                        GetAllowanceTranslator.GET_ALLOWANCE.encodeCallWithArgs(
                                XTestConstants.ERC721_TOKEN_ADDRESS,
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                HtsErc721TransferXTestConstants.APPROVED_HEADLONG_ADDRESS),
                        XTestConstants.ERC20_TOKEN_ID),
                XTestConstants.assertSuccess());
        runHtsCallAndExpectOnSuccess(
                HtsErc721TransferXTestConstants.APPROVED_BESU_ADDRESS,
                bytesForRedirect(
                        GetAllowanceTranslator.GET_ALLOWANCE.encodeCallWithArgs(
                                XTestConstants.ERC20_TOKEN_ADDRESS,
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                XTestConstants.SENDER_HEADLONG_ADDRESS),
                        XTestConstants.ERC20_TOKEN_ID),
                XTestConstants.assertSuccess());
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        return new HashMap<>() {
            {
                put(
                        XTestConstants.ERC20_TOKEN_ID,
                        Token.newBuilder()
                                .tokenId(XTestConstants.ERC20_TOKEN_ID)
                                .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .build());
                put(
                        XTestConstants.ERC721_TOKEN_ID,
                        Token.newBuilder()
                                .tokenId(XTestConstants.ERC721_TOKEN_ID)
                                .treasuryAccountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .build());
            }
        };
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        return new HashMap<>() {
            {
                put(ProtoBytes.newBuilder().value(XTestConstants.OWNER_ADDRESS).build(), XTestConstants.OWNER_ID);
                put(
                        ProtoBytes.newBuilder()
                                .value(HtsErc721TransferXTestConstants.APPROVED_ADDRESS)
                                .build(),
                        HtsErc721TransferXTestConstants.APPROVED_ID);
            }
        };
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        return new HashMap<>() {
            {
                put(
                        XTestConstants.OWNER_ID,
                        Account.newBuilder()
                                .accountId(XTestConstants.OWNER_ID)
                                .alias(XTestConstants.OWNER_ADDRESS)
                                .key(XTestConstants.SENDER_CONTRACT_ID_KEY)
                                .tokenAllowances(List.of(AccountFungibleTokenAllowance.newBuilder()
                                        .spenderId(HtsErc721TransferXTestConstants.APPROVED_ID)
                                        .tokenId(XTestConstants.ERC20_TOKEN_ID)
                                        .amount(1_000L)
                                        .build()))
                                .build());
                put(
                        HtsErc721TransferXTestConstants.APPROVED_ID,
                        Account.newBuilder()
                                .accountId(HtsErc721TransferXTestConstants.APPROVED_ID)
                                .alias(HtsErc721TransferXTestConstants.APPROVED_ADDRESS)
                                .build());
            }
        };
    }

    @Override
    protected Map<NftID, Nft> initialNfts() {
        return new HashMap<>() {
            {
                put(
                        XTestConstants.SN_1234,
                        Nft.newBuilder()
                                .nftId(XTestConstants.SN_1234)
                                .ownerId(XTestConstants.OWNER_ID)
                                .spenderId(XTestConstants.SENDER_ID)
                                .metadata(XTestConstants.SN_1234_METADATA)
                                .build());
            }
        };
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        XTestConstants.addErc20Relation(tokenRelationships, XTestConstants.OWNER_ID, 100_000_000L);
        XTestConstants.addErc20Relation(tokenRelationships, HtsErc721TransferXTestConstants.APPROVED_ID, 1_000L);
        return tokenRelationships;
    }
}
