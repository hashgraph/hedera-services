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

import static com.hedera.node.app.service.contract.impl.ContractServiceImpl.CONTRACT_SERVICE;
import static contract.MiscViewsXTestConstants.COINBASE_ID;
import static contract.MiscViewsXTestConstants.EQUIV_TINYCENTS;
import static contract.MiscViewsXTestConstants.ERC20_DECIMALS;
import static contract.MiscViewsXTestConstants.ERC20_NAME;
import static contract.MiscViewsXTestConstants.ERC20_SUPPLY;
import static contract.MiscViewsXTestConstants.ERC20_SYMBOL;
import static contract.MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS;
import static contract.MiscViewsXTestConstants.ERC20_USER_BALANCE;
import static contract.MiscViewsXTestConstants.ERC721_IS_OPERATOR;
import static contract.MiscViewsXTestConstants.ERC721_NAME;
import static contract.MiscViewsXTestConstants.ERC721_OPERATOR_ADDRESS;
import static contract.MiscViewsXTestConstants.ERC721_SN1_OWNER;
import static contract.MiscViewsXTestConstants.ERC721_SN2_METADATA;
import static contract.MiscViewsXTestConstants.ERC721_SYMBOL;
import static contract.MiscViewsXTestConstants.ERC721_USER_BALANCE;
import static contract.MiscViewsXTestConstants.ERC_USER_ADDRESS;
import static contract.MiscViewsXTestConstants.ERC_USER_ID;
import static contract.MiscViewsXTestConstants.GET_ERC721_IS_OPERATOR;
import static contract.MiscViewsXTestConstants.GET_ERC_20_BALANCE;
import static contract.MiscViewsXTestConstants.GET_ERC_20_DECIMALS;
import static contract.MiscViewsXTestConstants.GET_ERC_20_NAME;
import static contract.MiscViewsXTestConstants.GET_ERC_20_SUPPLY;
import static contract.MiscViewsXTestConstants.GET_ERC_20_SYMBOL;
import static contract.MiscViewsXTestConstants.GET_ERC_721_BALANCE;
import static contract.MiscViewsXTestConstants.GET_ERC_721_NAME;
import static contract.MiscViewsXTestConstants.GET_ERC_721_OWNER;
import static contract.MiscViewsXTestConstants.GET_ERC_721_SYMBOL;
import static contract.MiscViewsXTestConstants.GET_ERC_721_TOKEN_URI;
import static contract.MiscViewsXTestConstants.GET_PRNG_SEED;
import static contract.MiscViewsXTestConstants.GET_SECRET;
import static contract.MiscViewsXTestConstants.GET_TINYCENTS_EQUIV;
import static contract.MiscViewsXTestConstants.NEXT_ENTITY_NUM;
import static contract.MiscViewsXTestConstants.OPERATOR_ID;
import static contract.MiscViewsXTestConstants.PRNG_SEED;
import static contract.MiscViewsXTestConstants.RAW_ERC_USER_ADDRESS;
import static contract.MiscViewsXTestConstants.SECRET;
import static contract.MiscViewsXTestConstants.SPECIAL_QUERIES_X_TEST_ID;
import static contract.MiscViewsXTestConstants.TINYBARS;
import static contract.MiscViewsXTestConstants.UNCOVERED_SECRET;
import static contract.MiscViewsXTestConstants.VIEWS_INITCODE_FILE_ID;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.ERC20_TOKEN_ID;
import static contract.XTestConstants.ERC721_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC721_TOKEN_ID;
import static contract.XTestConstants.ONE_HBAR;

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
                miscViewsQuery(GET_SECRET),
                ERC_USER_ID,
                assertingCallLocalResultIsBuffer(UNCOVERED_SECRET, "GET_SECRET"));
        doPrngQuery();
        doExchangeRateQuery();
        doErc20Queries();
        doErc721Queries();
    }

    private void doPrngQuery() {
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_PRNG_SEED),
                ERC_USER_ID,
                assertingCallLocalResultIsBuffer(PRNG_SEED, "GET_PRNG_SEED"));
    }

    private void doExchangeRateQuery() {
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_TINYCENTS_EQUIV, TINYBARS),
                ERC_USER_ID,
                assertingCallLocalResultIsBuffer(EQUIV_TINYCENTS, "GET_TINYCENTS_EQUIV"));
    }

    private void doErc20Queries() {
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_ERC_20_BALANCE, ERC20_TOKEN_ADDRESS, ERC_USER_ADDRESS),
                ERC_USER_ID,
                assertingCallLocalResultIsBuffer(ERC20_USER_BALANCE, "GET_ERC_20_BALANCE"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_ERC_20_SUPPLY, ERC20_TOKEN_ADDRESS),
                ERC_USER_ID,
                assertingCallLocalResultIsBuffer(ERC20_SUPPLY, "GET_ERC_20_SUPPLY"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_ERC_20_NAME, ERC20_TOKEN_ADDRESS),
                ERC_USER_ID,
                assertingCallLocalResultIsBuffer(ERC20_NAME, "GET_ERC_20_NAME"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_ERC_20_SYMBOL, ERC20_TOKEN_ADDRESS),
                ERC_USER_ID,
                assertingCallLocalResultIsBuffer(ERC20_SYMBOL, "GET_ERC_20_SYMBOL"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_ERC_20_DECIMALS, ERC20_TOKEN_ADDRESS),
                ERC_USER_ID,
                assertingCallLocalResultIsBuffer(ERC20_DECIMALS, "GET_ERC_20_DECIMALS"));
    }

    private void doErc721Queries() {
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_ERC_721_NAME, ERC721_TOKEN_ADDRESS),
                ERC_USER_ID,
                assertingCallLocalResultIsBuffer(ERC721_NAME, "GET_ERC_721_NAME"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_ERC_721_SYMBOL, ERC721_TOKEN_ADDRESS),
                ERC_USER_ID,
                assertingCallLocalResultIsBuffer(ERC721_SYMBOL, "GET_ERC_721_SYMBOL"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_ERC_721_TOKEN_URI, ERC721_TOKEN_ADDRESS, BigInteger.TWO),
                ERC_USER_ID,
                assertingCallLocalResultIsBuffer(ERC721_SN2_METADATA, "GET_ERC_721_TOKEN_URI"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_ERC_721_BALANCE, ERC721_TOKEN_ADDRESS, ERC_USER_ADDRESS),
                ERC_USER_ID,
                assertingCallLocalResultIsBuffer(ERC721_USER_BALANCE, "GET_ERC_721_BALANCE"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_ERC_721_OWNER, ERC721_TOKEN_ADDRESS, BigInteger.ONE),
                ERC_USER_ID,
                assertingCallLocalResultIsBuffer(ERC721_SN1_OWNER, "GET_ERC_721_OWNER"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_ERC721_IS_OPERATOR, ERC721_TOKEN_ADDRESS, ERC_USER_ADDRESS, ERC721_OPERATOR_ADDRESS),
                ERC_USER_ID,
                assertingCallLocalResultIsBuffer(ERC721_IS_OPERATOR, "GET_ERC721_IS_OPERATOR"));
    }

    private Query miscViewsQuery(@NonNull final Function function, @NonNull final Object... args) {
        return Query.newBuilder()
                .contractCallLocal(ContractCallLocalQuery.newBuilder()
                        .contractID(SPECIAL_QUERIES_X_TEST_ID)
                        .gas(GAS_TO_OFFER)
                        .functionParameters(
                                Bytes.wrap(function.encodeCallWithArgs(args).array())))
                .build();
    }

    private TransactionBody synthCreateTxn() {
        final var params =
                Bytes.wrap(TupleType.parse("(uint256)").encodeElements(SECRET).array());
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(ERC_USER_ID))
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .autoRenewPeriod(STANDARD_AUTO_RENEW_PERIOD)
                        .constructorParameters(params)
                        .fileID(VIEWS_INITCODE_FILE_ID)
                        .gas(GAS_TO_OFFER)
                        .build())
                .build();
    }

    private TransactionBody synthCallPrng() {
        return createCallTransactionBody(
                ERC_USER_ID, 0L, SPECIAL_QUERIES_X_TEST_ID, GET_PRNG_SEED.encodeCallWithArgs());
    }

    @Override
    protected long initialEntityNum() {
        return NEXT_ENTITY_NUM - 1;
    }

    @Override
    protected Map<FileID, File> initialFiles() {
        final var files = new HashMap<FileID, File>();
        files.put(
                VIEWS_INITCODE_FILE_ID,
                File.newBuilder()
                        .contents(resourceAsBytes("initcode/SpecialQueriesXTest.bin"))
                        .build());
        return files;
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(RAW_ERC_USER_ADDRESS).build(), ERC_USER_ID);
        return aliases;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                ERC_USER_ID,
                Account.newBuilder()
                        .accountId(ERC_USER_ID)
                        .alias(RAW_ERC_USER_ADDRESS)
                        .key(AN_ED25519_KEY)
                        .tinybarBalance(100 * ONE_HBAR)
                        .approveForAllNftAllowances(List.of(AccountApprovalForAllAllowance.newBuilder()
                                .tokenId(ERC721_TOKEN_ID)
                                .spenderId(OPERATOR_ID)
                                .build()))
                        .build());
        accounts.put(
                OPERATOR_ID,
                Account.newBuilder()
                        .key(AN_ED25519_KEY)
                        .accountId(OPERATOR_ID)
                        .tinybarBalance(100 * ONE_HBAR)
                        .build());
        accounts.put(COINBASE_ID, Account.newBuilder().accountId(COINBASE_ID).build());
        return accounts;
    }

    @Override
    protected Map<NftID, Nft> initialNfts() {
        final var nfts = new HashMap<NftID, Nft>();
        for (long sn = 1; sn <= 3; sn++) {
            final var id =
                    NftID.newBuilder().tokenId(ERC721_TOKEN_ID).serialNumber(sn).build();
            nfts.put(
                    id,
                    Nft.newBuilder()
                            .nftId(id)
                            .ownerId(ERC_USER_ID)
                            .spenderId(OPERATOR_ID)
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
                        .tokenId(ERC20_TOKEN_ID)
                        .accountId(ERC_USER_ID)
                        .build(),
                TokenRelation.newBuilder()
                        .tokenId(ERC20_TOKEN_ID)
                        .accountId(ERC_USER_ID)
                        .balance(111L)
                        .kycGranted(true)
                        .build());
        tokenRelationships.put(
                EntityIDPair.newBuilder()
                        .tokenId(ERC721_TOKEN_ID)
                        .accountId(ERC_USER_ID)
                        .build(),
                TokenRelation.newBuilder()
                        .tokenId(ERC721_TOKEN_ID)
                        .accountId(ERC_USER_ID)
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
                ERC20_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC20_TOKEN_ID)
                        .memo(erc20Memo)
                        .name(erc20Name)
                        .symbol(erc20Symbol)
                        .decimals(erc20Decimals)
                        .totalSupply(erc20TotalSupply)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .build());
        tokens.put(
                ERC721_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC721_TOKEN_ID)
                        .memo(erc721Memo)
                        .name(erc721Name)
                        .symbol(erc721Symbol)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .build());
        return tokens;
    }

    @Override
    protected RunningHashes initialRunningHashes() {
        return RunningHashes.newBuilder().nMinus3RunningHash(PRNG_SEED).build();
    }
}
