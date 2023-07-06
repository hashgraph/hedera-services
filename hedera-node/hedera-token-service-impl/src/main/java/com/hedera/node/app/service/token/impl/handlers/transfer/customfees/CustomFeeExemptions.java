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

package com.hedera.node.app.service.token.impl.handlers.transfer.customfees;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.CustomFee;

public class CustomFeeExemptions {
    public static boolean isPayerExempt(final CustomFeeMeta feeMeta, final CustomFee fee, final AccountID sender) {
        if (feeMeta.treasuryId().equals(sender)) {
            return true;
        }
        if (fee.feeCollectorAccountIdOrElse(AccountID.DEFAULT).equals(sender)) {
            return true;
        }
        if (fee.allCollectorsAreExempt()) {
            return isPayerCollectorFor(feeMeta, sender);
        } else {
            // If payer isn't the treasury or the collector of a fee without
            // a global collector exemption, then it must pay, nothing more to check
            return false;
        }
    }

    private static boolean isPayerCollectorFor(final CustomFeeMeta feeMeta, final AccountID sender) {
        for (final var fee : feeMeta.customFees()) {
            if (fee.feeCollectorAccountIdOrElse(AccountID.DEFAULT).equals(sender)) {
                return true;
            }
        }
        return false;
    }
}
