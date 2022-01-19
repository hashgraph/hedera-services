package jasperdb;

import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractKeySerializer;
import com.hedera.services.state.virtual.ContractKeySupplier;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.ContractValueSupplier;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.jasperdb.VirtualInternalRecordSerializer;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.virtualmap.VirtualMap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static utils.CommonTestUtils.deleteDirectoryAndContents;

@SuppressWarnings("InfiniteLoopStatement")
public class ThorsVirtualMapHammer {
//	static {
//		JasperDbSettingsImpl settings = new JasperDbSettingsImpl();
//		settings.setMergeActivatedPeriod(1); // just merge as often as we can
//		JasperDbSettingsFactory.configure(settings);
//	}
	public static final int dataSize = Runtime.getRuntime().availableProcessors() > 10 ? 10_000_000 : 10_000;
	public static final int updateBatchSize = 8000;
	public static final Path dataSourcePath = Path.of("ThorsHammer-database").toAbsolutePath();
	private final AtomicReference<VirtualMap<ContractKey, ContractValue>> virtualMapRef = new AtomicReference<>();
	private final ConcurrentHashMap<ContractKey, ContractValue> compareToMe = new ConcurrentHashMap<>();
	/** Current progress percentage we are tracking in the range of 0 to 20 */
	private int progressPercentage;
	private static final Random RANDOM = new Random();

	public ThorsVirtualMapHammer() throws Exception {
		System.out.println("dataSourcePath = " + dataSourcePath);
		System.out.println("dataSize = " + dataSize);
		System.out.println("updateBatchSize = " + updateBatchSize);
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
		final VirtualMap<ContractKey, ContractValue> virtualMap = new VirtualMap<>("hello",dbBuilder);
		// populate with initial data
		System.out.printf("Creating initial data set of %,d leaves\n", dataSize);
		progressPercentage = 0;
		for (int i = 0; i < dataSize; i++) {
			final var key = new ContractKey(i/100,i);
			final var value = new ContractValue(RANDOM.nextLong());
			virtualMap.put(key,value);
			compareToMe.put(key,value);
			printProgress(i, dataSize);
		}
		virtualMapRef.set(virtualMap.copy());
		virtualMap.release();

		System.out.printf("Done creating initial data set of %,d leaves\n", dataSize);
	}

	public void hammer() throws InterruptedException {
		// do a check data once first
		System.out.println("\n===== CHECK INITIAL DATA IS ALL GOOD ================================================\n");
		checkAllValueOnce();

		// start up some random checkers
		final int randomReadThreads = 3;//Runtime.getRuntime().availableProcessors() / 2;
		System.out.println("\n===== STARTING READING THREADS" +
				" randomReadThreads="+randomReadThreads+
				" ================================================\n");
		for (int i = 0; i < randomReadThreads; i++) {
			Thread randomReadThread = new Thread(this::randomRead,"Random Read Thread "+i);
			randomReadThread.setDaemon(true);
			randomReadThread.start();
		}

		Thread.sleep(4000);

		System.out.println("\n===== STARTING UPDATING DATA ================================================\n");
		// do some updates
		for (int update = 0; update < 10_000_000; update++) {
			if ((update%10) == 0) {
				System.out.println("-- UPDATE BATCH-" + update + " --");
			}
			final var virtualMap = virtualMapRef.get();
			RANDOM.ints(updateBatchSize,0,dataSize).forEach(i -> {
				final var key = new ContractKey(i/100,i);
				final var value = virtualMap.getForModify(key);
				value.setValue(RANDOM.nextLong());
				compareToMe.put(key,value);
			});
			virtualMapRef.set(virtualMap.copy());
			virtualMap.release();
			// we can now check all data while we are not updating as everything should match
			if ((update%10_000) == 0) {
				System.out.println("\n-- CHECKING ALL --");
				checkAllValueOnce();
			}
		}
		virtualMapRef.get().release();
		System.exit(0);
	}


	public void checkAllValueOnce() {
		final var virtualMap = virtualMapRef.get();

		final int numberOfChunks = Runtime.getRuntime().availableProcessors()/4;
		final int chunkSize = dataSize/numberOfChunks;
		IntStream.range(0,numberOfChunks).parallel().forEach(chunk -> {
			final int start = (chunk*chunkSize);
			final int end = Math.min(dataSize, start+chunkSize);
			for (int i = start; i < end; i++) {
				final var key = new ContractKey(i/100,i);
				final var expectedValue = compareToMe.get(key);
				final var readValue = virtualMap.get(key);
				if(expectedValue == null || !expectedValue.equals(readValue)) {
					System.err.println(
							"DATA DID NOT MATCH FOR key=" + key + " expectedValue=" + expectedValue +
									" readValue=" + readValue);
				}
			}
		});
	}

//	public void checkAllValueOnce() {
//		final var virtualMap = virtualMapRef.get();
//		for (int i = 0; i < dataSize; i++) {
//			final var key = new ContractKey(i/100,i);
//			final var expectedValue = compareToMe.get(key);
//			final var readValue = virtualMap.get(key);
//			if(expectedValue == null || !expectedValue.equals(readValue)) {
//				System.err.println(
//						"DATA DID NOT MATCH FOR key=" + key + " expectedValue=" + expectedValue +
//								" readValue=" + readValue);
//			}
//		}
//	}

	private final AtomicLong randomReadCount = new AtomicLong();
	private final long READ_CHECK = Runtime.getRuntime().availableProcessors() > 10 ? 1_000_000 : 10_000;

	public void randomRead() {
		while (true) {
			if ((randomReadCount.getAndIncrement() % READ_CHECK) == 0) System.out.print("R");
			final int i = RANDOM.nextInt(dataSize);
			final var key = new ContractKey(i/100,i);
			virtualMapRef.get().get(key);
			Thread.yield();
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
		ThorsVirtualMapHammer thorsHammer = new ThorsVirtualMapHammer();
		thorsHammer.hammer();
	}
}
