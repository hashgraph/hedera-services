/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.test.factories.txns;

import static com.hedera.test.utils.IdUtils.asFile;

import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

public class FileDeleteFactory extends SignedTxnFactory<FileDeleteFactory> {
    private final String file;

    public FileDeleteFactory(String file) {
        this.file = file;
    }

    public static FileDeleteFactory newSignedFileDelete(String file) {
        return new FileDeleteFactory(file);
    }

    @Override
    protected FileDeleteFactory self() {
        return this;
    }

    @Override
    protected long feeFor(Transaction signedTxn, int numPayerKeys) {
        return 0;
    }

    @Override
    protected void customizeTxn(TransactionBody.Builder txn) {
        FileDeleteTransactionBody.Builder op =
                FileDeleteTransactionBody.newBuilder().setFileID(asFile(file));
        txn.setFileDelete(op);
    }
}
