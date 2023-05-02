/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl.test;

import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import java.util.Iterator;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceImplTest {
    @Mock
    private SchemaRegistry registry;

    @Test
    void testsSpi() {
        final ScheduleService service = new ScheduleServiceImpl();
        Assertions.assertNotNull(service, "We must always receive an instance");
        Assertions.assertEquals(
                ScheduleServiceImpl.class,
                service.getClass(),
                "We must always receive an instance of type " + ScheduleServiceImpl.class.getName());
        Assertions.assertEquals("ScheduleService", service.getServiceName());
    }

    @Test
    @SuppressWarnings("rawtypes")
    void registersExpectedSchema() {
        ArgumentCaptor<Schema> schemaCaptor = ArgumentCaptor.forClass(Schema.class);

        final ScheduleServiceImpl subject = new ScheduleServiceImpl();

        subject.registerSchemas(registry);
        Mockito.verify(registry).register(schemaCaptor.capture());

        final Schema schema = schemaCaptor.getValue();

        final Set<StateDefinition> statesToCreate = schema.statesToCreate();
        Assertions.assertEquals(4, statesToCreate.size());
        final Iterator<String> statesIterator =
                statesToCreate.stream().map(StateDefinition::stateKey).sorted().iterator();
        Assertions.assertEquals(ScheduleServiceImpl.SCHEDULES_BY_EQUALITY_KEY, statesIterator.next());
        Assertions.assertEquals(ScheduleServiceImpl.SCHEDULES_BY_EXPIRY_SEC_KEY, statesIterator.next());
        Assertions.assertEquals(ScheduleServiceImpl.SCHEDULES_BY_ID_KEY, statesIterator.next());
        Assertions.assertEquals(ScheduleServiceImpl.SCHEDULING_STATE_KEY, statesIterator.next());
    }
}
