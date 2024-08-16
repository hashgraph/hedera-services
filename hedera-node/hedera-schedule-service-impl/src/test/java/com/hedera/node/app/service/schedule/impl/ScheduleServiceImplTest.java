/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl;

import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema;
import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.SchemaRegistry;
import com.swirlds.state.spi.StateDefinition;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.BDDAssertions;
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
        BDDAssertions.assertThat(service).isNotNull();
        BDDAssertions.assertThat(service.getClass()).isEqualTo(ScheduleServiceImpl.class);
        BDDAssertions.assertThat(service.getServiceName()).isEqualTo("ScheduleService");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void registersExpectedSchema() {
        final ScheduleServiceImpl subject = new ScheduleServiceImpl();
        ArgumentCaptor<Schema> schemaCaptor = ArgumentCaptor.forClass(Schema.class);
        subject.registerSchemas(registry);
        Mockito.verify(registry).register(schemaCaptor.capture());

        final Schema schema = schemaCaptor.getValue();
        final Set<StateDefinition> statesToCreate = schema.statesToCreate();
        BDDAssertions.assertThat(statesToCreate).isNotNull();
        final List<String> statesList =
                statesToCreate.stream().map(StateDefinition::stateKey).sorted().toList();
        BDDAssertions.assertThat(statesToCreate.size()).isEqualTo(3);
        BDDAssertions.assertThat(statesList.get(0)).isEqualTo(V0490ScheduleSchema.SCHEDULES_BY_EQUALITY_KEY);
        BDDAssertions.assertThat(statesList.get(1)).isEqualTo(V0490ScheduleSchema.SCHEDULES_BY_EXPIRY_SEC_KEY);
        BDDAssertions.assertThat(statesList.get(2)).isEqualTo(V0490ScheduleSchema.SCHEDULES_BY_ID_KEY);
    }
}
