/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.throttle;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A modular implementation of the {@link ThrottleAccumulator}. TBD.
 */
@Singleton
public class ThrottleAccumulatorImpl implements ThrottleAccumulator {

    @Inject
    public ThrottleAccumulatorImpl() {
        // Needed for dagger DI
    }

    @Override
    public boolean shouldThrottle(@NonNull TransactionBody txn) {
        return false;
    }

    @Override
    public boolean shouldThrottleQuery(@NonNull HederaFunctionality functionality, @NonNull Query query) {
        return false;
    }
}
