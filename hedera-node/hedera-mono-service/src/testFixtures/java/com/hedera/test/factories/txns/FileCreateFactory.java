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

import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static com.hedera.test.factories.keys.NodeFactory.list;

import com.hedera.test.factories.keys.KeyTree;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

public class FileCreateFactory extends SignedTxnFactory<FileCreateFactory> {
    public static final KeyTree DEFAULT_WACL_KT = KeyTree.withRoot(list(ed25519()));

    private KeyTree waclKt = DEFAULT_WACL_KT;

    private FileCreateFactory() {}

    public static FileCreateFactory newSignedFileCreate() {
        return new FileCreateFactory();
    }

    @Override
    protected FileCreateFactory self() {
        return this;
    }

    @Override
    protected long feeFor(Transaction signedTxn, int numPayerKeys) {
        return 0;
    }

    @Override
    protected void customizeTxn(TransactionBody.Builder txn) {
        FileCreateTransactionBody.Builder op =
                FileCreateTransactionBody.newBuilder()
                        .setKeys(waclKt.asKey(keyFactory).getKeyList());
        txn.setFileCreate(op);
    }

    public FileCreateFactory waclKt(KeyTree waclKt) {
        this.waclKt = waclKt;
        return this;
    }
}
