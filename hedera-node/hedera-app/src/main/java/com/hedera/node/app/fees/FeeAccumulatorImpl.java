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

package com.hedera.node.app.fees;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * To be implemented: An implementation of {@link FeeAccumulator}.
 */
@Singleton
public class FeeAccumulatorImpl implements FeeAccumulator {

    @Inject
    public FeeAccumulatorImpl() {
        // For dagger
    }

    @NonNull
    @Override
    public FeeObject computePayment(
            @NonNull ReadableStoreFactory readableStoreFactory,
            @NonNull HederaFunctionality functionality,
            @NonNull Query query,
            @NonNull Timestamp now) {
        return new FeeObject(0, 0, 0);
    }
}
