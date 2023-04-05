/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.info;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Implementation of {@link NodeInfo} that delegates to the mono-service.
 */
public class MonoNodeInfo implements NodeInfo {

    private final com.hedera.node.app.service.mono.context.NodeInfo delegate;

    /**
     * Constructs a {@link MonoNodeInfo} with the given delegate.
     *
     * @param delegate the delegate
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    public MonoNodeInfo(@NonNull com.hedera.node.app.service.mono.context.NodeInfo delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public boolean isSelfZeroStake() {
        return delegate.isSelfZeroStake();
    }
}
