/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.files;

import static com.hedera.services.context.properties.PropertyNames.FILES_HAPI_PERMISSIONS;
import static com.hedera.services.context.properties.PropertyNames.FILES_NETWORK_PROPERTIES;
import static com.hedera.services.files.DataMapFactory.dataMapFrom;
import static com.hedera.services.files.MetadataMapFactory.metaMapFrom;
import static com.hedera.services.files.interceptors.ConfigListUtils.uncheckedParse;
import static com.hedera.services.files.interceptors.PureRatesValidation.isNormalIntradayChange;

import com.hedera.services.config.FileNumbers;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.files.interceptors.ConfigListUtils;
import com.hedera.services.files.interceptors.FeeSchedulesManager;
import com.hedera.services.files.interceptors.ThrottleDefsManager;
import com.hedera.services.files.interceptors.TxnAwareRatesManager;
import com.hedera.services.files.interceptors.ValidatingCallbackInterceptor;
import com.hedera.services.files.store.FcBlobsBytesStore;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.virtualmap.VirtualMap;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import javax.inject.Singleton;

@Module
public interface FilesModule {
    @Binds
    @Singleton
    HederaFs bindHederaFs(TieredHederaFs tieredHederaFs);

    @Provides
    @Singleton
    static Map<String, byte[]> provideBlobStore(
            Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> storage) {
        return new FcBlobsBytesStore(storage);
    }

    @Provides
    @Singleton
    static Map<FileID, byte[]> provideDataMap(Map<String, byte[]> blobStore) {
        return dataMapFrom(blobStore);
    }

    @Provides
    @Singleton
    static Map<FileID, HFileMeta> provideMetadataMap(Map<String, byte[]> blobStore) {
        return metaMapFrom(blobStore);
    }

    @Provides
    @Singleton
    static Consumer<ExchangeRateSet> provideExchangeRateSetUpdate(HbarCentExchange exchange) {
        return exchange::updateRates;
    }

    @Provides
    @Singleton
    static IntFunction<BiPredicate<ExchangeRates, ExchangeRateSet>>
            provideLimitChangeTestFactory() {
        return limitPercent ->
                (base, proposed) -> isNormalIntradayChange(base, proposed, limitPercent);
    }

    @Provides
    @ElementsIntoSet
    static Set<FileUpdateInterceptor> provideFileUpdateInterceptors(
            FileNumbers fileNums,
            SysFileCallbacks sysFileCallbacks,
            Supplier<AddressBook> addressBook,
            FeeSchedulesManager feeSchedulesManager,
            TxnAwareRatesManager txnAwareRatesManager,
            @CompositeProps PropertySource properties) {
        final var propertiesCb = sysFileCallbacks.propertiesCb();
        final var propertiesManager =
                new ValidatingCallbackInterceptor(
                        0,
                        FILES_NETWORK_PROPERTIES,
                        properties,
                        contents -> propertiesCb.accept(uncheckedParse(contents)),
                        ConfigListUtils::isConfigList);

        final var permissionsCb = sysFileCallbacks.permissionsCb();
        final var permissionsManager =
                new ValidatingCallbackInterceptor(
                        0,
                        FILES_HAPI_PERMISSIONS,
                        properties,
                        contents -> permissionsCb.accept(uncheckedParse(contents)),
                        ConfigListUtils::isConfigList);

        final var throttlesCb = sysFileCallbacks.throttlesCb();
        final var throttleDefsManager = new ThrottleDefsManager(fileNums, addressBook, throttlesCb);

        return Set.of(
                feeSchedulesManager,
                txnAwareRatesManager,
                propertiesManager,
                permissionsManager,
                throttleDefsManager);
    }
}
