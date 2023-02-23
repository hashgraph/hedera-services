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

package com.hedera.node.app.service.util.impl;

import com.hedera.node.app.service.util.UtilService;
import com.hedera.node.app.service.util.impl.handlers.UtilPrngHandler;
import com.hedera.node.app.spi.service.Service;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/** Standard implementation of the {@link UtilService} {@link Service}. */
public final class UtilServiceImpl implements UtilService {

    private final UtilPrngHandler prngHandler;

    /**
     * Creates a new {@link UtilServiceImpl} instance.
     */
    public UtilServiceImpl() {
        prngHandler = new UtilPrngHandler();
    }

    /**
     * Returns the {@link UtilPrngHandler} instance.
     *
     * @return the {@link UtilPrngHandler} instance.
     */
    @NonNull
    public UtilPrngHandler getPrngHandler() {
        return prngHandler;
    }

    @NonNull
    @Override
    public Set<TransactionHandler> getTransactionHandler() {
        return Set.of(prngHandler);
    }
}
