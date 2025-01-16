/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.SigWaivers;

/**
 * Signature waivers needed for transactions in {@link FileService}.
 * */
public interface FileSignatureWaivers extends SigWaivers {
    /**
     * Advises if the target file's key must sign a given file update.
     *
     * @param fileUpdateTxn a file update transaction
     * @return whether the target account's key must sign
     */
    boolean areFileUpdateSignaturesWaived(TransactionBody fileUpdateTxn, AccountID payer);

    /**
     * Advises if the target file's key must sign a given file append.
     *
     * @param fileAppendTxn a file append transaction
     * @return whether the target account's key must sign
     */
    boolean areFileAppendSignaturesWaived(TransactionBody fileAppendTxn, AccountID payer);
}
