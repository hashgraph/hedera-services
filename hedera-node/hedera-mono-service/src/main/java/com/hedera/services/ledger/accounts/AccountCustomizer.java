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

import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.ALIAS;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.AUTO_RENEW_ACCOUNT_ID;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.DECLINE_REWARD;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.EXPIRY;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.IS_DELETED;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.KEY;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.MEMO;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.PROXY;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.STAKED_ID;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.USED_AUTOMATIC_ASSOCIATIONS;
import static java.util.Collections.unmodifiableMap;

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.BeanProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.EntityId;
import java.util.EnumMap;
import java.util.Map;

/**
 * Implements a fluent builder for defining a set of standard customizations relevant to any account
 * on a ledger, no matter the id, account, and property types.
 *
 * @param <K> the type of the id used by the ledger.
 * @param <A> the type of the account stored in the ledger.
 * @param <P> the type of the properties applicable to the account.
 * @param <T> the type of a customizer appropriate to {@code K}, {@code A}, {@code P}.
 */
public abstract class AccountCustomizer<
        K, A, P extends Enum<P> & BeanProperty<A>, T extends AccountCustomizer<K, A, P, T>> {
    public enum Option {
        KEY,
        MEMO,
        PROXY,
        EXPIRY,
        IS_DELETED,
        IS_SMART_CONTRACT,
        AUTO_RENEW_PERIOD,
        IS_RECEIVER_SIG_REQUIRED,
        MAX_AUTOMATIC_ASSOCIATIONS,
        USED_AUTOMATIC_ASSOCIATIONS,
        ALIAS,
        AUTO_RENEW_ACCOUNT_ID,
        DECLINE_REWARD,
        STAKED_ID
    }

    private final Map<Option, P> optionProperties;
    private final ChangeSummaryManager<A, P> changeManager;
    protected final EnumMap<P, Object> changes;

    protected abstract T self();

    protected AccountCustomizer(
            final Class<P> propertyType,
            final Map<Option, P> optionProperties,
            final ChangeSummaryManager<A, P> changeManager) {
        this.changeManager = changeManager;
        this.optionProperties = optionProperties;
        this.changes = new EnumMap<>(propertyType);
    }

    public Map<P, Object> getChanges() {
        return changes;
    }

    public Map<Option, P> getOptionProperties() {
        return unmodifiableMap(optionProperties);
    }

    public A customizing(final A account) {
        changeManager.persist(changes, account);
        return account;
    }

    public void customize(final K id, final TransactionalLedger<K, P, A> ledger) {
        changes.entrySet().forEach(change -> ledger.set(id, change.getKey(), change.getValue()));
    }

    public T key(final JKey option) {
        changeManager.update(changes, optionProperties.get(KEY), option);
        return self();
    }

    public T memo(final String option) {
        changeManager.update(changes, optionProperties.get(MEMO), option);
        return self();
    }

    public T proxy(final EntityId option) {
        if (option != null) {
            changeManager.update(changes, optionProperties.get(PROXY), option);
        }
        return self();
    }

    public T expiry(final long option) {
        changeManager.update(changes, optionProperties.get(EXPIRY), option);
        return self();
    }

    public T alias(final ByteString option) {
        changeManager.update(changes, optionProperties.get(ALIAS), option);
        return self();
    }

    public T isDeleted(final boolean option) {
        changeManager.update(changes, optionProperties.get(IS_DELETED), option);
        return self();
    }

    public T autoRenewPeriod(final long option) {
        changeManager.update(changes, optionProperties.get(AUTO_RENEW_PERIOD), option);
        return self();
    }

    public T isSmartContract(final boolean option) {
        changeManager.update(changes, optionProperties.get(IS_SMART_CONTRACT), option);
        return self();
    }

    public T isReceiverSigRequired(final boolean option) {
        changeManager.update(changes, optionProperties.get(IS_RECEIVER_SIG_REQUIRED), option);
        return self();
    }

    public T maxAutomaticAssociations(final int option) {
        changeManager.update(changes, optionProperties.get(MAX_AUTOMATIC_ASSOCIATIONS), option);
        return self();
    }

    public T usedAutomaticAssociations(final int option) {
        changeManager.update(changes, optionProperties.get(USED_AUTOMATIC_ASSOCIATIONS), option);
        return self();
    }

    public T autoRenewAccount(final EntityId option) {
        if (option != null) {
            changeManager.update(changes, optionProperties.get(AUTO_RENEW_ACCOUNT_ID), option);
        }
        return self();
    }

    public T isDeclinedReward(final boolean option) {
        changeManager.update(changes, optionProperties.get(DECLINE_REWARD), option);
        return self();
    }

    public T stakedId(final long option) {
        changeManager.update(changes, optionProperties.get(STAKED_ID), option);
        return self();
    }
}
