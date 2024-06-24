/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.config.impl.converters;

import com.swirlds.config.api.converter.ConfigConverter;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Concrete {@link ConfigConverter} implementation that provides the support for {@link URL} values in the
 * configuration.
 */
public final class UrlConverter implements ConfigConverter<URL> {

    /**
     * {@inheritDoc}
     */
    @Override
    public URL convert(final String value) throws IllegalArgumentException {
        if (value == null) {
            throw new NullPointerException("null can not be converted");
        }
        try {
            return new URL(value);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Value '" + value + "' is not a valid URL", e);
        }
    }
}
