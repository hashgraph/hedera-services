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

import static java.util.Arrays.asList;

import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.EnumSet;
import java.util.Set;

/**
 * Encapsulates the expected answer to a query; if the {@link ResponseType#COST_ANSWER}
 * status is left null, it is assumed to be {@link ResponseCodeEnum#OK}.
 *
 * @param costAnswerStatus a failure status if the COST_ANSWER query should fail
 * @param answerOnlyStatus a failure status if just the ANSWER_ONLY query should fail
 */
public record ExpectedAnswer(
        @Nullable Set<ResponseCodeEnum> costAnswerStatus, @Nullable Set<ResponseCodeEnum> answerOnlyStatus) {
    public static ExpectedAnswer onCostAnswer(@NonNull ResponseCodeEnum... statuses) {
        return new ExpectedAnswer(EnumSet.copyOf(asList(statuses)), null);
    }

    public static ExpectedAnswer onAnswerOnly(@NonNull ResponseCodeEnum... statuses) {
        return new ExpectedAnswer(null, EnumSet.copyOf(asList(statuses)));
    }

    public void customize(@NonNull final HapiQueryOp<?> op) {
        if (costAnswerStatus != null) {
            op.hasCostAnswerPrecheckFrom(costAnswerStatus.toArray(ResponseCodeEnum[]::new));
        }
        if (answerOnlyStatus != null) {
            op.hasAnswerOnlyPrecheckFrom(answerOnlyStatus.toArray(ResponseCodeEnum[]::new));
        }
    }
}
