/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.transactions.lambda;

import static com.hedera.hapi.node.lambda.LambdaType.TRANSFER_ALLOWANCE;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.noOp;
import static com.hedera.services.bdd.suites.contract.Utils.lambdaBytecodeFromResources;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.lambda.LambdaChargingPattern;
import com.hedera.hapi.node.lambda.LambdaExplicitInit;
import com.hedera.hapi.node.lambda.LambdaInstallation;
import com.hedera.hapi.node.lambda.LambdaStorageSlot;
import com.hedera.hapi.node.lambda.LambdaType;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.spec.SpecOperation;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.function.Supplier;

public class LambdaInstaller {
    private static final long NO_EXPLICIT_INDEX = -1L;
    private static final long NO_DEFAULT_GAS_LIMIT = -1L;
    private static final LambdaChargingPattern DEFAULT_CHARGING_PATTERN = LambdaChargingPattern.CALLER_PAYS;

    private record ExplicitInitSource(
            @Nullable String lambda,
            @Nullable Bytes bytecode,
            @NonNull Supplier<List<LambdaStorageSlot>> slotsSupplier) {
        public LambdaExplicitInit op() {
            final var builder = LambdaExplicitInit.newBuilder().storageSlots(slotsSupplier.get());
            if (bytecode != null) {
                builder.bytecode(bytecode);
            } else {
                requireNonNull(lambda);
                builder.bytecode(lambdaBytecodeFromResources(lambda));
            }
            return builder.build();
        }
    }

    private record InitcodeSource(
            @Nullable String initcodeResource,
            @Nullable Bytes initcode,
            long gasLimit,
            @NonNull UploadMethod uploadMethod,
            @NonNull Object... args) {
        enum UploadMethod {
            INLINE,
            FILE
        }
    }

    @Nullable
    private final ExplicitInitSource explicitInitSource;

    @Nullable
    private final InitcodeSource initcodeSource;

    private final LambdaType type;
    private final LambdaChargingPattern chargingPattern;
    private final long defaultGasLimit;

    private long index;

    public static LambdaInstaller lambdaBytecode(@NonNull final String bytecodeResource) {
        return new LambdaInstaller(
                new ExplicitInitSource(bytecodeResource, null, List::of),
                null,
                TRANSFER_ALLOWANCE,
                DEFAULT_CHARGING_PATTERN,
                NO_DEFAULT_GAS_LIMIT,
                NO_EXPLICIT_INDEX);
    }

    public LambdaInstaller atIndex(final long index) {
        this.index = index;
        return this;
    }

    private LambdaInstaller(
            @Nullable final ExplicitInitSource explicitInitSource,
            @Nullable final InitcodeSource initcodeSource,
            @NonNull final LambdaType type,
            @NonNull final LambdaChargingPattern chargingPattern,
            final long defaultGasLimit,
            final long index) {
        this.explicitInitSource = explicitInitSource;
        this.initcodeSource = initcodeSource;
        this.type = type;
        this.chargingPattern = chargingPattern;
        this.defaultGasLimit = defaultGasLimit;
        this.index = index;
    }

    public SpecOperation specSetupOp() {
        return noOp();
    }

    public LambdaInstallation op() {
        final var builder = LambdaInstallation.newBuilder().type(type).chargingPattern(chargingPattern);
        if (defaultGasLimit != NO_DEFAULT_GAS_LIMIT) {
            builder.defaultGasLimit(defaultGasLimit);
        }
        if (index != NO_EXPLICIT_INDEX) {
            builder.index(index);
        }
        if (explicitInitSource != null) {
            builder.explicitInit(explicitInitSource.op());
        } else {
            throw new AssertionError("Not implemented");
        }
        return builder.build();
    }
}
