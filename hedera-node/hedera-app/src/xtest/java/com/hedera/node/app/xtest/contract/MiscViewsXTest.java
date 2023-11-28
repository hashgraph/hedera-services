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

import static com.hedera.node.app.service.contract.impl.ContractServiceImpl.CONTRACT_SERVICE;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallLocalQuery;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MiscViewsXTest extends AbstractContractXTest {

    @Override
    protected void doScenarioOperations() {
        handleAndCommit(CONTRACT_SERVICE.handlers().contractCreateHandler(), synthCreateTxn());
        handleAndCommitSingleTransaction(CONTRACT_SERVICE.handlers().contractCallHandler(), synthCallPrng());
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(MiscViewsXTestConstants.GET_SECRET),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(MiscViewsXTestConstants.UNCOVERED_SECRET, "GET_SECRET"));
        doPrngQuery();
        doExchangeRateQuery();
        doErc20Queries();
        doErc721Queries();
    }

    private void doPrngQuery() {
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(MiscViewsXTestConstants.GET_PRNG_SEED),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(MiscViewsXTestConstants.PRNG_SEED, "GET_PRNG_SEED"));
    }

    private void doExchangeRateQuery() {
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(MiscViewsXTestConstants.GET_TINYCENTS_EQUIV, MiscViewsXTestConstants.TINYBARS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(MiscViewsXTestConstants.EQUIV_TINYCENTS, "GET_TINYCENTS_EQUIV"));
    }

    private void doErc20Queries() {
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(
                        MiscViewsXTestConstants.GET_ERC_20_BALANCE,
                        MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS,
                        MiscViewsXTestConstants.ERC_USER_ADDRESS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(MiscViewsXTestConstants.ERC20_USER_BALANCE, "GET_ERC_20_BALANCE"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(MiscViewsXTestConstants.GET_ERC_20_SUPPLY, MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(MiscViewsXTestConstants.ERC20_SUPPLY, "GET_ERC_20_SUPPLY"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(MiscViewsXTestConstants.GET_ERC_20_NAME, MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(MiscViewsXTestConstants.ERC20_NAME, "GET_ERC_20_NAME"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(MiscViewsXTestConstants.GET_ERC_20_SYMBOL, MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(MiscViewsXTestConstants.ERC20_SYMBOL, "GET_ERC_20_SYMBOL"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(
                        MiscViewsXTestConstants.GET_ERC_20_DECIMALS, MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(MiscViewsXTestConstants.ERC20_DECIMALS, "GET_ERC_20_DECIMALS"));
    }

    private void doErc721Queries() {
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(MiscViewsXTestConstants.GET_ERC_721_NAME, XTestConstants.ERC721_TOKEN_ADDRESS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(MiscViewsXTestConstants.ERC721_NAME, "GET_ERC_721_NAME"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(MiscViewsXTestConstants.GET_ERC_721_SYMBOL, XTestConstants.ERC721_TOKEN_ADDRESS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(MiscViewsXTestConstants.ERC721_SYMBOL, "GET_ERC_721_SYMBOL"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(
                        MiscViewsXTestConstants.GET_ERC_721_TOKEN_URI,
                        XTestConstants.ERC721_TOKEN_ADDRESS,
                        BigInteger.TWO),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(MiscViewsXTestConstants.ERC721_SN2_METADATA, "GET_ERC_721_TOKEN_URI"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(
                        MiscViewsXTestConstants.GET_ERC_721_BALANCE,
                        XTestConstants.ERC721_TOKEN_ADDRESS,
                        MiscViewsXTestConstants.ERC_USER_ADDRESS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(MiscViewsXTestConstants.ERC721_USER_BALANCE, "GET_ERC_721_BALANCE"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(
                        MiscViewsXTestConstants.GET_ERC_721_OWNER, XTestConstants.ERC721_TOKEN_ADDRESS, BigInteger.ONE),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(MiscViewsXTestConstants.ERC721_SN1_OWNER, "GET_ERC_721_OWNER"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(
                        MiscViewsXTestConstants.GET_ERC721_IS_OPERATOR,
                        XTestConstants.ERC721_TOKEN_ADDRESS,
                        MiscViewsXTestConstants.ERC_USER_ADDRESS,
                        MiscViewsXTestConstants.ERC721_OPERATOR_ADDRESS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(MiscViewsXTestConstants.ERC721_IS_OPERATOR, "GET_ERC721_IS_OPERATOR"));
    }

    private Query miscViewsQuery(@NonNull final Function function, @NonNull final Object... args) {
        return Query.newBuilder()
                .contractCallLocal(ContractCallLocalQuery.newBuilder()
                        .contractID(MiscViewsXTestConstants.SPECIAL_QUERIES_X_TEST_ID)
                        .gas(GAS_TO_OFFER)
                        .functionParameters(
                                Bytes.wrap(function.encodeCallWithArgs(args).array())))
                .build();
    }

    private TransactionBody synthCreateTxn() {
        final var params = Bytes.wrap(TupleType.parse("(uint256)")
                .encodeElements(MiscViewsXTestConstants.SECRET)
                .array());
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(MiscViewsXTestConstants.ERC_USER_ID))
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .autoRenewPeriod(STANDARD_AUTO_RENEW_PERIOD)
                        .constructorParameters(params)
                        .fileID(MiscViewsXTestConstants.VIEWS_INITCODE_FILE_ID)
                        .gas(GAS_TO_OFFER)
                        .build())
                .build();
    }

    private TransactionBody synthCallPrng() {
        return createCallTransactionBody(
                MiscViewsXTestConstants.ERC_USER_ID,
                0L,
                MiscViewsXTestConstants.SPECIAL_QUERIES_X_TEST_ID,
                MiscViewsXTestConstants.GET_PRNG_SEED.encodeCallWithArgs());
    }

    @Override
    protected long initialEntityNum() {
        return MiscViewsXTestConstants.NEXT_ENTITY_NUM - 1;
    }

    @Override
    protected Map<FileID, File> initialFiles() {
        final var files = new HashMap<FileID, File>();
        files.put(
                MiscViewsXTestConstants.VIEWS_INITCODE_FILE_ID,
                File.newBuilder()
                        .contents(resourceAsBytes("initcode/SpecialQueriesXTest.bin"))
                        .build());
        return files;
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(
                ProtoBytes.newBuilder()
                        .value(MiscViewsXTestConstants.RAW_ERC_USER_ADDRESS)
                        .build(),
                MiscViewsXTestConstants.ERC_USER_ID);
        return aliases;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                MiscViewsXTestConstants.ERC_USER_ID,
                Account.newBuilder()
                        .accountId(MiscViewsXTestConstants.ERC_USER_ID)
                        .alias(MiscViewsXTestConstants.RAW_ERC_USER_ADDRESS)
                        .key(XTestConstants.AN_ED25519_KEY)
                        .tinybarBalance(100 * XTestConstants.ONE_HBAR)
                        .approveForAllNftAllowances(List.of(AccountApprovalForAllAllowance.newBuilder()
                                .tokenId(XTestConstants.ERC721_TOKEN_ID)
                                .spenderId(MiscViewsXTestConstants.OPERATOR_ID)
                                .build()))
                        .build());
        accounts.put(
                MiscViewsXTestConstants.OPERATOR_ID,
                Account.newBuilder()
                        .key(XTestConstants.AN_ED25519_KEY)
                        .accountId(MiscViewsXTestConstants.OPERATOR_ID)
                        .tinybarBalance(100 * XTestConstants.ONE_HBAR)
                        .build());
        accounts.put(
                MiscViewsXTestConstants.COINBASE_ID,
                Account.newBuilder()
                        .accountId(MiscViewsXTestConstants.COINBASE_ID)
                        .build());
        return accounts;
    }

    @Override
    protected Map<NftID, Nft> initialNfts() {
        final var nfts = new HashMap<NftID, Nft>();
        for (long sn = 1; sn <= 3; sn++) {
            final var id = NftID.newBuilder()
                    .tokenId(XTestConstants.ERC721_TOKEN_ID)
                    .serialNumber(sn)
                    .build();
            nfts.put(
                    id,
                    Nft.newBuilder()
                            .nftId(id)
                            .ownerId(MiscViewsXTestConstants.ERC_USER_ID)
                            .spenderId(MiscViewsXTestConstants.OPERATOR_ID)
                            .metadata(Bytes.wrap("https://example.com/721/" + sn))
                            .build());
        }
        return nfts;
    }

    @Override
    protected Map<EntityIDPair, TokenRelation> initialTokenRelationships() {
        final var tokenRelationships = new HashMap<EntityIDPair, TokenRelation>();
        tokenRelationships.put(
                EntityIDPair.newBuilder()
                        .tokenId(XTestConstants.ERC20_TOKEN_ID)
                        .accountId(MiscViewsXTestConstants.ERC_USER_ID)
                        .build(),
                TokenRelation.newBuilder()
                        .tokenId(XTestConstants.ERC20_TOKEN_ID)
                        .accountId(MiscViewsXTestConstants.ERC_USER_ID)
                        .balance(111L)
                        .kycGranted(true)
                        .build());
        tokenRelationships.put(
                EntityIDPair.newBuilder()
                        .tokenId(XTestConstants.ERC721_TOKEN_ID)
                        .accountId(MiscViewsXTestConstants.ERC_USER_ID)
                        .build(),
                TokenRelation.newBuilder()
                        .tokenId(XTestConstants.ERC721_TOKEN_ID)
                        .accountId(MiscViewsXTestConstants.ERC_USER_ID)
                        .balance(3L)
                        .kycGranted(true)
                        .build());
        return tokenRelationships;
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = new HashMap<TokenID, Token>();
        final var erc20Symbol = "SYM20";
        final var erc20Name = "20 Coin";
        final var erc20Memo = "20 Coin Memo";
        final var erc20Decimals = 2;
        final var erc20TotalSupply = 888L;
        final var erc721Symbol = "SYM721";
        final var erc721Name = "721 Unique Things";
        final var erc721Memo = "721 Unique Things Memo";
        tokens.put(
                XTestConstants.ERC20_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(XTestConstants.ERC20_TOKEN_ID)
                        .memo(erc20Memo)
                        .name(erc20Name)
                        .symbol(erc20Symbol)
                        .decimals(erc20Decimals)
                        .totalSupply(erc20TotalSupply)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .build());
        tokens.put(
                XTestConstants.ERC721_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(XTestConstants.ERC721_TOKEN_ID)
                        .memo(erc721Memo)
                        .name(erc721Name)
                        .symbol(erc721Symbol)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .build());
        return tokens;
    }

    @Override
    protected RunningHashes initialRunningHashes() {
        return RunningHashes.newBuilder()
                .nMinus3RunningHash(MiscViewsXTestConstants.PRNG_SEED)
                .build();
    }
}
