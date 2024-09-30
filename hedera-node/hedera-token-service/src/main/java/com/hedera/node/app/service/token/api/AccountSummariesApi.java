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

package com.hedera.node.app.service.token.api;

import static com.hedera.hapi.node.base.TokenFreezeStatus.FREEZE_NOT_APPLICABLE;
import static com.hedera.hapi.node.base.TokenFreezeStatus.FROZEN;
import static com.hedera.hapi.node.base.TokenFreezeStatus.UNFROZEN;
import static com.hedera.hapi.node.base.TokenKycStatus.GRANTED;
import static com.hedera.hapi.node.base.TokenKycStatus.KYC_NOT_APPLICABLE;
import static com.hedera.hapi.node.base.TokenKycStatus.REVOKED;
import static com.hedera.node.app.hapi.utils.CommonUtils.asEvmAddress;
import static com.hedera.node.app.service.token.api.StakingRewardsApi.epochSecondAtStartOfPeriod;
import static com.hedera.node.app.service.token.api.StakingRewardsApi.estimatePendingReward;
import static com.hedera.node.app.spi.key.KeyUtils.ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH;
import static com.swirlds.common.utility.CommonUtils.hex;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.StakingInfo;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenFreezeStatus;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenKycStatus;
import com.hedera.hapi.node.base.TokenRelationship;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.hapi.utils.EthSigsUtils;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Methods for summarizing an account's relationships and attributes to HAPI clients.
 */
public interface AccountSummariesApi {
    /**
     * The size of an EVM address.
     */
    int EVM_ADDRESS_SIZE = 20;
    /**
     * The sentinel node id to represent stakingNodeId is absent on account.
     * It is -1 because nodeId 0 is allowed for network.
     */
    long SENTINEL_NODE_ID = -1L;
    /**
     * The sentinel account id to represent stakedAccountId is absent on account.
     */
    AccountID SENTINEL_ACCOUNT_ID = AccountID.newBuilder().accountNum(0).build();

    /**
     * Returns the hexed EVM address of the given account.
     *
     * @param account the account to be calculated from
     * @return the hexed EVM address of the given account
     */
    static String hexedEvmAddressOf(@NonNull final Account account) {
        requireNonNull(account);
        final var alias = account.alias().toByteArray();
        if (alias.length == EVM_ADDRESS_SIZE) {
            return hex(alias);
        }
        // If we can recover an Ethereum EOA address from the account key, we should return that
        final var evmAddress = tryAddressRecovery(account.key(), EthSigsUtils::recoverAddressFromPubKey);
        if (evmAddress.length == EVM_ADDRESS_SIZE) {
            return Bytes.wrap(evmAddress).toHex();
        } else {
            return hex(asEvmAddress(account.accountIdOrThrow().accountNumOrThrow()));
        }
    }

    /**
     * Returns up to the given limit of token relationships for the given account, relative to the provided
     * {@link ReadableTokenStore} and {@link ReadableTokenRelationStore}.
     *
     * @param account the account to get token relationships for
     * @param readableTokenStore the readable token store
     * @param tokenRelationStore the readable token relation store
     * @param limit the maximum number of token relationships to return
     * @return the token relationships for the given account
     */
    static List<TokenRelationship> tokenRelationshipsOf(
            @NonNull final Account account,
            @NonNull final ReadableTokenStore readableTokenStore,
            @NonNull final ReadableTokenRelationStore tokenRelationStore,
            final long limit) {
        requireNonNull(account);
        requireNonNull(tokenRelationStore);
        requireNonNull(readableTokenStore);

        final var ret = new ArrayList<TokenRelationship>();
        var tokenId = account.headTokenId();
        int count = 0;
        TokenRelation tokenRelation;
        Token token; // token from readableToken store by tokenID
        AccountID accountID; // build from accountNumber
        while (tokenId != null && !tokenId.equals(TokenID.DEFAULT) && count < limit) {
            accountID = account.accountId();
            tokenRelation = tokenRelationStore.get(accountID, tokenId);
            if (tokenRelation != null) {
                token = readableTokenStore.get(tokenId);
                if (token != null) {
                    addTokenRelation(ret, token, tokenRelation, tokenId);
                }
                tokenId = tokenRelation.nextToken();
            } else {
                break;
            }
            count++;
        }
        return ret;
    }

