package com.hedera.services.state.migration;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;
import org.apache.commons.lang3.tuple.Pair;

@FunctionalInterface
public interface VirtualMapDataAccess {
    <K extends VirtualKey<? super K>, V extends VirtualValue> void extractVirtualMapData(
            VirtualMap<K, V> source,
            InterruptableConsumer<Pair<K, V>> handler,
            int threadCount)
            throws InterruptedException;
}
