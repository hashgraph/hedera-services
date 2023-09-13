package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ownerof;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asExactLongValueOrZero;

@Singleton
public class OwnerOfTranslator extends AbstractHtsCallTranslator {
    public static final Function OWNER_OF = new Function("ownerOf(uint256)", ReturnTypes.ADDRESS);

    @Inject
    public OwnerOfTranslator() {
        // Dagger2
    }

    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), OWNER_OF.selector());
    }

    @Override
    public OwnerOfCall callFrom(@NonNull final HtsCallAttempt attempt) {
        // Since zero is never a valid serial number, if we clamp the passed value, the result
        // will be a revert with INVALID_NFT_ID as reason
        final var serialNo = asExactLongValueOrZero(
                OWNER_OF.decodeCall(attempt.input().toArrayUnsafe()).get(0));
        return new OwnerOfCall(attempt.enhancement(), attempt.redirectToken(), serialNo);
    }
}
