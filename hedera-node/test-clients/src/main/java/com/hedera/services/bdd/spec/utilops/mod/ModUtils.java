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
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static java.util.Collections.emptyMap;
import static java.util.Map.entry;
import static java.util.Objects.requireNonNull;

import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.hedera.node.app.hapi.utils.exception.UnknownHederaFunctionality;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class ModUtils {
    private ModUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    private static Map<String, ExpectedResponse> GENERIC_MISSING_ACCOUNT_ID_RESPONSES = Map.ofEntries(
            entry("proto.TransactionID.accountID", ExpectedResponse.atIngest(PAYER_ACCOUNT_NOT_FOUND)),
            entry("proto.TransactionBody.nodeAccountID", ExpectedResponse.atIngest(INVALID_NODE_ACCOUNT)));

    private static Map<HederaFunctionality, Map<String, ExpectedResponse>> SPECIFIC_MISSING_ACCOUNT_ID_RESPONSES =
            Map.ofEntries(entry(
                    TokenAssociateToAccount,
                    Map.ofEntries(
                            // (FUTURE) Only enforce at consensus
                            entry(
                                    "proto.TokenAssociateTransactionBody.account",
                                    ExpectedResponse.atIngest(INVALID_ACCOUNT_ID)))));

    private static ExpectedResponse responseFor(
            @NonNull final HederaFunctionality functionality, @NonNull final String fieldName) {
        final var specificResponse = SPECIFIC_MISSING_ACCOUNT_ID_RESPONSES
                .getOrDefault(functionality, emptyMap())
                .get(fieldName);
        if (specificResponse != null) {
            return specificResponse;
        } else {
            return GENERIC_MISSING_ACCOUNT_ID_RESPONSES.get(fieldName);
        }
    }

    public static Function<Transaction, List<Modification>> withSuccessivelyClearedAccountIds() {
        return transaction -> {
            final TransactionBody body;
            final HederaFunctionality functionality;
            try {
                body = extractTransactionBody(transaction);
                functionality = functionOf(body);
            } catch (InvalidProtocolBufferException | UnknownHederaFunctionality e) {
                throw new IllegalArgumentException(e);
            }
            final var targetFields = getTargetFields(body.toBuilder(), (field, value) -> value instanceof AccountID);
            return targetFields.stream()
                    .map(field -> {
                        final var expectedResponse = responseFor(functionality, field.getFullName());
                        requireNonNull(
                                expectedResponse,
                                "No expected response for field " + field.getFullName() + " in " + functionality);
                        return new Modification(
                                "Clearing field " + field.getName() + " in " + functionality,
                                specAgnosticMod(b -> withClearedField(b, field)),
                                expectedResponse);
                    })
                    .toList();
        };
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
            @NonNull final T message, @NonNull final Descriptors.FieldDescriptor descriptor) {
        final var builder = message.toBuilder();
        if (descriptor.getContainingType().equals(builder.getDescriptorForType()) && builder.hasField(descriptor)) {
            builder.clearField(descriptor);
        } else {
            builder.getAllFields().forEach((field, value) -> {
                if (value instanceof GeneratedMessageV3 subMessage) {
                    builder.setField(field, withClearedField(subMessage, descriptor));
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
            }
        });
    }
}
