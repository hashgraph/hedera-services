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

package com.hedera.services.bdd.spec.utilops.mod;

import static com.hedera.node.app.hapi.utils.CommonUtils.extractTransactionBody;
import static com.hedera.node.app.hapi.utils.CommonUtils.functionOf;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static java.util.Collections.emptyMap;
import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;

import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.hedera.node.app.hapi.utils.exception.UnknownHederaFunctionality;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class ModUtils {
    private ModUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    private static final Map<String, ExpectedResponse> GENERIC_CLEARED_ID_RESPONSES = Map.ofEntries(
            entry("proto.TransactionID.accountID", ExpectedResponse.atIngest(PAYER_ACCOUNT_NOT_FOUND)),
            entry("proto.TransactionBody.nodeAccountID", ExpectedResponse.atIngest(INVALID_NODE_ACCOUNT)));

    // (FUTURE) Only enforce these failures at consensus
    private static final Map<HederaFunctionality, Map<String, ExpectedResponse>> SPECIFIC_CLEARED_ID_RESPONSES =
            Map.ofEntries(
                    entry(
                            TokenAssociateToAccount,
                            Map.ofEntries(
                                    entry(
                                            "proto.TokenAssociateTransactionBody.account",
                                            ExpectedResponse.atIngest(INVALID_ACCOUNT_ID)),
                                    entry(
                                            "proto.TokenAssociateTransactionBody.tokens",
                                            ExpectedResponse.atConsensusOneOf(
                                                    SUCCESS, TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT)))),
                    entry(
                            CryptoTransfer,
                            Map.ofEntries(
                                    entry(
                                            "proto.AccountAmount.accountID",
                                            ExpectedResponse.atIngestOneOf(
                                                    INVALID_ACCOUNT_ID, INVALID_TRANSFER_ACCOUNT_ID)),
                                    entry(
                                            "proto.NftTransfer.senderAccountID",
                                            ExpectedResponse.atIngest(INVALID_TRANSFER_ACCOUNT_ID)),
                                    entry(
                                            "proto.NftTransfer.receiverAccountID",
                                            ExpectedResponse.atIngest(INVALID_TRANSFER_ACCOUNT_ID)),
                                    entry(
                                            "proto.TokenTransferList.token",
                                            ExpectedResponse.atIngest(INVALID_TOKEN_ID)))),
                    entry(
                            CryptoUpdate,
                            Map.ofEntries(
                                    entry(
                                            "proto.CryptoUpdateTransactionBody.accountIDToUpdate",
                                            ExpectedResponse.atIngest(ACCOUNT_ID_DOES_NOT_EXIST)),
                                    entry(
                                            "proto.CryptoUpdateTransactionBody.staked_account_id",
                                            ExpectedResponse.atConsensus(SUCCESS)))),
                    entry(
                            ContractCall,
                            Map.ofEntries(entry(
                                    "proto.ContractCallTransactionBody.contractID",
                                    ExpectedResponse.atIngest(INVALID_CONTRACT_ID)))),
                    entry(
                            CryptoDelete,
                            Map.ofEntries(
                                    entry(
                                            "proto.CryptoDeleteTransactionBody.transferAccountID",
                                            ExpectedResponse.atIngest(ACCOUNT_ID_DOES_NOT_EXIST)),
                                    entry(
                                            "proto.CryptoDeleteTransactionBody.deleteAccountID",
                                            ExpectedResponse.atIngest(ACCOUNT_ID_DOES_NOT_EXIST)))));

    private static ExpectedResponse responseFor(
            @NonNull final HederaFunctionality functionality, @NonNull final String fieldName) {
        final var specificResponse = SPECIFIC_CLEARED_ID_RESPONSES
                .getOrDefault(functionality, emptyMap())
                .get(fieldName);
        if (specificResponse != null) {
            return specificResponse;
        } else {
            return GENERIC_CLEARED_ID_RESPONSES.get(fieldName);
        }
    }

    public static Function<Transaction, List<Modification>> withSuccessivelyVariedIds() {
        return transaction -> {
            final TransactionBody body;
            final HederaFunctionality functionality;
            try {
                body = extractTransactionBody(transaction);
                functionality = functionOf(body);
            } catch (InvalidProtocolBufferException | UnknownHederaFunctionality e) {
                throw new IllegalArgumentException(e);
            }
            final var targetFields = getTargetFields(body.toBuilder(), ModUtils::isClearableId);
            final Map<String, AtomicInteger> occurrenceCounts = new HashMap<>();
            return targetFields.stream()
                    .map(field -> {
                        final var encounterIndex = occurrenceCounts
                                .computeIfAbsent(field.getFullName(), k -> new AtomicInteger(0))
                                .getAndIncrement();
                        final var expectedResponse = responseFor(functionality, field.getFullName());
                        requireNonNull(
                                expectedResponse,
                                "No expected response for field " + field.getFullName() + " in " + functionality);
                        return new Modification(
                                "Clearing field " + field.getFullName() + " (#" + encounterIndex + ") in "
                                        + functionality,
                                specAgnosticMod(b -> withClearedField(b, field, encounterIndex)),
                                expectedResponse);
                    })
                    .toList();
        };
    }

    private static boolean isClearableId(
            @NonNull final Descriptors.FieldDescriptor field, @NonNull final Object value) {
        return value instanceof AccountID
                || value instanceof ContractID
                || value instanceof FileID
                || value instanceof TokenID
                || value instanceof TopicID;
    }

    private static BodyModification specAgnosticMod(UnaryOperator<TransactionBody> operator) {
        return (builder, spec) -> {
            final var body = builder.build();
            final var modifiedBody = operator.apply(body);
            return modifiedBody.toBuilder();
        };
    }

    @SuppressWarnings("unchecked")
    private static <T extends GeneratedMessageV3> T withClearedField(
            @NonNull final T message, @NonNull final Descriptors.FieldDescriptor descriptor, final int targetIndex) {
        final var currentIndex = new AtomicInteger(0);
        return withClearedField(message, descriptor, targetIndex, currentIndex);
    }

    private static <T extends GeneratedMessageV3> T withClearedField(
            @NonNull final T message,
            @NonNull final Descriptors.FieldDescriptor descriptor,
            final int targetIndex,
            @NonNull final AtomicInteger currentIndex) {
        final var builder = message.toBuilder();
        if (descriptor.getContainingType().equals(builder.getDescriptorForType())) {
            final var value = builder.getField(descriptor);
            if (value instanceof List<?> list) {
                final var clearedList = list.stream()
                        .filter(subValue -> currentIndex.getAndIncrement() != targetIndex)
                        .toList();
                builder.setField(descriptor, clearedList);
            } else {
                builder.clearField(descriptor);
            }
        } else {
            builder.getAllFields().forEach((field, value) -> {
                if (value instanceof GeneratedMessageV3 subMessage) {
                    builder.setField(field, withClearedField(subMessage, descriptor, targetIndex, currentIndex));
                } else if (value instanceof List<?> list) {
                    final var clearedList = list.stream()
                            .map(item -> (item instanceof GeneratedMessageV3 subMessageItem)
                                    ? withClearedField(subMessageItem, descriptor, targetIndex, currentIndex)
                                    : item)
                            .toList();
                    builder.setField(field, clearedList);
                }
            });
        }
        return (T) builder.build();
    }

    private static List<Descriptors.FieldDescriptor> getTargetFields(
            @NonNull final Message.Builder builder,
            @NonNull final BiPredicate<Descriptors.FieldDescriptor, Object> filter) {
        final List<Descriptors.FieldDescriptor> descriptors = new ArrayList<>();
        accumulateFields(builder, descriptors, filter);
        System.out.println("Descriptors: " + descriptors);
        return descriptors;
    }

    private static void accumulateFields(
            @NonNull final Message.Builder builder,
            @NonNull final List<Descriptors.FieldDescriptor> descriptors,
            @NonNull final BiPredicate<Descriptors.FieldDescriptor, Object> filter) {
        builder.getAllFields().forEach((field, value) -> {
            if (filter.test(field, value)) {
                descriptors.add(field);
            } else if (value instanceof Message message) {
                accumulateFields(message.toBuilder(), descriptors, filter);
            } else if (value instanceof List<?> list) {
                list.forEach(subValue -> {
                    if (filter.test(field, subValue)) {
                        descriptors.add(field);
                    } else if (subValue instanceof Message subMessage) {
                        accumulateFields(subMessage.toBuilder(), descriptors, filter);
                    }
                });
            }
        });
    }
}
