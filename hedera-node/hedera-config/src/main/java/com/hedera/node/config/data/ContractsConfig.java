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

package com.hedera.node.config.data;

import com.hedera.hapi.streams.SidecarType;
import com.hedera.node.config.NetworkProperty;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.util.Set;

@ConfigData("contracts")
public record ContractsConfig(
        @ConfigProperty(defaultValue = "true") @NetworkProperty boolean itemizeStorageFees,
        @ConfigProperty(defaultValue = "1062787,1461860") Set<Long> permittedDelegateCallers,
        @ConfigProperty(defaultValue = "31536000") @NetworkProperty long referenceSlotLifetime,
        @ConfigProperty(defaultValue = "100") @NetworkProperty int freeStorageTierLimit,
        @ConfigProperty(defaultValue = "0til100M,2000til450M") @NetworkProperty String storageSlotPriceTiers,
        @ConfigProperty(defaultValue = "7890000") @NetworkProperty long defaultLifetime,
        // @ConfigProperty(defaultValue = "") KnownBlockValues knownBlockHash,
        @ConfigProperty(value = "keys.legacyActivations", defaultValue = "1058134by[1062784]")
                String keysLegacyActivations,
        @ConfigProperty(value = "localCall.estRetBytes", defaultValue = "4096") @NetworkProperty
                int localCallEstRetBytes,
        @ConfigProperty(defaultValue = "true") @NetworkProperty boolean allowCreate2,
        @ConfigProperty(defaultValue = "0") @NetworkProperty long maxNumWithHapiSigsAccess,
        @ConfigProperty(value = "nonces.externalization.enabled", defaultValue = "true") @NetworkProperty
                boolean noncesExternalizationEnabled,
        @ConfigProperty(defaultValue = "false") @NetworkProperty boolean enforceCreationThrottle,
        @ConfigProperty(defaultValue = "15000000") @NetworkProperty long maxGasPerSec,
        @ConfigProperty(value = "maxKvPairs.aggregate", defaultValue = "500000000") @NetworkProperty
                long maxKvPairsAggregate,
        @ConfigProperty(value = "maxKvPairs.individual", defaultValue = "16384000") @NetworkProperty
                int maxKvPairsIndividual,
        @ConfigProperty(defaultValue = "5000000") @NetworkProperty long maxNumber,
        // CHAINID returns 295 (0x0127) for mainnet, 296 (0x0128) for testnet, and 297 (0x0129) for previewnet.
        // c.f. https://hips.hedera.com/hip/hip-26 for reference
        @ConfigProperty(defaultValue = "295") @NetworkProperty int chainId,
        @ConfigProperty(defaultValue = "CONTRACT_STATE_CHANGE,CONTRACT_BYTECODE,CONTRACT_ACTION") @NetworkProperty
                Set<SidecarType> sidecars,
        @ConfigProperty(defaultValue = "false") @NetworkProperty boolean sidecarValidationEnabled,
        @ConfigProperty(value = "throttle.throttleByGas", defaultValue = "true") @NetworkProperty
                boolean throttleThrottleByGas,
        @ConfigProperty(defaultValue = "20") @NetworkProperty int maxRefundPercentOfGasLimit,
        @ConfigProperty(defaultValue = "5000000") @NetworkProperty long scheduleThrottleMaxGasLimit,
        @ConfigProperty(defaultValue = "true") @NetworkProperty boolean redirectTokenCalls,
        @ConfigProperty(value = "precompile.exchangeRateGasCost", defaultValue = "100") @NetworkProperty
                long precompileExchangeRateGasCost,
        @ConfigProperty(value = "precompile.htsDefaultGasCost", defaultValue = "10000") @NetworkProperty
                long precompileHtsDefaultGasCost,

        // Default value of `sigVerificationCost` from fee schedule's CryptoTransfer servicedata vpt field
        // FUTURE: Fees for system contracts need to be in the fee schedule
        @ConfigProperty(value = "precompile.sigVerificationCost", defaultValue = "605466012") @NetworkProperty
                long sigVerificationCostInFeeScheduleUnits,
        @ConfigProperty(value = "precompile.exportRecordResults", defaultValue = "true") @NetworkProperty
                boolean precompileExportRecordResults,
        @ConfigProperty(value = "precompile.htsEnableTokenCreate", defaultValue = "true") @NetworkProperty
                boolean precompileHtsEnableTokenCreate,
        // @ConfigProperty(value = "precompile.unsupportedCustomFeeReceiverDebits", defaultValue = "")
        // Set<CustomFeeType> precompileUnsupportedCustomFeeReceiverDebits,
        @ConfigProperty(value = "precompile.atomicCryptoTransfer.enabled", defaultValue = "true") @NetworkProperty
                boolean precompileAtomicCryptoTransferEnabled,
        @ConfigProperty(value = "precompile.hrcFacade.associate.enabled", defaultValue = "true") @NetworkProperty
                boolean precompileHrcFacadeAssociateEnabled,
        @ConfigProperty(value = "systemContract.accountService.enabled", defaultValue = "true") @NetworkProperty
                boolean systemContractAccountServiceEnabled,
        @ConfigProperty(value = "systemContract.accountService.isAuthorizedRawEnabled", defaultValue = "true")
                @NetworkProperty
                boolean systemContractAccountServiceIsAuthorizedRawEnabled,
        @ConfigProperty(value = "systemContract.updateCustomFees.enabled", defaultValue = "true") @NetworkProperty
                boolean systemContractUpdateCustomFeesEnabled,
        @ConfigProperty(value = "systemContract.tokenInfo.v2.enabled", defaultValue = "false") @NetworkProperty
                boolean systemContractTokenInfoV2Enabled,
        @ConfigProperty(value = "systemContract.precisionLossFixForGas.enabled", defaultValue = "true") @NetworkProperty
                boolean isGasPrecisionLossFixEnabled,
        @ConfigProperty(value = "systemContract.canonicalViewGas.enabled", defaultValue = "true") @NetworkProperty
                boolean isCanonicalViewGasEnabled,
        @ConfigProperty(value = "systemContract.updateNFTsMetadata.enabled", defaultValue = "false") @NetworkProperty
                boolean systemContractUpdateNFTsMetadataEnabled,
        @ConfigProperty(value = "evm.version.dynamic", defaultValue = "false") @NetworkProperty
                boolean evmVersionDynamic,
        @ConfigProperty(value = "evm.allowCallsToNonContractAccounts", defaultValue = "true") @NetworkProperty
                boolean evmAllowCallsToNonContractAccounts,
        @ConfigProperty(value = "evm.chargeGasOnEvmHandleException", defaultValue = "true") @NetworkProperty
                boolean chargeGasOnEvmHandleException,
        @ConfigProperty(value = "evm.nonExtantContractsFail", defaultValue = "0") @NetworkProperty
                Set<Long> evmNonExtantContractsFail,
        @ConfigProperty(value = "evm.version", defaultValue = "v0.51") @NetworkProperty String evmVersion) {}
