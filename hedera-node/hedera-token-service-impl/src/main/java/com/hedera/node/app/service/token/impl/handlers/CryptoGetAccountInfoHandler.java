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
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseType.ANSWER_ONLY;
import static com.hedera.hapi.node.base.ResponseType.ANSWER_STATE_PROOF;
import static com.hedera.hapi.node.base.ResponseType.COST_ANSWER;
import static com.hedera.hapi.node.base.TokenFreezeStatus.FREEZE_NOT_APPLICABLE;
import static com.hedera.hapi.node.base.TokenFreezeStatus.FROZEN;
import static com.hedera.hapi.node.base.TokenKycStatus.GRANTED;
import static com.hedera.hapi.node.base.TokenKycStatus.KYC_NOT_APPLICABLE;
import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.EVM_ADDRESS_LEN;
import static com.hedera.node.app.spi.key.KeyUtils.isEmpty;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.swirlds.common.utility.CommonUtils.hex;
import static java.lang.System.arraycopy;
import static java.util.Objects.requireNonNull;

import com.google.common.primitives.Longs;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.ResponseType;
import com.hedera.hapi.node.base.StakingInfo;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenRelationship;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.AccountInfo;
import com.hedera.hapi.node.token.CryptoGetInfoQuery;
import com.hedera.hapi.node.token.CryptoGetInfoResponse;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.node.app.service.mono.ledger.accounts.staking.RewardCalculator;
import com.hedera.node.app.service.mono.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.workflows.PaidQueryHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.utility.CommonUtils;
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
    public boolean requiresNodePayment(@NonNull ResponseType responseType) {
        return responseType == ANSWER_ONLY || responseType == ANSWER_STATE_PROOF;
    }

    @Override
    public boolean needsAnswerOnlyCost(@NonNull ResponseType responseType) {
        return COST_ANSWER == responseType;
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
        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);
        final var ledgerConfig = context.configuration().getConfigData(LedgerConfig.class);
        final var accountStore = context.createStore(ReadableAccountStore.class);
        final var tokenRelationStore = context.createStore(ReadableTokenRelationStore.class);
        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var op = query.cryptoGetInfoOrThrow();
        final var response = CryptoGetInfoResponse.newBuilder();
        final var accountId = op.accountIDOrElse(AccountID.DEFAULT);

        response.header(header);
        final var responseType = op.headerOrElse(QueryHeader.DEFAULT).responseType();
        if (header.nodeTransactionPrecheckCode() == OK && responseType != COST_ANSWER) {
            final var optionalInfo =
                    infoForAccount(accountId, accountStore, tokenStore, tokenRelationStore, tokensConfig, ledgerConfig);
            optionalInfo.ifPresent(response::accountInfo);
        }

        return Response.newBuilder().cryptoGetInfo(response).build();
    }

    /**
     * Provides information about an account.
     * @param accountID account id
     * @param accountStore the account store
     *                     @param tokenStore the token store
     *                                       @param tokenRelationStore the token relation store
     * @param tokensConfig the TokensConfig
     * @param ledgerConfig the LedgerConfig
     * @return the information about the account
     */
    private Optional<AccountInfo> infoForAccount(
            @NonNull final AccountID accountID,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ReadableTokenStore tokenStore,
            @NonNull final ReadableTokenRelationStore tokenRelationStore,
            @NonNull final TokensConfig tokensConfig,
            @NonNull final LedgerConfig ledgerConfig) {
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
            //            info.proxyAccountID(); Deprecated
            info.alias(account.alias());
            info.tokenRelationships(getTokenRelationship(tokensConfig, account, tokenStore, tokenRelationStore));
            info.stakingInfo(getStakingInfo(account));
            return Optional.of(info.build());
        }
    }

    /**
     * Calculate TokenRelationship of an Account
     * @param tokenConfig use TokenConfig to get maxRelsPerInfoQuery value
     * @param account the account to be calculated from
     * @param readableTokenStore readable token store
     * @param tokenRelationStore token relation store
     * @return ArrayList of TokenBalance object
     */
    private List<TokenRelationship> getTokenRelationship(
            @NonNull final TokensConfig tokenConfig,
            @NonNull final Account account,
            @NonNull final ReadableTokenStore readableTokenStore,
            @NonNull final ReadableTokenRelationStore tokenRelationStore) {
        final var ret = new ArrayList<TokenRelationship>();
        var tokenNum = account.headTokenNumber();
        int count = 0;
        Optional<TokenRelation> tokenRelation;
        Token token; // token from readableToken store by tokenID
        TokenID tokenID; // build from tokenNum
        AccountID accountID; // build from accountNumber
        TokenRelationship tokenRelationship; // created TokenRelationship object
        while (tokenNum != 0 && count < tokenConfig.maxRelsPerInfoQuery()) {
            accountID =
                    AccountID.newBuilder().accountNum(account.accountNumber()).build();
            tokenID = TokenID.newBuilder().tokenNum(tokenNum).build();
            tokenRelation = tokenRelationStore.get(accountID, tokenID);
            if (tokenRelation.isPresent()) {
                token = readableTokenStore.get(tokenID);
                if (token != null) {
                    tokenRelationship = TokenRelationship.newBuilder()
                            .tokenId(TokenID.newBuilder().tokenNum(tokenNum).build())
                            .symbol(token.symbol())
                            .balance(tokenRelation.get().balance())
                            .decimals(token.decimals())
                            .kycStatus(tokenRelation.get().kycGranted() ? GRANTED : KYC_NOT_APPLICABLE)
                            .freezeStatus(tokenRelation.get().frozen() ? FROZEN : FREEZE_NOT_APPLICABLE)
                            .automaticAssociation(tokenRelation.get().automaticAssociation())
                            .build();
                    ret.add(tokenRelationship);
                }
                tokenNum = tokenRelation.get().nextToken();
            } else {
                break;
            }
            count++;
        }
        return ret;
    }

    private String getContractAccountId(Account account, final Bytes alias) {
        if (alias.toByteArray().length == EVM_ADDRESS_SIZE) {
            return CommonUtils.hex(alias.toByteArray());
        }
        // If we can recover an Ethereum EOA address from the account key, we should return that
        final var evmAddress = tryAddressRecovery(account.key(), EthSigsUtils::recoverAddressFromPubKey);
        if (evmAddress != null && evmAddress.length == EVM_ADDRESS_LEN) {
            return Bytes.wrap(evmAddress).toHex();
        } else {
            return CommonUtils.hex(asEvmAddress(account.accountNumber()));
        }
    }

    private static byte[] asEvmAddress(final long num) {
        final byte[] evmAddress = new byte[20];
        arraycopy(Longs.toByteArray(num), 0, evmAddress, 12, 8);
        return evmAddress;
    }

    public static byte[] tryAddressRecovery(@Nullable final Key key, final UnaryOperator<byte[]> addressRecovery) {
        if (key != null && key.hasEcdsaSecp256k1()) {
            // Only compressed keys are stored at the moment
            final var keyBytes = key.ecdsaSecp256k1().toByteArray();
            if (keyBytes.length == JECDSASecp256k1Key.ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH) {
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

    private StakingInfo getStakingInfo(final Account account) {
        final var stakingInfo =
                StakingInfo.newBuilder().declineReward(account.declineReward()).stakedToMe(account.stakedToMe());

        final var stakedNum = account.stakedNumber();
        if (stakedNum < 0) {
            // Staked num for a node is (-nodeId -1)
            stakingInfo.stakedNodeId(-stakedNum - 1);
            addNodeStakeMeta(stakingInfo, account);
        } else if (stakedNum > 0) {
            stakingInfo.stakedAccountId("0.0." + stakedNum);
        }

        return stakingInfo.build();
    }

    private void addNodeStakeMeta(
            final StakingInfo.Builder stakingInfo, final Account account, final RewardCalculator rewardCalculator) {
        final var startSecond = rewardCalculator.epochSecondAtStartOfPeriod(account.stakePeriodStart());
        stakingInfo.stakePeriodStart(Timestamp.newBuilder().seconds(startSecond));
        if (account.mayHavePendingReward()) {
            final var info = stateChildren.stakingInfo();
            final var nodeStakingInfo = info.get(EntityNum.fromLong(account.getStakedNodeAddressBookId()));
            final var pendingReward = rewardCalculator.estimatePendingRewards(account, nodeStakingInfo);
            stakingInfo.ep.setPendingReward(pendingReward);
        }
    }
}
