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

import com.google.protobuf.Descriptors;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A strategy for determining whether a field is one of the modification strategy's
 * target; and if so, how it should be modified.
 *
 * @param <T> the type of modifications used by the strategy
 */
public interface ModificationStrategy<T> {
    /**
     * Returns whether the given field should be modified.
     *
     * @param fieldDescriptor the field descriptor
     * @param value the value of the field
     * @return whether the field should be modified
     */
    boolean hasTarget(@NonNull Descriptors.FieldDescriptor fieldDescriptor, @NonNull Object value);

    /**
     * Returns a modification for the given field.
     *
     * @param targetField the field descriptor
     * @param encounterIndex the index of the encounter
     * @return the modification
     */
    @NonNull
    T modificationForTarget(@NonNull TargetField targetField, int encounterIndex);
}
