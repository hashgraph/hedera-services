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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static utils.CommonTestUtils.deleteDirectoryAndContents;
import static utils.CommonTestUtils.hash;

public class ThorsHammer2 {
	public static final int initialDataSize = Runtime.getRuntime().availableProcessors() > 10 ? 10_000_000 : 10_000;
	public static final long firstLeafPath = initialDataSize;
	public static final long middleLeafPath = firstLeafPath + (initialDataSize/2);
	public static final long lastLeafPath = firstLeafPath + initialDataSize;
	public static final int updateBatchSize = initialDataSize/6;
	public static final int numOfBatches = (int)((middleLeafPath - firstLeafPath) / updateBatchSize);
	public static final Path dataSourcePath = Path.of("ThorsHammer-database").toAbsolutePath();
	private final VirtualDataSourceJasperDB<ContractKey, ContractValue> dataSource;
	// array of values to compare to, index in array is path
	private final AtomicIntegerArray compareToMe = new AtomicIntegerArray((initialDataSize*2)+1);
	/** Current progress percentage we are tracking in the range of 0 to 20 */
	private int progressPercentage;
	private static final Random RANDOM = new Random();

	public ThorsHammer2() throws Exception {
		System.out.println("dataSourcePath = " + dataSourcePath);
		System.out.println("initialDataSize = " + initialDataSize);
		System.out.println("updateBatchSize = " + updateBatchSize);
		System.out.println("numOfBatches = " + numOfBatches);
		if (Files.exists(dataSourcePath)) {
			System.err.println("!!!!!!!!!!!!! Deleting old db.");
			deleteDirectoryAndContents(dataSourcePath);
		}
		// create data source
		VirtualLeafRecordSerializer<ContractKey, ContractValue> virtualLeafRecordSerializer =
				new VirtualLeafRecordSerializer<>(
						(short) 1, DigestType.SHA_384,
						(short) 1, DataFileCommon.VARIABLE_DATA_SIZE, new ContractKeySupplier(),
						(short) 1,ContractValue.SERIALIZED_SIZE, new ContractValueSupplier(),
						true);
		JasperDbBuilder<ContractKey, ContractValue> dbBuilder = new JasperDbBuilder<>();
		dbBuilder
				.virtualLeafRecordSerializer(virtualLeafRecordSerializer)
				.virtualInternalRecordSerializer(new VirtualInternalRecordSerializer())
				.keySerializer(new ContractKeySerializer())
				.storageDir(dataSourcePath)
				.internalHashesRamToDiskThreshold(0)
				.mergingEnabled(true);
		dataSource = dbBuilder.build("jdb", "hello");
		// populate with initial data
		System.out.printf("Creating initial data set of %,d leaves\n", initialDataSize);
		progressPercentage = 0;
		var internalRecordStream = LongStream.range(0, firstLeafPath)
				.mapToObj(path -> new VirtualInternalRecord(path, hash((int) path)))
				.peek(internalRecord -> printProgress(internalRecord.getPath(), lastLeafPath));
		var leafRecordStream = LongStream.range(firstLeafPath, lastLeafPath + 1)
				.mapToObj(path -> new VirtualLeafRecord<>(
						path,
						hash((int) path),
						new ContractKey(path/1000, path),
						new ContractValue(RANDOM.nextLong())
				))
				.peek(leaf -> printProgress(leaf.getPath(), lastLeafPath));
		dataSource.saveRecords(firstLeafPath, lastLeafPath, internalRecordStream, leafRecordStream, Stream.empty());
		System.out.printf("Done creating initial data set of %,d leaves\n", initialDataSize);
		// build initial values for compareToMe
		progressPercentage = 0;
		System.out.printf("Creating initial compareToMe data %,d leaves\n", initialDataSize);
		for (int path = (int)dataSource.getFirstLeafPath(); path <= dataSource.getLastLeafPath(); path++) {
			compareToMe.set(path, (int)dataSource.loadLeafRecord(path).getValue().asLong());
			//System.out.println("dataSource.loadLeafRecord("+path+") = " + dataSource.loadLeafRecord(path));
			printProgress(path, compareToMe.length());
		}
		System.out.printf("Done creating compareToMe data of %,d leaves\n", initialDataSize);
	}

	public void hammer() throws IOException, InterruptedException {
		// do a couple checks first
		System.out.println("\n===== CHECK INITIAL DATA IS ALL GOOD ================================================\n");
		for (int i = 0; i < 3; i++) {
			System.out.println(i);
			checkAllValueOnce();
		}

		// start up some random checkers
		final int randomReadThreads = Runtime.getRuntime().availableProcessors() / 4;
		final int checkerThreads = Runtime.getRuntime().availableProcessors() / 8;
		System.out.println("\n===== STARTING READING THREADS" +
				" randomReadThreads="+randomReadThreads+
				" checkerThreads = " + checkerThreads +
				" ================================================\n");
		for (int i = 0; i < randomReadThreads; i++) {
			Thread randomReadThread = new Thread(this::randomRead,"Random Read Thread "+i);
			randomReadThread.setDaemon(true);
			randomReadThread.start();
		}
		for (int i = 0; i < checkerThreads; i++) {
			Thread checkAllThread = new Thread(this::checkAllNonUpdatedValues, "Check All Thread "+i);
			checkAllThread.setDaemon(true);
			checkAllThread.start();
			Thread randomCheckThread = new Thread(this::randomCheck, "Random Check Thread"+i);
			randomCheckThread.setDaemon(true);
			randomCheckThread.start();
		}

		Thread.sleep(4000);

		System.out.println("\n===== STARTING UPDATING DATA ================================================\n");
		// do some updates
		for (int i = 0; i < 1000; i++) {
			System.out.println("\nUPDATE-"+i);
			updateAllValues();
		}
		dataSource.close();
		System.exit(0);
	}

