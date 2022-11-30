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
package com.hedera.node.app.service.mono.fees.calculation.file.txns;

import static com.hedera.node.app.service.mono.state.merkle.MerkleAccountState.DEFAULT_MEMO;

import com.hedera.node.app.hapi.fees.usage.SigUsage;
import com.hedera.node.app.hapi.fees.usage.file.ExtantFileContext;
import com.hedera.node.app.hapi.fees.usage.file.FileOpsUsage;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.calculation.TxnResourceUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FileUpdateResourceUsage implements TxnResourceUsageEstimator {
    private final FileOpsUsage fileOpsUsage;

    @Inject
    public FileUpdateResourceUsage(final FileOpsUsage fileOpsUsage) {
        this.fileOpsUsage = fileOpsUsage;
    }

    @Override
    public boolean applicableTo(final TransactionBody txn) {
        return txn.hasFileUpdate();
    }

    @Override
    public FeeData usageGiven(
            final TransactionBody txn, final SigValueObj svo, final StateView view) {
        final var op = txn.getFileUpdate();
        final var sigUsage =
                new SigUsage(
                        svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());
        final var info = view.infoForFile(op.getFileID());
        if (info.isPresent()) {
            final var details = info.get();
            final var ctx =
                    ExtantFileContext.newBuilder()
                            .setCurrentSize(details.getSize())
                            .setCurrentWacl(details.getKeys())
                            .setCurrentMemo(details.getMemo())
                            .setCurrentExpiry(details.getExpirationTime().getSeconds())
                            .build();
            return fileOpsUsage.fileUpdateUsage(txn, sigUsage, ctx);
        } else {
            final long now = txn.getTransactionID().getTransactionValidStart().getSeconds();
            return fileOpsUsage.fileUpdateUsage(txn, sigUsage, missingCtx(now));
        }
    }

    static ExtantFileContext missingCtx(final long now) {
        return ExtantFileContext.newBuilder()
                .setCurrentExpiry(now)
                .setCurrentMemo(DEFAULT_MEMO)
                .setCurrentWacl(KeyList.getDefaultInstance())
                .setCurrentSize(0)
                .build();
    }
}
