/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.test.state.DummySwirldState2;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.test.framework.TestTypeTags;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Background Hash Checker Tests")
class BackgroundHashCheckerTests {

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Test BackgroundHashChecker")
    void testBackgroundHashChecker() throws InterruptedException {

        final AtomicReference<SignedState> signedState = new AtomicReference<>();

        final Supplier<AutoCloseableWrapper<SignedState>> supplier = () -> {
            final SignedState ss = signedState.get();
            return new AutoCloseableWrapper<>(ss, () -> {});
        };

        final List<SignedState> validStates = new LinkedList<>();
        final List<SignedState> invalidStates = new LinkedList<>();

        final BackgroundHashChecker checker =
                new BackgroundHashChecker(getStaticThreadManager(), supplier, validStates::add, invalidStates::add);

        // Sleep for a while, let the checker observe a null state
        TimeUnit.MILLISECONDS.sleep(200);

        // Give the checker some valid states. Sleep for long enough to ensure that states are checked multiple times.
        final SignedState validState1 = new SignedState(new State());
        validState1.getState().setPlatformState(new PlatformState());
        validState1.getState().getPlatformState().setPlatformData(new PlatformData());
        validState1.getState().getPlatformState().getPlatformData().setEvents(new EventImpl[0]);
        validState1.getState().getPlatformState().getPlatformData().setMinGenInfo(List.of());
        validState1.getState().getPlatformState().getPlatformData().setRound(1);
        validState1.getState().setSwirldState(new DummySwirldState2());
        MerkleCryptoFactory.getInstance().digestTreeSync(validState1.getState());
        signedState.set(validState1);
        TimeUnit.MILLISECONDS.sleep(200);

        final SignedState validState2 = new SignedState(new State());
        validState2.getState().setPlatformState(new PlatformState());
        validState2.getState().getPlatformState().setPlatformData(new PlatformData());
        validState2.getState().getPlatformState().getPlatformData().setEvents(new EventImpl[0]);
        validState2.getState().getPlatformState().getPlatformData().setMinGenInfo(List.of());
        validState2.getState().getPlatformState().getPlatformData().setRound(2);
        validState2.getState().setSwirldState(new DummySwirldState2());
        MerkleCryptoFactory.getInstance().digestTreeSync(validState2.getState());
        signedState.set(validState2);
        TimeUnit.MILLISECONDS.sleep(200);

        final SignedState validState3 = new SignedState(new State());
        validState3.getState().setPlatformState(new PlatformState());
        validState3.getState().getPlatformState().setPlatformData(new PlatformData());
        validState3.getState().getPlatformState().getPlatformData().setEvents(new EventImpl[0]);
        validState3.getState().getPlatformState().getPlatformData().setMinGenInfo(List.of());
        validState3.getState().getPlatformState().getPlatformData().setRound(3);
        validState3.getState().setSwirldState(new DummySwirldState2());
        MerkleCryptoFactory.getInstance().digestTreeSync(validState3.getState());
        signedState.set(validState3);
        TimeUnit.MILLISECONDS.sleep(200);

        // Give the checker an invalid state
        final SignedState invalidState4 = new SignedState(new State());
        invalidState4.getState().setPlatformState(new PlatformState());
        invalidState4.getState().getPlatformState().setPlatformData(new PlatformData());
        invalidState4.getState().getPlatformState().getPlatformData().setEvents(new EventImpl[0]);
        invalidState4.getState().getPlatformState().getPlatformData().setMinGenInfo(List.of());
        invalidState4.getState().getPlatformState().getPlatformData().setRound(4);
        invalidState4.getState().setSwirldState(new DummySwirldState2());
        MerkleCryptoFactory.getInstance().digestTreeSync(invalidState4.getState());
        invalidState4.getState().getSwirldState().setHash(null);
        signedState.set(invalidState4);
        TimeUnit.MILLISECONDS.sleep(200);

        // And a final good state
        final SignedState validState5 = new SignedState(new State());
        validState5.getState().setPlatformState(new PlatformState());
        validState5.getState().getPlatformState().setPlatformData(new PlatformData());
        validState5.getState().getPlatformState().getPlatformData().setEvents(new EventImpl[0]);
        validState5.getState().getPlatformState().getPlatformData().setMinGenInfo(List.of());
        validState5.getState().getPlatformState().getPlatformData().setRound(5);
        validState5.getState().setSwirldState(new DummySwirldState2());
        MerkleCryptoFactory.getInstance().digestTreeSync(validState5.getState());
        signedState.set(validState5);
        TimeUnit.MILLISECONDS.sleep(200);

        assertTrue(checker.isAlive(), "background checker should be alive");

        checker.stop();
        checker.join(1000);
        assertFalse(checker.isAlive(), "background checker should have died");

        assertEquals(4, validStates.size(), "there should be 4 valid states");
        assertEquals(1, invalidStates.size(), "there should be 1 invalid state");

        assertSame(validState1, validStates.get(0), "expected to find different state in position");
        assertSame(validState2, validStates.get(1), "expected to find different state in position");
        assertSame(validState3, validStates.get(2), "expected to find different state in position");
        assertSame(validState5, validStates.get(3), "expected to find different state in position");

        assertSame(invalidState4, invalidStates.get(0), "expected to find different state in position");
    }
}
