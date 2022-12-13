/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.workflows;

import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

/** A {@code TransactionHandler} contains all methods for the different stages of a single operation. */
public interface TransactionHandler {

    /**
     * This method is called during the ingest-workflow. It does pre-checks that are specific to the operation.
     *
     * @param txBody the {@link TransactionBody} that needs to be validated
     * @throws NullPointerException if {@code txBody} is {@code null}
     * @throws PreCheckException if validation fails
     */
    void preCheck(@NonNull TransactionBody txBody) throws PreCheckException;

}
