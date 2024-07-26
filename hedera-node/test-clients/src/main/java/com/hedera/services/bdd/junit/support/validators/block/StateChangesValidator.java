/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit.support.validators.block;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.blocks.BlockStreamService;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.addressbook.impl.AddressBookServiceImpl;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.NetworkServiceImpl;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.util.impl.UtilServiceImpl;
import com.hedera.node.app.services.OrderedServiceMigrator;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.services.ServicesRegistryImpl;
import com.hedera.node.app.spi.fixtures.info.FakeNetworkInfo;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.hedera.node.config.VersionedConfiguration;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import java.time.InstantSource;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StateChangesValidator {
    private static final Logger logger = LogManager.getLogger(StateChangesValidator.class);

    public StateChangesValidator() {
        final ConstructableRegistry constructableRegistry = ConstructableRegistry.getInstance();
        final var bootstrapConfig = new BootstrapConfigProviderImpl().getConfiguration();
        final ServicesRegistry.Factory registryFactory = ServicesRegistryImpl::new;
        final var servicesRegistry = registryFactory.create(constructableRegistry, bootstrapConfig);
        final var migrator = new OrderedServiceMigrator();
        final var instantSource = InstantSource.system();
        final var state = new MerkleHederaState();

        registerServices(instantSource, servicesRegistry, bootstrapConfig);

        migrator.doMigrations(
                state,
                servicesRegistry,
                null,
                SemanticVersion.newBuilder().major(80).build(),
                bootstrapConfig,
                new FakeNetworkInfo(),
                new NoOpMetrics());

        logger.info("Registered all Service and migrated to version 80");
    }

    //    public void validateStreams(Path blockFilePath, BlockStreamConfig blockStreamConfig) {
    //        logger.info("Validating streams at path {}", blockFilePath);
    //        OutputStream out = null;
    //        try {
    //            out = Files.newOutputStream(blockFilePath);
    //            out = new BufferedOutputStream(out, 1024 * 1024); // 1 MB
    //            if (blockStreamConfig.compressFilesOnCreation()) {
    //                out = new GZIPOutputStream(out, 1024 * 256); // 256 KB
    //                // This double buffer is needed to reduce the number of synchronized calls to the underlying
    //                // GZIPOutputStream. We know most files are going to be ~3-4 MB, so we can safely buffer that
    // much.
    //                out = new BufferedOutputStream(out, 1024 * 1024 * 4); // 4 MB
    //            }
    //
    //            this.writableStreamingData = new WritableStreamingData(out);
    //        } catch (final IOException e) {
    //            // If an exception was thrown, we should close the stream if it was opened to prevent a resource leak.
    //            if (out != null) {
    //                try {
    //                    out.close();
    //                } catch (IOException ex) {
    //                    logger.error("Error closing the FileBlockItemWriter output stream", ex);
    //                }
    //            }
    //            // We must be able to produce blocks.
    //            logger.fatal("Could not create block file {}", blockFilePath, e);
    //            throw new UncheckedIOException(e);
    //        }
    //    }

    private void registerServices(
            final InstantSource instantSource,
            final ServicesRegistry servicesRegistry,
            final VersionedConfiguration bootstrapConfig) {
        // Register all service schema RuntimeConstructable factories before platform init
        Set.of(
                        new EntityIdService(),
                        new ConsensusServiceImpl(),
                        new ContractServiceImpl(instantSource),
                        new FileServiceImpl(),
                        new FreezeServiceImpl(),
                        new ScheduleServiceImpl(),
                        new TokenServiceImpl(),
                        new UtilServiceImpl(),
                        new RecordCacheService(),
                        new BlockRecordService(),
                        new BlockStreamService(bootstrapConfig),
                        new FeeService(),
                        new CongestionThrottleService(),
                        new NetworkServiceImpl(),
                        new AddressBookServiceImpl())
                .forEach(servicesRegistry::register);
    }

    public void validate() {}
}
