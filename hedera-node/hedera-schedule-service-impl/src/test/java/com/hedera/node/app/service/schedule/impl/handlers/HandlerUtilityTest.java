/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl.handlers;

import static org.assertj.core.api.BDDAssertions.assertThat;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody.DataOneOfType;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.PreCheckException;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.assertj.core.api.BDDAssertions;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HandlerUtilityTest extends ScheduleHandlerTestBase {

    @BeforeEach
    void setUp() throws PreCheckException, InvalidKeyException {
        setUpBase();
    }

    @Test
    void asOrdinaryHandlesAllTypes() {
        for (final Schedule next : listOfScheduledOptions) {
            final AccountID originalPayer =
                    next.originalCreateTransaction().transactionID().accountID();
            final AccountID payer = next.payerAccountIdOrElse(originalPayer);
            final TransactionBody result = HandlerUtility.childAsOrdinary(next);
            assertThat(result).isNotNull();
            assertThat(result.transactionFee()).isGreaterThan(0L);
            assertThat(result.transactionID()).isNotNull();
            assertThat(result.transactionID().scheduled()).isTrue();
            assertThat(result.transactionID().accountID()).isEqualTo(payer);
        }
    }

    @Test
    void functionalityForTypeHandlesAllTypes() {
        for (DataOneOfType input : DataOneOfType.values()) {
            assertThat(HandlerUtility.functionalityForType(input)).isNotNull();
        }
    }

    @Test
    void markExecutedModifiesSchedule() {
        // The utility method call should modify the return only by marking it executed, and
        // setting resolution time.
        // No other value should change, and the original Schedule should not change.
        for (final Schedule expected : listOfScheduledOptions) {
            final Schedule marked = HandlerUtility.markExecuted(expected, testConsensusTime);
            assertThat(expected.executed()).isFalse();
            assertThat(marked.executed()).isTrue();
            assertThat(expected.hasResolutionTime()).isFalse();
            assertThat(marked.hasResolutionTime()).isTrue();
            assertThat(marked.resolutionTime()).isEqualTo(timestampFromInstant(testConsensusTime));

            assertThat(marked.deleted()).isEqualTo(expected.deleted());
            assertThat(marked.signatories()).containsExactlyElementsOf(expected.signatories());

            verifyPartialEquality(marked, expected);
            assertThat(marked.scheduleId()).isEqualTo(expected.scheduleId());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void replaceSignatoriesModifiesSchedule() {
        // The utility method call should modify the return only by replacing signatories.
        // No other value should change, and the original Schedule should not change.
        final Set<Key> fakeSignatories = Set.of(schedulerKey, adminKey);
        for (final Schedule expected : listOfScheduledOptions) {
            final Schedule modified = HandlerUtility.replaceSignatories(expected, fakeSignatories);
            // AssertJ is terrible at inverse conditions, and the condition definitions are REALLY bad
            // Too much effort and confusing syntax for something that should be
            // "assertThat(modified.signatories()).not().containsExactlyInAnyOrderElementsOf(expected.signatories())"
            final var signatoryCondition = new ContainsAllElements(expected.signatories());
            assertThat(modified.signatories()).is(BDDAssertions.not(signatoryCondition));
            assertThat(modified.signatories()).containsExactlyInAnyOrderElementsOf(fakeSignatories);

            assertThat(modified.executed()).isEqualTo(expected.executed());
            assertThat(modified.resolutionTime()).isEqualTo(expected.resolutionTime());
            assertThat(modified.deleted()).isEqualTo(expected.deleted());

            verifyPartialEquality(modified, expected);
            assertThat(modified.scheduleId()).isEqualTo(expected.scheduleId());
        }
    }

    @Test
    void replaceSignatoriesAndMarkExecutedMakesBothModifications() {
        // The utility method call should modify the return only by replacing signatories and setting executed to true.
        // No other value should change, and the original Schedule should not change.
        final Set<Key> fakeSignatories = Set.of(payerKey, adminKey);
        for (final Schedule expected : listOfScheduledOptions) {
            final Schedule modified =
                    HandlerUtility.replaceSignatoriesAndMarkExecuted(expected, fakeSignatories, testConsensusTime);

            // AssertJ is terrible at inverse conditions, and the condition definitions are REALLY bad
            // Too much effort and confusing syntax for something that should be as simple as
            // "assertThat(modified.signatories()).not().containsExactlyInAnyOrderElementsOf(expected.signatories())"
            final ContainsAllElements<Key> signatoryCondition = new ContainsAllElements<>(expected.signatories());
            assertThat(modified.signatories()).is(BDDAssertions.not(signatoryCondition));
            assertThat(modified.signatories()).containsExactlyInAnyOrderElementsOf(fakeSignatories);

            assertThat(modified.executed()).isTrue();
            assertThat(modified.resolutionTime()).isNotEqualTo(expected.resolutionTime());
            assertThat(modified.resolutionTime()).isEqualTo(timestampFromInstant(testConsensusTime));
            assertThat(modified.deleted()).isEqualTo(expected.deleted());

            verifyPartialEquality(modified, expected);
            assertThat(modified.scheduleId()).isEqualTo(expected.scheduleId());
        }
    }

    @Test
    void createProvisionalScheduleCreatesCorrectSchedule() {
        // Creating a provisional schedule should produce the expected Schedule except for Schedule ID.
        for (final Schedule next : listOfScheduledOptions) {
            final TransactionBody createTransaction = next.originalCreateTransaction();
            final String createMemo = createTransaction.scheduleCreate().memo();
            final boolean createWait = createTransaction.scheduleCreate().waitForExpiry();
            final Schedule.Builder build = next.copyBuilder().memo(createMemo);
            final Schedule expected = build.waitForExpiry(createWait).build();
            final long maxLifeSeconds = scheduleConfig.maxExpirationFutureSeconds();
            final Schedule modified = HandlerUtility.createProvisionalSchedule(
                    createTransaction, testConsensusTime, maxLifeSeconds, true);

            assertThat(modified.executed()).isEqualTo(expected.executed());
            assertThat(modified.deleted()).isEqualTo(expected.deleted());
            assertThat(modified.resolutionTime()).isEqualTo(expected.resolutionTime());
            assertThat(modified.signatories()).containsExactlyElementsOf(expected.signatories());

            verifyPartialEquality(modified, expected);
            assertThat(modified.hasScheduleId()).isFalse();
        }
    }

    @Test
    void completeProvisionalScheduleModifiesWithNewId() {
        final Set<Key> fakeSignatories = Set.of(payerKey, adminKey, schedulerKey);
        final long testEntityNumber = 1791L;
        // Completing a provisional schedule should produce the exact same Schedule except for Schedule ID.
        for (final Schedule expected : listOfScheduledOptions) {
            final TransactionBody createTransaction = expected.originalCreateTransaction();
            final AccountID baseId = createTransaction.transactionID().accountID();
            final ScheduleID expectedId = new ScheduleID(baseId.shardNum(), baseId.realmNum(), testEntityNumber);
            final long maxLifeSeconds = scheduleConfig.maxExpirationFutureSeconds();
            final Schedule provisional = HandlerUtility.createProvisionalSchedule(
                    createTransaction, testConsensusTime, maxLifeSeconds, false);
            final Schedule completed =
                    HandlerUtility.completeProvisionalSchedule(provisional, testEntityNumber, fakeSignatories);

            assertThat(completed.scheduleId()).isNotEqualTo(provisional.scheduleId());
            assertThat(completed.scheduleId()).isEqualTo(expectedId);
            assertThat(completed.executed()).isEqualTo(provisional.executed());
            assertThat(completed.deleted()).isEqualTo(provisional.deleted());
            assertThat(completed.resolutionTime()).isEqualTo(provisional.resolutionTime());
            assertThat(completed.signatories()).containsExactlyElementsOf(fakeSignatories);

            verifyPartialEquality(completed, provisional);
        }
    }

    /**
     * Verify that "actual" is equal to "expected" with respect to almost all values.
     * <p> The following attributes are not verified here:
     * <ul>
     *     <li>schedule ID</li>
     *     <li>executed</li>
     *     <li>deleted</li>
     *     <li>resolution time</li>
     *     <li>signatories</li>
     * </ul>
     * These "un verified" values are what different tests expect to modify, so the specific tests verify each
     * value is, or is not, modified as appropriate for that test.
     * @param expected the expected values to match
     * @param actual the actual values to verify
     * @throws AssertionError if any verified value is not equal between the two parameters.
     */
    private static void verifyPartialEquality(final Schedule actual, final Schedule expected) {
        assertThat(actual.originalCreateTransaction()).isEqualTo(expected.originalCreateTransaction());
        assertThat(actual.memo()).isEqualTo(expected.memo());
        assertThat(actual.calculatedExpirationSecond()).isEqualTo(expected.calculatedExpirationSecond());
        assertThat(actual.providedExpirationSecond()).isEqualTo(expected.providedExpirationSecond());
        assertThat(actual.adminKey()).isEqualTo(expected.adminKey());
        assertThat(actual.payerAccountId()).isEqualTo(expected.payerAccountId());
        assertThat(actual.scheduledTransaction()).isEqualTo(expected.scheduledTransaction());
        assertThat(actual.schedulerAccountId()).isEqualTo(expected.schedulerAccountId());
        assertThat(actual.waitForExpiry()).isEqualTo(expected.waitForExpiry());
        assertThat(actual.scheduleValidStart()).isEqualTo(expected.scheduleValidStart());
    }

    private Timestamp timestampFromInstant(final Instant valueToConvert) {
        return new Timestamp(valueToConvert.getEpochSecond(), valueToConvert.getNano());
    }

    /**
     * AssertJ condition to match.
     * AssertJ is extremely bad at generic capture, forcing everything at least one superclass
     * up.  As a result this is required to match {@code Condition<?>} rather than the known collection
     * type.
     * <p>
     * The consequence is that the condition fails confusingly for results that are different types, rather than
     * the compiler detecting a change.
     *
     * @param <T> the type of the Collection this condition is expected to support.
     */
    private static class ContainsAllElements<T> extends Condition<Collection<?>> {
        private final Collection<T> valuesToMatch;

        public ContainsAllElements(@Nullable final Collection<T> expected) {
            valuesToMatch = expected;
        }

        @Override
        public boolean matches(final Collection<?> value) {
            return !(Collections.disjoint(value, valuesToMatch));
        }
    }
}
