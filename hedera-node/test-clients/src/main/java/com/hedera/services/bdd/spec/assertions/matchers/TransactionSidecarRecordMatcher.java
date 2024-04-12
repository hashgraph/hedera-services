/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.assertions.matchers;

import static com.hedera.services.bdd.spec.assertions.matchers.MatcherUtils.withEqualFields;
import static com.hedera.services.bdd.spec.assertions.matchers.MatcherUtils.within32Units;

import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.stream.proto.ContractActions;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.Map;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.shaded.org.hamcrest.Description;
import org.testcontainers.shaded.org.hamcrest.Matcher;
import org.testcontainers.shaded.org.hamcrest.Matchers;
import org.testcontainers.shaded.org.hamcrest.TypeSafeDiagnosingMatcher;
import org.testcontainers.shaded.org.hamcrest.core.IsEqual;

/**
 * Used in assertions to compare {@link TransactionSidecarRecord} objects.
 * For example, to check for the presence of a specific action, state change, or bytecode.
 *
 * @author vyanev
 */
public final class TransactionSidecarRecordMatcher extends TypeSafeDiagnosingMatcher<TransactionSidecarRecord> {

    private static final Logger log = LogManager.getLogger(TransactionSidecarRecordMatcher.class);

    /**
     * The expected consensus timestamp
     */
    private Timestamp consensusTimestamp;

    /**
     * The expected actions
     */
    private ContractActions actions;

    /**
     * The expected state changes
     */
    private ContractStateChanges stateChanges;

    /**
     * The expected bytecode
     */
    private ContractBytecode bytecode;

    /**
     * Matcher for the consensus timestamp
     */
    private Function<Timestamp, Matcher<Timestamp>> consensusTimestampMatcher;

    /**
     * Matcher for the actions
     */
    private Function<ContractAction, Matcher<ContractAction>> actionMatcher;

    /**
     * Matcher for the state changes
     */
    private Function<ContractStateChange, Matcher<ContractStateChange>> stateChangeMatcher;

    /**
     * Matcher for the bytecode
     */
    private Function<ContractBytecode, Matcher<ContractBytecode>> bytecodeMatcher;

    private TransactionSidecarRecordMatcher(
            final Timestamp consensusTimestamp,
            final ContractActions contractActions,
            final ContractStateChanges contractStateChanges,
            final ContractBytecode bytecode) {
        super(TransactionSidecarRecord.class);
        setConsensusTimestamp(consensusTimestamp);
        setActions(contractActions);
        setStateChanges(contractStateChanges);
        setBytecode(bytecode);
    }

    private void setConsensusTimestamp(final Timestamp consensusTimestamp) {
        this.consensusTimestamp = consensusTimestamp;
        this.consensusTimestampMatcher = Matchers::equalTo;
    }

    private void setActions(final ContractActions actions) {
        this.actions = actions;
        this.actionMatcher = action -> withEqualFields(action, action.getClass().getSuperclass())
                .withCustomMatchersForFields(Map.of(
                        "gas", within32Units(action.getGas()),
                        "gasUsed", within32Units(action.getGasUsed())));
    }

    private void setStateChanges(final ContractStateChanges stateChanges) {
        this.stateChanges = stateChanges;
        this.stateChangeMatcher = IsEqual::equalTo;
    }

    private void setBytecode(final ContractBytecode bytecode) {
        this.bytecode = bytecode;
        this.bytecodeMatcher = IsEqual::equalTo;
    }

    /**
     * Checks if the {@link TransactionSidecarRecord} matches the expected values.
     * @param sidecarRecord the {@link TransactionSidecarRecord} to check
     * @param mismatch {@link Description} of the mismatch
     * @return {@code true} if the record's properties match the expected values
     */
    @Override
    public boolean matchesSafely(TransactionSidecarRecord sidecarRecord, final Description mismatch) {
        return matchesConsensusTimestampOf(sidecarRecord, mismatch)
                && matchesActionsOf(sidecarRecord, mismatch)
                && matchesStateChangesOf(sidecarRecord, mismatch)
                && matchesBytecodeOf(sidecarRecord, mismatch);
    }

    /**
     * Describe the expected values for the {@link TransactionSidecarRecord}
     * @param description {@link Description}
     */
    @Override
    public void describeTo(Description description) {
        description.appendValue(this.toSidecarRecord());
    }

    /**
     * @return {@code true} if there are expected actions
     */
    public boolean hasActions() {
        return this.actions != null;
    }

    /**
     * @return {@code true} if there are expected state changes
     */
    public boolean hasStateChanges() {
        return this.stateChanges != null;
    }

    /**
     * @param sidecarRecord the actual {@link TransactionSidecarRecord}
     * @return {@code true} if the consensus timestamp of the given
     * {@link TransactionSidecarRecord} matches the expected value
     */
    public boolean matchesConsensusTimestampOf(final TransactionSidecarRecord sidecarRecord) {
        final Matcher<Timestamp> matcher = consensusTimestampMatcher.apply(consensusTimestamp);
        return consensusTimestamp == null || matcher.matches(sidecarRecord.getConsensusTimestamp());
    }

