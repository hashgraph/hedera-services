package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.isapprovedforall;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.isapprovedforall.IsApprovedForAllCall.IS_APPROVED_FOR_ALL;

@Singleton
public class IsApprovedForAllTranslator extends AbstractHtsCallTranslator {
    @Inject
    public IsApprovedForAllTranslator() {
        // Dagger2
    }

    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), IS_APPROVED_FOR_ALL.selector());
    }

    @Override
    public IsApprovedForAllCall callFrom(@NonNull final HtsCallAttempt attempt) {
        final var args = IS_APPROVED_FOR_ALL.decodeCall(attempt.input().toArrayUnsafe());
        return new IsApprovedForAllCall(attempt.enhancement(), attempt.redirectToken(), args.get(0), args.get(1));
    }
}
