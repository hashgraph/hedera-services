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

import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withClearedField;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static java.util.Objects.requireNonNull;

import com.google.protobuf.Descriptors;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

public class QueryIdClearingStrategy extends IdClearingStrategy<QueryModification>
        implements QueryModificationStrategy {
    private static final Map<String, ExpectedAnswer> CLEARED_ID_ANSWERS = Map.ofEntries(
            Map.entry("proto.ContractGetInfoQuery.contractID", ExpectedAnswer.onAnswerOnly(INVALID_CONTRACT_ID)));

    @NonNull
    @Override
    public QueryModification modificationForTarget(
            @NonNull final Descriptors.FieldDescriptor descriptor, final int encounterIndex) {
        final var expectedAnswer = CLEARED_ID_ANSWERS.get(descriptor.getFullName());
        requireNonNull(expectedAnswer, "No expected answer for field " + descriptor.getFullName());
        return new QueryModification(
                "Clearing field " + descriptor.getFullName() + " (#" + encounterIndex + ")",
                QueryMutation.withTransform(q -> withClearedField(q, descriptor, encounterIndex)),
                expectedAnswer);
    }
}
