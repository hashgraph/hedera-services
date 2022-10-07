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
package com.hedera.services.txns.submission;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.context.NodeInfo;
import com.hedera.services.txns.SubmissionFlow;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Performs precheck on a top-level transaction and submits it to the Platform if precheck passes.
 */
@Singleton
public final class BasicSubmissionFlow implements SubmissionFlow {
    private final NodeInfo nodeInfo;
    private final TransactionPrecheck precheck;
    private final PlatformSubmissionManager submissionManager;

    @Inject
    public BasicSubmissionFlow(
            final NodeInfo nodeInfo,
            final TransactionPrecheck precheck,
            final PlatformSubmissionManager submissionManager) {
        this.precheck = precheck;
        this.nodeInfo = nodeInfo;
        this.submissionManager = submissionManager;
    }

    @Override
    public TransactionResponse submit(final Transaction signedTxn) {
        if (nodeInfo.isSelfZeroStake()) {
            return responseWith(INVALID_NODE_ACCOUNT);
        }

        final var precheckResult = precheck.performForTopLevel(signedTxn);
        final var precheckResultMeta = precheckResult.getLeft();
        final var precheckResultValidity = precheckResultMeta.getValidity();
        @Nullable final var accessor = precheckResult.getRight();
        if (precheckResultValidity != OK) {
            return responseWith(precheckResultValidity, precheckResultMeta.getRequiredFee());
        } else if (null == accessor) {
            return responseWith(FAIL_INVALID);
        }

        return responseWith(submissionManager.trySubmission(accessor));
    }

    private TransactionResponse responseWith(final ResponseCodeEnum validity) {
        return responseWith(validity, 0);
    }

    private TransactionResponse responseWith(
            final ResponseCodeEnum validity, final long feeRequired) {
        return TransactionResponse.newBuilder()
                .setNodeTransactionPrecheckCode(validity)
                .setCost(feeRequired)
                .build();
    }
}
