/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static com.hedera.test.factories.scenarios.ContractCreateScenarios.KNOWN_SCHEDULE_IMMUTABLE_ID;
import static com.hedera.test.factories.scenarios.ContractCreateScenarios.KNOWN_SCHEDULE_WITH_ADMIN_ID;
import static com.hedera.test.factories.scenarios.ScheduleDeleteScenarios.SCHEDULE_DELETE_WITH_KNOWN_SCHEDULE;
import static com.hedera.test.factories.scenarios.ScheduleDeleteScenarios.SCHEDULE_DELETE_WITH_MISSING_SCHEDULE;
import static com.hedera.test.factories.scenarios.ScheduleDeleteScenarios.SCHEDULE_DELETE_WITH_MISSING_SCHEDULE_ADMIN_KEY;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleDeleteHandler;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.fixtures.state.MapReadableStates;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableKVStateBase;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.StateKeyAdapter;
import com.hedera.test.utils.TestFixturesKeyLookup;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleDeleteHandlerParityTest {
    static final KeyTree ADMIN_KEY = KeyTree.withRoot(ed25519());
    private final ScheduleDeleteHandler subject = new ScheduleDeleteHandler();
    private AccountAccess keyLookup;
    private ReadableScheduleStore scheduleStore;

    @BeforeEach
    void setUp() {
        keyLookup = AdapterUtils.wellKnownKeyLookupAt();
    }

    @Test
    void getsScheduleDeleteWithMissingSchedule() throws Throwable {
        final var theTxn = txnFrom(SCHEDULE_DELETE_WITH_MISSING_SCHEDULE);
        scheduleStore = AdapterUtils.mockSchedule(
                999L, ADMIN_KEY, theTxn); // use any schedule id that does not match UNKNOWN_SCHEDULE_ID
        final var context = new PreHandleContext(keyLookup, theTxn);

        assertThrowsPreCheck(() -> subject.preHandle(context, scheduleStore), INVALID_SCHEDULE_ID);
    }

    @Test
    void getsScheduleDeleteWithMissingAdminKey() throws Throwable {
        final var theTxn = txnFrom(SCHEDULE_DELETE_WITH_MISSING_SCHEDULE_ADMIN_KEY);
        scheduleStore = AdapterUtils.mockSchedule(
                IdUtils.asSchedule(KNOWN_SCHEDULE_IMMUTABLE_ID).getScheduleNum(), null, theTxn);
        final var context = new PreHandleContext(keyLookup, theTxn);

        assertThrowsPreCheck(() -> subject.preHandle(context, scheduleStore), SCHEDULE_IS_IMMUTABLE);
    }

    @Test
    void getsScheduleDeleteKnownSchedule() throws Throwable {
        final var theTxn = txnFrom(SCHEDULE_DELETE_WITH_KNOWN_SCHEDULE);
        scheduleStore = AdapterUtils.mockSchedule(
                IdUtils.asSchedule(KNOWN_SCHEDULE_WITH_ADMIN_ID).getScheduleNum(), ADMIN_KEY, theTxn);
        final var context = new PreHandleContext(keyLookup, theTxn);
        subject.preHandle(context, scheduleStore);

        assertTrue(context.requiredNonPayerKeys().contains(ADMIN_KEY.asPbjKey()));
    }

    private TransactionBody txnFrom(final TxnHandlingScenario scenario) {
        try {
            return PbjConverter.toPbj(scenario.platformTxn().getTxn());
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }
}

class AdapterUtils {
    // NOTE: This class is duplicated in more than one service module.
    // !!!!!!!!!!🔥🔥🔥 It should be deleted once we find where to keep it. 🔥🔥🔥!!!!!!!!!!!
    private static final String ACCOUNTS_KEY = "ACCOUNTS";
    private static final ScheduleVirtualValue schedule = mock(ScheduleVirtualValue.class);

    @SuppressWarnings("unchecked")
    private static final ReadableKVStateBase<Long, ScheduleVirtualValue> schedulesById =
            (ReadableKVStateBase<Long, ScheduleVirtualValue>) mock(ReadableKVStateBase.class);

    private AdapterUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Returns the {@link AccountAccess} containing the "well-known" accounts that exist in a
     * {@code SigRequirementsTest} scenario. This allows us to re-use these scenarios in unit tests
     * that require an {@link AccountAccess}.
     *
     * @return the well-known account store
     */
    public static AccountAccess wellKnownKeyLookupAt() {
        return new TestFixturesKeyLookup(mockStates(Map.of(ACCOUNTS_KEY, wellKnownAccountsState())));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static ReadableStates mockStates(final Map<String, ReadableKVState> keysToMock) {
        final var mockStates = mock(ReadableStates.class);
        keysToMock.forEach((key, state) -> given(mockStates.get(key)).willReturn(state));
        return mockStates;
    }

    public static ReadableScheduleStore mockSchedule(Long schedId, KeyTree key, TransactionBody txnBody)
            throws DecoderException {
        final ScheduleID scheduleID =
                ScheduleID.newBuilder().scheduleNum(schedId).build();
        given(schedule.adminKey()).willReturn(key == null ? Optional.empty() : Optional.of(key.asJKey()));
        given(schedule.ordinaryViewOfScheduledTxn()).willReturn(PbjConverter.fromPbj(txnBody));
        given(schedulesById.get(scheduleID.scheduleNum())).willReturn(schedule);
        return new ReadableScheduleStore(new MapReadableStates(Map.of("SCHEDULES_BY_ID", schedulesById)));
    }

    private static ReadableKVState<EntityNumVirtualKey, ? extends HederaAccount> wellKnownAccountsState() {
        final var wrappedState = new MapReadableKVState<>(ACCOUNTS_KEY, TxnHandlingScenario.wellKnownAccounts());
        return new StateKeyAdapter<>(wrappedState, EntityNumVirtualKey::asEntityNum);
    }
}
