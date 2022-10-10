/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.domain.security;

import static com.hedera.services.context.domain.security.PermissionFileUtils.legacyKeys;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.config.AccountNumbers;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import java.util.EnumMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class HapiOpPermissions {
    private static final Logger log = LogManager.getLogger(HapiOpPermissions.class);

    static final String MISSING_OP_TPL =
            "Ignoring key '%s', which does not correspond to a known Hedera operation!";
    static final String UNPARSEABLE_RANGE_TPL =
            "Ignoring entry for supported op %s---cannot interpret range '%s'!";

    private final AccountNumbers accountNums;

    @Inject
    public HapiOpPermissions(AccountNumbers accountNums) {
        this.accountNums = accountNums;
    }

    private EnumMap<HederaFunctionality, PermissionedAccountsRange> permissions =
            new EnumMap<>(HederaFunctionality.class);

    public void reloadFrom(ServicesConfigurationList config) {
        EnumMap<HederaFunctionality, PermissionedAccountsRange> newPerms =
                new EnumMap<>(HederaFunctionality.class);
        for (var permission : config.getNameValueList()) {
            var opName = permission.getName();
            if (legacyKeys.containsKey(opName)) {
                var op = legacyKeys.get(opName);
                var range = PermissionedAccountsRange.from(permission.getValue());
                if (range == null) {
                    log.warn(String.format(UNPARSEABLE_RANGE_TPL, op, permission.getValue()));
                } else {
                    newPerms.put(op, range);
                }
            } else {
                log.warn(String.format(MISSING_OP_TPL, opName));
            }
        }
        permissions = newPerms;
    }

    public ResponseCodeEnum permissibilityOf(HederaFunctionality function, AccountID givenPayer) {
        var num = givenPayer.getAccountNum();
        if (accountNums.isSuperuser(num)) {
            return OK;
        }

        PermissionedAccountsRange range;
        return (range = permissions.get(function)) != null && range.contains(num)
                ? OK
                : NOT_SUPPORTED;
    }

    EnumMap<HederaFunctionality, PermissionedAccountsRange> getPermissions() {
        return permissions;
    }
}
