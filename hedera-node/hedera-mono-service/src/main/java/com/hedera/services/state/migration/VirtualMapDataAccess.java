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
package com.hedera.services.state.migration;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;
import org.apache.commons.lang3.tuple.Pair;

@FunctionalInterface
public interface VirtualMapDataAccess {
    <K extends VirtualKey<? super K>, V extends VirtualValue> void extractVirtualMapData(
            VirtualMap<K, V> source, InterruptableConsumer<Pair<K, V>> handler, int threadCount)
            throws InterruptedException;
}
