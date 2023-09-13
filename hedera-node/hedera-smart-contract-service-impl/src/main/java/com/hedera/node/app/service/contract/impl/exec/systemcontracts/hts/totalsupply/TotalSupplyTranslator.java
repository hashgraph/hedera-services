package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.totalsupply;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Arrays;

import static java.util.Objects.requireNonNull;

@Singleton
public class TotalSupplyTranslator extends AbstractHtsCallTranslator {
    public static final Function TOTAL_SUPPLY = new Function("totalSupply()", ReturnTypes.INT);

    @Inject
    public TotalSupplyTranslator() {
        // Dagger2
    }

    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), TOTAL_SUPPLY.selector());
    }

    @Override
    public TotalSupplyCall callFrom(@NonNull final HtsCallAttempt attempt) {
        return new TotalSupplyCall(attempt.enhancement(), attempt.redirectToken());
    }
}
