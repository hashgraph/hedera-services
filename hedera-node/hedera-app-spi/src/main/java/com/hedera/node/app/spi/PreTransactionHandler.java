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
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * Marks a type able to "pre-handle" transactions, extracting the metadata needed to set the stage
 * for efficient processing of the transaction at consensus.
 */
public interface PreTransactionHandler {
    /**
     * pre-handles a transaction given the type of transaction based on the {@link
     * HederaFunctionality} function. This is a generic way to delegate to different service
     * pre-transaction handlers for schedule transactions. Payer for the transaction can be easily
     * found from {@link com.hederahashgraph.api.proto.java.TransactionID}, but payer is explicitly
     * given as an input because schedule transactions can have a custom payer in top level
     * transaction.
     *
     * @param tx given transaction
     * @param payer payer for the transaction
     * @return metadata after pre-handling the transaction
     */
    TransactionMetadata preHandle(final TransactionBody tx, AccountID payer);
}
