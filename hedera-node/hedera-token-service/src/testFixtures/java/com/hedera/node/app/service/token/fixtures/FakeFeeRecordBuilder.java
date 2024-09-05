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

package com.hedera.node.app.service.token.fixtures;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.service.token.api.FeeStreamBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A fake implementation of {@link FeeStreamBuilder} for testing purposes.
 */
public class FakeFeeRecordBuilder implements FeeStreamBuilder {
    private long transactionFee;

    public FakeFeeRecordBuilder() {
        // Just something to keep checkModuleInfo from claiming we don't require com.hedera.node.hapi
        requireNonNull(SemanticVersion.class);
    }

    @Override
    public long transactionFee() {
        return transactionFee;
    }

    @Override
    @NonNull
    public FeeStreamBuilder transactionFee(long transactionFee) {
        this.transactionFee = transactionFee;
        return this;
    }
}
