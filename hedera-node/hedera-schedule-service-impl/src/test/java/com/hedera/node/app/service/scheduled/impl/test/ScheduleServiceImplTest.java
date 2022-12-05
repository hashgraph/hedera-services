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
package com.hedera.node.app.service.scheduled.impl.test;

import com.hedera.node.app.service.mono.state.impl.InMemoryStateImpl;
import com.hedera.node.app.service.scheduled.ScheduleService;
import com.hedera.node.app.service.scheduled.impl.ScheduleServiceImpl;
import com.hedera.node.app.spi.PreHandleTxnAccessor;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.state.States;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceImplTest {
    private ScheduleServiceImpl subject;
    @Mock private InMemoryStateImpl schedules;
    @Mock private States states;
    @Mock private PreHandleTxnAccessor callContext;
    @Mock private PreHandleContext preHandleCtx;
    @BeforeEach
    void setUp(){
        subject = new ScheduleServiceImpl(callContext);
    }

    @Test
    void testsSpi() {
        final ScheduleService service = ScheduleService.getInstance();
        Assertions.assertNotNull(service, "We must always receive an instance");
        Assertions.assertEquals(ScheduleService.class, service.getClass(),
                "We must always receive an instance of type StandardScheduleService");
    }

    @Test
    void createsNewInstance() {
        given(states.get("SCHEDULES-BY-ID")).willReturn(schedules);
        given(states.get("SCHEDULES-BY-EQUALITY")).willReturn(schedules);
        given(states.get("SCHEDULES-BY-EXPIRY")).willReturn(schedules);

        final var serviceImpl = subject.createPreTransactionHandler(states, preHandleCtx);
        final var serviceImpl1 = subject.createPreTransactionHandler(states, preHandleCtx);
        assertNotEquals(serviceImpl1, serviceImpl);
    }
}