	public void updateAllValues() {
		try {
			System.out.println("ThorsHammer.updateAllValues numOfBatches="+numOfBatches);
			for (int batchIndex = 0; batchIndex < numOfBatches; batchIndex++) {
				final int firstLeaf = (int)(firstLeafPath + (batchIndex * updateBatchSize));
				final int lastLeaf = (int)Math.min(firstLeaf+updateBatchSize, middleLeafPath-1);
				final var leafRecordStream = LongStream.range(firstLeaf,lastLeaf)
						.mapToObj(path -> new VirtualLeafRecord<>(
								path,
								hash((int) path),
								new ContractKey(path/1000, path),
								new ContractValue(RANDOM.nextLong())
						));
					dataSource.saveRecords(dataSource.getFirstLeafPath(), dataSource.getLastLeafPath(),
							Stream.empty(), leafRecordStream, Stream.empty());
				Thread.sleep(5);
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void checkAllValueOnce() {
		try {
			for (int path = (int) dataSource.getFirstLeafPath(); path <= dataSource.getLastLeafPath(); path++) {
				if (compareToMe.get(path) != (int) dataSource.loadLeafRecord(path).getValue().asLong()) {
					System.err.println(
							"DATA DID NOT MATCH FOR path=" + path + " compareTo=" + compareToMe.get(path) +
									" read value=" + dataSource.loadLeafRecord(path).getValue().asLong());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public void checkAllNonUpdatedValues() {
		while(true) {
			System.out.print("C");
			for (int path = (int) middleLeafPath; path <= lastLeafPath; path++) {
				try {
					if (compareToMe.get(path) != (int) dataSource.loadLeafRecord(path).getValue().asLong()) {
						System.err.println(
								"DATA DID NOT MATCH FOR path=" + path + " compareTo=" + compareToMe.get(path) +
										" read value=" + dataSource.loadLeafRecord(path).getValue().asLong());
					}
				} catch (IOException e) {
					System.err.println("\n===== IOException IN checkAllValueOnce, path="+path+
							" middleLeafPath="+middleLeafPath+" lastLeafPath="+lastLeafPath+"\n");
					e.printStackTrace();
					System.exit(1);
				}
			}
		}
	}

	public void randomCheck() {
		while(true) {
			System.out.print("c");
			try {
				for (int i = 0; i < 1000; i++) {
					final int path = (int)(middleLeafPath + RANDOM.nextInt((int)(lastLeafPath-middleLeafPath)));
					if (compareToMe.get(path) != (int) dataSource.loadLeafRecord(path).getValue().asLong()) {
						System.err.println("DATA DID NOT MATCH FOR path=" + path + " compareTo=" + compareToMe.get(path) +
								" read value=" + dataSource.loadLeafRecord(path).getValue().asLong());
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
		}
	}

	private final AtomicLong randomReadCount = new AtomicLong();
	private final long READ_CHECK = Runtime.getRuntime().availableProcessors() > 10 ? 1_000_000 : 10_000;

	/**
	 * This thread runs unprotected by the read/write lock, this is how threads like hashes are reading. They are
	 * however protected by the virtual node cache from any half written values.
	 */
	public void randomRead() {
		while (true) {
			if ((randomReadCount.getAndIncrement() % READ_CHECK) == 0) System.out.print("R");
			final int path = (int) (middleLeafPath + RANDOM.nextInt((int) (lastLeafPath - middleLeafPath)));
			try {
				final var value = dataSource.loadLeafRecord(path).getValue();
				if (value == null) {
					System.err.println("DID NOT FIND VALUE FOR path=" + path);
				}
			} catch (IOException e) {
				System.err.println(
						"Error doing random read of path " + path + " FirstLeafPath=" + dataSource.getFirstLeafPath() +
								" LastLeafPath=" + dataSource.getLastLeafPath());
				e.printStackTrace();
				System.exit(1);
			}
		}
	}

	/**
	 * Print nice progress messages in the form [%%%%%%%%%%     ] 60%
	 *
	 * @param position the current progress position between 0 and total
	 * @param total the position value for 100%
	 */
	private void printProgress(long position, long total) {
		assert position >= 0 : "position ["+position+"] is < 0";
		assert total > 0 : "total ["+total+"] is <= 0";
		int newProgressPercentage = (int)((((double)position / (double)total)) * 20);
		assert newProgressPercentage >= 0 : "newProgressPercentage ["+newProgressPercentage+"] is < 0";
		assert newProgressPercentage <= 20 : "newProgressPercentage ["+newProgressPercentage+"] is > 20, position="+
				position+", total="+total;
		if (newProgressPercentage > progressPercentage) {
			progressPercentage = newProgressPercentage;
			System.out.printf("[%s] %d%%, %,d of %,d\r",
					("#".repeat(newProgressPercentage))+(" ".repeat(20-newProgressPercentage)),
					progressPercentage*5,
					position,
					total
			);
		}
	}

	public static void main(String[] args) throws Exception {
		ThorsHammer2 thorsHammer = new ThorsHammer2();
		thorsHammer.hammer();
	}
}
