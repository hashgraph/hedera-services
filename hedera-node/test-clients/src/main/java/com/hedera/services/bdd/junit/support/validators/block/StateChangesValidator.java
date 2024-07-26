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

import static java.util.Comparator.comparing;
import static org.junit.jupiter.api.Assertions.fail;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.blocks.BlockStreamService;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
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
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.platform.state.MerkleStateRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.InstantSource;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StateChangesValidator {
    private static final Logger logger = LogManager.getLogger(StateChangesValidator.class);
    final MerkleStateRoot state = new MerkleStateRoot();

    public static void main(String[] args) throws IOException {
        final var path = "hedera-node/test-clients/src/main/resource/block-streams/blocks";
        final var validator = new StateChangesValidator();
        validator.validateStreams(path);
    }

    public StateChangesValidator() {
        final ConstructableRegistry constructableRegistry = ConstructableRegistry.getInstance();
        final var bootstrapConfig = new BootstrapConfigProviderImpl().getConfiguration();
        final ServicesRegistry.Factory registryFactory = ServicesRegistryImpl::new;
        final var servicesRegistry = registryFactory.create(constructableRegistry, bootstrapConfig);
        final var migrator = new OrderedServiceMigrator();
        final var instantSource = InstantSource.system();

        registerServices(instantSource, servicesRegistry, bootstrapConfig);

        migrator.doMigrations(
                state,
                servicesRegistry,
                null,
                SemanticVersion.newBuilder().major(80).build(),
                new ConfigProviderImpl().getConfiguration(),
                new FakeNetworkInfo(),
                new NoOpMetrics());

        logger.info("Registered all Service and migrated to version 80");
    }

    public void validateStreams(String blockFileDir) throws IOException {
        logger.info("Validating streams at dir {}", blockFileDir);

        final var list = Files.walk(Path.of(blockFileDir))
                .map(Path::toString)
                .filter(f -> f.endsWith(".blk.gz"))
                .sorted(comparing(this::extractBlockNumber))
                .toList();

        for (final var file : list) {
            final var block = readBlockFromGzip(Path.of(file));
            logger.info("Block: {}", block);
            for (final var item : block.items()) {
               if(item.hasStateChanges()){
                   final var stateChanges = item.stateChangesOrThrow().stateChanges();
                   for(final var stateChange : stateChanges){
                       switch(stateChange.changeOperation().kind()){
                           case UNSET -> throw new IllegalStateException("Change operation is not set");
                           case STATE_ADD -> {

                           }
                           case STATE_REMOVE -> {
                           }
                           case SINGLETON_UPDATE -> {
                           }
                           case MAP_UPDATE -> {
                           }
                           case MAP_DELETE -> {
                           }
                           case QUEUE_PUSH -> {
                           }
                           case QUEUE_POP -> {
                           }
                       }
                   }
               }
            }
        }
    }

    public static Block readBlockFromGzip(Path file) {
        try (final GZIPInputStream in = new GZIPInputStream(Files.newInputStream(file))) {
            return Block.PROTOBUF.parse(Bytes.wrap(in.readAllBytes()));
        } catch (Exception e) {
            fail("Unknown file type: " + file.getFileName());
            return null;
        }
    }

    private long extractBlockNumber(String fileName) {
        String lastPart = fileName.substring(fileName.lastIndexOf('/') + 1);
        String numberPart = lastPart.substring(0, lastPart.indexOf(".blk.gz"));
        try {
            return Long.parseLong(numberPart);
        } catch (NumberFormatException e) {
            logger.warn("Unable to parse block number from file name: {}", fileName);
        }
        return -1;
    }

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
