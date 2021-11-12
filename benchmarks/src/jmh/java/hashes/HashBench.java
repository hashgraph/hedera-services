package hashes;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashBuilder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark to test hashing performance for different numbers of hashes
 */
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
