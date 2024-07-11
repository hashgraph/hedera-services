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

package com.hedera.node.app.workflows.handle.stack;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * The {@link BuilderSink} supports accumulating stream item builders that are either preceding or
 * following an implicit "base builder". It supports traversing and coalescing its builders.
 * <p>
 * Two items are worth noting, as follows:
 * <ul>
 *     <li>Client code is free to designate any builder as the base builder; this class is agnostic
 *     about the base builder's identity.</li>
 *     <li>This class interprets each added preceding builder as <b>coming before</b> all builders
 *     already added. This ensures that {@link TransactionCategory#PRECEDING} dispatches of child
 *     dispatches which are themselves {@link TransactionCategory#PRECEDING} end up in the correct
 *     order.</li>
 * </ul>
 */
public class BuilderSink {
    protected final List<SingleTransactionRecordBuilder> precedingBuilders = new ArrayList<>();
    protected final List<SingleTransactionRecordBuilder> followingBuilders = new ArrayList<>();

    /**
     * Returns all the builders accumulated in this sink, with the preceding builders appearing in the
     * reverse order they were added.
     * @return all accumulated builders
     */
    public List<SingleTransactionRecordBuilder> allBuilders() {
        if (precedingBuilders.isEmpty()) {
            return followingBuilders;
        } else {
            final List<SingleTransactionRecordBuilder> allBuilders = new ArrayList<>(precedingBuilders);
            Collections.reverse(allBuilders);
            allBuilders.addAll(followingBuilders);
            return allBuilders;
        }
    }

    /**
     * Returns whether this sink has accumulated any builders other than the designated base builder.
     * @param baseBuilder the base builder
     * @return whether this sink has any builders other than the base builder
     */
    public boolean hasOther(@NonNull final SingleTransactionRecordBuilder baseBuilder) {
        requireNonNull(baseBuilder);
        for (final var builder : precedingBuilders) {
            if (builder != baseBuilder) {
                return true;
            }
        }
        for (final var builder : followingBuilders) {
            if (builder != baseBuilder) {
                return true;
            }
        }
        return false;
    }

    /**
     * For each builder in this sink other than the designated base builder, invokes the given consumer
     * with the builder cast to the given type.
     *
     * @param consumer the consumer to invoke
     * @param builderType the type to cast the builders to
     * @param baseBuilder the base builder
     * @param <T> the type to cast the builders to
     */
    public <T> void forEachOther(
            @NonNull final Consumer<T> consumer,
            @NonNull final Class<T> builderType,
            @NonNull final SingleTransactionRecordBuilder baseBuilder) {
        requireNonNull(builderType);
        requireNonNull(consumer);
        requireNonNull(baseBuilder);
        for (final var builder : followingBuilders) {
            if (builder != baseBuilder) {
                consumer.accept(builderType.cast(builder));
            }
        }
        for (final var builder : precedingBuilders) {
            if (builder != baseBuilder) {
                consumer.accept(builderType.cast(builder));
            }
        }
    }

    /**
     * Returns the total number of builders in the sink.
     * @return the total number of builders in the sink
     */
    public int numBuilders() {
        return precedingBuilders.size() + followingBuilders.size();
    }
}