    private boolean matchesConsensusTimestampOf(
            final TransactionSidecarRecord sidecarRecord, final Description mismatch) {
        if (!matchesConsensusTimestampOf(sidecarRecord)) {
            describeMismatch(
                    """
                    ***Mismatch in Consensus Timestamp:***
                    Expected: %s
                    Actual: %s
                    """
                            .formatted(consensusTimestamp, sidecarRecord.getConsensusTimestamp()),
                    mismatch);
            return false;
        }
        return true;
    }

    private boolean matchesActionsOf(final TransactionSidecarRecord sidecarRecord, final Description mismatch) {
        if (this.actions == null) {
            return true;
        }
        final var mismatchedActions = this.actions.getContractActionsList().stream()
                .filter(expected -> {
                    final var actualActions = sidecarRecord.getActions().getContractActionsList();
                    return actualActions.stream()
                            .noneMatch(
                                    actual -> this.actionMatcher.apply(expected).matches(actual));
                })
                .toList();
        if (!mismatchedActions.isEmpty()) {
            describeMismatch(
                    """
                    ***Mismatch in Actions:***
                    The following actions were expected but not found in the actual actions:
                    %s
                    Actual actions:
                    %s
                    """
                            .formatted(
                                    mismatchedActions,
                                    sidecarRecord.getActions().getContractActionsList()),
                    mismatch);
            return false;
        }
        return true;
    }

    private boolean matchesStateChangesOf(final TransactionSidecarRecord sidecarRecord, final Description mismatch) {
        if (this.stateChanges == null) {
            return true;
        }
        final var mismatchedStateChanges = this.stateChanges.getContractStateChangesList().stream()
                .filter(expected -> {
                    final var actualStateChanges =
                            sidecarRecord.getStateChanges().getContractStateChangesList();
                    return actualStateChanges.stream()
                            .noneMatch(actual ->
                                    this.stateChangeMatcher.apply(expected).matches(actual));
                })
                .toList();
        if (!mismatchedStateChanges.isEmpty()) {
            describeMismatch(
                    """
                    ***Mismatch in State Changes:***
                    The following state changes were expected but not found in the actual state changes:
                    %s
                    Actual state changes:
                    %s
                    """
                            .formatted(
                                    mismatchedStateChanges,
                                    sidecarRecord.getStateChanges().getContractStateChangesList()),
                    mismatch);
            return false;
        }
        return true;
    }

    private boolean matchesBytecodeOf(final TransactionSidecarRecord sidecarRecord, final Description mismatch) {
        final Matcher<ContractBytecode> matcher = bytecodeMatcher.apply(bytecode);
        if (bytecode != null && !matcher.matches(sidecarRecord.getBytecode())) {
            describeMismatch(
                    """
                    ***Mismatch in Bytecode:***
                    Expected: %s
                    Actual: %s
                    """
                            .formatted(bytecode, sidecarRecord.getBytecode()),
                    mismatch);
            return false;
        }
        return true;
    }

    private static void describeMismatch(final String message, final Description mismatch) {
        log.warn(message);
        mismatch.appendText(message);
    }

    /**
     * @return a {@link TransactionSidecarRecord} with the expected fields
     */
    public TransactionSidecarRecord toSidecarRecord() {
        final TransactionSidecarRecord.Builder builder = TransactionSidecarRecord.newBuilder();
        if (this.consensusTimestamp != null) {
            builder.setConsensusTimestamp(consensusTimestamp);
        }
        if (this.actions != null) {
            builder.setActions(actions);
        }
        if (this.stateChanges != null) {
            builder.setStateChanges(stateChanges);
        }
        if (this.bytecode != null) {
            builder.setBytecode(bytecode);
        }
        return builder.build();
    }

    /**
     * @return {@link Builder} for the {@link TransactionSidecarRecordMatcher}
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder for the {@link TransactionSidecarRecordMatcher}
     */
    public static class Builder {

        private Timestamp consensusTimestamp;
        private ContractActions actions;
        private ContractStateChanges stateChanges;
        private ContractBytecode bytecode;

        /**
         * @param consensusTimestamp the expected consensus timestamp
         * @return {@code this}
         */
        public Builder setConsensusTimestamp(final Timestamp consensusTimestamp) {
            this.consensusTimestamp = consensusTimestamp;
            return this;
        }

        /**
         * @param actions the expected actions
         * @return {@code this}
         */
        public Builder setActions(final ContractActions actions) {
            this.actions = actions;
            return this;
        }

        /**
         * @param stateChanges the expected state changes
         * @return {@code this}
         */
        public Builder setStateChanges(final ContractStateChanges stateChanges) {
            this.stateChanges = stateChanges;
            return this;
        }

        /**
         * @param bytecode the expected bytecode
         * @return {@code this}
         */
        public Builder setBytecode(final ContractBytecode bytecode) {
            this.bytecode = bytecode;
            return this;
        }

        /**
         * @return {@link TransactionSidecarRecordMatcher} with the expected fields
         */
        public TransactionSidecarRecordMatcher build() {
            return new TransactionSidecarRecordMatcher(consensusTimestamp, actions, stateChanges, bytecode);
        }
    }
}
