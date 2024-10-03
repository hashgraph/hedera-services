/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.record.impl.producers;

import static com.swirlds.platform.test.fixtures.state.TestSchema.CURRENT_VERSION;

import com.hedera.node.app.records.impl.BlockRecordStreamProducer;
import com.hedera.node.app.records.impl.producers.BlockRecordWriterFactory;
import com.hedera.node.app.records.impl.producers.StreamFileProducerSingleThreaded;
import com.hedera.node.app.records.impl.producers.formats.v6.BlockRecordFormatV6;
import edu.umd.cs.findbugs.annotations.NonNull;

final class StreamFileProducerSingleThreadedTest extends StreamFileProducerTest {

    @Override
    BlockRecordStreamProducer createStreamProducer(@NonNull final BlockRecordWriterFactory factory) {
        return new StreamFileProducerSingleThreaded(BlockRecordFormatV6.INSTANCE, factory, CURRENT_VERSION);
    }
}
