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

package com.swirlds.common.threading.framework;

/**
 * Encapsulates an operation that is normally executed onto its own thread
 * so that it can be injected into a pre-existing thread.
 */
@FunctionalInterface
public interface ThreadSeed {

    /**
     * Inject this seed onto a thread. The seed will take over the thread and may
     * change thread settings. When the seed is finished with all of its work,
     * it will restore the original thread configuration and yield control back
     * to the caller. Until it yields control, this method will block.
     */
    void inject();
}
