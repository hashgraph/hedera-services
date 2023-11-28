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

import static com.hedera.hapi.node.base.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc20TransfersTranslator.ERC_20_TRANSFER_FROM;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersTranslator;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;

/**
 * Exercises approve and approveNFT for a token via the following steps relative to an {@code OWNER} and {@code SENDER}
 * accounts:
 * <ol>
 *     <li>Transfer {@code ERC20_TOKEN} to RECEIVER. This should fail with code SPENDER_DOES_NOT_HAVE_ALLOWANCE.</li>
 *     <li>Approve {@code ERC20_TOKEN} via {@link com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator#GRANT_APPROVAL}.</li>
 *     <li>Approve {@code ERC20_TOKEN} via {@link com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator#GRANT_APPROVAL}.
 *     This should fail with code TOKEN_NOT_ASSOCIATED_TO_ACCOUNT.</li>
 *     <li>Transfer {@code ERC20_TOKEN} from SENDER to RECEIVER. This should now succeed.</li>
 *     <li>Transfer {@code ERC20_TOKEN} from SENDER to RECEIVER. This should fail with code AMOUNT_EXCEEDS_ALLOWANCE.</li>
 *     <li>Transfer {@code ERC721_TOKEN} to RECEIVER. This should fail with code SPENDER_DOES_NOT_HAVE_ALLOWANCE.</li>
 *     <li>Approve NFT {@code ERC721_TOKEN} via {@link com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator#GRANT_APPROVAL_NFT}.
 *     This should fail with code INVALID_TOKEN_NFT_SERIAL_NUMBER.</li>
 *     <li>Approve NFT {@code ERC721_TOKEN} via {@link com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator#GRANT_APPROVAL_NFT}.</li>
 *     <li>Approve NFT {@code ERC721_TOKEN} via {@link com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator#GRANT_APPROVAL_NFT}.
 *     This should fail with code SENDER_DOES_NOT_OWN_NFT_SERIAL_NO.</li>
 *     <li>Approve NFT {@code ERC721_TOKEN} via {@link com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator#GRANT_APPROVAL_NFT}.
 *     This should fail with code FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES.</li>
 *     <li>Approve NFT {@code ERC721_TOKEN} via {@link com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator#GRANT_APPROVAL_NFT}.
 *     This should fail with code INVALID_TOKEN_ID.</li>
 *     <li>Transfer {@code ERC721_TOKEN} from  SENDER to RECEIVER. This should now succeed.</li>
 *     <li> ERC Approve {@code ERC20_TOKEN} via {@link com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator#ERC_GRANT_APPROVAL}.</li>
 *     <li>Transfer {@code ERC20_TOKEN} from  SENDER to RECEIVER. This should now succeed.</li>
 *     <li> ERC Approve {@code ERC721_TOKEN} via {@link com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator#ERC_GRANT_APPROVAL}.</li>
 *     <li>Transfer {@code ERC721_TOKEN} from  SENDER to RECEIVER. This should now succeed.</li>
 * </ol>
 */
public class GrantApprovalXTest extends AbstractContractXTest {

