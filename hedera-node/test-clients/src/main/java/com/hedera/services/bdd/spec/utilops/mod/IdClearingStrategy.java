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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Implementation support for a {@link ModificationStrategy} that clears
 * entity ids from a target message.
 *
 * @param <T> the type of modification used by the inheriting strategy
 */
public abstract class IdClearingStrategy<T> implements ModificationStrategy<T> {
    @Override
    public boolean hasTarget(@NonNull Descriptors.FieldDescriptor fieldDescriptor, @NonNull Object value) {
        return value instanceof AccountID
                || value instanceof ContractID
                || value instanceof ScheduleID
                || value instanceof FileID
                || value instanceof TokenID
                || value instanceof TopicID;
    }
}
