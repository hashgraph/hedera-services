package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;

@Singleton
public class BalanceOfTranslator extends AbstractHtsCallTranslator {
    public static final Function BALANCE_OF = new Function("balanceOf(address)", ReturnTypes.INT);

    @Inject
    public BalanceOfTranslator() {
        // Dagger2
    }

    @Override
    public BalanceOfCall callFrom(@NonNull final HtsCallAttempt attempt) {
        final Address owner =
                BalanceOfTranslator.BALANCE_OF.decodeCall(attempt.input().toArrayUnsafe()).get(0);
        return new BalanceOfCall(attempt.enhancement(), attempt.redirectToken(), owner);
    }

    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), BALANCE_OF.selector());
    }
}
