/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.api.implementation;

import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.StateRegistry;

/**
 * A pair of a {@link Service} and the {@link StateRegistry} it uses to handle state.
 *
 * @param service the {@code Service}
 * @param stateRegistry the {@code StateRegistry}
 * @param <S> the concrete type of the {@code ServiceContext}
 */
public record ServicesContext<S extends Service>(S service, StateRegistry stateRegistry) {}
