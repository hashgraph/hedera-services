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

package com.hedera.node.app.workflows.prehandle;

import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.sigs.sourcing.PojoSigMapPubKeyToSigBytes;
import com.hedera.node.app.service.mono.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.node.app.service.mono.txns.ProcessLogic;
import com.hedera.node.app.signature.MonoSignaturePreparer;
import com.hedera.node.app.signature.SignaturePreparer;
import com.hedera.node.app.signature.SignatureVerifier;
import com.hedera.node.app.signature.SignatureVerifierImpl;
import com.hedera.node.app.state.merkle.MerkleAddressBook;
import com.hedera.node.app.workflows.handle.AdaptedMonoProcessLogic;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

@Module
public interface PreHandleWorkflowModule {
    @Provides
    static Function<SignatureMap, PubKeyToSigBytes> provideKeyToSigFactory() {
        return signatureMap -> new PojoSigMapPubKeyToSigBytes(PbjConverter.fromPbj(signatureMap));
    }

    @Binds
    PreHandleWorkflow bindPreHandleWorkflow(PreHandleWorkflowImpl preHandleWorkflow);

    @Binds
    SignaturePreparer bindSignaturePreparer(MonoSignaturePreparer signaturePreparer);

    @Binds
    SignatureVerifier bindSignatureVerifier(SignatureVerifierImpl signatureVerifier);

    @Binds
    ProcessLogic bindProcessLogic(AdaptedMonoProcessLogic processLogic);

    @Provides
    static ExecutorService provideExecutorService() {
        return ForkJoinPool.commonPool();
    }

    @Provides
    static MerkleAddressBook provideAddressBook() {
        return new MerkleAddressBook();
    }
}
