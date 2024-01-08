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

package com.swirlds.logging;

import com.swirlds.logging.api.Marker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MarkerTest {

    @Test
    void testNullName() {
        Assertions.assertThrows(NullPointerException.class, () -> new Marker(null));
    }

    @Test
    void testSimpelMarker() {
        // given
        Marker marker = new Marker("markerName");

        // then
        Assertions.assertEquals("markerName", marker.name());
        Assertions.assertNull(marker.previous());
    }

    @Test
    void testWithParenMarker() {
        // given
        Marker parent = new Marker("parentName");
        Marker marker = new Marker("markerName", parent);

        // then
        Assertions.assertEquals("markerName", marker.name());
        Assertions.assertNotNull(marker.previous());
        Assertions.assertSame(parent, marker.previous());
        Assertions.assertNull(parent.previous());
    }

    @Test
    void testEquals() {
        // given
        Marker marker1 = new Marker("marker1");
        Marker marker2 = new Marker("marker2");
        Marker marker3 = new Marker("marker1");
        Marker marker4 = new Marker("marker1", marker2);

        // then
        Assertions.assertEquals(marker1, marker1);
        Assertions.assertEquals(marker1, marker3);
        Assertions.assertNotEquals(marker1, marker2);
        Assertions.assertNotEquals(marker1, null);
        Assertions.assertNotEquals(marker1, marker4);
    }

    @Test
    void testHashCode() {
        // given
        Marker marker1 = new Marker("marker1");
        Marker marker2 = new Marker("marker2");
        Marker marker3 = new Marker("marker1");
        Marker marker4 = new Marker("marker1", marker2);

        // then
        Assertions.assertEquals(marker1.hashCode(), marker1.hashCode());
        Assertions.assertEquals(marker1.hashCode(), marker3.hashCode());
        Assertions.assertNotEquals(marker1.hashCode(), marker2.hashCode());
        Assertions.assertNotEquals(marker1.hashCode(), marker4.hashCode());
    }
}
