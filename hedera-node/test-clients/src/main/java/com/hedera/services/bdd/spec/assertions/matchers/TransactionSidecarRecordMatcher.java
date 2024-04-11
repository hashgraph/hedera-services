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

import static com.hedera.services.bdd.spec.assertions.matchers.MatcherUtils.containsInAnyOrder;
import static com.hedera.services.bdd.spec.assertions.matchers.MatcherUtils.withEqualFields;
import static com.hedera.services.bdd.spec.assertions.matchers.MatcherUtils.within32Units;
import static org.testcontainers.shaded.org.hamcrest.Matchers.equalTo;

import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.Collection;
import java.util.Map;
import org.testcontainers.shaded.org.hamcrest.Description;
import org.testcontainers.shaded.org.hamcrest.Matcher;
import org.testcontainers.shaded.org.hamcrest.Matchers;
import org.testcontainers.shaded.org.hamcrest.TypeSafeDiagnosingMatcher;

/**
 * Used in assertions to check only certain fields of a {@link TransactionSidecarRecord} object.
 *
 * @author vyanev
 */
@SuppressWarnings({"java:S4968", "java:S1192"}) // wildcard types and string literals
public class TransactionSidecarRecordMatcher extends TypeSafeDiagnosingMatcher<TransactionSidecarRecord> {

    private final Matcher<Timestamp> consensusTimestampMatcher;
    private final Matcher<Iterable<? extends ContractAction>> actionsMatcher;
    private final Matcher<Iterable<? extends ContractStateChange>> stateChangesMatcher;
    private final Matcher<ContractBytecode> bytecodeMatcher;

    private TransactionSidecarRecordMatcher(
            Matcher<Timestamp> consensusTimestampMatcher,
            Matcher<Iterable<? extends ContractAction>> actionsMatcher,
            Matcher<Iterable<? extends ContractStateChange>> stateChangeMatchers,
            Matcher<ContractBytecode> bytecodeMatcher) {
        this.consensusTimestampMatcher = consensusTimestampMatcher;
        this.actionsMatcher = actionsMatcher;
        this.stateChangesMatcher = stateChangeMatchers;
        this.bytecodeMatcher = bytecodeMatcher;
    }

