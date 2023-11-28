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

package com.hedera.node.app.xtest.contract.approvals;

import static com.hedera.node.app.xtest.contract.XTestConstants.AN_ED25519_KEY;
import static com.hedera.node.app.xtest.contract.XTestConstants.COINBASE_ID;
import static com.hedera.node.app.xtest.contract.XTestConstants.ONE_HBAR;
import static com.hedera.node.app.xtest.contract.XTestConstants.SENDER_ADDRESS;
import static com.hedera.node.app.xtest.contract.XTestConstants.SENDER_ALIAS;
import static com.hedera.node.app.xtest.contract.XTestConstants.SENDER_ID;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.xtest.contract.AbstractContractXTest;
import java.util.HashMap;
import java.util.Map;

public class ApproveAllowanceXTest extends AbstractContractXTest {
    @Override
    protected void doScenarioOperations() {
        handleAndCommitSingleTransaction(createHandler(), createHtsApproveAllowanceTxn());
        handleAndCommitSingleTransaction(
                callHandler(),
                createCallTransactionBody(
                        SENDER_ID,
                        0L,
                        ApproveAllowanceXTestConstants.EXPECTED_HTS_APPROVE_ALLOWANCE_CONTRACT_ID,
                        ApproveAllowanceXTestConstants.IS_APPROVED_FOR_ALL_FUNCTION.encodeCallWithArgs(
                                asLongZeroHeadlongAddress(ApproveAllowanceXTestConstants.NFT_TOKEN_TYPE_ID),
                                asLongZeroHeadlongAddress(ApproveAllowanceXTestConstants.OWNER_ID),
                                asLongZeroHeadlongAddress(ApproveAllowanceXTestConstants.RECIPIENT_ID))));
    }

    @Override
    protected Map<FileID, File> initialFiles() {
        final var files = new HashMap<FileID, File>();
        files.put(
                ApproveAllowanceXTestConstants.HTS_APPROVE_ALLOWANCE_INITCODE_ID,
                File.newBuilder()
                        .contents(resourceAsBytes("initcode/HtsApproveAllowance.bin"))
                        .build());
        return files;
    }

    @Override
    protected long initialEntityNum() {
        return ApproveAllowanceXTestConstants.NEXT_ENTITY_NUM - 1;
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(SENDER_ALIAS).build(), SENDER_ID);
        aliases.put(ProtoBytes.newBuilder().value(SENDER_ADDRESS).build(), SENDER_ID);
        return aliases;
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = new HashMap<TokenID, Token>();
        tokens.put(
                ApproveAllowanceXTestConstants.NFT_TOKEN_TYPE_ID,
                Token.newBuilder()
                        .tokenId(ApproveAllowanceXTestConstants.NFT_TOKEN_TYPE_ID)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .treasuryAccountId(ApproveAllowanceXTestConstants.TOKEN_TREASURY_ID)
                        .adminKey(AN_ED25519_KEY)
                        .supplyKey(AN_ED25519_KEY)
                        .totalSupply(2)
                        .build());
        return tokens;
    }

    @Override
    protected Map<NftID, Nft> initialNfts() {
        final var nfts = super.initialNfts();
        addNft(nfts, ApproveAllowanceXTestConstants.NFT_TOKEN_TYPE_ID, 1, nft -> nft.ownerId(
                        ApproveAllowanceXTestConstants.OWNER_ID)
                .spenderId(ApproveAllowanceXTestConstants.RECIPIENT_ID));
        addNft(nfts, ApproveAllowanceXTestConstants.NFT_TOKEN_TYPE_ID, 2, nft -> nft.ownerId(
                        ApproveAllowanceXTestConstants.OWNER_ID)
                .spenderId(ApproveAllowanceXTestConstants.RECIPIENT_ID));
        return nfts;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRels = new HashMap<EntityIDPair, TokenRelation>();
        addUsableRelation(
                tokenRels,
                ApproveAllowanceXTestConstants.OWNER_ID,
                ApproveAllowanceXTestConstants.NFT_TOKEN_TYPE_ID,
                rel -> {});
        return tokenRels;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = withSenderAccount(new HashMap<>());
        accounts.put(
                ApproveAllowanceXTestConstants.OWNER_ID,
                Account.newBuilder()
                        .accountId(ApproveAllowanceXTestConstants.OWNER_ID)
                        .tinybarBalance(10_000 * ONE_HBAR)
                        .approveForAllNftAllowances(AccountApprovalForAllAllowance.newBuilder()
                                .tokenId(ApproveAllowanceXTestConstants.NFT_TOKEN_TYPE_ID)
                                .spenderId(ApproveAllowanceXTestConstants.RECIPIENT_ID)
                                .build())
                        .build());
        accounts.put(
                ApproveAllowanceXTestConstants.RECIPIENT_ID,
                Account.newBuilder()
                        .accountId(ApproveAllowanceXTestConstants.RECIPIENT_ID)
                        .tinybarBalance(10_000 * ONE_HBAR)
                        .build());
        accounts.put(
                ApproveAllowanceXTestConstants.ACCOUNT_ID,
                Account.newBuilder()
                        .accountId(ApproveAllowanceXTestConstants.ACCOUNT_ID)
                        .tinybarBalance(10_000 * ONE_HBAR)
                        .build());
        accounts.put(
                ApproveAllowanceXTestConstants.TOKEN_TREASURY_ID,
                Account.newBuilder()
                        .accountId(ApproveAllowanceXTestConstants.TOKEN_TREASURY_ID)
                        .tinybarBalance(10_000 * ONE_HBAR)
                        .build());
        accounts.put(COINBASE_ID, Account.newBuilder().accountId(COINBASE_ID).build());
        return accounts;
    }

    private TransactionBody createHtsApproveAllowanceTxn() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(SENDER_ID))
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .autoRenewPeriod(STANDARD_AUTO_RENEW_PERIOD)
                        .fileID(ApproveAllowanceXTestConstants.HTS_APPROVE_ALLOWANCE_INITCODE_ID)
                        .gas(ApproveAllowanceXTestConstants.GAS)
                        .build())
                .build();
    }
}
