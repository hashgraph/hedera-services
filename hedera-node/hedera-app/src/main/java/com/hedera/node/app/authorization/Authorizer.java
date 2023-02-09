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
package com.hedera.node.app.authorization;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.mono.context.domain.security.HapiOpPermissions;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Authorizer {

    private final HapiOpPermissions hapiOpPermissions;

    @Inject
    public Authorizer(@NonNull final HapiOpPermissions hapiOpPermissions) {
        this.hapiOpPermissions = requireNonNull(hapiOpPermissions);
    }

    public boolean isAuthorized(
            @NonNull final AccountID id, @NonNull final HederaFunctionality function) {
        final var permissionStatus = hapiOpPermissions.permissibilityOf(function, id);
        return permissionStatus == OK;
    }
}
