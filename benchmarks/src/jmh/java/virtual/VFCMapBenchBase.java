package virtual;

import com.swirlds.jasperdb.*;
import com.swirlds.jasperdb.files.hashmap.*;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class VFCMapBenchBase {
    public enum DataSourceType {
        /*lmdb,*/ jasperdbIhRam, jasperdbIhDisk, jasperdbIhHalf
    }

    protected static <K extends VirtualKey, V extends VirtualValue> VirtualMap<K,V> createMap(
            DataSourceType type,
            VirtualLeafRecordSerializer<K,V> virtualLeafRecordSerializer,
            KeySerializer<K> keySerializer,
            long numEntities,
            String extraPath) throws IOException {

        VirtualMap<K,V> map;
        //noinspection CommentedOutCode
        switch (type) {
//            case lmdb:
//                map = new VirtualMap<>(new VFCDataSourceLmdb<>(
//                    keySizeBytes,
//                    keyConstructor,
//                    valueSizeBytes,
//                    valueConstructor,
//                    Path.of("lmdb")));
//                break;
/*            case rocksdb -> new VirtualMap<>(new VFCDataSourceRocksDb<>(
                    keySizeBytes,
                    keyConstructor,
                    valueSizeBytes,
                    valueConstructor,
                    Path.of("rocksdb")));
 */
            case jasperdbIhRam:
                map = new VirtualMap<>(new VirtualDataSourceJasperDB<>(
                        virtualLeafRecordSerializer,
                        new VirtualInternalRecordSerializer(),
                        keySerializer,
                        Path.of("jasperdb_ih_ram_"+extraPath),
                        numEntities,
                        true,
                        Long.MAX_VALUE,
                        false));
                break;
            case jasperdbIhDisk:
            default:
                map = new VirtualMap<>(new VirtualDataSourceJasperDB<>(
                        virtualLeafRecordSerializer,
                        new VirtualInternalRecordSerializer(),
                        keySerializer,
                        Path.of("jasperdb_ih_disk_"+extraPath),
                        numEntities,
                        true,
                        0,
                        false));
                break;
            case jasperdbIhHalf:
                map = new VirtualMap<>(new VirtualDataSourceJasperDB<>(
                        virtualLeafRecordSerializer,
                        new VirtualInternalRecordSerializer(),
                        keySerializer,
                        Path.of("jasperdb_ih_half_"+extraPath),
                        numEntities,
                        true,
                        numEntities/2,
                        false));
                break;
        }
        return map;
    }

    protected static void printDataStoreSize() {
        // print data dir size
        Path dir =  Path.of("data");
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            try {
                long size = Files.walk(dir)
                        .filter(p -> p.toFile().isFile())
                        .mapToLong(p -> p.toFile().length())
                        .sum();
                long count = Files.walk(dir)
                        .filter(p -> p.toFile().isFile())
                        .count();
                System.out.printf("\nTest data storage %d files totalling size: %,.1f Mb\n",count,(double)size/(1024d*1024d));
            } catch (Exception e) {
                System.err.println("Failed to measure size of directory. ["+dir.toFile().getAbsolutePath()+"]");
                e.printStackTrace();
            }
        }
    }

}
