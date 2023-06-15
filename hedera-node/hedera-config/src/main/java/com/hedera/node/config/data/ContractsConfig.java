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

package com.hedera.node.config.data;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

@ConfigData("contracts")
public record ContractsConfig(
        @ConfigProperty(defaultValue = "true") boolean itemizeStorageFees,
        // @ConfigProperty(defaultValue = "1062787,1461860") Set<Address> permittedDelegateCallers,
        @ConfigProperty(defaultValue = "31536000") long referenceSlotLifetime,
        @ConfigProperty(defaultValue = "100") int freeStorageTierLimit,
        @ConfigProperty(defaultValue = "0til100M,2000til450M") String storageSlotPriceTiers,
        @ConfigProperty(defaultValue = "7890000") long defaultLifetime,
        // @ConfigProperty(defaultValue = "") KnownBlockValues knownBlockHash,
        // @ConfigProperty(value = "keys.legacyActivations", defaultValue="1058134by[1062784]")
        // LegacyContractIdActivations keysLegacyActivations,
        @ConfigProperty(value = "localCall.estRetBytes", defaultValue = "32") int localCallEstRetBytes,
        @ConfigProperty(defaultValue = "true") boolean allowCreate2,
        @ConfigProperty(defaultValue = "false") boolean allowAutoAssociations,
        // @ConfigProperty(defaultValue =
        // "TokenAssociateToAccount,TokenDissociateFromAccount,TokenFreezeAccount,TokenUnfreezeAccount,TokenGrantKycToAccount,TokenRevokeKycFromAccount,TokenAccountWipe,TokenBurn,TokenDelete,TokenMint,TokenUnpause,TokenPause,TokenCreate,TokenUpdate,ContractCall,CryptoTransfer") Set<HederaFunctionality> allowSystemUseOfHapiSigs,
        @ConfigProperty(defaultValue = "0") long maxNumWithHapiSigsAccess,
        // @ConfigProperty(defaultValue = "") Set<Address> withSpecialHapiSigsAccess,
        @ConfigProperty(defaultValue = "false") boolean enforceCreationThrottle,
        @ConfigProperty(defaultValue = "15000000") long maxGasPerSec,
        @ConfigProperty(value = "maxKvPairs.aggregate", defaultValue = "500000000") long maxKvPairsAggregate,
        @ConfigProperty(value = "maxKvPairs.individual", defaultValue = "163840") int maxKvPairsIndividual,
        @ConfigProperty(defaultValue = "5000000") long maxNumber,
        @ConfigProperty(defaultValue = "295") int chainId,
        // @ConfigProperty(defaultValue = "CONTRACT_STATE_CHANGE,CONTRACT_BYTECODE,CONTRACT_ACTION") Set<SidecarType>
        // sidecars,
        @ConfigProperty(defaultValue = "false") boolean sidecarValidationEnabled,
        @ConfigProperty(value = "throttle.throttleByGas", defaultValue = "true") boolean throttleThrottleByGas,
        @ConfigProperty(defaultValue = "20") int maxRefundPercentOfGasLimit,
        @ConfigProperty(defaultValue = "5000000") long scheduleThrottleMaxGasLimit,
        @ConfigProperty(defaultValue = "true") boolean redirectTokenCalls,
        @ConfigProperty(value = "precompile.exchangeRateGasCost", defaultValue = "100")
                long precompileExchangeRateGasCost,
        @ConfigProperty(value = "precompile.htsDefaultGasCost", defaultValue = "10000")
                long precompileHtsDefaultGasCost,
        @ConfigProperty(value = "precompile.exportRecordResults", defaultValue = "true")
                boolean precompileExportRecordResults,
        @ConfigProperty(value = "precompile.htsEnableTokenCreate", defaultValue = "true")
                boolean precompileHtsEnableTokenCreate,
        // @ConfigProperty(value = "precompile.unsupportedCustomFeeReceiverDebits", defaultValue = "")
        // Set<CustomFeeType> precompileUnsupportedCustomFeeReceiverDebits,
        @ConfigProperty(value = "precompile.atomicCryptoTransfer.enabled", defaultValue = "false")
                boolean precompileAtomicCryptoTransferEnabled,
        @ConfigProperty(value = "precompile.hrcFacade.associate.enabled", defaultValue = "true")
                boolean precompileHrcFacadeAssociateEnabled,
        @ConfigProperty(value = "evm.version.dynamic", defaultValue = "false") boolean evmVersionDynamic,
        @ConfigProperty(value = "evm.version", defaultValue = "v0.34") String evmVersion) {}
