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

import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
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

public class ModificationUtils {
    private ModificationUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static Function<Transaction, List<Modification>> withSuccessivelyVariedIds() {
        return withStrategies(List.of(new IdClearingStrategy()));
    }

    public static Function<Transaction, List<Modification>> withStrategies(
            @NonNull final List<ModificationStrategy> strategies) {
        return transaction -> {
            final TransactionBody body;
            try {
                body = extractTransactionBody(transaction);
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalArgumentException(e);
            }
            final List<Modification> modifications = new ArrayList<>();
            for (final var strategy : strategies) {
                final var targetFields = getTargetFields(body.toBuilder(), strategy::hasTarget);
                final Map<String, AtomicInteger> occurrenceCounts = new HashMap<>();
                modifications.addAll(targetFields.stream()
                        .map(field -> {
                            final var encounterIndex = occurrenceCounts
                                    .computeIfAbsent(field.getFullName(), k -> new AtomicInteger(0))
                                    .getAndIncrement();
                            return strategy.modificationForTarget(field, encounterIndex);
                        })
                        .toList());
            }
            return modifications;
        };
    }

    public static BodyModification specAgnosticMod(@NonNull final UnaryOperator<TransactionBody> operator) {
        return (builder, spec) -> {
            final var body = builder.build();
            final var modifiedBody = operator.apply(body);
            return modifiedBody.toBuilder();
        };
    }

    public static <T extends GeneratedMessageV3> T withClearedField(
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
