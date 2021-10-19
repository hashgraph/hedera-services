package com.hedera.services.state.virtual;

import com.swirlds.jasperdb.VirtualInternalRecordSerializer;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class VirtualMapFactory {
	private static final long MAX_BLOBS = 50_000_000;
	private static final long MAX_STORAGE_ENTRIES = 500_000_000;
	private static final long MAX_IN_MEMORY_INTERNAL_HASHES = 0;

	@FunctionalInterface
	public interface JasperDbFactory {
		<K extends VirtualKey, V extends VirtualValue> VirtualDataSource<K, V> newJdb(
				VirtualLeafRecordSerializer<K, V> leafRecordSerializer,
				VirtualInternalRecordSerializer virtualInternalRecordSerializer, KeySerializer<K> keySerializer,
				Path storageDir,
				long maxNumOfKeys,
				boolean mergingEnabled,
				long internalHashesRamToDiskThreshold,
				boolean preferDiskBasedIndexes
		) throws IOException;
	}

	private final String jdbDataLoc;
	private final JasperDbFactory jdbFactory;

	public VirtualMapFactory(
			final String jdbDataLoc,
			final JasperDbFactory jdbFactory
	) {
		this.jdbDataLoc = jdbDataLoc;
		this.jdbFactory = jdbFactory;
	}

	public <K extends VirtualKey, V extends VirtualValue> VirtualMap<K, V> newVirtualizedBlobs(
			final KeySerializer<K> blobKeySerializer,
			final VirtualLeafRecordSerializer<K, V> blobLeafRecordSerializer
	) {
		final VirtualDataSource<K, V> ds;
		try {
			ds = jdbFactory.newJdb(
					blobLeafRecordSerializer,
					new VirtualInternalRecordSerializer(),
					blobKeySerializer,
					Paths.get(blobsLoc()),
					MAX_BLOBS,
					true,
					MAX_IN_MEMORY_INTERNAL_HASHES,
					false);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return new VirtualMap<>(ds);
	}

	public <K extends VirtualKey, V extends VirtualValue> VirtualMap<K, V> newVirtualizedContractStorage(
			final KeySerializer<K> storageKeySerializer,
			final VirtualLeafRecordSerializer<K, V> storageLeafRecordSerializer
	) {
		final VirtualDataSource<K, V> ds;
		try {
			ds = jdbFactory.newJdb(
					storageLeafRecordSerializer,
					new VirtualInternalRecordSerializer(),
					storageKeySerializer,
					Paths.get(storageLoc()),
					MAX_STORAGE_ENTRIES,
					true,
					MAX_IN_MEMORY_INTERNAL_HASHES,
					false);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return new VirtualMap<>(ds);
	}

	private String blobsLoc() {
		return jdbDataLoc + File.separator + "blobs";
	}

	private String storageLoc() {
		return jdbDataLoc + File.separator + "storage";
	}
}
