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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.exceptions.ReferenceCountException;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import org.junit.Test;

public class SignedStateWrapperTests {

    private static final int INITIAL_NUM = 0;

    @Test
    public void testReservations() {
        final SignedState signedState = newSignedState();
        assertEquals(0, signedState.getReservationCount(), "Incorrect number of reservations at start");

        final SignedStateWrapper wrapper = new SignedStateWrapper(signedState);

        assertNotNull(wrapper.get(), "Signed state should not be null");
        assertEquals(signedState, wrapper.get(), "Signed state should be the same as provided");
        assertEquals(1, wrapper.get().getReservationCount(), "Incorrect number of reservations after wrapper creation");
        assertFalse(wrapper.isDestroyed(), "Wrapper should not be destroyed until released");

        wrapper.release();

        assertTrue(wrapper.isDestroyed(), "Wrapper should be destroyed after release");
        assertThrows(
                ReferenceCountException.class,
                wrapper::get,
                "Exception should be thrown when attempting to get the resource after the wrapper is destroyed");

        assertEquals(-1, signedState.getReservationCount(), "Incorrect number of reservations after wrapper release");
    }

    @Test
    public void testNull() {
        final SignedStateWrapper wrapper = assertDoesNotThrow(() -> new SignedStateWrapper(null));
        assertNull(wrapper.get(), "Signed state should be null");
        assertFalse(wrapper.isDestroyed(), "Wrapper should not be destroyed until released");

        wrapper.release();

        assertTrue(wrapper.isDestroyed(), "Wrapper should be destroyed after release");
    }

    @Test
    public void testExtraReservations() {
        final SignedState signedState = newSignedState();
        signedState.reserve();

        final SignedStateWrapper wrapper = new SignedStateWrapper(signedState);
        wrapper.get().reserve();

        wrapper.release();

        assertTrue(wrapper.isDestroyed(), "Wrapper should be destroyed after release");
        assertThrows(
                ReferenceCountException.class,
                wrapper::get,
                "Exception should be thrown when attempting to get the resource after the wrapper is destroyed");

        final int expectedReservations = INITIAL_NUM + 2;

        assertEquals(
                expectedReservations,
                signedState.getReservationCount(),
                "Incorrect number of reservations on signed state after wrapper release");
    }

    private static SignedState newSignedState() {
        final SignedState signedState = new RandomSignedStateGenerator().build();
        assertEquals(
                INITIAL_NUM,
                signedState.getReservationCount(),
                "Incorrect number of reservations after signed state creation");
        return signedState;
    }
}
