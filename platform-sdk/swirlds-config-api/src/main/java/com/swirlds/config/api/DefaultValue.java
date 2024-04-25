/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.config.api;

import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation that can be used to annotate properties for a config data object. This annotation is syntactic sugar for
 * when {@link ConfigProperty} is used to set the default value without overriding the name of the property.
 */
@Documented
@Retention(RUNTIME)
@Target(RECORD_COMPONENT)
public @interface DefaultValue {

    String NULL_DEFAULT_VALUE = "com.swirlds.config.value.nullValue#1f9@-32jk-klPQ-#m4r-!p8t";

    /**
     * This value is used to define default value of the property.
     *
     * @return the default value
     */
    @Nullable
    String value() default NULL_DEFAULT_VALUE;
}
