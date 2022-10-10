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
package com.hedera.services.ledger;

import static com.hedera.services.ledger.SigImpactHistorian.ChangeStatus.CHANGED;
import static com.hedera.services.ledger.SigImpactHistorian.ChangeStatus.UNCHANGED;
import static com.hedera.services.ledger.SigImpactHistorian.ChangeStatus.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SigImpactHistorianTest {
    private static final GlobalDynamicProperties dynamicProperties = new MockGlobalDynamicProps();

    final SigImpactHistorian subject = new SigImpactHistorian(dynamicProperties);

    @Test
    void allStatusesBeginUnknown() {
        assertEquals(UNKNOWN, subject.aliasStatusSince(firstNow, aAlias));
        assertEquals(UNKNOWN, subject.entityStatusSince(firstNow, aNum));
    }

    @Test
    void removesChangeOnceOutsideWindow() {
        subject.setChangeTime(firstNow);
        subject.markAliasChanged(aAlias);
        subject.markEntityChanged(aNum);

        assertEquals(firstNow, subject.getEntityChangeTimes().get(aNum));
        assertEquals(firstNow, subject.getAliasChangeTimes().get(aAlias));

        subject.setChangeTime(nowPostFirstWindow);
        subject.purge();

        assertFalse(subject.getAliasChangeTimes().containsKey(aAlias));
        assertFalse(subject.getEntityChangeTimes().containsKey(aNum));
    }

    @Test
    void safeForMultipleExpiriesToBeScheduledAtSameConsensusSecond() {
        final var sameSecondChange = firstNow.plusNanos(1L);

        subject.setChangeTime(firstNow);
        subject.markAliasChanged(aAlias);
        subject.markEntityChanged(aNum);
        subject.setChangeTime(sameSecondChange);
        subject.markAliasChanged(aAlias);
        subject.markEntityChanged(aNum);

        subject.setChangeTime(nowPostFirstWindow);
        subject.purge();

        assertTrue(subject.getAliasChangeTimes().isEmpty());
        assertTrue(subject.getEntityChangeTimes().isEmpty());
    }

    @Test
    void ignoresExpiryIfChangeStillInWindow() {
        subject.setChangeTime(firstNow);
        subject.markAliasChanged(aAlias);
        subject.markEntityChanged(aNum);

        assertEquals(firstNow, subject.getEntityChangeTimes().get(aNum));
        assertEquals(firstNow, subject.getAliasChangeTimes().get(aAlias));

        subject.setChangeTime(nowInMiddleOfFirstWindow);
        subject.markAliasChanged(aAlias);
        subject.markEntityChanged(aNum);

        assertEquals(nowInMiddleOfFirstWindow, subject.getEntityChangeTimes().get(aNum));
        assertEquals(nowInMiddleOfFirstWindow, subject.getAliasChangeTimes().get(aAlias));

        subject.setChangeTime(nowPostFirstWindow);
        subject.purge();

        assertEquals(nowInMiddleOfFirstWindow, subject.getEntityChangeTimes().get(aNum));
        assertEquals(nowInMiddleOfFirstWindow, subject.getAliasChangeTimes().get(aAlias));
    }

    @Test
    void markedChangesExpireInNoLessThanMemorySecs() {
        final var lastMemorySec =
                firstNow.getEpochSecond() + dynamicProperties.changeHistorianMemorySecs();

        subject.setChangeTime(firstNow);
        subject.markAliasChanged(aAlias);
        subject.markEntityChanged(aNum);

        assertFalse(subject.getAliasChangeExpiries().hasExpiringAt(lastMemorySec));
        assertTrue(subject.getAliasChangeExpiries().hasExpiringAt(lastMemorySec + 1));
        assertFalse(subject.getEntityChangeExpiries().hasExpiringAt(lastMemorySec));
        assertTrue(subject.getEntityChangeExpiries().hasExpiringAt(lastMemorySec + 1));
    }

    @Test
    void invalidationResetsEverything() {
        subject.setChangeTime(firstNow);
        subject.setChangeTime(nowPostFirstWindow);
        subject.markEntityChanged(aNum);
        subject.markAliasChanged(aAlias);

        subject.invalidateCurrentWindow();

        assertFalse(subject.isFullWindowElapsed());
        assertNull(subject.getFirstNow());
        assertNull(subject.getNow());
    }

    @Test
    void recognizesChangedWithinFirstPartialWindow() {
        subject.setChangeTime(firstNow);
        subject.setChangeTime(nowInMiddleOfFirstWindow);

        assertEquals(UNKNOWN, subject.aliasStatusSince(firstNow.minusNanos(1), aAlias));
        assertEquals(
                UNKNOWN, subject.aliasStatusSince(nowInMiddleOfFirstWindow.plusNanos(1), aAlias));
        assertEquals(UNKNOWN, subject.entityStatusSince(firstNow.minusNanos(1), aNum));
        assertEquals(
                UNKNOWN, subject.entityStatusSince(nowInMiddleOfFirstWindow.plusNanos(1), aNum));

        subject.markEntityChanged(aNum);
        subject.markAliasChanged(aAlias);

        assertEquals(CHANGED, subject.aliasStatusSince(firstNow.minusNanos(1), aAlias));
        assertEquals(CHANGED, subject.aliasStatusSince(firstNow.plusNanos(1), aAlias));
        assertEquals(
                UNKNOWN, subject.aliasStatusSince(nowInMiddleOfFirstWindow.plusNanos(1), aAlias));
        assertEquals(CHANGED, subject.entityStatusSince(firstNow.minusNanos(1), aNum));
        assertEquals(CHANGED, subject.entityStatusSince(firstNow.plusNanos(1), aNum));
        assertEquals(
                UNKNOWN, subject.entityStatusSince(nowInMiddleOfFirstWindow.plusNanos(1), aNum));

        subject.setChangeTime(nowNearEndOfFirstWindow);
        assertEquals(
                UNCHANGED, subject.aliasStatusSince(nowInMiddleOfFirstWindow.plusNanos(1), aAlias));
        assertEquals(
                UNCHANGED, subject.entityStatusSince(nowInMiddleOfFirstWindow.plusNanos(1), aNum));
    }

    @Test
    void recognizesChangedWithinFirstFullWindow() {
        subject.setChangeTime(firstNow);
        subject.setChangeTime(nowInMiddleOfFirstWindow);

        assertEquals(UNKNOWN, subject.aliasStatusSince(firstNow.minusNanos(1), aAlias));
        assertEquals(
                UNKNOWN, subject.aliasStatusSince(nowInMiddleOfFirstWindow.plusNanos(1), aAlias));
        assertEquals(UNKNOWN, subject.entityStatusSince(firstNow.minusNanos(1), aNum));
        assertEquals(
                UNKNOWN, subject.entityStatusSince(nowInMiddleOfFirstWindow.plusNanos(1), aNum));

        subject.markEntityChanged(aNum);
        subject.markAliasChanged(aAlias);

        assertEquals(CHANGED, subject.aliasStatusSince(firstNow.minusNanos(1), aAlias));
        assertEquals(CHANGED, subject.aliasStatusSince(firstNow.plusNanos(1), aAlias));
        assertEquals(
                UNKNOWN, subject.aliasStatusSince(nowInMiddleOfFirstWindow.plusNanos(1), aAlias));
        assertEquals(CHANGED, subject.entityStatusSince(firstNow.minusNanos(1), aNum));
        assertEquals(CHANGED, subject.entityStatusSince(firstNow.plusNanos(1), aNum));
        assertEquals(
                UNKNOWN, subject.entityStatusSince(nowInMiddleOfFirstWindow.plusNanos(1), aNum));

        subject.setChangeTime(nowPostFirstWindow);
        assertEquals(
                UNCHANGED, subject.aliasStatusSince(nowInMiddleOfFirstWindow.plusNanos(1), aAlias));
        assertEquals(
                UNCHANGED, subject.entityStatusSince(nowInMiddleOfFirstWindow.plusNanos(1), aNum));
        assertEquals(
                UNCHANGED, subject.entityStatusSince(nowInMiddleOfFirstWindow.plusNanos(1), bNum));
        assertEquals(
                UNCHANGED, subject.aliasStatusSince(nowInMiddleOfFirstWindow.plusNanos(1), bAlias));
        final var beforeWindow =
                nowPostFirstWindow.minusSeconds(dynamicProperties.changeHistorianMemorySecs() + 1);
        assertEquals(UNKNOWN, subject.entityStatusSince(beforeWindow, bNum));
    }

    @Test
    void windowMgmtAsExpected() {
        assertFalse(subject.isFullWindowElapsed());
        assertNull(subject.getFirstNow());
        assertNull(subject.getNow());

        subject.setChangeTime(firstNow);
        assertFalse(subject.isFullWindowElapsed());
        assertSame(firstNow, subject.getFirstNow());
        assertSame(firstNow, subject.getNow());

        subject.setChangeTime(nowNearEndOfFirstWindow);
        assertFalse(subject.isFullWindowElapsed());
        assertSame(firstNow, subject.getFirstNow());
        assertSame(nowNearEndOfFirstWindow, subject.getNow());

        subject.setChangeTime(nowPostFirstWindow);
        assertTrue(subject.isFullWindowElapsed());
        assertNull(subject.getFirstNow());
        assertSame(nowPostFirstWindow, subject.getNow());
    }

    private static final Instant firstNow = Instant.ofEpochSecond(1_234_567L, 890);
    private static final Instant nowInMiddleOfFirstWindow =
            firstNow.plusSeconds(dynamicProperties.changeHistorianMemorySecs() / 2);
    private static final Instant nowNearEndOfFirstWindow =
            firstNow.plusSeconds(dynamicProperties.changeHistorianMemorySecs());
    private static final Instant nowPostFirstWindow =
            firstNow.plusSeconds(dynamicProperties.changeHistorianMemorySecs() + 1);
    private static final long aNum = 1_234L;
    private static final long bNum = 2_345L;
    private static final ByteString aAlias = ByteString.copyFromUtf8("a");
    private static final ByteString bAlias = ByteString.copyFromUtf8("b");
}
