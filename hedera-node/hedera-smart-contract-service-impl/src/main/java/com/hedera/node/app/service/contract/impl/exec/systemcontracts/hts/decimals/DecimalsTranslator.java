package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.decimals;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;

@Singleton
public class DecimalsTranslator extends AbstractHtsCallTranslator {
    public static final Function DECIMALS = new Function("decimals()", ReturnTypes.BYTE);

    @Inject
    public DecimalsTranslator() {
        // Dagger2
    }

    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), DECIMALS.selector());
    }

    @Override
    public HtsCall callFrom(@NonNull final HtsCallAttempt attempt) {
        return new DecimalsCall(attempt.enhancement(), attempt.redirectToken());
    }
}
