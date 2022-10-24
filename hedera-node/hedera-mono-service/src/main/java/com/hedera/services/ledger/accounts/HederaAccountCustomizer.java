/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger.accounts;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;

import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class HederaAccountCustomizer
        extends AccountCustomizer<
                AccountID, HederaAccount, AccountProperty, HederaAccountCustomizer> {
    private static final Map<Option, AccountProperty> OPTION_PROPERTIES;
    public static final String STAKED_ID_NOT_SET_CASE = "STAKEDID_NOT_SET";
    public static final String STAKED_ACCOUNT_ID_CASE = "STAKED_ACCOUNT_ID";
    public static final String STAKED_NODE_ID_CASE = "STAKED_NODE_ID";

    static {
        Map<Option, AccountProperty> optionAccountPropertyMap = new EnumMap<>(Option.class);
        optionAccountPropertyMap.put(Option.KEY, AccountProperty.KEY);
        optionAccountPropertyMap.put(Option.MEMO, AccountProperty.MEMO);
        optionAccountPropertyMap.put(Option.PROXY, AccountProperty.PROXY);
        optionAccountPropertyMap.put(Option.EXPIRY, AccountProperty.EXPIRY);
        optionAccountPropertyMap.put(Option.IS_DELETED, AccountProperty.IS_DELETED);
        optionAccountPropertyMap.put(Option.AUTO_RENEW_PERIOD, AccountProperty.AUTO_RENEW_PERIOD);
        optionAccountPropertyMap.put(Option.IS_SMART_CONTRACT, AccountProperty.IS_SMART_CONTRACT);
        optionAccountPropertyMap.put(
                Option.IS_RECEIVER_SIG_REQUIRED, AccountProperty.IS_RECEIVER_SIG_REQUIRED);
        optionAccountPropertyMap.put(
                Option.MAX_AUTOMATIC_ASSOCIATIONS, AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS);
        optionAccountPropertyMap.put(
                Option.USED_AUTOMATIC_ASSOCIATIONS, AccountProperty.USED_AUTOMATIC_ASSOCIATIONS);
        optionAccountPropertyMap.put(Option.ALIAS, AccountProperty.ALIAS);
        optionAccountPropertyMap.put(
                Option.AUTO_RENEW_ACCOUNT_ID, AccountProperty.AUTO_RENEW_ACCOUNT_ID);
        optionAccountPropertyMap.put(Option.DECLINE_REWARD, AccountProperty.DECLINE_REWARD);
        optionAccountPropertyMap.put(Option.STAKED_ID, AccountProperty.STAKED_ID);
        OPTION_PROPERTIES = Collections.unmodifiableMap(optionAccountPropertyMap);
    }

    public HederaAccountCustomizer() {
        super(AccountProperty.class, OPTION_PROPERTIES, new ChangeSummaryManager<>());
    }

    public void customizeSynthetic(final ContractCreateTransactionBody.Builder op) {
        final var changes = getChanges();
        if (changes.containsKey(AccountProperty.MEMO)) {
            op.setMemo((String) changes.get(AccountProperty.MEMO));
        }
        if (changes.containsKey(AccountProperty.AUTO_RENEW_PERIOD)) {
            op.setAutoRenewPeriod(
                    Duration.newBuilder()
                            .setSeconds((long) changes.get(AccountProperty.AUTO_RENEW_PERIOD)));
        }
        if (changes.containsKey(AccountProperty.STAKED_ID)) {
            final long id = (long) changes.get(AccountProperty.STAKED_ID);
            if (id < 0) {
                op.setStakedNodeId(-id - 1);
            } else if (id > 0) {
                op.setStakedAccountId(STATIC_PROPERTIES.scopedAccountWith(id));
            }
        }
        if (changes.containsKey(AccountProperty.DECLINE_REWARD)) {
            op.setDeclineReward((boolean) changes.get(AccountProperty.DECLINE_REWARD));
        }
        if (changes.containsKey(AccountProperty.AUTO_RENEW_ACCOUNT_ID)) {
            op.setAutoRenewAccountId(
                    ((EntityId) changes.get(AccountProperty.AUTO_RENEW_ACCOUNT_ID))
                            .toGrpcAccountId());
        }
        if (changes.containsKey(AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS)) {
            op.setMaxAutomaticTokenAssociations(
                    ((int) changes.get(AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS)));
        }
    }

    @Override
    protected HederaAccountCustomizer self() {
        return this;
    }

    public static boolean hasStakedId(final String idCase) {
        return !idCase.equals(STAKED_ID_NOT_SET_CASE);
    }

    public void customizeStakedId(
            final String idCase, final AccountID stakedAccountId, final long stakedNodeId) {
        final var stakedId = getStakedId(idCase, stakedAccountId, stakedNodeId);
        this.stakedId(stakedId);
    }

    /**
     * Gets the stakedId from the provided staked_account_id or staked_node_id.
     *
     * @param stakedAccountId given staked_account_id
     * @param stakedNodeId given staked_node_id
     * @return valid staked id
     */
    static long getStakedId(
            final String idCase, final AccountID stakedAccountId, final long stakedNodeId) {
        if (idCase.matches(STAKED_ACCOUNT_ID_CASE)) {
            return stakedAccountId.getAccountNum();
        } else {
            // return a number less than the given node Id, in order to recognize the if nodeId 0 is
            // set
            return -stakedNodeId - 1;
        }
    }
}