    /**
     * Checks if the given {@link TransactionSidecarRecord} matches the expected values.
     * @param sidecarRecord the {@link TransactionSidecarRecord} to check
     * @param mismatch {@link Description} of the mismatch
     * @return {@code true} if the {@link TransactionSidecarRecord}'s properties match the expected values
     */
    @Override
    public boolean matchesSafely(TransactionSidecarRecord sidecarRecord, Description mismatch) {
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
    @SuppressWarnings("java:S1192") // string literals
    public void describeTo(Description description) {
        description.appendText("transaction_sidecar_record {\n");
        if (this.consensusTimestampMatcher != null) {
            description.appendText("  consensus_timestamp {\n");
            description
                    .appendText("    ")
                    .appendValue(this.consensusTimestampMatcher)
                    .appendText("\n");
            description.appendText("  }\n");
        }
        if (this.hasActions()) {
            description.appendText("  actions {\n");
            description.appendText("    ").appendValue(this.actionsMatcher).appendText("\n");
            description.appendText("  }\n");
        }
        if (this.hasStateChanges()) {
            description.appendText("  state_changes {\n");
            description.appendText("    ").appendValue(this.stateChangesMatcher).appendText("\n");
            description.appendText("  }\n");
        }
        description.appendText("}");
    }

    /**
     * @return {@code true} if the expected {@link TransactionSidecarRecord} has matchers for actions
     */
    public boolean hasActions() {
        return this.actionsMatcher != null;
    }

    /**
     * @return {@code true} if the expected {@link TransactionSidecarRecord} has matchers for state changes
     */
    public boolean hasStateChanges() {
        return this.stateChangesMatcher != null;
    }

    /**
     * @param sidecarRecord the actual {@link TransactionSidecarRecord}
     * @param mismatch {@link Description} of the mismatch
     * @return {@code true} if the consensus timestamp of the given
     * {@link TransactionSidecarRecord} matches the expected value
     */
    public boolean matchesConsensusTimestampOf(TransactionSidecarRecord sidecarRecord, Description mismatch) {
        if (this.consensusTimestampMatcher != null
                && !this.consensusTimestampMatcher.matches(sidecarRecord.getConsensusTimestamp())) {
            mismatch.appendText("***Mismatch in Consensus Timestamp:****\n")
                    .appendText("***Expected:***\n")
                    .appendValue(this.consensusTimestampMatcher)
                    .appendText("\n")
                    .appendText("***Actual:***\n")
                    .appendValue(sidecarRecord.getConsensusTimestamp())
                    .appendText("\n");
            return false;
        }
        return true;
    }

    /**
     * @param sidecarRecord the actual {@link TransactionSidecarRecord}
     * @param mismatch {@link Description} of the mismatch
     * @return {@code true} if the actions of the given
     * {@link TransactionSidecarRecord} match the expected value
     */
    public boolean matchesActionsOf(TransactionSidecarRecord sidecarRecord, Description mismatch) {
        if (this.actionsMatcher != null
                && !this.actionsMatcher.matches(sidecarRecord.getActions().getContractActionsList())) {
            mismatch.appendText("***Mismatch in Actions:****\n");
            this.actionsMatcher.describeMismatch(sidecarRecord.getActions().getContractActionsList(), mismatch);
            return false;
        }
        return true;
    }

    /**
     * @param sidecarRecord the actual {@link TransactionSidecarRecord}
     * @param mismatch {@link Description} of the mismatch
     * @return {@code true} if the state changes of the given
     * {@link TransactionSidecarRecord} match the expected value
     */
    public boolean matchesStateChangesOf(TransactionSidecarRecord sidecarRecord, Description mismatch) {
        if (this.stateChangesMatcher != null
                && !this.stateChangesMatcher.matches(
                        sidecarRecord.getStateChanges().getContractStateChangesList())) {
            mismatch.appendText("***Mismatch in State Changes:****\n");
            this.stateChangesMatcher.describeMismatch(
                    sidecarRecord.getStateChanges().getContractStateChangesList(), mismatch);
            return false;
        }
        return true;
    }

    /**
     * @param sidecarRecord the actual {@link TransactionSidecarRecord}
     * @param mismatch {@link Description} of the mismatch
     * @return {@code true} if the bytecode of the given
     * {@link TransactionSidecarRecord} matches the expected value
     */
    public boolean matchesBytecodeOf(TransactionSidecarRecord sidecarRecord, Description mismatch) {
        if (this.bytecodeMatcher != null && !this.bytecodeMatcher.matches(sidecarRecord.getBytecode())) {
            mismatch.appendText("***Mismatch in Bytecode:****\n")
                    .appendText("***Expected***:\n")
                    .appendValue(this.bytecodeMatcher)
                    .appendText("\n")
                    .appendText("***Actual***:\n")
                    .appendValue(sidecarRecord.getBytecode())
                    .appendText("\n");
            return false;
        }
        return true;
    }

    /**
     * @return {@link Builder} for the {@link TransactionSidecarRecordMatcher}
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {

        private Matcher<Timestamp> consensusTimestampMatcher;
        private Matcher<Iterable<? extends ContractAction>> contractActionsMatcher;
        private Matcher<Iterable<? extends ContractStateChange>> stateChangesMatcher;
        private Matcher<ContractBytecode> bytecodeMatcher;

        public Builder setConsensusTimestamp(Timestamp consensusTimestamp) {
            this.consensusTimestampMatcher = equalTo(consensusTimestamp);
            return this;
        }

        public Builder setActions(Collection<ContractAction> contractActions) {
            this.contractActionsMatcher = containsInAnyOrder(contractActions, action ->
                    withEqualFields(action, action.getClass().getSuperclass())
                            .withCustomMatchersForFields(Map.of(
                                    "gas", within32Units(action.getGas()),
                                    "gasUsed", within32Units(action.getGasUsed())
                            )));
            return this;
        }

        public Builder setStateChanges(Collection<ContractStateChange> contractStateChanges) {
            this.stateChangesMatcher =
                    containsInAnyOrder(contractStateChanges, Matchers::equalTo);
            return this;
        }

        public Builder setBytecode(ContractBytecode bytecode) {
            this.bytecodeMatcher = equalTo(bytecode);
            return this;
        }

        public TransactionSidecarRecordMatcher build() {
            return new TransactionSidecarRecordMatcher(
                    consensusTimestampMatcher, contractActionsMatcher, stateChangesMatcher, bytecodeMatcher);
        }
    }
}
