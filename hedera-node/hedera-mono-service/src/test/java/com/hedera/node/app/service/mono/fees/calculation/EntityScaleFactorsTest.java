/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.fees.calculation;

import static com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ScaleFactor.ONE_TO_ONE;
import static com.hedera.node.app.service.mono.context.properties.EntityType.ACCOUNT;
import static com.hedera.node.app.service.mono.context.properties.EntityType.FILE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ScaleFactor;
import org.junit.jupiter.api.Test;

class EntityScaleFactorsTest {
    @Test
    void usesDefaultScalesIfAvailable() {
        final var csv = "DEFAULT(90,10:1,95,25:1,99,100:1),ACCOUNT(50,23:3)";

        final var subject = EntityScaleFactors.from(csv);

        final var accountAtTrigger = subject.scaleForNew(ACCOUNT, 50);
        final var accountBelowTrigger = subject.scaleForNew(ACCOUNT, 49);
        final var fileInMiddleTrigger = subject.scaleForNew(FILE, 96);

        assertEquals(new ScaleFactor(23, 3), accountAtTrigger);
        assertSame(ONE_TO_ONE, accountBelowTrigger);
        assertEquals(new ScaleFactor(25, 1), fileInMiddleTrigger);
    }

    @Test
    void allScalesAreOneToOneIfUnparseable() {
        final var csv = "DEFAULT(90,10:1,95,25:1,99,100:1),UNRECOGNIZED(50,23:3)";

        final var subject = EntityScaleFactors.from(csv);

        final var fileInHighUtil = subject.scaleForNew(FILE, 100);
        assertSame(ONE_TO_ONE, fileInHighUtil);
    }
}
