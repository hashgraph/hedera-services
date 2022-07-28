/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides logic to identify what {@link TransitionLogic} applies to the active node and
 * transaction context.
 */
@Singleton
public class TransitionLogicLookup {
    private final EnumMap<HederaFunctionality, Optional<TransitionLogic>> unambiguousLookups =
            new EnumMap<>(HederaFunctionality.class);
    private final Map<HederaFunctionality, List<TransitionLogic>> transitions;

    @Inject
    public TransitionLogicLookup(Map<HederaFunctionality, List<TransitionLogic>> transitions) {
        this.transitions = transitions;
        for (var function : HederaFunctionality.class.getEnumConstants()) {
            final var allTransitions = transitions.get(function);
            if (allTransitions != null && allTransitions.size() == 1) {
                unambiguousLookups.put(function, Optional.of(allTransitions.get(0)));
            }
        }
    }

    /**
     * Returns the {@link TransitionLogic}, if any, relevant to the given txn.
     *
     * @param function the HederaFunctionality that txn requires.
     * @param txn the txn to find logic for.
     * @return relevant transition logic, if it exists.
     */
    public Optional<TransitionLogic> lookupFor(HederaFunctionality function, TransactionBody txn) {
        if (unambiguousLookups.containsKey(function)) {
            final var onlyCandidate = unambiguousLookups.get(function);
            if (onlyCandidate.isPresent() && onlyCandidate.get().applicability().test(txn)) {
                return onlyCandidate;
            }
            return Optional.empty();
        }
        return Optional.ofNullable(transitions.get(function)).flatMap(trans -> from(trans, txn));
    }

    private Optional<TransitionLogic> from(List<TransitionLogic> transitions, TransactionBody txn) {
        for (TransitionLogic candidate : transitions) {
            if (candidate.applicability().test(txn)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
