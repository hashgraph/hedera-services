/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.swirlds.component.framework.component;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a name that should be used as an override for the task scheduler's name instead of a component's interface
 * name. Can also be used to annotate a method parameter used to implement a transformer/filter (these get turned into
 * direct schedulers, which need to be named).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface SchedulerLabel {

    /**
     * The label of the task scheduler that will operate the transformer/filter.
     *
     * @return the label of the task scheduler that will operate the transformer/filter
     */
    @NonNull
    String value();
}
