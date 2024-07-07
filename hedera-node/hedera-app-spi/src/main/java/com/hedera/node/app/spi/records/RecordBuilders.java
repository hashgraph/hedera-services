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

package com.hedera.node.app.spi.records;

import com.hedera.node.app.spi.workflows.HandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;

public interface RecordBuilders {

    /**
     * Returns a record builder for the given record builder subtype.
     *
     * @param recordBuilderClass the record type
     * @param <T> the record type
     * @return a builder for the given record type
     * @throws NullPointerException if {@code recordBuilderClass} is {@code null}
     * @throws IllegalArgumentException if the record builder type is unknown to the app
     */
    @NonNull
    <T> T getOrCreate(@NonNull Class<T> recordBuilderClass);

    /**
     * Adds a child record builder to the list of record builders. If the current {@link HandleContext} (or any parent
     * context) is rolled back, all child record builders will be reverted.
     *
     * @param recordBuilderClass the record type
     * @return the new child record builder
     * @param <T> the record type
     * @throws NullPointerException if {@code recordBuilderClass} is {@code null}
     * @throws IllegalArgumentException if the record builder type is unknown to the app
     */
    @NonNull
    <T> T addChildRecordBuilder(@NonNull Class<T> recordBuilderClass);

    /**
     * Adds a removable child record builder to the list of record builders. Unlike a regular child record builder,
     * a removable child record builder is removed, if the current {@link HandleContext} (or any parent context) is
     * rolled back.
     *
     * @param recordBuilderClass the record type
     * @return the new child record builder
     * @param <T> the record type
     * @throws NullPointerException if {@code recordBuilderClass} is {@code null}
     * @throws IllegalArgumentException if the record builder type is unknown to the app
     */
    @NonNull
    <T> T addRemovableChildRecordBuilder(@NonNull Class<T> recordBuilderClass);
}
