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
import com.hedera.node.app.service.mono.grpc.marshalling.CustomFeeMeta;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;

/**
 * Defines a type that determines if a custom fee's payer is exempt from a given custom fee. Please
 * note that there are two other cases in which we exempt a custom fee:
 *
 * <ol>
 *   <li>When a fractional fee collector sends units of its collected token, we do not immediately
 *       reclaim any of these from the receiving account (which would be the effective payer).
 *   <li>When a token treasury sends NFTs with a fallback to an account without any value exchanged,
 *       we do not apply the fallback fee to the receiving account (which would be the effective
 *       payer).
 * </ol>
 */
public class CustomPayerExemptions {

    /**
     * Given the fee metadata for a token, and one of this token's custom fees, returns whether the
     * given payer is exempt from the specific custom fee provided.
     *
     * @param feeMeta metadata for the token that "owns" the specific custom fee
     * @param fee the fee to check for a payer exemption
     * @param payer the potential fee payer
     * @return whether the payer is exempt from the fee
     */
    public boolean isPayerExempt(final CustomFeeMeta feeMeta, final FcCustomFee fee, final AccountID payer) {
        if (feeMeta.treasuryId().equals(payer)) {
            return true;
        }
        if (fee.getFeeCollectorAsId().equals(payer)) {
            return true;
        }
        if (fee.getAllCollectorsAreExempt()) {
            return isPayerCollectorFor(feeMeta, payer);
        } else {
            // If payer isn't the treasury or the collector of a fee without
            // a global collector exemption, then it must pay, nothing more to check
            return false;
        }
    }

    private boolean isPayerCollectorFor(final CustomFeeMeta feeMeta, final AccountID payer) {
        for (final var fee : feeMeta.customFees()) {
            if (fee.getFeeCollectorAsId().equals(payer)) {
                return true;
            }
        }
        return false;
    }
}
