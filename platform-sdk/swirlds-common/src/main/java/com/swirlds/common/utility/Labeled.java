/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.utility;

/**
 * An object with a label.
 */
@FunctionalInterface
public interface Labeled {

    /**
     * The maximum permitted character length of a label.
     */
    int MAX_LABEL_LENGTH = 1024;

    /**
     * Get the label associated with this object.
     *
     * @return this object's label
     */
    String getLabel();
}
