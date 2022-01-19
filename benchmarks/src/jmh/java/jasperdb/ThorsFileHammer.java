package jasperdb;

import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractKeySupplier;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.ContractValueSupplier;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.collections.LongListOffHeap;
import com.swirlds.jasperdb.files.DataFileCollection;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.LongStream;

import static utils.CommonTestUtils.deleteDirectoryAndContents;
import static utils.CommonTestUtils.hash;

@SuppressWarnings({ "DuplicatedCode", "InfiniteLoopStatement", "BusyWait" })
public class ThorsFileHammer {
	public static final int initialDataSize = 1_000_000;
	public static final Path dataSourcePath = Path.of("ThorsHammer-database").toAbsolutePath();
	private final DataFileCollection<VirtualLeafRecord<ContractKey,ContractValue>> fileCollection;
	private final LongListOffHeap index = new LongListOffHeap(
			initialDataSize*2, initialDataSize*2);
	// array of values to compare to, index in array is path
	private final AtomicIntegerArray compareToMe = new AtomicIntegerArray(initialDataSize*2);
	/** Current progress percentage we are tracking in the range of 0 to 20 */
	private int progressPercentage;
	private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
	private static final Random RANDOM = new Random();

	public ThorsFileHammer() throws Exception {
		System.out.println("dataSourcePath = " + dataSourcePath);
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
		fileCollection = new DataFileCollection<>(dataSourcePath,"Thor",
				virtualLeafRecordSerializer, null);
		// populate with initial data
		System.out.printf("Creating initial data set of %,d leaves\n", initialDataSize);
		progressPercentage = 0;
		final long firstLeafPath = initialDataSize;
		final long lastLeafPath = firstLeafPath + initialDataSize - 1;
		var leafRecordStream = LongStream.range(firstLeafPath, lastLeafPath + 1)
				.mapToObj(path -> new VirtualLeafRecord<>(
						path,
						hash((int) path),
						new ContractKey(path/1000, path),
						new ContractValue(RANDOM.nextLong())
				))
				.peek(leaf -> printProgress(leaf.getPath(), lastLeafPath));
		fileCollection.startWriting();
		leafRecordStream.forEach(leaf -> {
			try {
				index.put(leaf.getPath(),fileCollection.storeDataItem(leaf));
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		final var dataFile = fileCollection.endWriting(
				firstLeafPath,lastLeafPath);
		dataFile.setFileAvailableForMerging(true);
		System.out.printf("Done creating initial data set of %,d leaves\n", initialDataSize);
		// build initial values for compareToMe
		progressPercentage = 0;
		System.out.printf("Creating initial compareToMe data %,d leaves\n", initialDataSize);
		for (int path = (int) fileCollection.getMinimumValidKey(); path <= fileCollection.getMaximumValidKey(); path++) {
			final var leaf = fileCollection.readDataItemUsingIndex(index,path);
			compareToMe.set(path, (int) leaf.getValue().asLong());
//			System.out.println("dataSource.loadLeafRecord("+path+") = " + leaf);
			printProgress(path, compareToMe.length());
		}
		System.out.printf("Done creating compareToMe data of %,d leaves\n", initialDataSize);
	}

	public void hammer() throws IOException, InterruptedException {
		Thread checkAllThread = new Thread(this::checkAllValues,"Check All Thread");
		checkAllThread.setDaemon(true);
		checkAllThread.start();
		Thread randomCheckThread = new Thread(this::randomCheck,"Random Check Thread");
		randomCheckThread.setDaemon(true);
		randomCheckThread.start();
		Thread mergeThread = new Thread(this::merger,"Merge Thread");
		mergeThread.setDaemon(true);
		mergeThread.start();

		// do a couple checks first
		for (int i = 0; i < 10; i++) {
			System.out.println(i);
			checkAllValueOnce();
		}

		for (int i = 0; i < 16; i++) {
			Thread randomReadThread = new Thread(this::randomRead,"Random Read Thread "+i);
			randomReadThread.setDaemon(true);
			randomReadThread.start();
		}

		Thread.sleep(2000);

		// do some updates
		for (int i = 0; i < 10_000; i++) {
			System.out.println("\nUPDATE-"+i);
			updateAllValues();

			Thread.sleep(30);
		}
		fileCollection.close();
		System.exit(0);
	}

	public void merger() {
		while(true) {
			try {
				Thread.sleep(1500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			try {
				final var filesToMerge = fileCollection.getAllFilesAvailableForMerge();
				System.out.println("\nfilesToMerge = " + filesToMerge+" allFiles="+fileCollection.getAllFullyWrittenFiles());
				fileCollection.mergeFiles(
						changes -> changes.forEach(index::putIfEqual),
						filesToMerge,
						new AtomicBoolean());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void updateAllValues() {
		try {
			final int batchSize = 1000;
			final int numOfBatches = (int)((fileCollection.getMaximumValidKey() - fileCollection.getMinimumValidKey()) / batchSize);
			System.out.println("ThorsHammer.updateAllValues numOfBatches="+numOfBatches);
			for (int batchIndex = 0; batchIndex < numOfBatches; batchIndex++) {
				final int firstLeaf = (int)(fileCollection.getMinimumValidKey() + (batchIndex * batchSize));
				final int lastLeaf = firstLeaf+batchSize;
				final var leafRecordStream = LongStream.range(firstLeaf,lastLeaf)
						.mapToObj(path -> new VirtualLeafRecord<>(
								path,
								hash((int) path),
								new ContractKey(path/1000, path),
								new ContractValue(RANDOM.nextLong())
						))
						.peek(leaf -> compareToMe.set((int)leaf.getPath(), (int)leaf.getValue().asLong()));
				try {
					readWriteLock.writeLock().lock();
					fileCollection.startWriting();
					final Map<Long,Long> indexUpdates = new HashMap<>();
					leafRecordStream.forEach(leaf -> {
						try {
							indexUpdates.put(leaf.getPath(),fileCollection.storeDataItem(leaf));
						} catch (IOException e) {
							e.printStackTrace();
						}
					});
					final var dataFile = fileCollection.endWriting(
							fileCollection.getMinimumValidKey(), fileCollection.getMaximumValidKey());
					for (var update:indexUpdates.entrySet()) {
						index.put(update.getKey(), update.getValue());
					}
					dataFile.setFileAvailableForMerging(true);
				} finally {
					readWriteLock.writeLock().unlock();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void checkAllValues() {
		while(true) {
			checkAllValueOnce();
		}
	}

	public void checkAllValueOnce() {
			System.out.print("C");
			try {
				readWriteLock.readLock().lock();
				for (int path = (int) fileCollection.getMinimumValidKey(); path <= fileCollection.getMaximumValidKey(); path++) {
					final var leaf = fileCollection.readDataItemUsingIndex(index,path);
					if (compareToMe.get(path) != (int) leaf.getValue().asLong()) {
						System.err.println(
								"DATA DID NOT MATCH FOR path=" + path + " compareTo=" + compareToMe.get(path) +
										" read value=" + leaf.getValue().asLong());
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			} finally {
				readWriteLock.readLock().unlock();
			}
	}

	public void randomCheck() {
		while(true) {
			System.out.print("c");
			try {
				readWriteLock.readLock().lock();
				for (int i = 0; i < 1000; i++) {
					final int path = (int)(fileCollection.getMinimumValidKey() +
							RANDOM.nextInt((int)(fileCollection.getMaximumValidKey()- fileCollection.getMinimumValidKey())));
					final var leaf = fileCollection.readDataItemUsingIndex(index,path);
					if (compareToMe.get(path) != (int) leaf.getValue().asLong()) {
						System.err.println("DATA DID NOT MATCH FOR path=" + path + " compareTo=" + compareToMe.get(path) +
								" read value=" + leaf.getValue().asLong());
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			} finally {
				readWriteLock.readLock().unlock();
			}
		}
	}

	private long randomReadCount = 0;

	public void randomRead() {
		while(true) {
			if ((randomReadCount%1000) == 0) System.out.print("R");
			randomReadCount ++;
			final int path = (int)(fileCollection.getMinimumValidKey() +
					RANDOM.nextInt((int)(fileCollection.getMaximumValidKey()- fileCollection.getMinimumValidKey())));
			try {
				final var leaf = fileCollection.readDataItemUsingIndex(index,path);
				final var value = leaf.getValue();
				if (value == null) {
					System.err.println("DID NOT FIND VALUE FOR path=" + path);
				}
			} catch (IOException e) {
				System.err.println("Error doing random read of path "+path+" FirstLeafPath="+ fileCollection.getMinimumValidKey()+
						" LastLeafPath="+ fileCollection.getMaximumValidKey());
				e.printStackTrace();
				Throwable t = e;
				while (t.getCause() != null) {
					t = t.getCause();
					t.printStackTrace();

				}
				System.exit(1);
			}
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
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
		ThorsFileHammer thorsHammer = new ThorsFileHammer();
		thorsHammer.hammer();
	}
}
