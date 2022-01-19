package jasperdb;

import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractKeySerializer;
import com.hedera.services.state.virtual.ContractKeySupplier;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.ContractValueSupplier;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.jasperdb.VirtualDataSourceJasperDB;
import com.swirlds.jasperdb.VirtualInternalRecordSerializer;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static utils.CommonTestUtils.deleteDirectoryAndContents;
import static utils.CommonTestUtils.hash;

@State(Scope.Thread)
@Warmup(iterations = 2, time = 30, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 30, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class VirtualDataSourceBench {
	public final Path dataSourcePath = Path.of("test-database").toAbsolutePath();
	public final Path dataSourceSnapshotPath = Path.of("test-database-snapshot").toAbsolutePath();
	public Random random;
	public VirtualDataSourceJasperDB<ContractKey, ContractValue> dataSource;
	/** Current progress percentage we are tracking in the range of 0 to 20 */
	private int progressPercentage = 0;
	private long randomInternalPath;
	private long randomLeafPath;

	@Param({ "1000000" })
	public int initialDataSize;

	@Setup(Level.Trial)
	public void setup() throws IOException {
		random = new Random(1234);
		System.out.println("dataSourcePath = " + dataSourcePath);
		if (Files.exists(dataSourcePath)) {
			System.err.println("!!!!!!!!!!!!! Deleting old db.");
			deleteDirectoryAndContents(dataSourcePath);
		}
		if (Files.exists(dataSourceSnapshotPath)) {
			System.err.println("!!!!!!!!!!!!! Deleting old db snapshot.");
			deleteDirectoryAndContents(dataSourceSnapshotPath);
		}
		// create data source
		VirtualLeafRecordSerializer<ContractKey, ContractValue> virtualLeafRecordSerializer =
				new VirtualLeafRecordSerializer<>(
						(short) 1, DigestType.SHA_384,
						(short) 1, DataFileCommon.VARIABLE_DATA_SIZE, new ContractKeySupplier(),
						(short) 1, ContractValue.SERIALIZED_SIZE, new ContractValueSupplier(),
						true);
		JasperDbBuilder<ContractKey, ContractValue> dbBuilder = new JasperDbBuilder<>();
		dbBuilder
				.virtualLeafRecordSerializer(virtualLeafRecordSerializer)
				.virtualInternalRecordSerializer(new VirtualInternalRecordSerializer())
				.keySerializer(new ContractKeySerializer())
				.storageDir(dataSourcePath)
				.maxNumOfKeys(500_000_000)
				.preferDiskBasedIndexes(false)
				.internalHashesRamToDiskThreshold(0)
				.mergingEnabled(false);
		dataSource = dbBuilder.build("jdb", "4dsBench");
		// populate with initial data
		System.out.printf("Creating initial data set of %,d leaves\n", initialDataSize);
		progressPercentage = 0;
		final long firstLeafPath = initialDataSize;
		final long lastLeafPath = firstLeafPath + initialDataSize;
		var internalRecordStream = LongStream.range(0, firstLeafPath)
				.mapToObj(path -> new VirtualInternalRecord(path, hash((int) path)));
		var leafRecordStream = LongStream.range(firstLeafPath, lastLeafPath + 1)
				.mapToObj(path -> new VirtualLeafRecord<>(
						path,
						hash((int) path),
						new ContractKey(path, path),
						new ContractValue(path)
				))
				.peek(leaf -> printProgress(leaf.getPath(), lastLeafPath));
		dataSource.saveRecords(firstLeafPath, lastLeafPath, internalRecordStream, leafRecordStream, Stream.empty());
		System.out.printf("Done creating initial data set of %,d leaves\n", initialDataSize);
	}

	@Setup(Level.Invocation)
	public void setupInvocation() {
		randomInternalPath = random.nextInt(initialDataSize);
		randomLeafPath = initialDataSize + randomInternalPath;
		if (Files.exists(dataSourceSnapshotPath)) {
			System.err.println("!!!!!!!!!!!!! Deleting old db snapshot.");
			deleteDirectoryAndContents(dataSourceSnapshotPath);
		}
	}

	@Benchmark
	public void a_randomLoadInternalRecord() throws IOException {
		dataSource.loadInternalRecord(randomInternalPath);
	}

	@Benchmark
	public void a_randomLoadLeafRecordByPath() throws IOException {
		dataSource.loadLeafRecord(randomLeafPath);
	}

	@Benchmark
	public void a_randomLoadLeafRecordByKey() throws IOException {
		dataSource.loadLeafRecord(new ContractKey(randomLeafPath, randomLeafPath));
	}

	@Benchmark
	public void b_add500Leaves() throws IOException {
		final long firstLeafPath = dataSource.getFirstLeafPath();
		final long lastLeafPath = dataSource.getLastLeafPath();
		final long newFirstLeafPath = firstLeafPath + 500;
		final long newLastLeafPath = lastLeafPath + 1000;
		var internalRecordStream = LongStream.range(firstLeafPath, newFirstLeafPath)
				.mapToObj(path -> new VirtualInternalRecord(path, hash((int) path)));
		var leafRecordStream = LongStream.range(lastLeafPath, newLastLeafPath + 1)
				.mapToObj(path -> new VirtualLeafRecord<>(
						path,
						hash((int) path),
						new ContractKey(path, path),
						new ContractValue(path)
				));
		dataSource.saveRecords(newFirstLeafPath, newLastLeafPath, internalRecordStream, leafRecordStream, Stream.empty());
	}

	@Benchmark
	public void c_snapshot() throws IOException {
		dataSource.snapshot(dataSourceSnapshotPath);
	}

	/**
	 * Print nice progress messages in the form [%%%%%%%%%%     ] 60%
	 *
	 * @param position
	 * 		the current progress position between 0 and total
	 * @param total
	 * 		the position value for 100%
	 */
	private void printProgress(long position, long total) {
		assert position >= 0 : "position [" + position + "] is < 0";
		assert total > 0 : "total [" + total + "] is <= 0";
		int newProgressPercentage = (int) ((((double) position / (double) total)) * 20);
		assert newProgressPercentage >= 0 : "newProgressPercentage [" + newProgressPercentage + "] is < 0";
		assert newProgressPercentage <= 20 : "newProgressPercentage [" + newProgressPercentage + "] is > 20, " +
				"position=" +
				position + ", total=" + total;
		if (newProgressPercentage > progressPercentage) {
			progressPercentage = newProgressPercentage;
			System.out.printf("[%s] %d%%, %,d of %,d\r",
					("#".repeat(newProgressPercentage)) + (" ".repeat(20 - newProgressPercentage)),
					progressPercentage * 5,
					position,
					total
			);
		}
	}
}
