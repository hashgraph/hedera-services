/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.admin.FreezeService;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.network.NetworkService;
import com.hedera.node.app.service.scheduled.ScheduleService;
import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.util.UtilService;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@code ServiceAccessor} is used to pass all services to components via a single parameter.
 *
 * @param consensusService a {@link ConsensusService}
 * @param contractService a {@link ContractService}
 * @param cryptoService a {@link CryptoService}
 * @param fileService a {@link FileService}
 * @param freezeService a {@link FreezeService}
 * @param networkService a {@link NetworkService}
 * @param scheduleService a {@link ScheduleService}
 * @param tokenService a {@link TokenService}
 * @param utilService a {@link UtilService}
 */
public record ServicesAccessor(
        @NonNull ConsensusService consensusService,
        @NonNull ContractService contractService,
        @NonNull CryptoService cryptoService,
        @NonNull FileService fileService,
        @NonNull FreezeService freezeService,
        @NonNull NetworkService networkService,
        @NonNull ScheduleService scheduleService,
        @NonNull TokenService tokenService,
        @NonNull UtilService utilService) {

    public ServicesAccessor(
            @NonNull final ConsensusService consensusService,
            @NonNull final ContractService contractService,
            @NonNull final CryptoService cryptoService,
            @NonNull final FileService fileService,
            @NonNull final FreezeService freezeService,
            @NonNull final NetworkService networkService,
            @NonNull final ScheduleService scheduleService,
            @NonNull final TokenService tokenService,
            @NonNull final UtilService utilService) {
        this.consensusService = requireNonNull(consensusService);
        this.contractService = requireNonNull(contractService);
        this.cryptoService = requireNonNull(cryptoService);
        this.fileService = requireNonNull(fileService);
        this.freezeService = requireNonNull(freezeService);
        this.networkService = requireNonNull(networkService);
        this.scheduleService = requireNonNull(scheduleService);
        this.tokenService = requireNonNull(tokenService);
        this.utilService = requireNonNull(utilService);
    }
}
