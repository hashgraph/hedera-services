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
