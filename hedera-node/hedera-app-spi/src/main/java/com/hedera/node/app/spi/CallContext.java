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
package com.hedera.node.app.spi;

import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

/** The {@code CallContext} provides access to all services without leaking the underlying state. */
public interface CallContext {

    /**
     * Dispatch a pre-handle request. The transaction is forwarded to the correct handler, which
     * takes care of the specific functionality
     *
     * @param transactionBody the {@link TransactionBody} of the request
     * @param payer the {@link AccountID} of the payer
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @NonNull
    TransactionMetadata preHandle(
            @NonNull final TransactionBody transactionBody, @NonNull AccountID payer);
}
