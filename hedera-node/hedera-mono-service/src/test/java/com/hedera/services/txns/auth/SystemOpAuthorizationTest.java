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
package com.hedera.services.txns.auth;

import static com.hedera.services.txns.auth.SystemOpAuthorization.AUTHORIZED;
import static com.hedera.services.txns.auth.SystemOpAuthorization.IMPERMISSIBLE;
import static com.hedera.services.txns.auth.SystemOpAuthorization.UNAUTHORIZED;
import static com.hedera.services.txns.auth.SystemOpAuthorization.UNNECESSARY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SystemOpAuthorizationTest {
    @Test
    void haveExpectedStatusRepresentations() {
        // expect:
        assertEquals(OK, UNNECESSARY.asStatus());
        assertEquals(OK, AUTHORIZED.asStatus());
        assertEquals(ENTITY_NOT_ALLOWED_TO_DELETE, IMPERMISSIBLE.asStatus());
        assertEquals(AUTHORIZATION_FAILED, UNAUTHORIZED.asStatus());
    }
}
