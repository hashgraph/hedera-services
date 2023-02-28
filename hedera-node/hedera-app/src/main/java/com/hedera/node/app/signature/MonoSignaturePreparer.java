/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.signature;

import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.state.HederaState;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.swirlds.common.crypto.TransactionSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Implementation of {@link SignaturePreparer} that delegates to the respective mono-service's functionality
 */
@Singleton
public class MonoSignaturePreparer implements SignaturePreparer {

    @Inject
    public MonoSignaturePreparer() {}

    @NonNull
    @Override
    public TransactionSignature prepareSignature(
            @NonNull HederaState state,
            @NonNull byte[] txBodyBytes,
            @NonNull SignatureMap signatureMap,
            @NonNull AccountID accountID) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @NonNull
    @Override
    public Map<HederaKey, TransactionSignature> prepareSignatures(
            @NonNull HederaState state,
            @NonNull byte[] txBodyBytes,
            @NonNull SignatureMap signatureMap,
            @NonNull List<HederaKey> keys) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
