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
package com.hedera.node.app.state;

import com.hedera.node.app.spi.state.States;

// Placeholder until the state-implementation is added
public interface HederaState {

    String CONSENSUS_SERVICE = "ConsensusService";
    String CONTRACT_SERVICE = "ContractService";
    String CRYPTO_SERVICE = "CryptoService";
    String FILE_SERVICE = "FileService";
    String FREEZE_SERVICE = "FreezeService";
    String NETWORK_SERVICE = "NetworkService";
    String SCHEDULE_SERVICE = "ScheduleService";
    String TOKEN_SERVICE = "TokenService";
    String UTIL_SERVICE = "UtilService";

    States createReadableStates(String serviceName);
}
