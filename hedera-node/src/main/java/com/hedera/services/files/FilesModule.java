package com.hedera.services.files;

import com.hedera.services.files.store.FcBlobsBytesStore;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.fcmap.FCMap;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.util.Map;
import java.util.function.Supplier;

@Module
public abstract class FilesModule {
	@Provides @Singleton
	public static Map<String, byte[]> provideBlobStore(Supplier<FCMap<MerkleBlobMeta, MerkleOptionalBlob>> storage) {
		return new FcBlobsBytesStore(MerkleOptionalBlob::new, storage);
	}

	@Provides @Singleton
	public static Map<FileID, byte[]> provideDataMap() {
		throw new AssertionError("Not implemented!");
	}
}
