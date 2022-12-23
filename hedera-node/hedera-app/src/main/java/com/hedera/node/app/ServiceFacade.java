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
package com.hedera.node.app;

import com.hedera.node.app.service.admin.FreezeService;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.network.NetworkService;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.util.UtilService;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.ServiceFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

public class ServiceFacade {

    private ServiceFacade() {}

    @NonNull
    public static Set<Service> getAll() {
        return ServiceFactory.loadServices();
    }

    @NonNull
    public static FreezeService getFreezeService() {
        return FreezeService.getInstance();
    }

    @NonNull
    public static ConsensusService getConsensusService() {
        return ConsensusService.getInstance();
    }

    @NonNull
    public static FileService getFileService() {
        return FileService.getInstance();
    }

    @NonNull
    public static NetworkService getNetworkService() {
        return NetworkService.getInstance();
    }

    @NonNull
    public static ScheduleService getScheduleService() {
        return ScheduleService.getInstance();
    }

    @NonNull
    public static ContractService getContractService() {
        return ContractService.getInstance();
    }

    @NonNull
    public static CryptoService getCryptoService() {
        return CryptoService.getInstance();
    }

    @NonNull
    public static TokenService getTokenService() {
        return TokenService.getInstance();
    }

    @NonNull
    public static UtilService getUtilService() {
        return UtilService.getInstance();
    }
}
