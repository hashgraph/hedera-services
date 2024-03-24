/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.statedumpers.files;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum SystemFileType {
    ADDRESS_BOOK(101),
    NODE_DETAILS(102),
    FEE_SCHEDULES(111),
    EXCHANGE_RATES(112),
    NETWORK_PROPERTIES(121),
    HAPI_PERMISSIONS(122),
    THROTTLE_DEFINITIONS(123),
    SOFTWARE_UPDATE0(150),
    SOFTWARE_UPDATE1(151),
    SOFTWARE_UPDATE2(152),
    SOFTWARE_UPDATE3(153),
    SOFTWARE_UPDATE4(154),
    SOFTWARE_UPDATE5(155),
    SOFTWARE_UPDATE6(156),
    SOFTWARE_UPDATE7(157),
    SOFTWARE_UPDATE8(158),
    SOFTWARE_UPDATE9(159),
    UNKNOWN(-1);

    public final int id;

    public static final Map<Integer, SystemFileType> byId = new HashMap<>();

    SystemFileType(final int id) {
        this.id = id;
    }

    static {
        EnumSet.allOf(SystemFileType.class).forEach(e -> byId.put(e.id, e));
    }
}
