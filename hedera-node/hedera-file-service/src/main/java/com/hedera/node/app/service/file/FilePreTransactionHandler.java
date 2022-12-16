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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * The pre-handler for the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/file_service.proto">File
 * Service</a>.
 */
public interface FilePreTransactionHandler extends PreTransactionHandler {
    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#FileCreate}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.FileCreateTransactionBody}
     * @param payer payer of the transaction
     * @return the metadata for the file creation
     */
    TransactionMetadata preHandleCreateFile(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#FileUpdate}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.FileUpdateTransactionBody}
     * @param payer payer of the transaction
     * @return the metadata for the file update
     */
    TransactionMetadata preHandleUpdateFile(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#FileDelete}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.FileDeleteTransactionBody}
     * @param payer payer of the transaction
     * @return the metadata for the file deletion
     */
    TransactionMetadata preHandleDeleteFile(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#FileAppend}
     * transaction, returning the metadata required to, at minimum, validate the signatures of all
     * required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.FileAppendTransactionBody}
     * @param payer payer of the transaction
     * @return the metadata for the file append
     */
    TransactionMetadata preHandleAppendContent(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#SystemDelete}
     * transaction that targets a file, returning the metadata required to, at minimum, validate the
     * signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody} targeting a file
     * @param payer payer of the transaction
     * @return the metadata for the system file deletion
     */
    TransactionMetadata preHandleSystemDelete(TransactionBody txn, AccountID payer);

    /**
     * Pre-handles a {@link com.hederahashgraph.api.proto.java.HederaFunctionality#SystemUndelete}
     * transaction that targets a file, returning the metadata required to, at minimum, validate the
     * signatures of all required signing keys.
     *
     * @param txn a transaction with a {@link
     *     com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody} targeting a file
     * @param payer payer of the transaction
     * @return the metadata for the system file un-deletion
     */
    TransactionMetadata preHandleSystemUndelete(TransactionBody txn, AccountID payer);
}
