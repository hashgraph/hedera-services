package com.hedera.services.queries.schedule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
class ScheduleAnswersTest {
    GetScheduleInfoAnswer scheduleInfo;

    @BeforeEach
    private void setup() {
        scheduleInfo = mock(GetScheduleInfoAnswer.class);
    }

    @Test
    void getsQueryBalance() {
        // given:
        ScheduleAnswers subject = new ScheduleAnswers(scheduleInfo);

        // expect:
        assertSame(scheduleInfo, subject.getScheduleInfo());
    }
}