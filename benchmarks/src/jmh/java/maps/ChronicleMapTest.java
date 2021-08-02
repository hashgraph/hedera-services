package maps;

import net.openhft.chronicle.core.values.LongValue;
import net.openhft.chronicle.map.ChronicleMapBuilder;
import net.openhft.chronicle.values.Values;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Setup;
import virtual.VFCMapBenchBase;

import java.util.concurrent.ConcurrentMap;

import static virtual.VFCMapBenchBase.asAccount;

public class ChronicleMapTest {
    public static void main(String[] args) {
        LongValue key = Values.newHeapInstance(LongValue.class);
        ConcurrentMap<LongValue, VFCMapBenchBase.Account> accountMap = ChronicleMapBuilder
                    .of(LongValue.class, VFCMapBenchBase.Account.class)
                    .name("account-map")
                    .entries(50_000_000)
                    .averageValue(asAccount(1))
                    .create();
        // fill with some data
        for (int i = 0; i < 1000; i++) {
            key.setValue(i);
            accountMap.put(key, asAccount(i));
        }
    }
}
