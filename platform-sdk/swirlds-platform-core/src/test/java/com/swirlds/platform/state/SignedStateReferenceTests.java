/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SignedStateReference Tests")
class SignedStateReferenceTests {

    /**
     * Build a mock signed state.
     *
     * @param refCountDelta an atomic integer that is incremented/decremented whenever a reservation is taken/released
     */
    public static SignedState buildSignedState(final AtomicInteger refCountDelta) {

        final SignedState signedState = mock(SignedState.class);

        doAnswer(invocation -> {
                    refCountDelta.getAndIncrement();
                    return null;
                })
                .when(signedState)
                .reserve();

        doAnswer(invocation -> {
                    assertTrue(refCountDelta.decrementAndGet() >= 0, "reference count should never be negative");
                    return null;
                })
                .when(signedState)
                .release();

        return signedState;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Null Initial Value Test")
    void nullInitialValueTest(final boolean defaultValue) {
        final SignedStateReference reference;
        if (defaultValue) {
            reference = new SignedStateReference();
        } else {
            reference = new SignedStateReference(null);
        }

        assertTrue(reference.isNull(), "should point to a null value");
        assertEquals(-1, reference.getRound(), "round should be -1 if state is null");

        final AutoCloseableWrapper<SignedState> wrapper1 = reference.getAndReserve();
        assertNotNull(wrapper1, "wrapper should never be null");
        assertNull(wrapper1.get(), "state should be null");
        wrapper1.close();

        final AutoCloseableWrapper<SignedState> wrapper2 = reference.getAndReserve();
        assertNotNull(wrapper2, "wrapper should never be null");
        assertNull(wrapper2.get(), "state should be null");
        wrapper2.close();

        final AutoCloseableWrapper<SignedState> wrapper3 = reference.getAndReserve();
        assertNotNull(wrapper3, "wrapper should never be null");
        assertNull(wrapper3.get(), "state should be null");
        wrapper3.close();

        // This should not break anything
        reference.set(null);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Initial Value Constructor Test")
    void initialValueTest(final boolean defaultValue) {

        final AtomicInteger count = new AtomicInteger();

        final SignedState state = buildSignedState(count);
        doReturn(1234L).when(state).getRound();

        final SignedStateReference reference;
        if (defaultValue) {
            reference = new SignedStateReference();
            reference.set(state);
        } else {
            reference = new SignedStateReference(state);
        }

        assertFalse(reference.isNull(), "should not be null");
        assertEquals(1234, reference.getRound(), "invalid round");
        assertEquals(1, count.get(), "invalid reference count");

        final AutoCloseableWrapper<SignedState> wrapper1 = reference.getAndReserve();
        assertNotNull(wrapper1, "wrapper should never be null");
        assertSame(state, wrapper1.get(), "incorrect state");
        assertEquals(2, count.get(), "incorrect reference count");
        wrapper1.close();
        assertEquals(1, count.get(), "incorrect reference count");

        reference.set(null);

        assertEquals(0, count.get(), "incorrect reference count");
    }

    @Test
    @DisplayName("Replacement Test")
    void replacementTest() {
        final AtomicInteger count1 = new AtomicInteger();
        final SignedState state1 = buildSignedState(count1);

        final AtomicInteger count2 = new AtomicInteger();
        final SignedState state2 = buildSignedState(count2);

        final SignedStateReference reference = new SignedStateReference(state1);
        assertEquals(1, count1.get(), "incorrect reference count");
        assertEquals(0, count2.get(), "incorrect reference count");

        // replace value with itself
        reference.set(state1);
        assertEquals(1, count1.get(), "incorrect reference count");
        assertEquals(0, count2.get(), "incorrect reference count");

        // replace non-null value with non-null value
        reference.set(state2);
        assertEquals(0, count1.get(), "incorrect reference count");
        assertEquals(1, count2.get(), "incorrect reference count");

        // replace non-null value with null
        reference.set(null);
        assertEquals(0, count1.get(), "incorrect reference count");
        assertEquals(0, count2.get(), "incorrect reference count");

        // replace null with null
        reference.set(null);
        assertEquals(0, count1.get(), "incorrect reference count");
        assertEquals(0, count2.get(), "incorrect reference count");

        // replace null with non-null value
        reference.set(state1);
        assertEquals(1, count1.get(), "incorrect reference count");
        assertEquals(0, count2.get(), "incorrect reference count");
    }
}
