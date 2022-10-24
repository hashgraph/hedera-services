package com.hedera.services.fees.calculation;

import com.hedera.services.sysfiles.domain.throttling.ScaleFactor;
import org.junit.jupiter.api.Test;

import static com.hedera.services.context.properties.EntityType.ACCOUNT;
import static com.hedera.services.context.properties.EntityType.FILE;
import static com.hedera.services.sysfiles.domain.throttling.ScaleFactor.ONE_TO_ONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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