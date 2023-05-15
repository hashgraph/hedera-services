/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.service.mono;

import com.hedera.node.app.service.mono.config.ConfigModule;
import com.hedera.node.app.service.mono.context.ContextModule;
import com.hedera.node.app.service.mono.context.properties.PropertiesModule;
import com.hedera.node.app.service.mono.contracts.ContractsModule;
import com.hedera.node.app.service.mono.fees.FeesModule;
import com.hedera.node.app.service.mono.files.FilesModule;
import com.hedera.node.app.service.mono.grpc.GrpcModule;
import com.hedera.node.app.service.mono.keys.KeysModule;
import com.hedera.node.app.service.mono.ledger.LedgerModule;
import com.hedera.node.app.service.mono.queries.QueriesModule;
import com.hedera.node.app.service.mono.records.RecordsModule;
import com.hedera.node.app.service.mono.sigs.SigsModule;
import com.hedera.node.app.service.mono.state.StateModule;
import com.hedera.node.app.service.mono.state.expiry.ExpiryModule;
import com.hedera.node.app.service.mono.state.tasks.TaskModule;
import com.hedera.node.app.service.mono.stats.StatsModule;
import com.hedera.node.app.service.mono.store.StoresModule;
import com.hedera.node.app.service.mono.throttling.ThrottlingModule;
import com.hedera.node.app.service.mono.txns.TransactionsModule;
import com.hedera.node.app.service.mono.txns.submission.SubmissionModule;
import dagger.Module;

/**
 * Dagger module for all dagger module of the mono service module.
 *
 * @deprecated will be removed once mono service is 100% refactored
 */
@Module(includes = {
        ConfigModule.class,
        ContextModule.class,
        PropertiesModule.class,
        ContractsModule.class,
        FilesModule.class,
        GrpcModule.class,
        KeysModule.class,
        LedgerModule.class,
        QueriesModule.class,
        RecordsModule.class,
        SigsModule.class,
        StateModule.class,
        ExpiryModule.class,
        TaskModule.class,
        StatsModule.class,
        StoresModule.class,
        ThrottlingModule.class,
        TransactionsModule.class,
        SubmissionModule.class,
        FeesModule.class
})
@Deprecated(forRemoval = true)
public class LegacyMonoDaggerModule {
}
