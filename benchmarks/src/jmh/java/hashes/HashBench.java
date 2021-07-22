package hashes;

import com.hedera.services.state.merkle.v2.VFCDataSourceImpl;
import com.hedera.services.state.merkle.virtual.ContractKey;
import com.hedera.services.state.merkle.virtual.ContractUint256;
import com.hedera.services.store.models.Id;
import com.swirlds.common.crypto.*;
import com.swirlds.fcmap.VFCDataSource;
import fcmmap.FCVirtualMapTestUtils;
import lmdb.SequentialInsertsVFCDataSource;
import lmdb.VFCDataSourceLmdb;
import lmdb.VFCDataSourceLmdbHashesRam;
import lmdb.VFCDataSourceLmdbTwoIndexes;
import org.openjdk.jmh.annotations.*;
import rockdb.VFCDataSourceRocksDb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

@SuppressWarnings({"jol", "DuplicatedCode", "DefaultAnnotationParam", "SameParameterValue", "SpellCheckingInspection"})
@State(Scope.Thread)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class HashBench {

    @Param({"1","2","4","8","16","32","64","128","256"})
    public long batchSize;

    // state
    private Hash[] hashes;

    @Setup(Level.Trial)
    public void setup() {
        hashes = new Hash[256];
        Random random = new Random();
        for (int i = 0; i < hashes.length; i++) {
            byte[] hashData = new byte[48];
            random.nextBytes(hashData);
            hashes[i] = new Hash(hashData);
        }
    }


    @Benchmark
    public void hash() throws Exception {
        HashBuilder hashBuilder = new HashBuilder(DigestType.SHA_384);
        for (int i = 0; i < batchSize; i++) {
            hashBuilder.update(hashes[i]);
        }
        hashBuilder.build();
    }
}
