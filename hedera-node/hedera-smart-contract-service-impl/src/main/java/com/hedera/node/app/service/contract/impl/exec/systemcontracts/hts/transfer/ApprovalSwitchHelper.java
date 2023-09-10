/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer;

import com.hedera.hapi.node.token.CryptoTransferTransactionBody;

/**
 * Helper class for switching non-sender debits to approvals in a synthetic {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody}.
 */
public class ApprovalSwitchHelper {
    public static final ApprovalSwitchHelper APPROVAL_SWITCH_HELPER = new ApprovalSwitchHelper();

    private ApprovalSwitchHelper() {
        // Singleton
    }

    /**
     * Given a synthetic {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody}, returns a new
     * {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody} that is identical except that all non-sender
     * debits have been switched to approvals.
     *
     * @param nominalBody the synthetic {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody} to switch
     * @return the new {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody}
     */
    public CryptoTransferTransactionBody switchToApprovalsForNonSenderDebitsIn(
            final CryptoTransferTransactionBody nominalBody) {
        throw new AssertionError("Not implemented");
    }
}
