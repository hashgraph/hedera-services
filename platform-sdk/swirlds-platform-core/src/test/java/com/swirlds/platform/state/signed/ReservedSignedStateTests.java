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

package com.swirlds.platform.state.signed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.exceptions.ReferenceCountException;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReservedSignedState Tests")
class ReservedSignedStateTests {

    @Test
    @DisplayName("Null State Test")
    void nullStateTest() {
        try (final ReservedSignedState reservedSignedState = new ReservedSignedState()) {
            assertThrows(NullPointerException.class, reservedSignedState::get);
            assertNull(reservedSignedState.getNullable());
            assertEquals("", reservedSignedState.getReason());
            assertTrue(reservedSignedState.isNull());
            assertFalse(reservedSignedState.isNotNull());

            try (final ReservedSignedState reservedSignedState2 = reservedSignedState.getAndReserve("reason")) {
                assertThrows(NullPointerException.class, reservedSignedState2::get);
                assertNull(reservedSignedState2.getNullable());

                // reason is ignored for null state
                assertEquals("", reservedSignedState2.getReason());
                assertTrue(reservedSignedState2.isNull());
                assertFalse(reservedSignedState2.isNotNull());

                assertNotEquals(reservedSignedState.getReservationId(), reservedSignedState2.getReservationId());
            }
        }
    }

    @Test
    @DisplayName("Non-Null State Test")
    void NonNullStateTest() {
        final SignedState signedState = new RandomSignedStateGenerator().build();
        assertEquals(0, signedState.getReservationCount());

        try (final ReservedSignedState reservedSignedState = new ReservedSignedState(signedState, "reason")) {

            assertSame(signedState, reservedSignedState.get());
            assertSame(signedState, reservedSignedState.getNullable());
            assertEquals("reason", reservedSignedState.getReason());
            assertFalse(reservedSignedState.isNull());
            assertTrue(reservedSignedState.isNotNull());
            assertEquals(1, signedState.getReservationCount());

            try (final ReservedSignedState reservedSignedState2 = reservedSignedState.getAndReserve("reason2")) {
                assertSame(signedState, reservedSignedState2.get());
                assertSame(signedState, reservedSignedState2.getNullable());
                assertEquals("reason2", reservedSignedState2.getReason());
                assertFalse(reservedSignedState2.isNull());
                assertTrue(reservedSignedState2.isNotNull());
                assertEquals(2, signedState.getReservationCount());

                assertNotEquals(reservedSignedState.getReservationId(), reservedSignedState2.getReservationId());
            }
            assertEquals(1, signedState.getReservationCount());
        }
        assertEquals(-1, signedState.getReservationCount());
    }

    @Test
    @DisplayName("Null Bad Lifecycle Test")
    void nullBadLifecycleTest() {
        final ReservedSignedState reservedSignedState = new ReservedSignedState();
        reservedSignedState.close();

        assertThrows(ReferenceCountException.class, reservedSignedState::get);
        assertThrows(ReferenceCountException.class, () -> reservedSignedState.getAndReserve("reason"));
        assertThrows(ReferenceCountException.class, reservedSignedState::getNullable);
        assertThrows(ReferenceCountException.class, reservedSignedState::isNull);
        assertThrows(ReferenceCountException.class, reservedSignedState::isNotNull);
        assertThrows(ReferenceCountException.class, reservedSignedState::close);
    }

    @Test
    @DisplayName("Non-Null Bad Lifecycle Test")
    void nonNullBadLifecycleTest() {
        final ReservedSignedState reservedSignedState =
                new ReservedSignedState(new RandomSignedStateGenerator().build(), "reason");
        reservedSignedState.close();

        assertThrows(ReferenceCountException.class, reservedSignedState::get);
        assertThrows(ReferenceCountException.class, () -> reservedSignedState.getAndReserve("reason"));
        assertThrows(ReferenceCountException.class, reservedSignedState::getNullable);
        assertThrows(ReferenceCountException.class, reservedSignedState::isNull);
        assertThrows(ReferenceCountException.class, reservedSignedState::isNotNull);
        assertThrows(ReferenceCountException.class, reservedSignedState::close);
    }
}
