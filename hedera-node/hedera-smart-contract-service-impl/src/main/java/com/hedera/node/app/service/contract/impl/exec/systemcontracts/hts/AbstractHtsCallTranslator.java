package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsCallTranslator;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import static java.util.Objects.requireNonNull;

public abstract class AbstractHtsCallTranslator implements HtsCallTranslator<HtsCall> {
    @Override
    public @Nullable HtsCall translate(@NonNull HtsCallAttempt attempt) {
        requireNonNull(attempt);
        if (matches(attempt)) {
            return callFrom(attempt);
        }
        return null;
    }

    /**
     * Returns true if the attempt matches the selector of the call this translator is responsible for.
     *
     * @param attempt the selector to match
     * @return true if the selector matches the selector of the call this translator is responsible for
     */
    public abstract boolean matches(@NonNull final HtsCallAttempt attempt);

    /**
     * Returns a call from the given attempt.
     *
     * @param attempt the attempt to get the call from
     * @return a call from the given attempt
     */
    public abstract HtsCall callFrom(@NonNull final HtsCallAttempt attempt);
}