    @Override
    protected void doScenarioOperations() {
        // TRY TRANSFER AND EXPECT FAIL
        runHtsCallAndExpectRevert(
                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_20_TRANSFER_FROM.encodeCallWithArgs(
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                XTestConstants.RECEIVER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(100L)),
                        XTestConstants.ERC20_TOKEN_ID),
                SPENDER_DOES_NOT_HAVE_ALLOWANCE,
                "Unauthorized spending of fungible units");
        // APPROVE
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(GrantApprovalTranslator.GRANT_APPROVAL
                        .encodeCallWithArgs(
                                XTestConstants.ERC20_TOKEN_ADDRESS,
                                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(100L))
                        .array()),
                XTestConstants.assertSuccess(),
                "Owner granting approval of 100 fungible units");
        // TRY APPROVE AND EXPECT IVALID
        runHtsCallAndExpectRevert(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(GrantApprovalTranslator.GRANT_APPROVAL
                        .encodeCallWithArgs(
                                XTestConstants.ERC20_TOKEN_ADDRESS,
                                XTestConstants.ERC721_TOKEN_ADDRESS,
                                BigInteger.valueOf(100L))
                        .array()),
                ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT,
                "Owner granting approval of 100 fungible units for invalid spender");
        // TRY TRANSFER AND EXPECT SUCCESS
        runHtsCallAndExpectOnSuccess(
                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_20_TRANSFER_FROM.encodeCallWithArgs(
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                XTestConstants.RECEIVER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(50L)),
                        XTestConstants.ERC20_TOKEN_ID),
                output -> assertEquals(
                        asBytesResult(ERC_20_TRANSFER_FROM.getOutputs().encodeElements(true)), output),
                "Owner transferring 50 of its own units");
        // TRY TRANSFER AND EXPECT FAIL
        runHtsCallAndExpectRevert(
                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_20_TRANSFER_FROM.encodeCallWithArgs(
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                XTestConstants.RECEIVER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(100L)),
                        XTestConstants.ERC20_TOKEN_ID),
                AMOUNT_EXCEEDS_ALLOWANCE,
                "Excessive spending (100 units vs 50 approved)");
        // TRY APPROVE NFT WITH INVALID SERIAL
        runHtsCallAndExpectRevert(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(GrantApprovalTranslator.GRANT_APPROVAL_NFT
                        .encodeCallWithArgs(
                                XTestConstants.ERC721_TOKEN_ADDRESS,
                                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(9L))
                        .array()),
                INVALID_TOKEN_NFT_SERIAL_NUMBER,
                "Owner granting approval on missing SN#9");
        // APPROVE NFT
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(GrantApprovalTranslator.GRANT_APPROVAL_NFT
                        .encodeCallWithArgs(
                                XTestConstants.ERC721_TOKEN_ADDRESS,
                                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(XTestConstants.SN_1234.serialNumber()))
                        .array()),
                XTestConstants.assertSuccess(),
                "Owner granting approval on present SN#1234");
        // TRY APPROVE NFT WITH WITH NOT OWNED SERIAL
        runHtsCallAndExpectRevert(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(GrantApprovalTranslator.GRANT_APPROVAL_NFT
                        .encodeCallWithArgs(
                                XTestConstants.ERC721_TOKEN_ADDRESS,
                                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(XTestConstants.SN_3456.serialNumber()))
                        .array()),
                SENDER_DOES_NOT_OWN_NFT_SERIAL_NO,
                "Owner granting approval on not owned SN#3456");
        // TRY APPROVE NFT WITH WITH FUNGIBLE TOKEN
        runHtsCallAndExpectRevert(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(GrantApprovalTranslator.GRANT_APPROVAL_NFT
                        .encodeCallWithArgs(
                                XTestConstants.ERC20_TOKEN_ADDRESS,
                                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(XTestConstants.SN_3456.serialNumber()))
                        .array()),
                FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES,
                "Owner granting nft approval on erc20 token");
        // TRY APPROVE NFT WITH WITH INVALID TOKEN ADDRESS
        runHtsCallAndExpectRevert(
                XTestConstants.OWNER_BESU_ADDRESS,
                Bytes.wrap(GrantApprovalTranslator.GRANT_APPROVAL_NFT
                        .encodeCallWithArgs(
                                XTestConstants.SENDER_HEADLONG_ADDRESS,
                                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(XTestConstants.SN_3456.serialNumber()))
                        .array()),
                INVALID_TOKEN_ID,
                "Owner granting approval on invalid id");
        // TRANSFER NFT
        runHtsCallAndExpectOnSuccess(
                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_BESU_ADDRESS,
                Bytes.wrap(ClassicTransfersTranslator.TRANSFER_NFT
                        .encodeCallWithArgs(
                                XTestConstants.ERC721_TOKEN_ADDRESS,
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                XTestConstants.RECEIVER_HEADLONG_ADDRESS,
                                XTestConstants.SN_1234.serialNumber())
                        .array()),
                XTestConstants.assertSuccess(),
                "Owner transferring SN#1234");
        // ERC APPROVE FUNGIBLE
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                bytesForRedirect(
                        GrantApprovalTranslator.ERC_GRANT_APPROVAL.encodeCallWithArgs(
                                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(100L)),
                        XTestConstants.ERC20_TOKEN_ID),
                output -> assertEquals(
                        asBytesResult(GrantApprovalTranslator.ERC_GRANT_APPROVAL
                                .getOutputs()
                                .encodeElements(true)),
                        output),
                "Owner granting approval on fungible units via ERC call");
        // TRY TRANSFER AND EXPECT SUCCESS
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_20_TRANSFER_FROM.encodeCallWithArgs(
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                XTestConstants.RECEIVER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(100L)),
                        XTestConstants.ERC20_TOKEN_ID),
                output -> assertEquals(
                        asBytesResult(ERC_20_TRANSFER_FROM.getOutputs().encodeElements(true)), output),
                "Owner transferring 100 of its own units via ERC call");
        // ERC APPROVE NFT
        runHtsCallAndExpectOnSuccess(
                XTestConstants.OWNER_BESU_ADDRESS,
                bytesForRedirect(
                        GrantApprovalTranslator.ERC_GRANT_APPROVAL_NFT.encodeCallWithArgs(
                                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(XTestConstants.SN_2345.serialNumber())),
                        XTestConstants.ERC721_TOKEN_ID),
                output -> assertEquals(
                        asBytesResult(GrantApprovalTranslator.ERC_GRANT_APPROVAL_NFT
                                .getOutputs()
                                .encodeElements()),
                        output),
                "Owner granting approval on SN#2345");
        // TRANSFER NFT
        runHtsCallAndExpectOnSuccess(
                HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_BESU_ADDRESS,
                Bytes.wrap(ClassicTransfersTranslator.TRANSFER_NFT
                        .encodeCallWithArgs(
                                XTestConstants.ERC721_TOKEN_ADDRESS,
                                XTestConstants.OWNER_HEADLONG_ADDRESS,
                                XTestConstants.RECEIVER_HEADLONG_ADDRESS,
                                XTestConstants.SN_2345.serialNumber())
                        .array()),
                XTestConstants.assertSuccess(),
                "Spender transferring SN#2345 via classic transfer");
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        return new HashMap<>() {
            {
                put(ProtoBytes.newBuilder().value(XTestConstants.OWNER_ADDRESS).build(), XTestConstants.OWNER_ID);
                put(
                        ProtoBytes.newBuilder()
                                .value(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ADDRESS)
                                .build(),
                        HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID);
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
                                        .amount(Long.MAX_VALUE)
                                        .build()))
                                .build());
                put(
                        HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID,
                        Account.newBuilder()
                                .accountId(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID)
                                .alias(HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ADDRESS)
                                .key(XTestConstants.AN_ED25519_KEY)
                                .build());
                put(
                        XTestConstants.RECEIVER_ID,
                        Account.newBuilder()
                                .accountId(XTestConstants.RECEIVER_ID)
                                .build());
            }
        };
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        XTestConstants.addErc20Relation(tokenRelationships, XTestConstants.OWNER_ID, 1_000L);
        XTestConstants.addErc20Relation(
                tokenRelationships, HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID, 0L);
        XTestConstants.addErc20Relation(tokenRelationships, XTestConstants.RECEIVER_ID, 0L);
        XTestConstants.addErc721Relation(tokenRelationships, XTestConstants.OWNER_ID, 3L);
        XTestConstants.addErc721Relation(tokenRelationships, XTestConstants.RECEIVER_ID, 0L);
        return tokenRelationships;
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
                put(
                        XTestConstants.SN_2345,
                        Nft.newBuilder()
                                .nftId(XTestConstants.SN_2345)
                                .ownerId(XTestConstants.OWNER_ID)
                                .spenderId(XTestConstants.SENDER_ID)
                                .metadata(XTestConstants.SN_2345_METADATA)
                                .build());
                put(
                        XTestConstants.SN_3456,
                        Nft.newBuilder()
                                .nftId(XTestConstants.SN_3456)
                                .ownerId(XTestConstants.SENDER_ID)
                                .spenderId(XTestConstants.SENDER_ID)
                                .metadata(XTestConstants.SN_3456_METADATA)
                                .build());
            }
        };
    }
}
