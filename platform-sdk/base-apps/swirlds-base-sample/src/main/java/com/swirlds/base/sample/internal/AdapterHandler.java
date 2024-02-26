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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.swirlds.base.sample.metrics.ApplicationMetrics;
import com.swirlds.base.sample.persistence.EntityNotFoundException;
import com.swirlds.base.sample.service.CrudService;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.extensions.CountPerSecond;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Adapts a CrudService into an HttpHandler for undertow
 */
public class AdapterHandler<T> implements HttpHandler {
    private static final Logger logger = LogManager.getLogger(AdapterHandler.class);
    private @NonNull final PlatformContext context;
    private @NonNull final CrudService<T> delegatedService;
    private @NonNull final String path;
    private @NonNull final CountPerSecond tps;

    protected AdapterHandler(
            final @NonNull PlatformContext context, CrudService<T> delegatedService, final String path) {
        this.context = Objects.requireNonNull(context);
        this.path = Objects.requireNonNull(path);
        this.delegatedService = Objects.requireNonNull(delegatedService);
        this.tps = new CountPerSecond(context.getMetrics(), ApplicationMetrics.REQUESTS_PER_SECOND);
    }

    /**
     * Handle the given request and generate an appropriate response. See {@code HttpServerExchange} for a description
     * of the steps involved in handling an exchange.
     *
     * @throws NullPointerException if exchange is {@code null}
     */
    @Override
    public void handleRequest(final @NonNull HttpServerExchange exchange) {
        final String requestMethod = exchange.getRequestMethod().toString();
        long start = System.nanoTime();

        final List<String> urlParts = DataTransferUtils.urlToList(exchange);
        final String id = urlParts.getLast();
        final boolean isId = !id.equalsIgnoreCase(getControllerName());
        Object result = null;
        int statusCode;
        try {
            switch (requestMethod) {
                case "GET":
                    result = (isId && !id.contains("?"))
                            ? retrieve(id)
                            : retrieveAll(DataTransferUtils.getUrlParams(exchange));
                    statusCode = 200;
                    break;
                case "POST":
                    result = this.create(
                            DataTransferUtils.deserializeJsonFromExchange(exchange, delegatedService.getResultType()));
                    statusCode = 201;
                    break;
                case "PUT":
                    if (!isId) {
                        throw new IllegalArgumentException("Identification must be sent in url");
                    }
                    result = this.update(
                            id,
                            DataTransferUtils.deserializeJsonFromExchange(exchange, delegatedService.getResultType()));
                    statusCode = 200;
                    break;
                case "DELETE":
                    if (!isId) {
                        throw new IllegalArgumentException("Identification must be sent in url");
                    }
                    this.delete(id);
                    statusCode = 202;
                    break;
                case "OPTIONS": {
                    handleOptionsRequest(exchange);
                    return;
                }

                default:
                    throw new UnsupportedOperationException("Operation not supported for %s".formatted(requestMethod));
            }
        } catch (UnsupportedOperationException e) {
            statusCode = 405;
            result = ImmutableMap.of("error", "Operation not supported for %s".formatted(requestMethod));
        } catch (IllegalArgumentException e) {
            statusCode = 400;
            result = ImmutableMap.of("error", e.getMessage());
        } catch (EntityNotFoundException e) {
            statusCode = 404;
            result = ImmutableMap.of("error", "%s with code %s not found".formatted(e.getEntityType(), e.getId()));
        } catch (RuntimeException e) {
            statusCode = 500;
            result = ImmutableMap.of("error", "Internal error");
        }

        final String response = DataTransferUtils.serializeToJson(result);
        long duration = System.nanoTime() - start;
        context.getMetrics().getOrCreate(ApplicationMetrics.REQUEST_TOTAL).increment();
        context.getMetrics().getOrCreate(ApplicationMetrics.REQUEST_AVG_TIME).update(duration);
        tps.count();
        if (statusCode - 200 >= 100) {
            context.getMetrics().getOrCreate(ApplicationMetrics.ERROR_TOTAL).increment();
        }
        exchange.setStatusCode(statusCode);
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json;charset=utf-8");
        exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Origin"), "*");
        exchange.getResponseSender().send(response);

        logger.trace(
                "Received Request to:{}{} took:{}ns answered status:{} body:{}",
                requestMethod,
                exchange.getRequestURL(),
                duration,
                statusCode,
                response);
    }

    protected void delete(final @NonNull String key) {
        this.delegatedService.delete(key);
    }

    protected @NonNull T update(final @NonNull String key, final @Nullable T body) {
        Preconditions.checkArgument(Objects.nonNull(body), "Body is required");
        return this.delegatedService.update(key, body);
    }

    protected @NonNull T create(final @Nullable T body) {
        Preconditions.checkArgument(Objects.nonNull(body), "Body is required");

        return this.delegatedService.create(body);
    }

    protected @NonNull T retrieve(final @NonNull String key) {
        return this.delegatedService.retrieve(key);
    }

    protected @NonNull List<T> retrieveAll(@NonNull Map<String, String> params) {
        return this.delegatedService.retrieveAll(params);
    }

    private @NonNull String getControllerName() {
        return DataTransferUtils.urlToList(this.path).getLast();
    }

    @Override
    public String toString() {
        return this.path;
    }

    /**
     * Adds the handler into a pathHandler
     */
    public void into(@NonNull final PathHandler pathHandler) {
        pathHandler.addPrefixPath(path, this);
    }

    /**
     * cors support
     */
    private static void handleOptionsRequest(@NonNull final HttpServerExchange exchange) {
        exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Origin"), "*");
        exchange.getResponseHeaders()
                .put(new HttpString("Access-Control-Allow-Methods"), "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().put(new HttpString("Access-Control-Max-Age"), "3600");
        exchange.getResponseHeaders()
                .put(new HttpString("Access-Control-Allow-Headers"), "content-type, authorization");
        exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Credentials"), "true");

        exchange.endExchange();
    }
}
