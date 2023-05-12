/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.notification;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows per {@link Listener} configuration of {@link DispatchMode} and {@link DispatchOrder}. Default configuration is
 * {@link DispatchMode#SYNC} and {@link DispatchOrder#UNORDERED}.
 */
@Inherited
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DispatchModel {
    /**
     * Specifies the {@link DispatchMode} to be used for all {@link Notification} implementations supported by this
     * listener.
     *
     * The default is {@link DispatchMode#SYNC}.
     *
     * @return the configured {@link DispatchMode}
     */
    DispatchMode mode() default DispatchMode.SYNC;

    /**
     * Specifies the {@link DispatchOrder} to be used for all {@link Notification} implementations supported by this
     * listener.
     *
     * The default is {@link DispatchOrder#UNORDERED}.
     *
     * @return the configured {@link DispatchOrder}
     */
    DispatchOrder order() default DispatchOrder.UNORDERED;
}
