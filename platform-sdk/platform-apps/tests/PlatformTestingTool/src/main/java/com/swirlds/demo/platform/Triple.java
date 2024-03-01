/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A simple triple of objects.
 *
 * @param left value for the left object
 * @param middle value for the middle object
 * @param right value for the right object
 * @param <L> type of the left object
 * @param <M> type of the middle object
 * @param <R> type of the right object
 * @deprecated Please consider using own data structure instead of this class.
 *             To have more meaningful names than left, middle and right
 */
@Deprecated(forRemoval = true)
public record Triple<L, M, R>(L left, M middle, R right) {

    /**
     * Creates a new triple of objects.
     *
     * @param left value for the left object
     * @param middle value for the middle object
     * @param right value for the right object
     * @param <L> type of the left object
     * @param <M> type of the middle object
     * @param <R> type of the right object
     *
     * @return a new triple of objects
     * @deprecated Please consider using own data structure instead of this class.
     */
    @NonNull
    @Deprecated(forRemoval = true)
    public static <L, M, R> Triple<L, M, R> of(@Nullable L left, @Nullable M middle, @Nullable R right) {
        return new Triple<>(left, middle, right);
    }
}
