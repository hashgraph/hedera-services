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

package com.hedera.node.app.service.token.impl.handlers;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.config.data.AccountsConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * This class contains common functionality needed for crypto handlers.
 */
public class BaseCryptoHandler {
    /**
     * Gets the stakedId from the provided staked_account_id or staked_node_id.
     * When staked_node_id is provided, it is stored as negative number in state to
     * distinguish it from staked_account_id. It will be converted back to positive number
     * when it is retrieved from state.
     * To distinguish for node 0, it will be stored as - node_id -1.
     * For example, if staked_node_id is 0, it will be stored as -1 in state.
     *
     * @param stakedIdType staked id type, if staked node id or staked account id
     * @param stakedNodeId staked node id
     * @param stakedAccountId staked account id
     * @return valid staked id
     */
    protected long getStakedId(
            @NonNull final String stakedIdType,
            @Nullable final Long stakedNodeId,
            @Nullable final AccountID stakedAccountId) {
        if ("STAKED_ACCOUNT_ID".equals(stakedIdType) && stakedAccountId != null) {
            return stakedAccountId.accountNum();
        } else if ("STAKED_NODE_ID".equals(stakedIdType) && stakedNodeId != null) {
            // return a number less than the given node Id, in order to recognize the if nodeId 0 is
            // set
            return -stakedNodeId - 1;
        } else {
            throw new IllegalStateException("StakedIdOneOfType is not set");
        }
    }

    @NonNull
    public static AccountID asAccount(final long num) {
        return AccountID.newBuilder().accountNum(num).build();
    }

    /**
     * Checks that an accountId represents one of the staking accounts
     * @param accountID    the accountID to check
     */
    public static boolean isStakingAccount(
            @NonNull final Configuration configuration, @Nullable final AccountID accountID) {
        final var accountNum = accountID != null ? accountID.accountNum() : 0;
        final var accountsConfig = configuration.getConfigData(AccountsConfig.class);
        if (accountNum == accountsConfig.stakingRewardAccount() || accountNum == accountsConfig.nodeRewardAccount()) {
            return true;
        }
        return false;
    }
}
