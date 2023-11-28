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
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.ADMIN_KEY;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.AUTORENEW_SECONDS;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.CLASSIC_QUERIES_X_TEST_ID;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.CLASSIC_VIEWS_INITCODE_FILE_ID;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.CUSTOM_FEES;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.EXPECTED_CUSTOM_FEES;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.EXPECTED_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.EXPECTED_NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.EXPECTED_TOKEN_EXPIRY;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.EXPECTED_TOKEN_INFO;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.EXPIRATION_SECONDS;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.FEE_SCHEDULE_KEY;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.FREEZE_KEY;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.GET_DEFAULT_FREEZE_STATUS;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.GET_DEFAULT_KYC_STATUS;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.GET_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.GET_IS_FROZEN;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.GET_IS_KYC;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.GET_IS_TOKEN;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.GET_NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.GET_TOKEN_CUSTOM_FEES;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.GET_TOKEN_EXPIRY;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.GET_TOKEN_INFO;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.GET_TOKEN_KEY;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.GET_TOKEN_TYPE;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.KYC_KEY;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.PAUSE_KEY;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.SUPPLY_KEY;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.TOKEN_FROZEN_STATUS;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.TOKEN_IS_FROZEN;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.TOKEN_IS_KYC;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.TOKEN_IS_TOKEN;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.TOKEN_KYC_GRANTED_STATUS;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.TOKEN_TYPE_FUNGIBLE;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.WIPE_KEY;
import static com.hedera.node.app.xtest.contract.ClassicViewsXTestConstants.returnExpectedKey;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallLocalQuery;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
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
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassicViewsXTest extends AbstractContractXTest {

    @Override
    protected void doScenarioOperations() {
        handleAndCommit(CONTRACT_SERVICE.handlers().contractCreateHandler(), synthCreateTxn());
        doClassicQueries();
    }

    @Override
    protected Configuration configuration() {
        return HederaTestConfigBuilder.create()
                .withValue("contracts.chainId", "298")
                .withValue("ledger.id", "03")
                .getOrCreateConfig();
    }

    private void doClassicQueries() {
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(
                        GET_IS_FROZEN,
                        MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS,
                        MiscViewsXTestConstants.ERC_USER_ADDRESS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(TOKEN_IS_FROZEN, "GET_IS_FROZEN"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(
                        GET_IS_KYC,
                        MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS,
                        MiscViewsXTestConstants.ERC_USER_ADDRESS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(TOKEN_IS_KYC, "GET_IS_KYC"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_IS_TOKEN, MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(TOKEN_IS_TOKEN, "GET_IS_TOKEN"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_TOKEN_TYPE, MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(TOKEN_TYPE_FUNGIBLE, "GET_TOKEN_TYPE"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_DEFAULT_FREEZE_STATUS, MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(TOKEN_FROZEN_STATUS, "GET_DEFAULT_FREEZE_STATUS"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_DEFAULT_KYC_STATUS, MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(TOKEN_KYC_GRANTED_STATUS, "GET_DEFAULT_KYC_STATUS"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_TOKEN_EXPIRY, MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(EXPECTED_TOKEN_EXPIRY, "GET_TOKEN_EXPIRY"));
        // Token Keys
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_TOKEN_KEY, MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS, BigInteger.valueOf(1L)),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(returnExpectedKey(ADMIN_KEY), "GET_TOKEN_KEY (ADMIN_KEY)"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_TOKEN_KEY, MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS, BigInteger.valueOf(2L)),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(returnExpectedKey(KYC_KEY), "GET_TOKEN_KEY (KYC_KEY)"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_TOKEN_KEY, MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS, BigInteger.valueOf(4L)),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(returnExpectedKey(FREEZE_KEY), "GET_TOKEN_KEY (FREEZE_KEY)"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_TOKEN_KEY, MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS, BigInteger.valueOf(8L)),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(returnExpectedKey(WIPE_KEY), "GET_TOKEN_KEY (WIPE_KEY)"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_TOKEN_KEY, MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS, BigInteger.valueOf(16L)),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(returnExpectedKey(SUPPLY_KEY), "GET_TOKEN_KEY (SUPPLY_KEY)"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_TOKEN_KEY, MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS, BigInteger.valueOf(32L)),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(
                        returnExpectedKey(FEE_SCHEDULE_KEY), "GET_TOKEN_KEY (FEE_SCHEDULE_KEY)"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_TOKEN_KEY, MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS, BigInteger.valueOf(64L)),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(returnExpectedKey(PAUSE_KEY), "GET_TOKEN_KEY (PAUSE_KEY)"));

        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_TOKEN_CUSTOM_FEES, MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(EXPECTED_CUSTOM_FEES, "GET_TOKEN_CUSTOM_FEES"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_TOKEN_INFO, MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(EXPECTED_TOKEN_INFO, "GET_TOKEN_INFO"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_FUNGIBLE_TOKEN_INFO, MiscViewsXTestConstants.ERC20_TOKEN_ADDRESS),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(EXPECTED_FUNGIBLE_TOKEN_INFO, "GET_FUNGIBLE_TOKEN_INFO"));
        answerSingleQuery(
                CONTRACT_SERVICE.handlers().contractCallLocalHandler(),
                miscViewsQuery(GET_NON_FUNGIBLE_TOKEN_INFO, XTestConstants.ERC721_TOKEN_ADDRESS, 1L),
                MiscViewsXTestConstants.ERC_USER_ID,
                assertingCallLocalResultIsBuffer(EXPECTED_NON_FUNGIBLE_TOKEN_INFO, "GET_NON_FUNGIBLE_TOKEN_INFO"));
    }

    private Query miscViewsQuery(@NonNull final Function function, @NonNull final Object... args) {
        return Query.newBuilder()
                .contractCallLocal(ContractCallLocalQuery.newBuilder()
                        .contractID(CLASSIC_QUERIES_X_TEST_ID)
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
                        .fileID(CLASSIC_VIEWS_INITCODE_FILE_ID)
                        .gas(GAS_TO_OFFER)
                        .build())
                .build();
    }

    @Override
    protected long initialEntityNum() {
        return MiscViewsXTestConstants.NEXT_ENTITY_NUM - 1;
    }

    @Override
    protected Map<FileID, File> initialFiles() {
        final var files = new HashMap<FileID, File>();
        files.put(
                CLASSIC_VIEWS_INITCODE_FILE_ID,
                File.newBuilder()
                        .contents(resourceAsBytes("initcode/ClassicQueriesXTest.bin"))
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
                        .tinybarBalance(100 * XTestConstants.ONE_HBAR)
                        .key(XTestConstants.AN_ED25519_KEY)
                        .approveForAllNftAllowances(List.of(AccountApprovalForAllAllowance.newBuilder()
                                .tokenId(XTestConstants.ERC721_TOKEN_ID)
                                .spenderId(MiscViewsXTestConstants.OPERATOR_ID)
                                .build()))
                        .build());
        accounts.put(
                MiscViewsXTestConstants.OPERATOR_ID,
                Account.newBuilder()
                        .accountId(MiscViewsXTestConstants.OPERATOR_ID)
                        .key(XTestConstants.AN_ED25519_KEY)
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
        final var erc20MaxSupply = 999L;
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
                        .maxSupply(erc20MaxSupply)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .expirationSecond(EXPIRATION_SECONDS)
                        .autoRenewSeconds(AUTORENEW_SECONDS)
                        .autoRenewAccountId(MiscViewsXTestConstants.OPERATOR_ID)
                        .adminKey(ADMIN_KEY)
                        .kycKey(KYC_KEY)
                        .freezeKey(FREEZE_KEY)
                        .wipeKey(WIPE_KEY)
                        .supplyKey(SUPPLY_KEY)
                        .feeScheduleKey(FEE_SCHEDULE_KEY)
                        .pauseKey(PAUSE_KEY)
                        .customFees(CUSTOM_FEES)
                        .accountsFrozenByDefault(true)
                        .accountsKycGrantedByDefault(true)
                        .paused(true)
                        .supplyType(TokenSupplyType.FINITE)
                        .build());
        tokens.put(
                XTestConstants.ERC721_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(XTestConstants.ERC721_TOKEN_ID)
                        .memo(erc721Memo)
                        .name(erc721Name)
                        .symbol(erc721Symbol)
                        .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                        .totalSupply(erc20TotalSupply)
                        .maxSupply(erc20MaxSupply)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .expirationSecond(EXPIRATION_SECONDS)
                        .autoRenewSeconds(AUTORENEW_SECONDS)
                        .autoRenewAccountId(MiscViewsXTestConstants.OPERATOR_ID)
                        .adminKey(ADMIN_KEY)
                        .kycKey(KYC_KEY)
                        .freezeKey(FREEZE_KEY)
                        .wipeKey(WIPE_KEY)
                        .supplyKey(SUPPLY_KEY)
                        .feeScheduleKey(FEE_SCHEDULE_KEY)
                        .pauseKey(PAUSE_KEY)
                        .customFees(CUSTOM_FEES)
                        .accountsFrozenByDefault(true)
                        .accountsKycGrantedByDefault(true)
                        .paused(true)
                        .supplyType(TokenSupplyType.FINITE)
                        .build());
        return tokens;
    }
}
