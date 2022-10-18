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
package com.hedera.services.context.properties;

import static com.hedera.services.context.properties.BootstrapProperties.BOOTSTRAP_PROP_NAMES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.doCallRealMethod;

import com.hedera.services.throttling.MapAccessType;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StandardizedPropertySourcesTest {
    @Mock private PropertySource bootstrapProps;
    @Mock private ScreenedSysFileProps dynamicGlobalProps;
    @Mock private ScreenedNodeFileProps nodeProps;

    private StandardizedPropertySources subject;

    @BeforeEach
    void setup() {
        subject = new StandardizedPropertySources(bootstrapProps, dynamicGlobalProps, nodeProps);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getsExpectedRatios() {
        final var prop = "0:3,1:-1,2:,3:1.1,4:NONSENSE,5:4,12345";
        final Map<Long, Long> ratios =
                (Map<Long, Long>) PropertySource.AS_NODE_STAKE_RATIOS.apply(prop);
        final var expected = Map.of(0L, 3L, 5L, 4L);
        assertEquals(expected, ratios);

        final var name = "ratios";
        final var mockSubject = Mockito.mock(PropertySource.class);
        doCallRealMethod().when(mockSubject).getNodeStakeRatiosProperty(name);
        given(mockSubject.getProperty(name)).willReturn(expected);
        doCallRealMethod().when(mockSubject).getTypedProperty(Map.class, name);
        assertEquals(expected, mockSubject.getNodeStakeRatiosProperty(name));

        assertEquals(Collections.emptyMap(), PropertySource.AS_NODE_STAKE_RATIOS.apply(""));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getsExpectedAccessList() {
        final var prop = "ACCOUNTS_GET,STORAGE_PUT";
        final List<MapAccessType> accesses =
                (List<MapAccessType>) PropertySource.AS_ACCESS_LIST.apply(prop);
        final List<MapAccessType> expected =
                List.of(MapAccessType.ACCOUNTS_GET, MapAccessType.STORAGE_PUT);
        assertEquals(expected, accesses);

        final var name = "accessList";
        final var mockSubject = Mockito.mock(PropertySource.class);
        doCallRealMethod().when(mockSubject).getAccessListProperty(name);
        given(mockSubject.getProperty(name)).willReturn(expected);
        doCallRealMethod().when(mockSubject).getTypedProperty(List.class, name);
        assertEquals(expected, mockSubject.getAccessListProperty(name));
    }

    @Test
    void usesDynamicGlobalAsPriority() {
        given(dynamicGlobalProps.containsProperty("testProp")).willReturn(true);
        given(dynamicGlobalProps.getProperty("testProp")).willReturn("perfectAnswer");

        subject.reloadFrom(ServicesConfigurationList.getDefaultInstance());

        assertEquals("perfectAnswer", subject.asResolvingSource().getStringProperty("testProp"));
    }

    @Test
    void usesNodeAsSecondPriority() {
        given(nodeProps.containsProperty("testProp2")).willReturn(true);
        given(nodeProps.getProperty("testProp2")).willReturn("goodEnoughForMe");
        given(dynamicGlobalProps.containsProperty("testProp")).willReturn(true);
        given(dynamicGlobalProps.getProperty("testProp")).willReturn("perfectAnswer");

        subject.reloadFrom(ServicesConfigurationList.getDefaultInstance());

        assertEquals("perfectAnswer", subject.asResolvingSource().getStringProperty("testProp"));
        assertEquals("goodEnoughForMe", subject.asResolvingSource().getStringProperty("testProp2"));
    }

    @Test
    void propagatesReloadToDynamicGlobalProps() {
        subject.reloadFrom(ServicesConfigurationList.getDefaultInstance());

        verify(dynamicGlobalProps).screenNew(ServicesConfigurationList.getDefaultInstance());
    }

    @Test
    void usesBootstrapSourceAsApropos() {
        subject.getNodeProps().getFromFile().clear();

        final var properties = subject.asResolvingSource();
        BOOTSTRAP_PROP_NAMES.forEach(properties::getProperty);

        for (final var bootstrapProp : BOOTSTRAP_PROP_NAMES) {
            verify(bootstrapProps).getProperty(bootstrapProp);
        }
    }

    @Test
    void noClassCastExceptionForRangeProp() {
        final var updateRange = Pair.of(150L, 159L);
        given(bootstrapProps.getProperty("files.softwareUpdateRange")).willReturn(updateRange);
        final var properties = subject.asResolvingSource();

        assertSame(updateRange, properties.getEntityNumRange("files.softwareUpdateRange"));
    }
}
