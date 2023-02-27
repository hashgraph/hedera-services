/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.config.impl.validators.annotation;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.validation.ConfigViolation;
import com.swirlds.config.api.validation.annotation.ConstraintMethod;
import com.swirlds.config.impl.validators.DefaultConfigViolation;

@ConfigData("method")
public record ConstraintMethodConfigData(
        @ConstraintMethod("checkA") boolean valueA, @ConstraintMethod("checkB") boolean valueB) {

    public ConfigViolation checkA(final Configuration configuration) {
        if (valueA) {
            return null;
        }
        return new DefaultConfigViolation("method.checkA", valueA + "", true, "error");
    }

    public ConfigViolation checkB(final Configuration configuration) {
        if (valueB) {
            return null;
        }
        throw new RuntimeException("Error in validation");
    }
}
