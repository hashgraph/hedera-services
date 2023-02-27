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

package com.swirlds.common.merkle.synchronization.internal;

/**
 * Describes different types of lessons.
 */
public final class LessonType {

    private LessonType() {}

    /**
     * This lesson informs the learner that the node possessed by the learner has the correct data already.
     */
    public static final byte NODE_IS_UP_TO_DATE = 0;

    /**
     * This lesson contains all data required to reconstruct a leaf node node.
     * Corresponds to {@link LeafDataLesson}.
     */
    public static final byte LEAF_NODE_DATA = 1;

    /**
     * This lesson contains all data required to reconstruct an internal node.
     * Corresponds to {@link InternalDataLesson}.
     */
    public static final byte INTERNAL_NODE_DATA = 2;

    /**
     * This lesson describes the root of a subtree that needs a custom root to do reconnect.
     * Corresponds to {@link CustomViewRootLesson}.
     */
    public static final byte CUSTOM_VIEW_ROOT = 3;
}
