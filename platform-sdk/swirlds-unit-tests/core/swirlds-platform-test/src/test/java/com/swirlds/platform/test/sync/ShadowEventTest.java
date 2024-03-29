/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.sync;

import static com.swirlds.platform.test.fixtures.event.EventImplTestUtils.createEventImpl;
import static com.swirlds.platform.test.sync.EventEquality.identicalHashes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.gossip.shadowgraph.ShadowEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("shadow event tests")
class ShadowEventTest {
    private TestingEventBuilder builder;

    @BeforeEach
    void setUp() {
        final Random random = RandomUtils.getRandomPrintSeed();
        builder = TestingEventBuilder.builder(random);
    }

    @Test
    @DisplayName("toString")
    void testToString() {
        final EventImpl e = createEventImpl(builder, null, null);
        final EventImpl esp = createEventImpl(builder, null, null);
        final EventImpl eop = createEventImpl(builder, null, null);

        final ShadowEvent ssp = new ShadowEvent(esp);
        final ShadowEvent sop = new ShadowEvent(eop);

        final ShadowEvent s = new ShadowEvent(e, ssp, sop);
        final String str;

        str = s.toString();

        assertTrue(str.contains("sp"), "a shadow event string should annotate its self-parent");
        assertTrue(str.contains("op"), "a shadow event string should annotate its other-parent");
    }

    @Test
    @DisplayName("equals")
    void testEquals() {
        final EventImpl e0 = createEventImpl(builder, null, null);
        final EventImpl e1 = createEventImpl(builder, null, null);

        final ShadowEvent s0 = new ShadowEvent(e0);
        final ShadowEvent s1 = new ShadowEvent(e1);

        assertEquals(s0, s0, "Every shadow event compares equal to itself");

        assertNotEquals(s0, s1, "two shadow events with different event hashes must not compare equal");

        assertNotEquals(
                s0,
                new Object(),
                "A shadow event instance must not compare to equal to an instance of a non-derived type");

        assertNotNull(s0, "A shadow event instance must not compare to equal to null");
    }

    @Test
    @DisplayName("parent and children getters")
    void testGetters() {
        final EventImpl e = createEventImpl(builder, null, null);
        final EventImpl esp = createEventImpl(builder, null, null);
        final EventImpl eop = createEventImpl(builder, null, null);

        final ShadowEvent ssp = new ShadowEvent(esp);
        final ShadowEvent sop = new ShadowEvent(eop);

        final ShadowEvent s = new ShadowEvent(e, ssp, sop);

        assertTrue(identicalHashes(s.getSelfParent().getEvent(), ssp.getEvent()), "expected SP");
        assertTrue(identicalHashes(s.getOtherParent().getEvent(), sop.getEvent()), "expected OP");

        assertSame(s.getEvent(), e, "getting the EventImpl should give the EventImpl instnace itself");
    }

    @Test
    @DisplayName("disconnect an event")
    void testDisconnect() {
        final EventImpl e = createEventImpl(builder, null, null);
        final EventImpl esp = createEventImpl(builder, null, null);
        final EventImpl eop = createEventImpl(builder, null, null);

        final ShadowEvent ssp = new ShadowEvent(esp);
        final ShadowEvent sop = new ShadowEvent(eop);

        final ShadowEvent s = new ShadowEvent(e, ssp, sop);

        assertNotNull(s.getSelfParent(), "SP should not be null before disconnect");

        assertNotNull(s.getOtherParent(), "OP should not be null before disconnect");

        s.disconnect();

        assertNull(s.getSelfParent(), "SP should be null after disconnect");

        assertNull(s.getOtherParent(), "OP should be null after disconnect");
    }

    @Test
    @DisplayName("the hash of a shadow event is the hash of the referenced hashgraph event")
    void testHash() {
        final EventImpl e = createEventImpl(builder, null, null);
        final EventImpl esp = createEventImpl(builder, null, null);
        final EventImpl eop = createEventImpl(builder, null, null);

        // Parents, unlinked
        final ShadowEvent ssp = new ShadowEvent(esp);
        final ShadowEvent sop = new ShadowEvent(eop);

        // The shadow event, linked
        final ShadowEvent s = new ShadowEvent(e, ssp, sop);

        // The hash of an event Shadow is the hash of the event
        assertEquals(e.getBaseHash(), s.getEventBaseHash(), "false");
    }

    @Test
    @DisplayName("parents linked by construction")
    void testLinkedConstruction() {
        final EventImpl e = createEventImpl(builder, null, null);
        final EventImpl esp = createEventImpl(builder, null, null);
        final EventImpl eop = createEventImpl(builder, null, null);

        // Parents, unlinked
        final ShadowEvent ssp = new ShadowEvent(esp);
        final ShadowEvent sop = new ShadowEvent(eop);

        // The shadow event, linked
        final ShadowEvent s = new ShadowEvent(e, ssp, sop);

        testLinkedConstruction(s, ssp, sop);
    }

    private static void testLinkedConstruction(final ShadowEvent s, final ShadowEvent ssp, final ShadowEvent sop) {
        testSP(s, ssp);
        testOP(s, sop);
    }

    @Test
    @DisplayName("no links when constructed without other events")
    void testUnlinkedConstruction() {
        final EventImpl e = createEventImpl(builder, null, null);
        final ShadowEvent s = new ShadowEvent(e);

        testUnlinkedConstruction(s);
    }

    private static void testUnlinkedConstruction(final ShadowEvent s) {
        assertNull(s.getSelfParent(), "");
        assertNull(s.getOtherParent(), "");
    }

    private static void testSP(final ShadowEvent s, final ShadowEvent ssp) {
        assertEquals(ssp, s.getSelfParent(), "expect given SP == SP of shadow event");
    }

    private static void testOP(final ShadowEvent s, final ShadowEvent sop) {
        assertEquals(sop, s.getOtherParent(), "expect given OP == OP of shadow event");
    }
}
