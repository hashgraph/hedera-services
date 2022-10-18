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
package com.hedera.services.fees.calculation.file.txns;

import static com.hedera.services.state.merkle.MerkleAccountState.DEFAULT_MEMO;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.file.ExtantFileContext;
import com.hedera.services.usage.file.FileOpsUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FileUpdateResourceUsage implements TxnResourceUsageEstimator {
    private final FileOpsUsage fileOpsUsage;

    @Inject
    public FileUpdateResourceUsage(FileOpsUsage fileOpsUsage) {
        this.fileOpsUsage = fileOpsUsage;
    }

    @Override
    public boolean applicableTo(TransactionBody txn) {
        return txn.hasFileUpdate();
    }

    @Override
    public FeeData usageGiven(TransactionBody txn, SigValueObj svo, StateView view) {
        var op = txn.getFileUpdate();
        var sigUsage =
                new SigUsage(
                        svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());
        var info = view.infoForFile(op.getFileID());
        if (info.isPresent()) {
            var details = info.get();
            var ctx =
                    ExtantFileContext.newBuilder()
                            .setCurrentSize(details.getSize())
                            .setCurrentWacl(details.getKeys())
                            .setCurrentMemo(details.getMemo())
                            .setCurrentExpiry(details.getExpirationTime().getSeconds())
                            .build();
            return fileOpsUsage.fileUpdateUsage(txn, sigUsage, ctx);
        } else {
            long now = txn.getTransactionID().getTransactionValidStart().getSeconds();
            return fileOpsUsage.fileUpdateUsage(txn, sigUsage, missingCtx(now));
        }
    }

    static ExtantFileContext missingCtx(long now) {
        return ExtantFileContext.newBuilder()
                .setCurrentExpiry(now)
                .setCurrentMemo(DEFAULT_MEMO)
                .setCurrentWacl(KeyList.getDefaultInstance())
                .setCurrentSize(0)
                .build();
    }
}