    private static void addTokenRelation(
            List<TokenRelationship> ret, Token token, TokenRelation tokenRelation, TokenID tokenId) {
        TokenFreezeStatus freezeStatus = FREEZE_NOT_APPLICABLE;
        if (token.hasFreezeKey()) {
            freezeStatus = tokenRelation.frozen() ? FROZEN : UNFROZEN;
        }

        TokenKycStatus kycStatus = KYC_NOT_APPLICABLE;
        if (token.hasKycKey()) {
            kycStatus = tokenRelation.kycGranted() ? GRANTED : REVOKED;
        }

        final var tokenRelationship = TokenRelationship.newBuilder()
                .tokenId(tokenId)
                .symbol(token.symbol())
                .balance(tokenRelation.balance())
                .decimals(token.decimals())
                .kycStatus(kycStatus)
                .freezeStatus(freezeStatus)
                .automaticAssociation(tokenRelation.automaticAssociation())
                .build();
        ret.add(tokenRelationship);
    }

    /**
     * Tries to recover EVM address from an account key with a given recovery function.
     *
     * @param key   the key of the account
     * @param addressRecovery    the function to recover EVM address
     * @return the explicit EVM address bytes, if recovered; empty array otherwise
     */
    private static byte[] tryAddressRecovery(@Nullable final Key key, final UnaryOperator<byte[]> addressRecovery) {
        if (key != null && key.hasEcdsaSecp256k1()) {
            // Only compressed keys are stored at the moment
            final var keyBytes = key.ecdsaSecp256k1().toByteArray();
            if (keyBytes.length == ECDSA_SECP256K1_COMPRESSED_KEY_LENGTH) {
                final var evmAddress = addressRecovery.apply(keyBytes);
                if (evmAddress != null && evmAddress.length == EVM_ADDRESS_SIZE) {
                    return evmAddress;
                } else {
                    // Not ever expected, since above checks should imply a valid input to the
                    // LibSecp256k1 library
                    throw new IllegalArgumentException(
                            "Unable to recover EVM address from structurally valid " + hex(keyBytes));
                }
            }
        }
        return new byte[0];
    }

    /**
     * Returns the summary of an account's staking info, given the account, the staking info store, and some
     * information about the network.
     *
     * @param numStoredPeriods the number of periods for which the rewards are stored
     * @param stakePeriodMins the duration of a stake period
     * @param areRewardsActive whether the rewards are active
     * @param account the account
     * @param stakingInfoStore the staking info store
     * @param estimatedConsensusNow the estimated consensus time
     * @return the summary of the account's staking info
     */
    static StakingInfo summarizeStakingInfo(
            final int numStoredPeriods,
            final long stakePeriodMins,
            final boolean areRewardsActive,
            @NonNull final Account account,
            @NonNull final ReadableStakingInfoStore stakingInfoStore,
            @NonNull final Instant estimatedConsensusNow) {
        requireNonNull(account);
        requireNonNull(stakingInfoStore);
        requireNonNull(estimatedConsensusNow);

        final var stakingInfo =
                StakingInfo.newBuilder().declineReward(account.declineReward()).stakedToMe(account.stakedToMe());
        if (account.hasStakedNodeId() && account.stakedNodeIdOrThrow() != SENTINEL_NODE_ID) {
            stakingInfo.stakedNodeId(account.stakedNodeIdOrThrow());
            addNodeStakeMeta(
                    numStoredPeriods,
                    stakePeriodMins,
                    areRewardsActive,
                    account,
                    stakingInfoStore,
                    stakingInfo,
                    estimatedConsensusNow);
        } else if (account.hasStakedAccountId() && account.stakedAccountId() != null) {
            stakingInfo.stakedAccountId(account.stakedAccountIdOrThrow());
        }
        return stakingInfo.build();
    }

    /**
     * Adds the node stake meta to the given staking info builder, given the account
     * and some information about the network.
     *
     * @param numStoredPeriods the number of periods for which the rewards are stored
     * @param stakePeriodMins the duration of a stake period
     * @param areRewardsActive whether the rewards are active
     * @param account the account
     * @param readableStakingInfoStore the readable staking info store
     * @param stakingInfo the staking info builder
     */
    static void addNodeStakeMeta(
            final int numStoredPeriods,
            final long stakePeriodMins,
            final boolean areRewardsActive,
            @NonNull final Account account,
            @NonNull final ReadableStakingInfoStore readableStakingInfoStore,
            @NonNull final StakingInfo.Builder stakingInfo,
            @NonNull final Instant estimatedConsensusNow) {
        stakingInfo
                .stakePeriodStart(Timestamp.newBuilder()
                        .seconds(epochSecondAtStartOfPeriod(account.stakePeriodStart(), stakePeriodMins)))
                .pendingReward(estimatePendingReward(
                        numStoredPeriods,
                        stakePeriodMins,
                        areRewardsActive,
                        account,
                        readableStakingInfoStore,
                        estimatedConsensusNow));
    }
}
