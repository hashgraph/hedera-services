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

package com.hedera.node.app.service.contract.impl.exec.v038;

import com.hedera.node.app.service.contract.impl.exec.v030.Version030AddressChecks;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.processor.AbstractMessageProcessor;

/**
 * The system-aware address checks for the 0.38+, which customize some {@link Operation} and
 * {@link AbstractMessageProcessor} behavior to treat system addresses below 0.0.750 as special.
 */
@Singleton
public class Version038AddressChecks extends Version030AddressChecks {
    private static final int NUM_SYSTEM_ACCOUNTS = 750;
    private static final int FIRST_USER_ACCOUNT = 1_001;

    @Inject
    public Version038AddressChecks(@NonNull Map<Address, PrecompiledContract> hederaPrecompiles) {
        super(hederaPrecompiles);
    }

    @Override
    public boolean isSystemAccount(@NonNull final Address address) {
        return address.numberOfLeadingZeroBytes() >= 18 && address.getInt(16) <= NUM_SYSTEM_ACCOUNTS;
    }

    @Override
    public boolean isNonUserAccount(@NonNull final Address address) {
        return address.numberOfLeadingZeroBytes() >= 18 && address.getInt(16) < FIRST_USER_ACCOUNT;
    }
}
