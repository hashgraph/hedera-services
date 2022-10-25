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
package com.hedera.node.app.service.file;

import com.hedera.node.app.spi.PreTransactionHandler;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.TransactionBody;

public interface FilePreTransactionHandler extends PreTransactionHandler {
    /** Creates a file */
    TransactionMetadata preHandleCreateFile(TransactionBody txn);

    /** Updates a file */
    TransactionMetadata preHandleUpdateFile(TransactionBody txn);

    /** Deletes a file */
    TransactionMetadata preHandleDeleteFile(TransactionBody txn);

    /** Appends to a file */
    TransactionMetadata preHandleAppendContent(TransactionBody txn);

    /** Deletes a file if the submitting account has network admin privileges */
    TransactionMetadata preHandleSystemDelete(TransactionBody txn);

    /** Undeletes a file if the submitting account has network admin privileges */
    TransactionMetadata preHandleSystemUndelete(TransactionBody txn);
}
