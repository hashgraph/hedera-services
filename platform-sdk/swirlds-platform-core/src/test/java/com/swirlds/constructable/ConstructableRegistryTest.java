/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.constructable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.constructable.ConstructableRegistryFactory;
import com.swirlds.common.constructable.ConstructorRegistry;
import com.swirlds.common.constructable.NoArgsConstructor;
import com.swirlds.common.merkle.utility.MerkleLong;
import org.junit.jupiter.api.Test;

public class ConstructableRegistryTest {
    @Test
    public void testRegisterClassesFromAnotherJPMSModule() throws ConstructableRegistryException {
        final ConstructableRegistry registry = ConstructableRegistryFactory.createConstructableRegistry();
        final long classId = new MerkleLong().getClassId();
        registry.registerConstructables(MerkleLong.class.getPackageName());

        final ConstructorRegistry<NoArgsConstructor> subRegistry = registry.getRegistry(NoArgsConstructor.class);
        assertNotNull(subRegistry.getConstructor(classId));
        assertNotNull(subRegistry.getConstructor(classId).get());
        assertEquals(classId, subRegistry.getConstructor(classId).get().getClassId());
    }
}
