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

    public static Path getDataSourcePath(DataSourceType type) {
        //noinspection CommentedOutCode
        switch (type) {
//            case lmdb:
//                    return Path.of("lmdb");
/*            case rocksdb:
                    return Path.of("rocksdb");
 */
            case jasperdbIhRam:
                return Path.of("jasperdb_ih_ram");
            case jasperdbIhDisk:
                return Path.of("jasperdb_ih_disk");
            case jasperdbIhHalf:
                return Path.of("jasperdb_ih_half");
            default:
                return Path.of("jasperdb");
        }
    }

    protected static <K extends VirtualKey, V extends VirtualValue> VirtualMap<K,V> createMap(
            DataSourceType type,
            VirtualLeafRecordSerializer<K, V> virtualLeafRecordSerializer,
            KeySerializer<K> keySerializer,
            long numEntities,
            Path dataSourcePath, boolean preferDiskBasedIndexes) throws IOException {

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
                JasperDbBuilder<K, V> ramDbBuilder = new JasperDbBuilder<>();
                ramDbBuilder
                        .virtualLeafRecordSerializer(virtualLeafRecordSerializer)
                        .virtualInternalRecordSerializer(new VirtualInternalRecordSerializer())
                        .keySerializer(keySerializer)
                        .storageDir(dataSourcePath)
                        .maxNumOfKeys(numEntities)
                        .preferDiskBasedIndexes(preferDiskBasedIndexes)
                        .internalHashesRamToDiskThreshold(Long.MAX_VALUE)
                        .mergingEnabled(true);
                map = new VirtualMap<>("vm", ramDbBuilder);
                break;
            case jasperdbIhDisk:
            default:
                JasperDbBuilder<K, V> diskDbBuilder = new JasperDbBuilder<>();
                diskDbBuilder
                        .virtualLeafRecordSerializer(virtualLeafRecordSerializer)
                        .virtualInternalRecordSerializer(new VirtualInternalRecordSerializer())
                        .keySerializer(keySerializer)
                        .storageDir(dataSourcePath)
                        .maxNumOfKeys(numEntities)
                        .preferDiskBasedIndexes(preferDiskBasedIndexes)
                        .internalHashesRamToDiskThreshold(0)
                        .mergingEnabled(true);
                map = new VirtualMap<>("vm", diskDbBuilder);
                break;
            case jasperdbIhHalf:
                JasperDbBuilder<K, V> halfDiskHalfRamDbBuilder = new JasperDbBuilder<>();
                halfDiskHalfRamDbBuilder
                        .virtualLeafRecordSerializer(virtualLeafRecordSerializer)
                        .virtualInternalRecordSerializer(new VirtualInternalRecordSerializer())
                        .keySerializer(keySerializer)
                        .storageDir(dataSourcePath)
                        .maxNumOfKeys(numEntities)
                        .preferDiskBasedIndexes(preferDiskBasedIndexes)
                        .internalHashesRamToDiskThreshold(numEntities / 2)
                        .mergingEnabled(true);
                map = new VirtualMap<>("vm", halfDiskHalfRamDbBuilder);
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
