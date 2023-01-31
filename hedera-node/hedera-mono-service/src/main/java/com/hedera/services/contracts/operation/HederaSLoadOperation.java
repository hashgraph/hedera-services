/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts.operation;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.evm.contracts.operations.HederaEvmSLoadOperation;
import com.hedera.services.stream.proto.SidecarType;
import javax.inject.Inject;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/**
 * Hedera adapted version of the {@link org.hyperledger.besu.evm.operation.SLoadOperation}. No
 * externally visible changes, the result of sload is stored for the benefit of their record stream.
 */
public class HederaSLoadOperation extends HederaEvmSLoadOperation {
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public HederaSLoadOperation(
            final GasCalculator gasCalculator, final GlobalDynamicProperties dynamicProperties) {
        super(gasCalculator);
        this.dynamicProperties = dynamicProperties;
    }

    @Override
    protected void intermediateCustomLogic(
            MessageFrame frame, Address address, Bytes32 key, UInt256 storageValue) {
        if (dynamicProperties.enabledSidecars().contains(SidecarType.CONTRACT_STATE_CHANGE)) {
            HederaOperationUtil.cacheExistingValue(frame, address, key, storageValue);
        }
    }
}
