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

package com.hedera.node.app.services;

import com.hedera.node.app.spi.Service;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import javax.inject.Singleton;

/**
 * A registry providing access to all services registered with the application.
 */
@Singleton
public interface ServicesRegistry {
    /**
     * Gets the full set of services registered.
     *
     * @return The set of services. May be empty.
     */
    @NonNull
    Set<Service> services();
}
