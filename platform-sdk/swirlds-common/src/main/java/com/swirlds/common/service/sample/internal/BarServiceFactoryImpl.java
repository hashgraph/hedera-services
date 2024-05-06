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

package com.swirlds.common.service.sample.internal;

import com.google.auto.service.AutoService;
import com.swirlds.common.service.api.AbstractServiceFactory;
import com.swirlds.common.service.api.EmptyContext;
import com.swirlds.common.service.sample.api.BarService;
import com.swirlds.common.service.sample.api.BarServiceFactory;
import edu.umd.cs.findbugs.annotations.NonNull;

@AutoService(BarServiceFactory.class)
public class BarServiceFactoryImpl extends AbstractServiceFactory<EmptyContext, BarService>
        implements BarServiceFactory {

    public BarServiceFactoryImpl() {
        super(BarService.class, EmptyContext.class);
    }

    @Override
    public BarService createService(@NonNull EmptyContext serviceContext) {
        return new BarService() {
            @Override
            public String getUser() {
                return "bar";
            }
        };
    }
}
