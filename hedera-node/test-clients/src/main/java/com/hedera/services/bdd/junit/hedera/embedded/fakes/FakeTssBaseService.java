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

package com.hedera.services.bdd.junit.hedera.embedded.fakes;

import com.hedera.node.app.tss.TssBaseService;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.BiConsumer;

public class FakeTssBaseService implements TssBaseService {
    @Override
    public void requestLedgerSignature(@NonNull final byte[] messageHash) {}

    @Override
    public void registerLedgerSignatureConsumer(@NonNull final BiConsumer<byte[], byte[]> consumer) {}

    @Override
    public void unregisterLedgerSignatureConsumer(@NonNull final BiConsumer<byte[], byte[]> consumer) {}
}
