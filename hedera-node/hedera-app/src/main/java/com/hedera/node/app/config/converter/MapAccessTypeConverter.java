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

package com.hedera.node.app.config.converter;

import com.hedera.node.app.service.mono.throttling.MapAccessType;
import com.hedera.node.app.spi.config.converter.AbstractEnumConfigConverter;
import com.swirlds.config.api.converter.ConfigConverter;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Config api {@link ConfigConverter} implementation for the type {@link MapAccessType}. Based on
 * https://github.com/hashgraph/hedera-services/issues/6106 we need to add {@code implements ConfigConverter} to the
 * class for now.
 */
public class MapAccessTypeConverter extends AbstractEnumConfigConverter<MapAccessType>
        implements ConfigConverter<MapAccessType> {
    @NonNull
    @Override
    protected Class<MapAccessType> getEnumType() {
        return MapAccessType.class;
    }
}
