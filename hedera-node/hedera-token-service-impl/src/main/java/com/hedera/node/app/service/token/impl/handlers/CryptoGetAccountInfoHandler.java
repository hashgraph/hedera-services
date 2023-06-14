/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseType.COST_ANSWER;
import static com.hedera.hapi.node.base.TokenFreezeStatus.FREEZE_NOT_APPLICABLE;
import static com.hedera.hapi.node.base.TokenFreezeStatus.FROZEN;
import static com.hedera.hapi.node.base.TokenKycStatus.GRANTED;
import static com.hedera.hapi.node.base.TokenKycStatus.KYC_NOT_APPLICABLE;
import static com.hedera.node.app.hapi.utils.CommonUtils.asEvmAddress;
import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.EVM_ADDRESS_LEN;
import static com.hedera.node.app.spi.key.KeyUtils.ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH;
import static com.hedera.node.app.spi.key.KeyUtils.isEmpty;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.swirlds.common.utility.CommonUtils.hex;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.StakingInfo;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenRelationship;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.AccountInfo;
import com.hedera.hapi.node.token.CryptoGetInfoQuery;
import com.hedera.hapi.node.token.CryptoGetInfoResponse;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.utils.RewardCalculator;
import com.hedera.node.app.spi.workflows.PaidQueryHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#CRYPTO_GET_INFO}.
 */
@Singleton
public class CryptoGetAccountInfoHandler extends PaidQueryHandler {
    private static final int EVM_ADDRESS_SIZE = 20;
    private static final Logger log = LogManager.getLogger(CryptoGetAccountInfoHandler.class);

    @Inject
    public CryptoGetAccountInfoHandler() {
        // Exists for injection
    }

    @Override
    public QueryHeader extractHeader(@NonNull final Query query) {
        requireNonNull(query);
        return query.cryptoGetInfoOrThrow().header();
    }

    @Override
    public Response createEmptyResponse(@NonNull final ResponseHeader header) {
        requireNonNull(header);
        final var response = CryptoGetInfoResponse.newBuilder().header(requireNonNull(header));
        return Response.newBuilder().cryptoGetInfo(response).build();
    }

    @Override
    public void validate(@NonNull final QueryContext context) throws PreCheckException {
        requireNonNull(context);
        final var query = context.query();
        final var accountStore = context.createStore(ReadableAccountStore.class);
        final CryptoGetInfoQuery op = query.cryptoGetInfoOrThrow();
        if (op.hasAccountID()) {
            final var account = accountStore.getAccountById(requireNonNull(op.accountID()));
            validateFalsePreCheck(account == null, INVALID_ACCOUNT_ID);
            validateFalsePreCheck(account.deleted(), ACCOUNT_DELETED);
        } else {
            throw new PreCheckException(INVALID_ACCOUNT_ID);
        }
    }

    @Override
    public Response findResponse(@NonNull final QueryContext context, @NonNull final ResponseHeader header) {
        requireNonNull(context);
        requireNonNull(header);
        final var query = context.query();
        final var op = query.cryptoGetInfoOrThrow();
        final var response = CryptoGetInfoResponse.newBuilder();
        final var accountId = op.accountIDOrElse(AccountID.DEFAULT);

        response.header(header);
        final var responseType = op.headerOrElse(QueryHeader.DEFAULT).responseType();
        if (header.nodeTransactionPrecheckCode() == OK && responseType != COST_ANSWER) {
            final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);
            final var ledgerConfig = context.configuration().getConfigData(LedgerConfig.class);
            final var accountStore = context.createStore(ReadableAccountStore.class);
            final var tokenRelationStore = context.createStore(ReadableTokenRelationStore.class);
            final var tokenStore = context.createStore(ReadableTokenStore.class);
            final var stakingInfoStore = context.createStore(ReadableStakingInfoStore.class);
            final var optionalInfo = infoForAccount(
                    accountId,
                    accountStore,
                    tokenStore,
                    tokenRelationStore,
                    stakingInfoStore,
                    tokensConfig,
                    ledgerConfig,
                    new RewardCalculator() {
                        @Override
                        public long epochSecondAtStartOfPeriod(long stakePeriod) {
                            return 0;
                        }

                        @Override
                        public long estimatePendingRewards(Account account, @Nullable StakingNodeInfo stakingNodeInfo) {
                            return 0;
                        }
                    }); // remove this when we have a real reward calculator
            if (optionalInfo.isPresent()) {
                response.accountInfo(optionalInfo.get());
            } else {
                response.header(ResponseHeader.newBuilder()
                        .nodeTransactionPrecheckCode(FAIL_INVALID)
                        .cost(0)); // FUTURE: from mono service, check in EET
            }
        }

