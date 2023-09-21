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

import static com.hedera.hapi.node.base.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.Erc20TransfersTranslator.ERC_20_TRANSFER_FROM;
import static contract.HtsErc721TransferXTestConstants.APPROVED_ID;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_BESU_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS;
import static contract.HtsErc721TransferXTestConstants.UNAUTHORIZED_SPENDER_ID;
import static contract.XTestConstants.ERC20_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC20_TOKEN_ID;
import static contract.XTestConstants.ERC721_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC721_TOKEN_ID;
import static contract.XTestConstants.OWNER_ADDRESS;
import static contract.XTestConstants.OWNER_BESU_ADDRESS;
import static contract.XTestConstants.OWNER_HEADLONG_ADDRESS;
import static contract.XTestConstants.OWNER_ID;
import static contract.XTestConstants.RECEIVER_HEADLONG_ADDRESS;
import static contract.XTestConstants.RECEIVER_ID;
import static contract.XTestConstants.SENDER_CONTRACT_ID_KEY;
import static contract.XTestConstants.SENDER_ID;
import static contract.XTestConstants.SN_1234;
import static contract.XTestConstants.SN_1234_METADATA;
import static contract.XTestConstants.SN_2345;
import static contract.XTestConstants.SN_2345_METADATA;
import static contract.XTestConstants.addErc20Relation;
import static contract.XTestConstants.addErc721Relation;
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
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
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
 *     <li>Transfer {@code ERC20_TOKEN} from SENDER to RECEIVER. This should now succeed.</li>
 *     <li>Transfer {@code ERC20_TOKEN} from SENDER to RECEIVER. This should fail with code AMOUNT_EXCEEDS_ALLOWANCE.</li>
 *     <li>Transfer {@code ERC721_TOKEN} to RECEIVER. This should fail with code SPENDER_DOES_NOT_HAVE_ALLOWANCE.</li>
 *     <li>Approve NFT {@code ERC721_TOKEN} via {@link com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator#GRANT_APPROVAL_NFT}.
 *     This should fail with code INVALID_TOKEN_NFT_SERIAL_NUMBER.</li>
 *     <li>Approve NFT {@code ERC721_TOKEN} via {@link com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator#GRANT_APPROVAL_NFT}.</li>
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
                UNAUTHORIZED_SPENDER_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_20_TRANSFER_FROM.encodeCallWithArgs(
                                OWNER_HEADLONG_ADDRESS, RECEIVER_HEADLONG_ADDRESS, BigInteger.valueOf(100L)),
                        ERC20_TOKEN_ID),
                SPENDER_DOES_NOT_HAVE_ALLOWANCE);
        // APPROVE
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(GrantApprovalTranslator.GRANT_APPROVAL
                        .encodeCallWithArgs(
                                ERC20_TOKEN_ADDRESS, UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS, BigInteger.valueOf(100L))
                        .array()),
                assertSuccess());
        // TRY TRANSFER AND EXPECT SUCCESS
        runHtsCallAndExpectOnSuccess(
                UNAUTHORIZED_SPENDER_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_20_TRANSFER_FROM.encodeCallWithArgs(
                                OWNER_HEADLONG_ADDRESS, RECEIVER_HEADLONG_ADDRESS, BigInteger.valueOf(50L)),
                        ERC20_TOKEN_ID),
                output -> assertEquals(
                        asBytesResult(ERC_20_TRANSFER_FROM.getOutputs().encodeElements(true)), output));
        // TRY TRANSFER AND EXPECT FAIL
        runHtsCallAndExpectRevert(
                UNAUTHORIZED_SPENDER_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_20_TRANSFER_FROM.encodeCallWithArgs(
                                OWNER_HEADLONG_ADDRESS, RECEIVER_HEADLONG_ADDRESS, BigInteger.valueOf(100L)),
                        ERC20_TOKEN_ID),
                AMOUNT_EXCEEDS_ALLOWANCE);
        // TRY APPROVE NFT WITH INVALID SERIAL
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(GrantApprovalTranslator.GRANT_APPROVAL_NFT
                        .encodeCallWithArgs(
                                ERC721_TOKEN_ADDRESS, UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS, BigInteger.valueOf(9L))
                        .array()),
                output -> assertEquals(
                        Bytes.wrap(ReturnTypes.encodedRc(INVALID_TOKEN_NFT_SERIAL_NUMBER)
                                .array()),
                        output));
        // APPROVE NFT
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                Bytes.wrap(GrantApprovalTranslator.GRANT_APPROVAL_NFT
                        .encodeCallWithArgs(
                                ERC721_TOKEN_ADDRESS,
                                UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS,
                                BigInteger.valueOf(SN_1234.serialNumber()))
                        .array()),
                assertSuccess());
        // TRANSFER NFT
        runHtsCallAndExpectOnSuccess(
                UNAUTHORIZED_SPENDER_BESU_ADDRESS,
                Bytes.wrap(ClassicTransfersTranslator.TRANSFER_NFT
                        .encodeCallWithArgs(
                                ERC721_TOKEN_ADDRESS,
                                OWNER_HEADLONG_ADDRESS,
                                RECEIVER_HEADLONG_ADDRESS,
                                SN_1234.serialNumber())
                        .array()),
                assertSuccess());
        // ERC APPROVE FUNGIBLE
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                bytesForRedirect(
                        GrantApprovalTranslator.ERC_GRANT_APPROVAL.encodeCallWithArgs(
                                UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS, BigInteger.valueOf(100L)),
                        ERC20_TOKEN_ID),
                assertSuccess());
        // TRY TRANSFER AND EXPECT SUCCESS
        runHtsCallAndExpectOnSuccess(
                UNAUTHORIZED_SPENDER_BESU_ADDRESS,
                bytesForRedirect(
                        ERC_20_TRANSFER_FROM.encodeCallWithArgs(
                                OWNER_HEADLONG_ADDRESS, RECEIVER_HEADLONG_ADDRESS, BigInteger.valueOf(100L)),
                        ERC20_TOKEN_ID),
                output -> assertEquals(
                        asBytesResult(ERC_20_TRANSFER_FROM.getOutputs().encodeElements(true)), output));
        // ERC APPROVE NFT
        runHtsCallAndExpectOnSuccess(
                OWNER_BESU_ADDRESS,
                bytesForRedirect(
                        GrantApprovalTranslator.ERC_GRANT_APPROVAL.encodeCallWithArgs(
                                UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS, BigInteger.valueOf(SN_2345.serialNumber())),
                        ERC721_TOKEN_ID),
                assertSuccess());
        // TRANSFER NFT
        runHtsCallAndExpectOnSuccess(
                UNAUTHORIZED_SPENDER_BESU_ADDRESS,
                Bytes.wrap(ClassicTransfersTranslator.TRANSFER_NFT
                        .encodeCallWithArgs(
                                ERC721_TOKEN_ADDRESS,
                                OWNER_HEADLONG_ADDRESS,
                                RECEIVER_HEADLONG_ADDRESS,
                                SN_2345.serialNumber())
                        .array()),
                assertSuccess());
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        return new HashMap<>() {
            {
                put(ProtoBytes.newBuilder().value(OWNER_ADDRESS).build(), OWNER_ID);
                put(ProtoBytes.newBuilder().value(UNAUTHORIZED_SPENDER_ADDRESS).build(), UNAUTHORIZED_SPENDER_ID);
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
                                        .amount(Long.MAX_VALUE)
                                        .build()))
                                .build());
                put(
                        UNAUTHORIZED_SPENDER_ID,
                        Account.newBuilder()
                                .accountId(UNAUTHORIZED_SPENDER_ID)
                                .alias(UNAUTHORIZED_SPENDER_ADDRESS)
                                .build());
                put(RECEIVER_ID, Account.newBuilder().accountId(RECEIVER_ID).build());
            }
        };
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        addErc20Relation(tokenRelationships, OWNER_ID, 1_000L);
        addErc20Relation(tokenRelationships, UNAUTHORIZED_SPENDER_ID, 0L);
        addErc20Relation(tokenRelationships, RECEIVER_ID, 0L);
        addErc721Relation(tokenRelationships, OWNER_ID, 3L);
        addErc721Relation(tokenRelationships, RECEIVER_ID, 0L);
        return tokenRelationships;
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
                put(
                        SN_2345,
                        Nft.newBuilder()
                                .nftId(SN_2345)
                                .ownerId(OWNER_ID)
                                .spenderId(SENDER_ID)
                                .metadata(SN_2345_METADATA)
                                .build());
            }
        };
    }
}
