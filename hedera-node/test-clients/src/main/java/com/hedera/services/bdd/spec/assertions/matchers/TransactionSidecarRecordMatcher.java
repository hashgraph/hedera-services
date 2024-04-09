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

import static org.testcontainers.shaded.org.hamcrest.Matchers.equalTo;

import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChange;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.testcontainers.shaded.org.hamcrest.Description;
import org.testcontainers.shaded.org.hamcrest.Matcher;
import org.testcontainers.shaded.org.hamcrest.TypeSafeMatcher;

/**
 * Used in assertions to check only certain fields of a {@link TransactionSidecarRecord} object.
 *
 * @author vyanev
 */
@SuppressWarnings("java:S4968") // wildcard types
public class TransactionSidecarRecordMatcher extends TypeSafeMatcher<TransactionSidecarRecord> {

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
     * @param transactionSidecarRecord the {@link TransactionSidecarRecord} to check
     * @return {@code true} if the {@link TransactionSidecarRecord}'s properties match the expected values
     */
    @Override
    public boolean matchesSafely(TransactionSidecarRecord transactionSidecarRecord) {
        return (this.consensusTimestampMatcher == null || matchesConsensusTimestampOf(transactionSidecarRecord))
                && (this.actionsMatcher == null || matchesActionsOf(transactionSidecarRecord))
                && (this.stateChangesMatcher == null || matchesStateChangesOf(transactionSidecarRecord))
                && (this.bytecodeMatcher == null || matchesBytecodeOf(transactionSidecarRecord));
    }

    /**
     * Describe the expected values for the {@link TransactionSidecarRecord}
     * @param description {@link Description}
     */
    @Override
    @SuppressWarnings("java:S1192") // string literals
    public void describeTo(Description description) {
        description
                .appendText("transaction_sidecar_record {\n")
                .appendText("  consensus_timestamp {\n")
                .appendText("    ")
                .appendValue(this.consensusTimestampMatcher)
                .appendText("  }\n");
        if (this.hasActions()) {
            description.appendText("  actions {\n");
            description.appendValue(this.actionsMatcher).appendText("\n");
            description.appendText("  }\n");
        }
        if (this.hasStateChanges()) {
            description.appendText("  state_changes {\n");
            description.appendValue(this.stateChangesMatcher).appendText("\n");
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
     * @return {@code true} if the consensus timestamp of the given
     * {@link TransactionSidecarRecord} matches the expected value
     */
    public boolean matchesConsensusTimestampOf(TransactionSidecarRecord sidecarRecord) {
        return this.consensusTimestampMatcher.matches(sidecarRecord.getConsensusTimestamp());
    }

    /**
     * @param sidecarRecord the actual {@link TransactionSidecarRecord}
     * @return {@code true} if the actions of the given
     * {@link TransactionSidecarRecord} match the expected value
     */
    public boolean matchesActionsOf(TransactionSidecarRecord sidecarRecord) {
        return this.actionsMatcher.matches(sidecarRecord.getActions().getContractActionsList());
    }

    /**
     * @param sidecarRecord the actual {@link TransactionSidecarRecord}
     * @return {@code true} if the state changes of the given
     * {@link TransactionSidecarRecord} match the expected value
     */
    public boolean matchesStateChangesOf(TransactionSidecarRecord sidecarRecord) {
        return this.stateChangesMatcher.matches(sidecarRecord.getStateChanges().getContractStateChangesList());
    }

    /**
     * @param sidecarRecord the actual {@link TransactionSidecarRecord}
     * @return {@code true} if the bytecode of the given
     * {@link TransactionSidecarRecord} matches the expected value
     */
    public boolean matchesBytecodeOf(TransactionSidecarRecord sidecarRecord) {
        return this.bytecodeMatcher.matches(sidecarRecord.getBytecode());
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

        public Builder setActions(Matcher<Iterable<? extends ContractAction>> contractActionMatchers) {
            this.contractActionsMatcher = contractActionMatchers;
            return this;
        }

        public Builder setStateChanges(Matcher<Iterable<? extends ContractStateChange>> stateChangeMatchers) {
            this.stateChangesMatcher = stateChangeMatchers;
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
