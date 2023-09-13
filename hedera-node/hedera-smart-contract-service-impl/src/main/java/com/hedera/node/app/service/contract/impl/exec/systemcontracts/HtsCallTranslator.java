package com.hedera.node.app.service.contract.impl.exec.systemcontracts;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Strategy interface for translating {@link HtsCallAttempt}s into {@link HtsCall}s.
 *
 * @param <T> the type of {@link HtsCall} to translate to
 */
public interface HtsCallTranslator<T extends HtsCall> {
    /**
     * Tries to translate the given {@code attempt} into a {@link HtsCall}, returning null if the call
     * doesn't match the target type of this translator.
     *
     * @param attempt the attempt to translate
     * @return the translated {@link HtsCall}
     */
    @Nullable
    T translate(@NonNull HtsCallAttempt attempt);
}
