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
package com.hedera.services.queries.file;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FileAnswers {
    private final GetFileInfoAnswer getFileInfo;
    private final GetFileContentsAnswer getFileContents;

    @Inject
    public FileAnswers(GetFileInfoAnswer getFileInfo, GetFileContentsAnswer getFileContents) {
        this.getFileInfo = getFileInfo;
        this.getFileContents = getFileContents;
    }

    public GetFileInfoAnswer fileInfo() {
        return getFileInfo;
    }

    public GetFileContentsAnswer fileContents() {
        return getFileContents;
    }
}
