/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.blocks.cloud.uploader;

import com.hedera.node.config.types.BucketProvider;
import java.nio.file.Path;
import java.util.concurrent.Future;

public interface CloudBucketUploader {
    void uploadBlock(Path blockPath);

    boolean blockExists(String objectKey);

    String getBlockMd5(String objectKey);

    BucketProvider getProvider();
}
