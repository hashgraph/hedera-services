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

package com.hedera.services.bdd.spec.infrastructure;

import com.hedera.services.bdd.spec.props.NodeConnectInfo;
import com.hederahashgraph.service.proto.java.*;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.grpc.ManagedChannel;
import java.util.Objects;

public record ChannelStubs(
        NodeConnectInfo nodeConnectInfo,
        boolean useTls,
        @NonNull ManagedChannel channel,
        @NonNull ConsensusServiceGrpc.ConsensusServiceBlockingStub consSvcStubs,
        @NonNull FileServiceGrpc.FileServiceBlockingStub fileSvcStubs,
        @NonNull TokenServiceGrpc.TokenServiceBlockingStub tokenSvcStubs,
        @NonNull CryptoServiceGrpc.CryptoServiceBlockingStub cryptoSvcStubs,
        @NonNull FreezeServiceGrpc.FreezeServiceBlockingStub freezeSvcStubs,
        @NonNull NetworkServiceGrpc.NetworkServiceBlockingStub networkSvcStubs,
        @NonNull ScheduleServiceGrpc.ScheduleServiceBlockingStub scheduleSvcStubs,
        @NonNull SmartContractServiceGrpc.SmartContractServiceBlockingStub scSvcStubs,
        @NonNull AddressBookServiceGrpc.AddressBookServiceBlockingStub addressBookSvcStubs,
        @NonNull UtilServiceGrpc.UtilServiceBlockingStub utilSvcStubs) {

    public ChannelStubs {
        Objects.requireNonNull(nodeConnectInfo);
        Objects.requireNonNull(channel);
        Objects.requireNonNull(consSvcStubs);
        Objects.requireNonNull(fileSvcStubs);
        Objects.requireNonNull(tokenSvcStubs);
        Objects.requireNonNull(cryptoSvcStubs);
        Objects.requireNonNull(freezeSvcStubs);
        Objects.requireNonNull(networkSvcStubs);
        Objects.requireNonNull(scheduleSvcStubs);
        Objects.requireNonNull(scSvcStubs);
        Objects.requireNonNull(utilSvcStubs);
        Objects.requireNonNull(addressBookSvcStubs);
    }

    public void shutdown() {
        channel.shutdown();
    }

    public static ChannelStubs from(
            @NonNull final ManagedChannel channel,
            @NonNull final NodeConnectInfo nodeConnectInfo,
            final boolean useTls) {
        return new ChannelStubs(
                nodeConnectInfo,
                useTls,
                channel,
                ConsensusServiceGrpc.newBlockingStub(channel),
                FileServiceGrpc.newBlockingStub(channel),
                TokenServiceGrpc.newBlockingStub(channel),
                CryptoServiceGrpc.newBlockingStub(channel),
                FreezeServiceGrpc.newBlockingStub(channel),
                NetworkServiceGrpc.newBlockingStub(channel),
                ScheduleServiceGrpc.newBlockingStub(channel),
                SmartContractServiceGrpc.newBlockingStub(channel),
                AddressBookServiceGrpc.newBlockingStub(channel),
                UtilServiceGrpc.newBlockingStub(channel));
    }
}
