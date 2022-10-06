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
package com.hedera.services.sigs.order;

import static com.hedera.services.ledger.SigImpactHistorian.ChangeStatus.CHANGED;
import static com.hedera.services.ledger.SigImpactHistorian.ChangeStatus.UNCHANGED;
import static com.hedera.services.ledger.SigImpactHistorian.ChangeStatus.UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.SigImpactHistorian;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkedRefsTest {
    private LinkedRefs subject = new LinkedRefs();

    @Mock private SigImpactHistorian historian;

    @Test
    void recognizesNoChangesToNumOnly() {
        given(historian.entityStatusSince(when, 1L)).willReturn(UNCHANGED);
        given(historian.entityStatusSince(when, 2L)).willReturn(UNCHANGED);
        given(historian.entityStatusSince(when, 3L)).willReturn(UNCHANGED);

        subject.setSourceSignedAt(when);
        subject.link(1L);
        subject.link(2L);
        subject.link(3L);

        assertTrue(subject.haveNoChangesAccordingTo(historian));
    }

    @Test
    void linkingNonPositiveDoesNothing() {
        subject.link(1);
        subject.link(-1);
        subject.link(0);
        subject.link(2);

        assertArrayEquals(new long[] {1, 2}, Arrays.copyOfRange(subject.linkedNumbers(), 0, 2));
    }

    @Test
    void recognizesNoChangesToNumOrAlias() {
        given(historian.entityStatusSince(when, 1L)).willReturn(UNCHANGED);
        given(historian.entityStatusSince(when, 2L)).willReturn(UNCHANGED);
        given(historian.entityStatusSince(when, 3L)).willReturn(UNCHANGED);
        given(historian.aliasStatusSince(when, alias)).willReturn(UNCHANGED);

        subject.setSourceSignedAt(when);
        subject.link(1L);
        subject.link(2L);
        subject.link(3L);
        subject.link(alias);

        assertTrue(subject.haveNoChangesAccordingTo(historian));
    }

    @Test
    void recognizesChangeToNum() {
        given(historian.entityStatusSince(when, 1L)).willReturn(UNCHANGED);
        given(historian.entityStatusSince(when, 2L)).willReturn(CHANGED);

        subject.setSourceSignedAt(when);
        subject.link(1L);
        subject.link(2L);

        assertFalse(subject.haveNoChangesAccordingTo(historian));
    }

    @Test
    void recognizesChangeToAlias() {
        given(historian.entityStatusSince(when, 1L)).willReturn(UNCHANGED);
        given(historian.entityStatusSince(when, 2L)).willReturn(UNCHANGED);
        given(historian.aliasStatusSince(when, alias)).willReturn(UNKNOWN);

        subject.setSourceSignedAt(when);
        subject.link(1L);
        subject.link(2L);
        subject.link(alias);

        assertFalse(subject.haveNoChangesAccordingTo(historian));
    }

    @Test
    void canTrackAliases() {
        final var firstAlias = ByteString.copyFromUtf8("pretend");
        final var secondAlias = ByteString.copyFromUtf8("imaginary");

        assertSame(Collections.emptyList(), subject.linkedAliases());

        subject.link(firstAlias);
        subject.link(secondAlias);

        assertEquals(List.of(firstAlias, secondAlias), subject.linkedAliases());
    }

    @Test
    void canTrackNumbers() {
        for (long i = 1; i <= 10; i++) {
            subject.link(i);
        }

        assertArrayEquals(
                new long[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10},
                Arrays.copyOfRange(subject.linkedNumbers(), 0, 10));
    }

    @Test
    void canManageSourceSigningTime() {

        subject.setSourceSignedAt(when);
        assertSame(when, subject.getSourceSignedAt());
    }

    @Test
    void toStringWorks() {
        subject.setSourceSignedAt(when);
        subject.link(1L);
        subject.link(2L);
        subject.link(3L);
        final var expectedString =
                "LinkedRefs{sourceSignedAt=1970-01-15T06:56:07Z, linkedAliases=null, linkedNums=[1,"
                        + " 2, 3, 0]}";
        assertEquals(expectedString, subject.toString());
    }

    private static final ByteString alias = ByteString.copyFromUtf8("0123456789");
    private static final Instant when = Instant.ofEpochSecond(1_234_567L);
}
