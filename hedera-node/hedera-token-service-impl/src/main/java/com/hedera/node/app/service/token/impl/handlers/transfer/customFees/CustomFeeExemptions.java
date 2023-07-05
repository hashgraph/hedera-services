package com.hedera.node.app.service.token.impl.handlers.transfer.customFees;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.service.mono.grpc.marshalling.CustomFeeMeta;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;

public class CustomFeeExemptions {
    public static boolean isPayerExempt(final CustomFeeMeta feeMeta,
                                 final FcCustomFee fee,
                                 final AccountID payer) {
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

    private static boolean isPayerCollectorFor(final CustomFeeMeta feeMeta, final AccountID payer) {
        for (final var fee : feeMeta.customFees()) {
            if (fee.getFeeCollectorAsId().equals(payer)) {
                return true;
            }
        }
        return false;
    }
}
