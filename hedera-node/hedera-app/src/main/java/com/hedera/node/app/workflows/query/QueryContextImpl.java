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

package com.hedera.node.app.workflows.query;

import com.hedera.node.app.service.mono.config.NetworkInfo;
import com.hedera.node.app.spi.meta.QueryContext;
import com.hedera.pbj.runtime.io.Bytes;
import javax.inject.Inject;

/**
 * Provides context for query processing.Currently, it only has {@link NetworkInfo} but it might be
 * extended to provide more context for other queries in the future.
 */
public class QueryContextImpl implements QueryContext {
    private final NetworkInfo networkInfo;

    @Inject
    public QueryContextImpl(final NetworkInfo networkInfo) {
        this.networkInfo = networkInfo;
    }

    @Override
    public Bytes getLedgerId() {
        return Bytes.wrap(networkInfo.ledgerId().toByteArray());
    }
}
