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
package com.hedera.node.app.service.token;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * A Placeholder implementation that will provide access to {@link com.hedera.services.txns.auth.SystemOpPolicies}
 * and {@link com.hedera.services.sigs.order.SignatureWaivers} to check which accounts
 * are waived from signing the transaction.
 * This will be deleted when both the above classes are implemented.
 */
public interface StaticContext {
    /**
     * Advises if the target account's key must sign a given crypto update.
     * Since, accounts 0.0.2 and 0.0.50 can update any non-0.0.2 system accounts with no
     * other signatures. Checks if the target account being updated by the above accounts.
     *
     * @param cryptoUpdateTxn a crypto update transaction
     * @return whether the target account's key must sign
     */
    boolean isTargetAccountSignatureWaived(TransactionBody cryptoUpdateTxn, final AccountID payer);

    /**
     * Advises if the new key for an account must sign a given crypto update.
     * Since, accounts 0.0.2 and 0.0.50 can update any non-0.0.2 system accounts with no
     * other signatures. Checks if the target account being updated by the above accounts.
     * When updating the treasury account 0.0.2 key, the new key must also sign the transaction.
     * Checks if the account being updated is treasury account.
     *
     * @param cryptoUpdateTxn a crypto update transaction
     * @return whether the new key from the transaction must sign
     */
    boolean isNewKeySignatureWaived(TransactionBody cryptoUpdateTxn, final AccountID payer);
}