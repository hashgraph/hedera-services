/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.spi.config.data;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

@ConfigData("contracts")
public record ContractsConfig(@ConfigProperty Object itemizeStorageFees,
                              @ConfigProperty Object permittedDelegateCallers,
                              @ConfigProperty Object referenceSlotLifetime,
                              @ConfigProperty Object freeStorageTierLimit,
                              @ConfigProperty Object storageSlotPriceTiers,
                              @ConfigProperty Object defaultLifetime,
                              @ConfigProperty Object knownBlockHash,
                              @ConfigProperty("keys.legacyActivations") Object keysLegacyActivations,
                              @ConfigProperty("localCall.estRetBytes") Object localCallEstRetBytes,
                              @ConfigProperty Object allowCreate2,
                              @ConfigProperty Object allowAutoAssociations,
                              @ConfigProperty Object allowSystemUseOfHapiSigs,
                              @ConfigProperty Object maxNumWithHapiSigsAccess,
                              @ConfigProperty Object withSpecialHapiSigsAccess,
                              @ConfigProperty Object enforceCreationThrottle,
                              @ConfigProperty Object maxGasPerSec,
                              @ConfigProperty("maxKvPairs.aggregate") Object maxKvPairsAggregate,
                              @ConfigProperty("maxKvPairs.individual") Object maxKvPairsIndividual,
                              @ConfigProperty Object maxNumber,
                              @ConfigProperty Object chainId,
                              @ConfigProperty Object sidecars,
                              @ConfigProperty Object sidecarValidationEnabled,
                              @ConfigProperty("throttle.throttleByGas") Object throttleThrottleByGas,
                              @ConfigProperty Object maxRefundPercentOfGasLimit,
                              @ConfigProperty Object scheduleThrottleMaxGasLimit,
                              @ConfigProperty Object redirectTokenCalls,
                              @ConfigProperty("precompile.exchangeRateGasCost") Object precompileExchangeRateGasCost,
                              @ConfigProperty("precompile.htsDefaultGasCost") Object precompileHtsDefaultGasCost,
                              @ConfigProperty("precompile.exportRecordResults") Object precompileExportRecordResults,
                              @ConfigProperty("precompile.htsEnableTokenCreate") Object precompileHtsEnableTokenCreate,
                              @ConfigProperty("precompile.unsupportedCustomFeeReceiverDebits") Object precompileUnsupportedCustomFeeReceiverDebits,
                              @ConfigProperty("precompile.atomicCryptoTransfer.enabled") Object precompileAtomicCryptoTransferEnabled,
                              @ConfigProperty("precompile.hrcFacade.associate.enabled") Object precompileHrcFacadeAssociateEnabled,
                              @ConfigProperty("evm.version.dynamic") Object evmVersionDynamic,
                              @ConfigProperty("evm.version") Object evmVersion) {

}
