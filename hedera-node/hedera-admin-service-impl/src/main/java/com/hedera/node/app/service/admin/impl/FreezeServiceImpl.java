/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.admin.impl;

import com.hedera.node.app.service.admin.FreezeService;
import com.hedera.node.app.service.admin.impl.handlers.FreezeHandler;
import com.hedera.node.app.spi.service.Service;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/** Standard implementation of the {@link FreezeService} {@link Service}. */
public final class FreezeServiceImpl implements FreezeService {

    private final FreezeHandler freezeHandler;

    /**
     * Creates a new {@link FreezeServiceImpl} instance.
     */
    public FreezeServiceImpl() {
        this.freezeHandler = new FreezeHandler();
    }

    /**
     * Returns the {@link FreezeHandler} instance.
     *
     * @return the {@link FreezeHandler} instance.
     */
    @NonNull
    public FreezeHandler getFreezeHandler() {
        return freezeHandler;
    }

    @NonNull
    @Override
    public Set<TransactionHandler> getTransactionHandler() {
        return Set.of(freezeHandler);
    }

}