        return Response.newBuilder().cryptoGetInfo(response).build();
    }

    /**
     * Provides information about an account.
     * @param accountID account id
     * @param accountStore the account store
     * @param tokenStore the token store
     * @param tokenRelationStore the token relation store
     * @param readableStakingInfoStore the staking info store
     * @param tokensConfig the TokensConfig
     * @param ledgerConfig the LedgerConfig
     * @param rewardCalculator the RewardCalculator
     * @return the information about the account
     */
    private Optional<AccountInfo> infoForAccount(
            @NonNull final AccountID accountID,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ReadableTokenStore tokenStore,
            @NonNull final ReadableTokenRelationStore tokenRelationStore,
            @NonNull final ReadableStakingInfoStore readableStakingInfoStore,
            @NonNull final TokensConfig tokensConfig,
            @NonNull final LedgerConfig ledgerConfig,
            @NonNull final RewardCalculator rewardCalculator) {
        requireNonNull(accountID);
        requireNonNull(accountStore);
        requireNonNull(tokenStore);
        requireNonNull(tokenRelationStore);
        requireNonNull(readableStakingInfoStore);
        requireNonNull(tokensConfig);
        requireNonNull(ledgerConfig);
        requireNonNull(rewardCalculator);
        final var account = accountStore.getAccountById(accountID);
        if (account == null) {
            return Optional.empty();
        } else {
            final var info = AccountInfo.newBuilder();
            info.ledgerId(ledgerConfig.id());
            if (!isEmpty(account.key())) info.key(account.key());
            info.accountID(accountID);
            info.receiverSigRequired(account.receiverSigRequired());
            info.deleted(account.deleted());
            info.memo(account.memo());
            info.autoRenewPeriod(Duration.newBuilder().seconds(account.autoRenewSecs()));
            info.balance(account.tinybarBalance());
            info.expirationTime(Timestamp.newBuilder().seconds(account.expiry()));
            info.contractAccountID(getContractAccountId(account, account.alias()));
            info.ownedNfts(account.numberOwnedNfts());
            info.maxAutomaticTokenAssociations(account.maxAutoAssociations());
            info.ethereumNonce(account.ethereumNonce());
            //  info.proxyAccountID(); Deprecated
            info.alias(account.alias());
            info.tokenRelationships(getTokenRelationship(tokensConfig, account, tokenStore, tokenRelationStore));
            info.stakingInfo(getStakingInfo(account, rewardCalculator, readableStakingInfoStore));
            return Optional.of(info.build());
        }
    }

    /**
     * get TokenRelationship of an Account
     * @param tokenConfig use TokenConfig to get maxRelsPerInfoQuery value
     * @param account the account to be calculated from
     * @param readableTokenStore readable token store
     * @param tokenRelationStore token relation store
     * @return ArrayList of TokenRelationship object
     */
    private List<TokenRelationship> getTokenRelationship(
            @NonNull final TokensConfig tokenConfig,
            @NonNull final Account account,
            @NonNull final ReadableTokenStore readableTokenStore,
            @NonNull final ReadableTokenRelationStore tokenRelationStore) {
        requireNonNull(account);
        requireNonNull(tokenConfig);
        requireNonNull(readableTokenStore);
        requireNonNull(tokenRelationStore);
        final var ret = new ArrayList<TokenRelationship>();
        var tokenNum = account.headTokenNumber();
        int count = 0;
        TokenRelation tokenRelation;
        Token token; // token from readableToken store by tokenID
        TokenID tokenID; // build from tokenNum
        AccountID accountID; // build from accountNumber
        while (tokenNum != 0 && count < tokenConfig.maxRelsPerInfoQuery()) {
            accountID =
                    AccountID.newBuilder().accountNum(account.accountNumber()).build();
            tokenID = TokenID.newBuilder().tokenNum(tokenNum).build();
            tokenRelation = tokenRelationStore.get(accountID, tokenID);
            if (tokenRelation != null) {
                token = readableTokenStore.get(tokenID);
                if (token != null) {
                    addTokenRelation(ret, token, tokenRelation, tokenNum);
                }
                tokenNum = tokenRelation.nextToken();
            } else {
                break;
            }
            count++;
        }
        return ret;
    }

    /**
     * add TokenRelationship to ArrayList
     * @param ret ArrayList of TokenRelationship object
     * @param token token from readableToken store by tokenID
     * @param tokenRelation token relation from token relation store
     * @param tokenNum token number
     */
    private void addTokenRelation(
            ArrayList<TokenRelationship> ret, Token token, TokenRelation tokenRelation, long tokenNum) {
        final var tokenRelationship = TokenRelationship.newBuilder()
                .tokenId(TokenID.newBuilder().tokenNum(tokenNum).build())
                .symbol(token.symbol())
                .balance(tokenRelation.balance())
                .decimals(token.decimals())
                .kycStatus(tokenRelation.kycGranted() ? GRANTED : KYC_NOT_APPLICABLE)
                .freezeStatus(tokenRelation.frozen() ? FROZEN : FREEZE_NOT_APPLICABLE)
                .automaticAssociation(tokenRelation.automaticAssociation())
                .build();
        ret.add(tokenRelationship);
    }

    /**
     * get ContractAccountId String of an Account
     * @param account   the account to be calculated from
     * @param alias    the alias of the account
     * @return String of ContractAccountId
     */
    private String getContractAccountId(Account account, final Bytes alias) {
        if (alias.toByteArray().length == EVM_ADDRESS_SIZE) {
            return hex(alias.toByteArray());
        }
        // If we can recover an Ethereum EOA address from the account key, we should return that
        final var evmAddress = tryAddressRecovery(account.key(), EthSigsUtils::recoverAddressFromPubKey);
        if (evmAddress != null && evmAddress.length == EVM_ADDRESS_LEN) {
            return Bytes.wrap(evmAddress).toHex();
        } else {
            return hex(asEvmAddress(account.accountNumber()));
        }
    }

    /**
     * recover EVM address from account key
     * @param key   the key of the account
     * @param addressRecovery    the function to recover EVM address
     * @return byte[] of EVM address
     */
    public static byte[] tryAddressRecovery(@Nullable final Key key, final UnaryOperator<byte[]> addressRecovery) {
        if (key != null && key.hasEcdsaSecp256k1()) {
            // Only compressed keys are stored at the moment
            final var keyBytes = key.ecdsaSecp256k1().toByteArray();
            if (keyBytes.length == ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH) {
                final var evmAddress = addressRecovery.apply(keyBytes);
                if (evmAddress != null && evmAddress.length == EVM_ADDRESS_LEN) {
                    return evmAddress;
                } else {
                    // Not ever expected, since above checks should imply a valid input to the
                    // LibSecp256k1 library
                    log.warn("Unable to recover EVM address from {}", () -> hex(keyBytes));
                }
            }
        }
        return null;
    }

    /**
     * get StakingInfo of an Account
     * @param account   the account to be calculated from
     * @param rewardCalculator  the reward calculator
     * @param readableStakingInfoStore  readable staking info store
     * @return StakingInfo object
     */
    private StakingInfo getStakingInfo(
            final Account account,
            @NonNull final RewardCalculator rewardCalculator,
            @NonNull final ReadableStakingInfoStore readableStakingInfoStore) {
        final var stakingInfo =
                StakingInfo.newBuilder().declineReward(account.declineReward()).stakedToMe(account.stakedToMe());

        final var stakedNum = account.stakedNumber();
        if (stakedNum < 0) {
            // Staked num for a node is (-nodeId -1)
            stakingInfo.stakedNodeId(-stakedNum - 1);
            addNodeStakeMeta(stakingInfo, account, rewardCalculator, readableStakingInfoStore);
        } else if (stakedNum > 0) {
            stakingInfo.stakedAccountId(
                    AccountID.newBuilder().realmNum(0).shardNum(0).accountNum(stakedNum));
        }

        return stakingInfo.build();
    }

    /**
     * add staking meta to StakingInfo
     * @param stakingInfo   the staking info to be added to
     * @param account   the account to be calculated from
     * @param rewardCalculator  the reward calculator
     * @param readableStakingInfoStore  readable staking info store
     * @return long of StakedNodeAddressBookId
     */
    private void addNodeStakeMeta(
            final StakingInfo.Builder stakingInfo,
            @NonNull final Account account,
            @NonNull final RewardCalculator rewardCalculator,
            @NonNull final ReadableStakingInfoStore readableStakingInfoStore) {
        final var startSecond = rewardCalculator.epochSecondAtStartOfPeriod(account.stakePeriodStart());
        stakingInfo.stakePeriodStart(Timestamp.newBuilder().seconds(startSecond));
        if (mayHavePendingReward(account)) {
            final var stakingNodeInfo = readableStakingInfoStore.get(AccountID.newBuilder()
                    .accountNum(getStakedNodeAddressBookId(account))
                    .build());
            final var pendingReward = rewardCalculator.estimatePendingRewards(account, stakingNodeInfo);
            stakingInfo.pendingReward(pendingReward);
        }
    }

    private boolean mayHavePendingReward(Account account) {
        return account.stakedNumber() < 0 && !account.declineReward();
    }

    private long getStakedNodeAddressBookId(Account account) {
        if (account.stakedNumber() >= 0) {
            throw new IllegalStateException("Account is not staked to a node");
        }
        return -account.stakedNumber() - 1;
    }
}
