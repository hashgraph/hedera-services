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

package com.hedera.node.app.service.contract.impl;

import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.handlers.ContractHandlers;
import com.hedera.node.app.spi.signatures.SignatureVerifier;
import dagger.BindsInstance;
import dagger.Component;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.InstantSource;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Singleton;
import org.hyperledger.besu.evm.tracing.OperationTracer;

@Singleton
@Component(modules = ContractServiceModule.class)
public interface ContractServiceComponent {
    @Component.Factory
    interface Factory {
        ContractServiceComponent create(
                @BindsInstance InstantSource instantSource,
                @BindsInstance SignatureVerifier signatureVerifier,
                @BindsInstance VerificationStrategies verificationStrategies,
                @BindsInstance @Nullable Supplier<List<OperationTracer>> addOnTracers);
    }

    ContractHandlers handlers();
}
