/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.perf;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class PerfUtilOps {
    public static HapiSpecOperation mgmtOfIntProp(AtomicInteger value, String prop) {
        return withOpContext((spec, opLog) -> {
            var ciProps = spec.setup().ciPropertiesMap();
            if (ciProps.has(prop)) {
                value.set(ciProps.getInteger(prop));
            }
        });
    }

    public static HapiSpecOperation mgmtOfBooleanProp(AtomicBoolean value, String prop) {
        return withOpContext((spec, opLog) -> {
            var ciProps = spec.setup().ciPropertiesMap();
            if (ciProps.has(prop)) {
                value.set(ciProps.getBoolean(prop));
            }
        });
    }

    public static HapiSpecOperation stdMgmtOf(
            AtomicLong duration, AtomicReference<TimeUnit> unit, AtomicInteger maxOpsPerSec) {
        return stdMgmtOf(duration, unit, maxOpsPerSec, "");
    }

    public static HapiSpecOperation stdMgmtOf(
            AtomicLong duration, AtomicReference<TimeUnit> unit, AtomicInteger maxOpsPerSec, String ciPropPrefix) {
        return withOpContext((spec, opLog) -> {
            final var durationProp = ciPropPrefix + "duration";
            var ciProps = spec.setup().ciPropertiesMap();
            if (ciProps.has(durationProp)) {
                duration.set(ciProps.getLong(durationProp));
            }
            final var unitProp = ciPropPrefix + "unit";
            if (ciProps.has(unitProp)) {
                unit.set(ciProps.getTimeUnit(unitProp));
            }
            final var opsPerSecProp = ciPropPrefix + "maxOpsPerSec";
            if (ciProps.has(opsPerSecProp)) {
                maxOpsPerSec.set(ciProps.getInteger(opsPerSecProp));
            }
        });
    }
}
