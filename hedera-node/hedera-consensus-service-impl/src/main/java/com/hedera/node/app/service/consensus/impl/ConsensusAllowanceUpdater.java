/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.consensus.impl;

import static com.hedera.node.app.service.consensus.impl.util.ConsensusHandlerHelper.getIfUsable;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ConsensusCryptoFeeScheduleAllowance;
import com.hedera.hapi.node.base.ConsensusTokenFeeScheduleAllowance;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.TopicCryptoAllowance;
import com.hedera.hapi.node.state.consensus.TopicFungibleTokenAllowance;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

// todo update this class when allowances are done!!!
@Singleton
public class ConsensusAllowanceUpdater {

    /**
     * Constructs a {@link ConsensusAllowanceUpdater} instance.
     */
    @Inject
    public ConsensusAllowanceUpdater() {
        // Needed for Dagger injection
    }


    /**
     * Applies all changes needed for Crypto allowances from the transaction.
     * If the topic already has an allowance, the allowance value will be replaced with values
     * from transaction. If the amount specified is 0, the allowance will be removed.
     * @param topicCryptoAllowances the list of crypto allowances
     * @param topicStore the topic store
     */
    public void applyCryptoAllowances(
            @NonNull final List<ConsensusCryptoFeeScheduleAllowance> topicCryptoAllowances,
            @NonNull final WritableTopicStore topicStore) {
        requireNonNull(topicCryptoAllowances);
        requireNonNull(topicStore);

        for (final var allowance : topicCryptoAllowances) {
            final var ownerId = allowance.owner();
            final var topicId = allowance.topicIdOrThrow();
            final var topic = getIfUsable(topicId, topicStore);
            final var mutableAllowances = new ArrayList<>(topic.cryptoAllowances());

            final var amount = allowance.amount();
            final var amountPerMessage = allowance.amountPerMessage();

            updateCryptoAllowance(mutableAllowances, amount, amountPerMessage, ownerId);
            final var copy =
                    topic.copyBuilder().cryptoAllowances(mutableAllowances).build();

            topicStore.put(copy);
        }
    }

    public void applyCryptoAllowances(
            @NonNull final TopicID topicId,
            @NonNull final TopicCryptoAllowance allowance,
            @NonNull final WritableTopicStore topicStore) {
        requireNonNull(allowance);
        requireNonNull(topicStore);

        final var ownerId = allowance.spenderId();
        final var topic = getIfUsable(topicId, topicStore);
        final var mutableAllowances = new ArrayList<>(topic.cryptoAllowances());

        final var amount = allowance.amount();
        final var amountPerMessage = allowance.amountPerMessage();

        updateCryptoAllowance(mutableAllowances, amount, amountPerMessage, ownerId);
        final var copy = topic.copyBuilder().cryptoAllowances(mutableAllowances).build();

        topicStore.put(copy);
    }

    /**
     * Updates the crypto allowance amount if the allowance exists, otherwise adds a new allowance.
     * If the amount is zero removes the allowance if it exists in the list.
     * @param mutableAllowances the list of mutable allowances of owner
     * @param amount the amount
     * @param spenderId the spender id
     */
    private void updateCryptoAllowance(
            final List<TopicCryptoAllowance> mutableAllowances,
            final long amount,
            final long amountPerMessage,
            final AccountID spenderId) {
        final var newAllowanceBuilder = TopicCryptoAllowance.newBuilder().spenderId(spenderId);
        // get the index of the allowance with same spender in existing list
        final var index = lookupSpender(mutableAllowances, spenderId);
        // If given amount is zero, if the element exists remove it, otherwise do nothing
        if (amount == 0) {
            if (index != -1) {
                // If amount is 0, remove the allowance
                mutableAllowances.remove(index);
            }
            return;
        }
        if (index != -1) {
            mutableAllowances.set(
                    index,
                    newAllowanceBuilder
                            .amount(amount)
                            .amountPerMessage(amountPerMessage)
                            .build());
        } else {
            mutableAllowances.add(newAllowanceBuilder
                    .amount(amount)
                    .amountPerMessage(amountPerMessage)
                    .build());
        }
    }

