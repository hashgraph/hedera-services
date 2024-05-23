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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asNumberedContractId;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractNativeSystemContract;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallFactory;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

@Singleton
public class HtsSystemContract extends AbstractNativeSystemContract implements HederaSystemContract {
    public static final String HTS_SYSTEM_CONTRACT_NAME = "HTS";
    public static final String HTS_EVM_ADDRESS = "0x167";
    public static final ContractID HTS_CONTRACT_ID = asNumberedContractId(Address.fromHexString(HTS_EVM_ADDRESS));

    @Inject
    public HtsSystemContract(@NonNull final GasCalculator gasCalculator, @NonNull final HtsCallFactory callFactory) {
        super(HTS_SYSTEM_CONTRACT_NAME, callFactory, HTS_CONTRACT_ID, gasCalculator);
    }

    @Override
    protected FrameUtils.CallType callTypeOf(@NonNull MessageFrame frame) {
        return FrameUtils.callTypeOf(frame);
    }
}
