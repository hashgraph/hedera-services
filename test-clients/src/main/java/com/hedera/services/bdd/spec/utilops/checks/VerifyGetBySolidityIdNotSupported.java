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
package com.hedera.services.bdd.spec.utilops.checks;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hederahashgraph.api.proto.java.GetBySolidityIDQuery;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import org.junit.jupiter.api.Assertions;

public class VerifyGetBySolidityIdNotSupported extends UtilOp {
    @Override
    protected boolean submitOp(HapiSpec spec) {
        GetBySolidityIDQuery.Builder op = GetBySolidityIDQuery.newBuilder();
        Query query = Query.newBuilder().setGetBySolidityID(op).build();
        Response response =
                spec.clients().getScSvcStub(targetNodeFor(spec), useTls).getBySolidityID(query);
        Assertions.assertEquals(
                NOT_SUPPORTED,
                response.getGetBySolidityID().getHeader().getNodeTransactionPrecheckCode());
        return false;
    }
}
