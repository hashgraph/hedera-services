/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.service.mono.context.domain.security.HapiOpPermissions;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static java.util.Objects.requireNonNull;

/**
 * An implementation of {@link Authorizer} based on the existing mono-service {@link HapiOpPermissions} facility.
 */
@Singleton
public class AuthorizerImpl implements Authorizer {
    private final HapiOpPermissions hapiOpPermissions;

    @Inject
    public AuthorizerImpl(@NonNull final HapiOpPermissions hapiOpPermissions) {
        this.hapiOpPermissions = requireNonNull(hapiOpPermissions);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAuthorized(@NonNull final AccountID id, @NonNull final HederaFunctionality function) {
        final var permissionStatus = hapiOpPermissions.permissibilityOf2(function, id);
        return permissionStatus == OK;
    }
}
