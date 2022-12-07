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
package com.hedera.node.app.hapi.utils.sysfiles.validation;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Optional;

public final class ErrorCodeUtils {
    private static final String EXC_MSG_TPL = "%s :: %s";

    private ErrorCodeUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static String exceptionMsgFor(final ResponseCodeEnum error, final String details) {
        return String.format(EXC_MSG_TPL, error, details);
    }

    public static Optional<ResponseCodeEnum> errorFrom(final String exceptionMsg) {
        final var parts = exceptionMsg.split(" :: ");
        if (parts.length != 2) {
            return Optional.empty();
        }
        return Optional.of(ResponseCodeEnum.valueOf(parts[0]));
    }
}
