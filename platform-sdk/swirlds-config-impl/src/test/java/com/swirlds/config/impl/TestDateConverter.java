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

package com.swirlds.config.impl;

import com.swirlds.config.api.converter.ConfigConverter;
import java.util.Date;

/**
 * Custom converter that is used for tests
 */
public class TestDateConverter implements ConfigConverter<Date> {

    @Override
    public Date convert(final String value) throws IllegalArgumentException, NullPointerException {
        return new Date(Long.parseLong(value));
    }
}
