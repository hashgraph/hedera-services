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

package com.hedera.services.bdd.spec.utilops;

import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.TargetNetworkType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * A {@link UtilOp} that runs a {@link HapiSpecOperation} if the {@link HapiSpec} is configured to run on a
 * {@link TargetNetworkType} that is contained in the {@link Set} of {@link TargetNetworkType} provided to the
 * constructor.
 */
public class EnvFilterOp extends UtilOp {
    public enum EnvType {
        CI,
        NOT_CI
    }

    private final EnvType allowedEnvType;

    private final HapiSpecOperation[] ops;

    public EnvFilterOp(@NonNull final EnvType allowedEnvType, @NonNull final HapiSpecOperation[] ops) {
        this.allowedEnvType = requireNonNull(allowedEnvType);
        this.ops = requireNonNull(ops);
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        requireNonNull(spec);
        final var inCi = System.getenv("CI") != null;
        final var targetsCi = allowedEnvType == EnvType.CI;
        if (inCi == targetsCi) {
            allRunFor(spec, ops);
        }
        return false;
    }

    @Override
    public String toString() {
        return "EnvFilterOp{allowed=" + allowedEnvType + "}";
    }
}
