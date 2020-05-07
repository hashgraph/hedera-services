package com.hedera.services.legacy.netty;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.legacy.service.GlobalFlag;
import com.swirlds.common.PlatformStatus;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * @author SK
 * @Date 2019-03-14
 * This interceptor will be first in chain for crypto service, if platform is down then it will send GRPC.UNAVAILABLE
 * Assuming we will need to have separate interceptors for different service.
 *
 */
public class CryptoServiceInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
       if(GlobalFlag.getInstance().getPlatformStatus() != PlatformStatus.ACTIVE) {
           call.close(Status.UNAVAILABLE, headers);
           return new ServerCall.Listener<ReqT>() {};
       }

        return next.startCall(call, headers);

    }

}
