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

package com.hedera.node.app.workflows.handle.flow.modules;

import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.workflows.handle.flow.annotations.UserTxnScope;
import com.hedera.node.app.workflows.handle.flow.process.ProcessRunner;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Module
public interface RecordStreamModule {
    @Provides
    @UserTxnScope
    static SingleTransactionRecordBuilderImpl provideUserTransactionRecordBuilder(
            @NonNull RecordListBuilder recordListBuilder) {
        return recordListBuilder.userTransactionRecordBuilder();
    }

    @Binds
    @UserTxnScope
    Supplier<Stream<SingleTransactionRecord>> recordStreamSupplier(ProcessRunner processRunner);
}
