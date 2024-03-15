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

package com.hedera.node.blocknode.core.downloader.block;

import com.hedera.node.blocknode.config.ConfigProvider;
import com.hedera.node.blocknode.config.data.BlockNodeConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockDownloader {
    private static final Logger logger = LogManager.getLogger(BlockDownloader.class);
    private final int duration;

    public BlockDownloader() {
        final var configProvider = new ConfigProvider();
        final var blockNodeConfig = configProvider.configuration.getConfigData(BlockNodeConfig.class);

        duration = blockNodeConfig.duration();
    }

    public void download() {
        logger.info("here");
    }
}
