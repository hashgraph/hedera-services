/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.files;

import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Map.Entry;
import java.util.OptionalInt;

public interface FileUpdateInterceptor {
    /**
     * Returns <i>either</i> the priority order this interceptor has relative to the given file
     * (Integer.MIN_VALUE is first, Integer.MAX_VALUE is last); <i>or</i> an empty optional if this
     * interceptor is not applicable.
     *
     * @param id the file to be updated
     * @return this interceptor's priority, if applicable
     */
    OptionalInt priorityForCandidate(FileID id);

    /**
     * Returns an {@link Entry} mapping this interceptor's assessment of the candidate update to a
     * flag indicating whether the update should occur.
     *
     * @param id the file to be updated
     * @param newContents the proposed new contents of the file
     * @return this interceptor's assessment of and gate on the candidate update
     */
    Entry<ResponseCodeEnum, Boolean> preUpdate(FileID id, byte[] newContents);

    /**
     * Performs any post-processing for the given completed update.
     *
     * @param id the file that was updated
     * @param contents the new contents of the file
     */
    void postUpdate(FileID id, byte[] contents);

    /**
     * Returns an {@link Entry} mapping this interceptor's assessment of the candidate delete to a
     * flag indicating whether the delete should occur.
     *
     * @param id the file to be deleted
     * @return this interceptor's assessment of and gate on the candidate delete
     */
    Entry<ResponseCodeEnum, Boolean> preDelete(FileID id);

    /**
     * Returns an {@link Entry} mapping this interceptor's assessment of the candidate attribute
     * change to a flag indicating whether the change should occur.
     *
     * @param id the file whose attributes are to be changed
     * @param newAttr the proposed new attributes of the file
     * @return this interceptor's assessment of and gate on the candidate change
     */
    Entry<ResponseCodeEnum, Boolean> preAttrChange(FileID id, HFileMeta newAttr);
}
