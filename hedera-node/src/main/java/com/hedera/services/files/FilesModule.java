package com.hedera.services.files;

import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.files.interceptors.ConfigListUtils;
import com.hedera.services.files.interceptors.FeeSchedulesManager;
import com.hedera.services.files.interceptors.TxnAwareRatesManager;
import com.hedera.services.files.interceptors.ValidatingCallbackInterceptor;
import com.hedera.services.files.store.FcBlobsBytesStore;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.fcmap.FCMap;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;

import javax.inject.Singleton;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static com.hedera.services.files.DataMapFactory.dataMapFrom;
import static com.hedera.services.files.MetadataMapFactory.metaMapFrom;
import static com.hedera.services.files.interceptors.ConfigListUtils.uncheckedParse;
import static com.hedera.services.files.interceptors.PureRatesValidation.isNormalIntradayChange;

@Module
public abstract class FilesModule {
	@Binds
	@Singleton
	public abstract HederaFs bindHederaFs(TieredHederaFs tieredHederaFs);

	@Provides
	@Singleton
	public static Map<String, byte[]> provideBlobStore(Supplier<FCMap<MerkleBlobMeta, MerkleOptionalBlob>> storage) {
		return new FcBlobsBytesStore(MerkleOptionalBlob::new, storage);
	}

	@Provides
	@Singleton
	public static Map<FileID, byte[]> provideDataMap(Map<String, byte[]> blobStore) {
		return dataMapFrom(blobStore);
	}

	@Provides
	@Singleton
	public static Map<FileID, HFileMeta> provideMetadataMap(Map<String, byte[]> blobStore) {
		return metaMapFrom(blobStore);
	}

	@Provides
	@Singleton
	public static Consumer<ExchangeRateSet> provideExchangeRateSetUpdate(HbarCentExchange exchange) {
		return exchange::updateRates;
	}

	@Provides
	@Singleton
	public static IntFunction<BiPredicate<ExchangeRates, ExchangeRateSet>> provideLimitChangeTestFactory() {
		return limitPercent -> (base, proposed) -> isNormalIntradayChange(base, proposed, limitPercent);
	}

	@Provides
	@ElementsIntoSet
	public static Set<FileUpdateInterceptor> provideFeeSchedulesInterceptor(
			FeeSchedulesManager feeSchedulesManager,
			TxnAwareRatesManager txnAwareRatesManager,
			SysFileCallbacks sysFileCallbacks,
			@CompositeProps PropertySource properties
	) {
		final var propertiesCb = sysFileCallbacks.propertiesCb();
		final var propertiesManager = new ValidatingCallbackInterceptor(
				0,
				"files.networkProperties",
				properties,
				contents -> propertiesCb.accept(uncheckedParse(contents)),
				ConfigListUtils::isConfigList);

		final var permissionsCb = sysFileCallbacks.permissionsCb();
		final var permissionsManager = new ValidatingCallbackInterceptor(
				0,
				"files.hapiPermissions",
				properties,
				contents -> permissionsCb.accept(uncheckedParse(contents)),
				ConfigListUtils::isConfigList);

		return Set.of(feeSchedulesManager, txnAwareRatesManager, propertiesManager, permissionsManager);
	}
}
