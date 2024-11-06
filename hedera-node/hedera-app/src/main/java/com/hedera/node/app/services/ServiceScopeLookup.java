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

package com.hedera.node.app.services;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.addressbook.AddressBookService;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.networkadmin.FreezeService;
import com.hedera.node.app.service.networkadmin.NetworkService;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.util.UtilService;
import com.hedera.node.app.tss.TssBaseService;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides a mapping from a transaction to the service it belongs to.
 *
 * <p>The initial implementation contains all known mappings hard-coded. In a future version, this will be replaced by a
 * dynamic approach.
 */
@Singleton
public class ServiceScopeLookup {

    private static final String NON_EXISTING_SERVICE = "";

    @Inject
    public ServiceScopeLookup() {
        // dagger
    }

    /**
     * Returns the name of the service a given transaction belongs to. A transaction can only modify state of the
     * service it belongs to.
     *
     * @param txBody the transaction for which the service name should be returned
     * @return the name of the service that the transaction belongs to or an empty string if no service was found
     * @throws NullPointerException if the {@code txBody} is {@code null}
     */
    @NonNull
    public String getServiceName(@NonNull final TransactionBody txBody) {
        return switch (txBody.data().kind()) {
            case CONSENSUS_CREATE_TOPIC,
                    CONSENSUS_UPDATE_TOPIC,
                    CONSENSUS_DELETE_TOPIC,
                    CONSENSUS_SUBMIT_MESSAGE -> ConsensusService.NAME;

            case CONTRACT_CREATE_INSTANCE,
                    CONTRACT_UPDATE_INSTANCE,
                    CONTRACT_CALL,
                    CONTRACT_DELETE_INSTANCE,
                    ETHEREUM_TRANSACTION -> ContractService.NAME;

            case CRYPTO_CREATE_ACCOUNT,
                    CRYPTO_UPDATE_ACCOUNT,
                    CRYPTO_TRANSFER,
                    CRYPTO_DELETE,
                    CRYPTO_APPROVE_ALLOWANCE,
                    CRYPTO_DELETE_ALLOWANCE,
                    CRYPTO_ADD_LIVE_HASH,
                    CRYPTO_DELETE_LIVE_HASH -> TokenService.NAME;

            case FILE_CREATE, FILE_UPDATE, FILE_DELETE, FILE_APPEND -> FileService.NAME;

            case FREEZE -> FreezeService.NAME;

            case UNCHECKED_SUBMIT -> NetworkService.NAME;

            case SCHEDULE_CREATE, SCHEDULE_SIGN, SCHEDULE_DELETE -> ScheduleService.NAME;

            case TOKEN_CREATION,
                    TOKEN_UPDATE,
                    TOKEN_MINT,
                    TOKEN_BURN,
                    TOKEN_DELETION,
                    TOKEN_WIPE,
                    TOKEN_FREEZE,
                    TOKEN_UNFREEZE,
                    TOKEN_GRANT_KYC,
                    TOKEN_REVOKE_KYC,
                    TOKEN_ASSOCIATE,
                    TOKEN_DISSOCIATE,
                    TOKEN_FEE_SCHEDULE_UPDATE,
                    TOKEN_PAUSE,
                    TOKEN_UNPAUSE,
                    TOKEN_UPDATE_NFTS,
                    TOKEN_AIRDROP,
                    TOKEN_CLAIM_AIRDROP,
                    TOKEN_CANCEL_AIRDROP,
                    TOKEN_REJECT -> TokenService.NAME;

            case UTIL_PRNG -> UtilService.NAME;

            case SYSTEM_DELETE -> switch (txBody.systemDeleteOrThrow().id().kind()) {
                case CONTRACT_ID -> ContractService.NAME;
                case FILE_ID -> FileService.NAME;
                default -> NON_EXISTING_SERVICE;
            };
            case SYSTEM_UNDELETE -> switch (txBody.systemUndeleteOrThrow().id().kind()) {
                case CONTRACT_ID -> ContractService.NAME;
                case FILE_ID -> FileService.NAME;
                default -> NON_EXISTING_SERVICE;
            };

            case NODE_CREATE, NODE_DELETE, NODE_UPDATE -> AddressBookService.NAME;
            case TSS_MESSAGE, TSS_VOTE -> TssBaseService.NAME;

            default -> NON_EXISTING_SERVICE;
        };
    }
}
