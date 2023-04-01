/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.file.impl.handlers;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.file.FileUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import static java.util.Objects.requireNonNull;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#FILE_UPDATE}.
 */
@Singleton
public class FileUpdateHandler implements TransactionHandler {
    @Inject
    public FileUpdateHandler() {
        // Exists for injection
    }

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Typically, this method validates the {@link TransactionBody} semantically, gathers all
     * required keys, warms the cache.
     *
     * @param context the {@link PreHandleContext} which collects all information that will be
     *     passed to {@link #handle(FileUpdateTransactionBody)}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(@NonNull final PreHandleContext context) {
        requireNonNull(context);
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * @param tx the transaction to handle
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull final FileUpdateTransactionBody tx) {
        requireNonNull(tx);
        throw new UnsupportedOperationException("Not implemented");
    }
}
