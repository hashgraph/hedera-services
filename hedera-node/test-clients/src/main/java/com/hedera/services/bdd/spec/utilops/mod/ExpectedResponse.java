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

import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.EnumSet;
import java.util.Set;

public record ExpectedResponse(
        @Nullable Set<ResponseCodeEnum> permissiblePrechecks, @Nullable Set<ResponseCodeEnum> permissibleOutcomes) {

    public static ExpectedResponse atIngest(@NonNull final ResponseCodeEnum status) {
        return new ExpectedResponse(EnumSet.of(status), null);
    }

    public static ExpectedResponse atConsensus(@NonNull final ResponseCodeEnum status) {
        return new ExpectedResponse(null, EnumSet.of(status));
    }

    public void customize(@NonNull final HapiTxnOp<?> op) {
        if (permissiblePrechecks != null) {
            op.hasPrecheckFrom(permissiblePrechecks.toArray(ResponseCodeEnum[]::new));
        } else {
            requireNonNull(permissibleOutcomes);
            op.hasKnownStatusFrom(permissibleOutcomes.toArray(ResponseCodeEnum[]::new));
        }
    }
}
