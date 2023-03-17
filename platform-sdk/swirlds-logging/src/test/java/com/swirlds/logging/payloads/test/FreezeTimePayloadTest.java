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

package com.swirlds.logging.payloads.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.swirlds.logging.payloads.AbstractLogPayload;
import com.swirlds.logging.payloads.ApplicationDualStatePayload;
import com.swirlds.logging.payloads.SetFreezeTimePayload;
import com.swirlds.logging.payloads.SetLastFrozenTimePayload;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FreezeTimePayloadTest {
    @Test
    void freezeTimePayloadTest() {
        Instant now = Instant.now();
        Instant next = now.plusSeconds(100);

        // Test SetFreezeTimePayload
        SetFreezeTimePayload setFreezeTimePayload = new SetFreezeTimePayload(now);
        assertEquals(now, setFreezeTimePayload.getFreezeTime(), "Freezetime should match NOW timestamp");

        setFreezeTimePayload.setFreezeTime(next);
        assertEquals(next, setFreezeTimePayload.getFreezeTime(), "Freezetime should match NEXT timestamp");

        // Test SetLastFrozenTimePayload
        SetLastFrozenTimePayload setLastFrozenTimePayload = new SetLastFrozenTimePayload(now);
        assertEquals(now, setLastFrozenTimePayload.getLastFrozenTime(), "LastFrozenTime should match NOW timestamp");

        setLastFrozenTimePayload.setLastFrozenTime(next);
        assertEquals(next, setLastFrozenTimePayload.getLastFrozenTime(), "LastFrozenTime should match NEXT timestamp");

        // Test ApplicationDualStatePayload
        ApplicationDualStatePayload applicationDualStatePayload = new ApplicationDualStatePayload(now, null);
        assertNull(applicationDualStatePayload.getLastFrozenTime());
        assertEquals(now, applicationDualStatePayload.getFreezeTime(), "Freezetime should match NOW timestamp");

        applicationDualStatePayload.setFreezeTime(next);
        assertEquals(next, applicationDualStatePayload.getFreezeTime(), "Freezetime should match NEXT timestamp");

        applicationDualStatePayload.setLastFrozenTime(next);
        assertEquals(
                next, applicationDualStatePayload.getLastFrozenTime(), "LastFrozenTime should match NEXT timestamp");
    }

    @Test
    void serializationTest() {
        SetFreezeTimePayload setFreezeTimePayload = new SetFreezeTimePayload(Instant.now());
        SetFreezeTimePayload parsedsetFreezeTimePayload =
                AbstractLogPayload.parsePayload(SetFreezeTimePayload.class, setFreezeTimePayload.toString());
        assertEquals(
                parsedsetFreezeTimePayload.getFreezeTime(),
                setFreezeTimePayload.getFreezeTime(),
                "Freezetime should match");

        SetLastFrozenTimePayload setLastFrozenTimePayload = new SetLastFrozenTimePayload(Instant.now());
        SetLastFrozenTimePayload parsedsetLastFrozenTimePayload =
                AbstractLogPayload.parsePayload(SetLastFrozenTimePayload.class, setLastFrozenTimePayload.toString());
        assertEquals(
                parsedsetLastFrozenTimePayload.getLastFrozenTime(),
                setLastFrozenTimePayload.getLastFrozenTime(),
                "LastFrozenTime should match");

        ApplicationDualStatePayload applicationDualStatePayload =
                new ApplicationDualStatePayload(Instant.now(), Instant.now().plusSeconds(100));
        ApplicationDualStatePayload parsedApplicationDualStatePayload = AbstractLogPayload.parsePayload(
                ApplicationDualStatePayload.class, applicationDualStatePayload.toString());
        assertEquals(
                parsedApplicationDualStatePayload.getFreezeTime(),
                applicationDualStatePayload.getFreezeTime(),
                "Freezetime should match");
        assertEquals(
                parsedApplicationDualStatePayload.getLastFrozenTime(),
                applicationDualStatePayload.getLastFrozenTime(),
                "LastFrozenTime should match");
    }
}
