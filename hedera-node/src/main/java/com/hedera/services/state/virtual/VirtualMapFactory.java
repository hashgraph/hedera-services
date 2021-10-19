package com.hedera.services.state.virtual;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.jasperdb.VirtualInternalRecordSerializer;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.DataFileCommon;
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

	public VirtualMap<VirtualBlobKey, VirtualBlobValue> newVirtualizedBlobs() {
		final var blobKeySerializer = new VirtualBlobKeySerializer();
		final VirtualLeafRecordSerializer<VirtualBlobKey, VirtualBlobValue> blobLeafRecordSerializer =
				new VirtualLeafRecordSerializer<>(
						1,
						DigestType.SHA_384,
						1,
						VirtualBlobKey.sizeInBytes(),
						VirtualBlobKey::new,
						1,
						VirtualBlobValue.sizeInBytes(),
						VirtualBlobValue::new,
						false);

		final VirtualDataSource<VirtualBlobKey, VirtualBlobValue> ds;
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

	public VirtualMap<ContractKey, ContractValue> newVirtualizedContractStorage() {
		final var storageKeySerializer = new ContractKeySerializer();
		final VirtualLeafRecordSerializer<ContractKey, ContractValue> storageLeafRecordSerializer =
				new VirtualLeafRecordSerializer<>(
						1,
						DigestType.SHA_384,
						1,
						DataFileCommon.VARIABLE_DATA_SIZE,
						ContractKey::new,
						1,
						ContractValue.SERIALIZED_SIZE,
						ContractValue::new,
						true);
		;

		final VirtualDataSource<ContractKey, ContractValue> ds;
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
