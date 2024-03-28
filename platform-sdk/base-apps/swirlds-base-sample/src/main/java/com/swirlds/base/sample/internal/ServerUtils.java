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

package com.swirlds.base.sample.internal;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.spi.HttpServerProvider;
import com.swirlds.base.sample.config.BaseApiConfig;
import com.swirlds.base.sample.service.InventoryService;
import com.swirlds.base.sample.service.ItemService;
import com.swirlds.base.sample.service.OperationService;
import com.swirlds.base.sample.service.PurchaseService;
import com.swirlds.base.sample.service.SaleService;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Http Server manager utils
 */
public class ServerUtils {
    private static final Logger logger = LogManager.getLogger(ServerUtils.class);

    private ServerUtils() {}

    /**
     * Creates and starts a Http server with a set of defined handlers
     */
    public static void createServer(final @NonNull BaseApiConfig config, final @NonNull Context swirldsContext)
            throws IOException {
        // Create HTTP server instance
        final HttpServerProvider provider = HttpServerProvider.provider();
        final HttpServer server = provider.createHttpServer(new InetSocketAddress(config.host(), config.port()), 0);

        final List<AdapterHandler<?>> handlers = createHandlers(config, swirldsContext, server);

        // Start the server
        server.start();

        if (config.banner()) {
            logger.info("\n              _      _     _           _                    \n"
                    + " _____      _(_)_ __| | __| |___      | |__   __ _ ___  ___ \n"
                    + "/ __\\ \\ /\\ / / | '__| |/ _` / __|_____| '_ \\ / _` / __|/ _ \\\n"
                    + "\\__ \\\\ V  V /| | |  | | (_| \\__ \\_____| |_) | (_| \\__ \\  __/\n"
                    + "|___/ \\_/\\_/ |_|_|  |_|\\__,_|___/ _   |_.__/ \\__,_|___/\\___|\n"
                    + " ___  __ _ _ __ ___  _ __ | | ___| |                        \n"
                    + "/ __|/ _` | '_ ` _ \\| '_ \\| |/ _ \\ |                        \n"
                    + "\\__ \\ (_| | | | | | | |_) | |  __/_|                        \n"
                    + "|___/\\__,_|_| |_| |_| .__/|_|\\___(_) ");
        }
        logger.info("Server started on {}:{}", config.host(), config.port());
        logger.trace("All registered handlers:{}", handlers);
    }

    @NonNull
    private static List<AdapterHandler<?>> createHandlers(
            final @NonNull BaseApiConfig config, final @NonNull Context swirldsContext, final HttpServer server) {
        final List<AdapterHandler<?>> handlers = List.of(
                new AdapterHandler<>(swirldsContext, new InventoryService(), config.apiBasePath() + "/inventories"),
                new AdapterHandler<>(
                        swirldsContext, new OperationService(swirldsContext), config.apiBasePath() + "/operations"),
                new AdapterHandler<>(swirldsContext, new ItemService(), config.apiBasePath() + "/items"),
                new AdapterHandler<>(swirldsContext, new SaleService(swirldsContext), config.apiBasePath() + "/sales"),
                new AdapterHandler<>(
                        swirldsContext, new PurchaseService(swirldsContext), config.apiBasePath() + "/purchases"));

        handlers.forEach(h -> h.registerInto(server));
        return handlers;
    }
}
