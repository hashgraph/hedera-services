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

package com.hedera.node.app.service.contract.impl.exec.v030;

import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

/**
 * The initial implementation of {@link AddressChecks} from v0.30; did not have a concept of system accounts.
 */
@Singleton
public class Version030AddressChecks implements AddressChecks {
    private final int[] precompileAccountNumbers;

    @Inject
    public Version030AddressChecks(@NonNull final Map<Address, PrecompiledContract> hederaPrecompiles) {
        precompileAccountNumbers = new int[hederaPrecompiles.size()];
        int i = 0;
        for (final var address : hederaPrecompiles.keySet()) {
            if (address.numberOfLeadingZeroBytes() != 18) {
                throw new IllegalArgumentException("Precompile address " + address + " is outside system range");
            }
            precompileAccountNumbers[i++] = address.getInt(16);
        }
    }

    @Override
    public boolean isPresent(@NonNull final Address address, @NonNull final MessageFrame frame) {
        return isHederaPrecompile(address) || frame.getWorldUpdater().get(address) != null;
    }

    @Override
    public boolean isSystemAccount(@NonNull final Address address) {
        return false;
    }

    @Override
    public boolean isNonUserAccount(@NonNull final Address address) {
        return false;
    }

    @Override
    public boolean isHederaPrecompile(@NonNull final Address address) {
        return address.numberOfLeadingZeroBytes() >= 18 && isPrecompile(address.getInt(16));
    }

    private boolean isPrecompile(final int number) {
        for (final var precompileNumber : precompileAccountNumbers) {
            if (precompileNumber == number) {
                return true;
            }
        }
        return false;
    }
}
