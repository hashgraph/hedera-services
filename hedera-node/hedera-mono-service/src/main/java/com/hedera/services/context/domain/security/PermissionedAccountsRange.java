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
package com.hedera.services.context.domain.security;

import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public class PermissionedAccountsRange {
    private static final Pattern DEGENERATE = Pattern.compile("\\d+");
    private static final Pattern NON_DEGENERATE = Pattern.compile("(\\d+)-(\\d+)");
    private static final Pattern NON_DEGENERATE_WILDCARD = Pattern.compile("(\\d+)-[*]");

    final Long from;
    final Long inclusiveTo;

    public static PermissionedAccountsRange from(String description) {
        if (StringUtils.isEmpty(description)) {
            return null;
        }

        var degenMatch = DEGENERATE.matcher(description);
        if (degenMatch.matches()) {
            return new PermissionedAccountsRange(Long.valueOf(description));
        }

        var nonDegenMatch = NON_DEGENERATE.matcher(description);
        if (nonDegenMatch.matches()) {
            var supposedFrom = Long.valueOf(nonDegenMatch.group(1));
            var supposedTo = Long.valueOf(nonDegenMatch.group(2));
            if (supposedFrom < supposedTo) {
                return new PermissionedAccountsRange(supposedFrom, supposedTo);
            } else if (supposedFrom.equals(supposedTo)) {
                return new PermissionedAccountsRange(supposedFrom);
            }
        }

        var nonDegenWildMatch = NON_DEGENERATE_WILDCARD.matcher(description);
        if (nonDegenWildMatch.matches()) {
            return new PermissionedAccountsRange(
                    Long.valueOf(nonDegenWildMatch.group(1)), Long.MAX_VALUE);
        }

        return null;
    }

    public PermissionedAccountsRange(Long from, Long inclusiveTo) {
        this.from = from;
        this.inclusiveTo = inclusiveTo;
    }

    public PermissionedAccountsRange(Long from) {
        this.from = from;
        this.inclusiveTo = null;
    }

    public Long from() {
        return from;
    }

    public Long inclusiveTo() {
        return inclusiveTo;
    }

    public boolean contains(long num) {
        if (inclusiveTo == null) {
            return num == from;
        } else {
            return from <= num && num <= inclusiveTo;
        }
    }
}
