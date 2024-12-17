/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.config.converter;

import com.hedera.node.config.types.EntityScaleFactors;
import com.swirlds.config.api.converter.ConfigConverter;

/**
 * Config api {@link ConfigConverter} implementation for the type {@link EntityScaleFactors}.
 */
public class EntityScaleFactorsConverter implements ConfigConverter<EntityScaleFactors> {

    @Override
    public EntityScaleFactors convert(final String value) throws IllegalArgumentException, NullPointerException {
        if (value == null) {
            throw new NullPointerException("null can not be converted");
        }
        return EntityScaleFactors.from(value);
    }
}
