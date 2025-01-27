/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Set;

/**
 * Represents the context of a single {@code preHandle()}-call.
 *
 * <p>During pre-handle, each transaction handler needs access to the transaction body data (i.e. the "operation"
 * being performed, colloquially also called the "transaction" and "transaction body" although both are more
 * or less technically incorrect). The actual {@link TransactionBody} can be accessed from this context. The body
 * contains the operation, the transaction ID, the originating node, and other information.
 *
 * <p>The main responsibility for a transaction handler during pre-handle is to semantically validate the operation
 * and to gather all required keys. The handler, when created, is preloaded with the correct payer key (which is
 * almost always the same as the transaction body's {@link TransactionID}, except in the case of a scheduled
 * transaction). {@link TransactionHandler}s must add any additional required signing keys. Several convenience
 * methods have been created for this purpose.
 *
 * <p>{@link #requireKey(Key)} is used to add a required non-payer signing key (remember, the payer signing
 * key was added when the context was created). Some basic validation is performed (the key cannot be null or empty).
 */
@SuppressWarnings("UnusedReturnValue")
public interface PureChecksContext {

    /**
     * Gets the {@link TransactionBody}
     *
     * @return the {@link TransactionBody} in this context
     */
    @NonNull
    TransactionBody body();

    /**
     * Returns the current {@link Configuration}.
     *
     * @return the {@link Configuration}
     */
    @NonNull
    Configuration configuration();


    @NonNull
    void pureChecks(@NonNull TransactionBody body)
            throws PreCheckException;

    /**
     * Returns the TransactionBogy from the given transaction.
     * @return the TransactionBogy
     */
    @Nullable
    TransactionBody bodyFromTransaction(@NonNull final Transaction tx) throws PreCheckException;

}
