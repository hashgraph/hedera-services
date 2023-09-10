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

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Predicate;

/**
 * Helper class for switching unauthorized debits to approvals in a synthetic
 * {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody}.
 */
public class ApprovalSwitchHelper {
    public static final ApprovalSwitchHelper APPROVAL_SWITCH_HELPER = new ApprovalSwitchHelper();

    private ApprovalSwitchHelper() {
        // Singleton
    }

    /**
     * Given a synthetic {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody}, returns a new
     * {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody} that is identical except any debits
     * whose linked signing keys do not have active signatures are switched to approvals.
     *
     * @param nominalBody the synthetic {@link CryptoTransferTransactionBody} to switch
     * @param signatureTest the {@link Predicate} that determines whether a given key has an active signature
     * @param nativeOperations the {@link HederaNativeOperations} that provides account key access
     * @return the new {@link com.hedera.hapi.node.token.CryptoTransferTransactionBody}
     */
    public CryptoTransferTransactionBody switchToApprovalsAsNeededIn(
            @NonNull final CryptoTransferTransactionBody nominalBody,
            @NonNull final Predicate<Key> signatureTest,
            @NonNull final HederaNativeOperations nativeOperations) {
        throw new AssertionError("Not implemented");
    }
}
