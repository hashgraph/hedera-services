/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.files.interceptors;

import static com.hedera.services.files.interceptors.FeeSchedulesManager.OK_FOR_NOW_VERDICT;
import static com.hedera.services.files.interceptors.FeeSchedulesManager.YES_VERDICT;
import static com.hedera.test.utils.IdUtils.asFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hederahashgraph.api.proto.java.FileID;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeeSchedulesManagerTest {
    private HFileMeta attr;

    public static final String R4_FEE_SCHEDULE_REPR_PATH =
            "src/test/resources/testfiles/r4FeeSchedule.bin";
    private static byte[] validBytes;
    private static byte[] invalidBytes;

    FileID feeSchedule = asFile("0.0.111");
    FileID otherFile = asFile("0.0.911");

    FeeCalculator fees;

    FeeSchedulesManager subject;

    @BeforeAll
    public static void setupAll() throws IOException {
        validBytes = Files.readAllBytes(Path.of(R4_FEE_SCHEDULE_REPR_PATH));
        invalidBytes = Arrays.copyOfRange(validBytes, 0, validBytes.length / 2 + 1);
    }

    @BeforeEach
    void setup() {
        attr = new HFileMeta(false, new JContractIDKey(1, 2, 3), Instant.now().getEpochSecond());

        fees = mock(FeeCalculator.class);

        subject = new FeeSchedulesManager(new MockFileNumbers(), fees);
    }

    @Test
    void rubberstampsIrrelevantInvocation() {
        // expect:
        assertEquals(YES_VERDICT, subject.preUpdate(otherFile, invalidBytes));
    }

    @Test
    void approvesValidSchedules() {
        // given:
        var verdict = subject.preUpdate(feeSchedule, validBytes);

        // expect:
        assertEquals(YES_VERDICT, verdict);
    }

    @Test
    void oksInvalidScheduleForNow() {
        // when:
        var verdict = subject.preUpdate(feeSchedule, invalidBytes);

        // then:
        assertEquals(OK_FOR_NOW_VERDICT, verdict);
    }

    @Test
    void reloadsOnValidUpdate() {
        // when:
        subject.postUpdate(feeSchedule, validBytes);

        // then:
        verify(fees).init();
    }

    @Test
    void doesntReloadOnInvalidUpdate() {
        // when:
        subject.postUpdate(feeSchedule, invalidBytes);

        // then:
        verify(fees, never()).init();
    }

    @Test
    void doesntReloadOnIrrelevantUpdate() {
        // when:
        subject.postUpdate(otherFile, validBytes);

        // then:
        verify(fees, never()).init();
    }

    @Test
    void hasExpectedRelevanceAndPriority() {
        // expect:
        assertTrue(subject.priorityForCandidate(otherFile).isEmpty());
        // and:
        assertEquals(0, subject.priorityForCandidate(feeSchedule).getAsInt());
    }

    @Test
    void rubberstampsPreDelete() {
        // expect:
        assertEquals(YES_VERDICT, subject.preDelete(feeSchedule));
    }

    @Test
    void rubberstampsPreChange() {
        // expect:
        assertEquals(YES_VERDICT, subject.preAttrChange(feeSchedule, attr));
    }
}
