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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static contract.HtsErc721TransferXTestConstants.APPROVED_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.APPROVED_BESU_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.APPROVED_HEADLONG_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.APPROVED_ID;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID;
import static contract.XTestConstants.ERC20_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC20_TOKEN_ID;
import static contract.XTestConstants.ERC721_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC721_TOKEN_ID;
import static contract.XTestConstants.OWNER_ADDRESS;
import static contract.XTestConstants.OWNER_HEADLONG_ADDRESS;
import static contract.XTestConstants.OWNER_ID;
import static contract.XTestConstants.SENDER_CONTRACT_ID_KEY;
import static contract.XTestConstants.SENDER_HEADLONG_ADDRESS;
import static contract.XTestConstants.SENDER_ID;
import static contract.XTestConstants.SN_1234;
import static contract.XTestConstants.SN_1234_METADATA;
import static contract.XTestConstants.addErc20Relation;
import static contract.XTestConstants.assertSuccess;
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
                APPROVED_BESU_ADDRESS,
                bytesForRedirect(
                        GetAllowanceTranslator.GET_ALLOWANCE.encodeCallWithArgs(
                                ERC20_TOKEN_ADDRESS, OWNER_HEADLONG_ADDRESS, APPROVED_HEADLONG_ADDRESS),
                        ERC20_TOKEN_ID),
                output -> assertEquals(
                        asBytesResult(GetAllowanceTranslator.GET_ALLOWANCE
                                .getOutputs()
                                .encodeElements((long) SUCCESS.getNumber(), BigInteger.valueOf(1_000L))),
                        output));
        runHtsCallAndExpectOnSuccess(
                APPROVED_BESU_ADDRESS,
                bytesForRedirect(
                        GetAllowanceTranslator.ERC_GET_ALLOWANCE.encodeCallWithArgs(
                                OWNER_HEADLONG_ADDRESS, APPROVED_HEADLONG_ADDRESS),
                        ERC20_TOKEN_ID),
                output -> assertEquals(
                        asBytesResult(GetAllowanceTranslator.ERC_GET_ALLOWANCE
                                .getOutputs()
                                .encodeElements(BigInteger.valueOf(1_000L))),
                        output));
        runHtsCallAndExpectOnSuccess(
                APPROVED_BESU_ADDRESS,
                bytesForRedirect(
                        GetAllowanceTranslator.GET_ALLOWANCE.encodeCallWithArgs(
                                ERC721_TOKEN_ADDRESS, OWNER_HEADLONG_ADDRESS, APPROVED_HEADLONG_ADDRESS),
                        ERC20_TOKEN_ID),
                assertSuccess());
        runHtsCallAndExpectOnSuccess(
                APPROVED_BESU_ADDRESS,
                bytesForRedirect(
                        GetAllowanceTranslator.GET_ALLOWANCE.encodeCallWithArgs(
                                ERC20_TOKEN_ADDRESS, OWNER_HEADLONG_ADDRESS, SENDER_HEADLONG_ADDRESS),
                        ERC20_TOKEN_ID),
                assertSuccess());
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        return new HashMap<>() {
            {
                put(
                        ERC20_TOKEN_ID,
                        Token.newBuilder()
                                .tokenId(ERC20_TOKEN_ID)
                                .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .build());
                put(
                        ERC721_TOKEN_ID,
                        Token.newBuilder()
                                .tokenId(ERC721_TOKEN_ID)
                                .treasuryAccountId(UNAUTHORIZED_SPENDER_ID)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .build());
            }
        };
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        return new HashMap<>() {
            {
                put(ProtoBytes.newBuilder().value(OWNER_ADDRESS).build(), OWNER_ID);
                put(ProtoBytes.newBuilder().value(APPROVED_ADDRESS).build(), APPROVED_ID);
            }
        };
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        return new HashMap<>() {
            {
                put(
                        OWNER_ID,
                        Account.newBuilder()
                                .accountId(OWNER_ID)
                                .alias(OWNER_ADDRESS)
                                .key(SENDER_CONTRACT_ID_KEY)
                                .tokenAllowances(List.of(AccountFungibleTokenAllowance.newBuilder()
                                        .spenderId(APPROVED_ID)
                                        .tokenId(ERC20_TOKEN_ID)
                                        .amount(1_000L)
                                        .build()))
                                .build());
                put(
                        APPROVED_ID,
                        Account.newBuilder()
                                .accountId(APPROVED_ID)
                                .alias(APPROVED_ADDRESS)
                                .build());
            }
        };
    }

    @Override
    protected Map<NftID, Nft> initialNfts() {
        return new HashMap<>() {
            {
                put(
                        SN_1234,
                        Nft.newBuilder()
                                .nftId(SN_1234)
                                .ownerId(OWNER_ID)
                                .spenderId(SENDER_ID)
                                .metadata(SN_1234_METADATA)
                                .build());
            }
        };
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        addErc20Relation(tokenRelationships, OWNER_ID, 100_000_000L);
        addErc20Relation(tokenRelationships, APPROVED_ID, 1_000L);
        return tokenRelationships;
    }
}