    /**
     * Applies all changes needed for fungible token allowances from the transaction. If the key
     * {token, spender} already has an allowance, the allowance value will be replaced with values
     * from transaction.
     * @param tokenAllowances the list of token allowances
     * @param topicStore the topic store
     */
    public void applyFungibleTokenAllowances(
            @NonNull final List<ConsensusTokenFeeScheduleAllowance> tokenAllowances,
            @NonNull final WritableTopicStore topicStore) {
        requireNonNull(tokenAllowances);
        requireNonNull(topicStore);

        for (final var allowance : tokenAllowances) {
            final var ownerId = allowance.owner();
            final var amount = allowance.amount();
            final var amountPerMessage = allowance.amountPerMessage();
            final var tokenId = allowance.tokenIdOrThrow();
            final var topicId = allowance.topicIdOrThrow();
            final var topic = getIfUsable(topicId, topicStore);

            final var mutableTokenAllowances = new ArrayList<>(topic.tokenAllowances());

            updateTokenAllowance(mutableTokenAllowances, amount, amountPerMessage, ownerId, tokenId);
            final var copy =
                    topic.copyBuilder().tokenAllowances(mutableTokenAllowances).build();

            topicStore.put(copy);
        }
    }

    /*
     *
     */
    public void applyFungibleTokenAllowances(
            @NonNull final TopicID topicId,
            @NonNull final TopicFungibleTokenAllowance allowance,
            @NonNull final WritableTopicStore topicStore) {
        requireNonNull(allowance);
        requireNonNull(topicStore);

        final var ownerId = allowance.spenderIdOrThrow();
        final var amount = allowance.amount();
        final var amountPerMessage = allowance.amountPerMessage();
        final var tokenId = allowance.tokenIdOrThrow();
        final var topic = getIfUsable(topicId, topicStore);

        final var mutableTokenAllowances = new ArrayList<>(topic.tokenAllowances());

        updateTokenAllowance(mutableTokenAllowances, amount, amountPerMessage, ownerId, tokenId);
        final var copy =
                topic.copyBuilder().tokenAllowances(mutableTokenAllowances).build();

        topicStore.put(copy);
    }

    /**
     * Updates the token allowance amount if the allowance for given tokenNuma dn spenderNum exists,
     * otherwise adds a new allowance.
     * If the amount is zero removes the allowance if it exists in the list
     * @param mutableAllowances the list of mutable allowances of owner
     * @param amount the amount
     * @param spenderId the spender number
     * @param tokenId the token number
     */
    private void updateTokenAllowance(
            final List<TopicFungibleTokenAllowance> mutableAllowances,
            final long amount,
            final long amountPerMessage,
            final AccountID spenderId,
            final TokenID tokenId) {
        final var newAllowanceBuilder =
                TopicFungibleTokenAllowance.newBuilder().spenderId(spenderId).tokenId(tokenId);
        final var index = lookupSpenderAndToken(mutableAllowances, spenderId, tokenId);
        // If given amount is zero, if the element exists remove it
        if (amount == 0) {
            if (index != -1) {
                mutableAllowances.remove(index);
            }
            return;
        }
        if (index != -1) {
            mutableAllowances.set(
                    index,
                    newAllowanceBuilder
                            .amount(amount)
                            .amountPerMessage(amountPerMessage)
                            .build());
        } else {
            mutableAllowances.add(newAllowanceBuilder
                    .amount(amount)
                    .amountPerMessage(amountPerMessage)
                    .build());
        }
    }

    /**
     * Returns the index of the allowance with the given spender in the list if it exists,
     * otherwise returns -1.
     * @param topicCryptoAllowances list of allowances
     * @param spenderNum spender account number
     * @return index of the allowance if it exists, otherwise -1
     */
    private int lookupSpender(
            final List<TopicCryptoAllowance> topicCryptoAllowances, final AccountID spenderNum) {
        for (int i = 0; i < topicCryptoAllowances.size(); i++) {
            final var allowance = topicCryptoAllowances.get(i);
            if (allowance.spenderIdOrThrow().equals(spenderNum)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the index of the allowance  with the given spender and token in the list if it exists,
     * otherwise returns -1.
     * @param topicTokenAllowances list of allowances
     * @param spenderId spender account number
     * @param tokenId token number
     * @return index of the allowance if it exists, otherwise -1
     */
    private int lookupSpenderAndToken(
            final List<TopicFungibleTokenAllowance> topicTokenAllowances,
            final AccountID spenderId,
            final TokenID tokenId) {
        for (int i = 0; i < topicTokenAllowances.size(); i++) {
            final var allowance = topicTokenAllowances.get(i);
            if (allowance.spenderIdOrThrow().equals(spenderId)
                    && allowance.tokenIdOrThrow().equals(tokenId)) {
                return i;
            }
        }
        return -1;
    }
}
