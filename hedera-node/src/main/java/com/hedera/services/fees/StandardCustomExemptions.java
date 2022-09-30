package com.hedera.services.fees;

import com.hedera.services.grpc.marshalling.CustomFeeMeta;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Our policy for exemptions is the following:
 * <ul>
 *     <li>A token's treasury is exempt from all its custom fees.</li>
 *     <li>A fee collection account is exempt from any fee for which it would be the collector.</li>
 *     <li>A fee collection account is exempt from any of its token's fees with {@code all_collectors_are_exempt=true}.</li>
 * </ul>
 */
@Singleton
public class StandardCustomExemptions implements CustomFeeExemptions {
    @Inject
    public StandardCustomExemptions() {
        // For Dagger2
    }

    /**
     * Given the fee metadata for a token, and one of this token's custom fees, returns whether
     * the given payer is exempt from the specific custom fee provided.
     *
     * @param feeMeta metadata for the token that "owns" the specific custom fee
     * @param fee the fee to check for a payer exemption
     * @param payer the potential fee payer
     * @return whether the payer is exempt from the fee
     */
    @Override
    public boolean isPayerExempt(final CustomFeeMeta feeMeta, final FcCustomFee fee, final Id payer) {
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

    private boolean isPayerCollectorFor(final CustomFeeMeta feeMeta, final Id payer) {
        for (final var fee : feeMeta.customFees()) {
            if (fee.getFeeCollectorAsId().equals(payer)) {
                return true;
            }
        }
        return false;
    }
}
