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

package com.hedera.node.app.service.consensus.impl.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOPIC_DELETED;
import static com.hedera.node.app.service.consensus.impl.util.ConsensusHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ConsensusCryptoFeeScheduleAllowance;
import com.hedera.hapi.node.base.ConsensusTokenFeeScheduleAllowance;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.ConsensusApproveAllowanceTransactionBody;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.List;
import javax.inject.Inject;

public class ConsensusAllowancesValidator {

    /**
     * Constructs a {@link ConsensusAllowancesValidator} instance.
     */
    @Inject
    public ConsensusAllowancesValidator() {
        // Needed for Dagger injection
    }

    public void pureChecks(ConsensusApproveAllowanceTransactionBody op) throws PreCheckException {
        // The transaction must have at least one type of allowance.
        final var cryptoAllowances = op.consensusCryptoFeeScheduleAllowances();
        final var tokenAllowances = op.consensusTokenFeeScheduleAllowances();
        final var totalAllowancesSize = cryptoAllowances.size() + tokenAllowances.size();
        validateTruePreCheck(totalAllowancesSize != 0, EMPTY_ALLOWANCES);
        validateCryptoAllowances(cryptoAllowances);
        validateTokenAllowances(tokenAllowances);
    }

    public void validateSemantics(
            @NonNull final HandleContext context,
            @NonNull final ConsensusApproveAllowanceTransactionBody op,
            @NonNull final ReadableTopicStore topicStore) {
        final var accountStore = context.storeFactory().readableStore(ReadableAccountStore.class);
        final var tokenStore = context.storeFactory().readableStore(ReadableTokenStore.class);
        final var tokenRelStore = context.storeFactory().readableStore(ReadableTokenRelationStore.class);

        for (final var cryptoAllowance : op.consensusCryptoFeeScheduleAllowances()) {
            final var topicId = cryptoAllowance.topicIdOrThrow();
            final var ownerId = cryptoAllowance.ownerOrThrow();

            // validate spender account
            final var spenderAccount = accountStore.getAccountById(ownerId);
            validateSpender(cryptoAllowance.amount(), spenderAccount);
            validateTopic(topicId, topicStore);
        }

        for (var tokenAllowance : op.consensusTokenFeeScheduleAllowances()) {
            final var topicId = tokenAllowance.topicIdOrThrow();
            final var ownerId = tokenAllowance.ownerOrThrow();
            final var tokenId = tokenAllowance.tokenIdOrThrow();

            final var token = tokenStore.get(tokenId);
            // check if token exists
            validateTrue(token != null, INVALID_TOKEN_ID);

            // validate spender account
            final var spenderAccount = accountStore.getAccountById(ownerId);
            validateTrue(TokenType.FUNGIBLE_COMMON.equals(token.tokenType()), NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES);

            // validate token amount
            final var amount = tokenAllowance.amount();
            validateSpender(amount, spenderAccount);
            final var relation = tokenRelStore.get(ownerId, tokenId);
            validateTrue(relation != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);

            validateTopic(topicId, topicStore);
        }
    }

    /**
     * Validates that either the amount to be approved is 0, or the spender account actually exists and has not been
     * deleted.
     *
     * @param amount If 0, then always valid. Otherwise, we check the spender account.
     * @param spenderAccount If the amount is not zero, then this must be non-null and not deleted.
     */
    private void validateSpender(final long amount, @Nullable final Account spenderAccount) {
        validateTrue(
                amount == 0 || (spenderAccount != null && !spenderAccount.deleted()), INVALID_ALLOWANCE_SPENDER_ID);
    }

    /**
     * Validates that the topic exists and has not been deleted.
     *
     * @param topicID Validates that this is non-null and not deleted.
     */
    private void validateTopic(@Nullable final TopicID topicID, @NonNull final ReadableTopicStore topicStore) {
        requireNonNull(topicStore);

        validateTrue(topicID != null, INVALID_TOPIC_ID);
        final var topic = getIfUsable(topicID, topicStore);
        validateFalse(topic.deleted(), TOPIC_DELETED);
    }

    private static void validateCryptoAllowances(List<ConsensusCryptoFeeScheduleAllowance> cryptoAllowances)
            throws PreCheckException {
        final var uniqueMap = new HashMap<AccountID, TopicID>();
        for (var hbarAllowance : cryptoAllowances) {
            // Check if a given AccountId/TopicId pair already exists in the crypto allowances list
            validateFalsePreCheck(
                    uniqueMap.containsKey(hbarAllowance.owner())
                            && uniqueMap.get(hbarAllowance.owner()).equals(hbarAllowance.topicId()),
                    ResponseCodeEnum.REPEATED_ALLOWANCE_IN_TRANSACTION_BODY);
            // Validate the allowance amount and amount per message
            validateTruePreCheck(hbarAllowance.amount() >= 0, ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT);
            validateTruePreCheck(hbarAllowance.amountPerMessage() >= 0, ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT);
            validateTruePreCheck(
                    hbarAllowance.amount() > hbarAllowance.amountPerMessage(),
                    ResponseCodeEnum.ALLOWANCE_PER_MESSAGE_EXCEEDS_TOTAL_ALLOWANCE);
            // Add the unique (AccountID, TopicID) pair to the map
            uniqueMap.put(hbarAllowance.owner(), hbarAllowance.topicId());
        }
    }

    private static void validateTokenAllowances(List<ConsensusTokenFeeScheduleAllowance> tokenAllowances)
            throws PreCheckException {
        // TODO: add check for tokenID
        final var uniqueMap = new HashMap<AccountID, TopicID>();
        for (var tokenAllowance : tokenAllowances) {
            // Check if a given AccountId/TopicId pair already exists in the token allowances list
            validateFalsePreCheck(
                    uniqueMap.containsKey(tokenAllowance.owner())
                            && uniqueMap.get(tokenAllowance.owner()).equals(tokenAllowance.topicId()),
                    ResponseCodeEnum.REPEATED_ALLOWANCE_IN_TRANSACTION_BODY);
            // Validate the allowance amount and amount per message
            validateTruePreCheck(tokenAllowance.amount() >= 0, ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT);
            validateTruePreCheck(tokenAllowance.amountPerMessage() >= 0, ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT);
            validateTruePreCheck(
                    tokenAllowance.amount() > tokenAllowance.amountPerMessage(),
                    ResponseCodeEnum.ALLOWANCE_PER_MESSAGE_EXCEEDS_TOTAL_ALLOWANCE);
            mustExist(tokenAllowance.tokenId(), INVALID_TOKEN_ID);
            // Add the unique (AccountID, TopicID) pair to the map
            uniqueMap.put(tokenAllowance.owner(), tokenAllowance.topicId());
        }
    }
}
