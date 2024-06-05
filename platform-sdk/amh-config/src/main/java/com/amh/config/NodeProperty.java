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

package com.amh.config;

import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.swirlds.config.api.ConfigProperty;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotates a specific configuration property (annotated with {@link ConfigProperty}) as being a property specific
 * to the node itself. Such properties can have (and almost certainly do have) different values for each node in the
 * network. This annotation is mutually exclusive with {@link NetworkProperty}.
 */
@Retention(RUNTIME)
@Target(RECORD_COMPONENT)
public @interface NodeProperty {}
