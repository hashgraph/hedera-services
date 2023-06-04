/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.tools.impl;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

public class JsonFileLoader {

    // (Just for debugging)
    public Path path;
    public URI uri;
    public File file;

    public Object result;

    @SuppressWarnings("unchecked")
    @NonNull
    public <T> Optional<T> load(
            @NonNull final Class<T> klass, @NonNull final Path path, @NonNull String fileDescription) {
        load(klass, path)
                .onOk(m -> {
                    System.out.printf("%s %s loaded%n".formatted(fileDescription, path));
                    result = Optional.ofNullable(m);
                })
                .onErr(ex -> {
                    System.err.printf(
                            "*** Cannot load/deserialize %s from %s: %s%n".formatted(fileDescription, path, ex));
                    result = Optional.empty();
                });
        return (Optional<T>) result;
    }

    @NonNull
    public <T> Result<T> load(@NonNull final Class<T> klass, @NonNull final Path path) {
        this.path = path;
        uri = this.path.toUri();
        file = new File(uri);

        if (!file.canRead() || !file.isFile()) {
            return Result.err(new FileNotFoundException(
                    "file %s does not exist or is not a file or can't be read".formatted(path)));
        }

        final var om = new ObjectMapper()
                .configure(DeserializationFeature.WRAP_EXCEPTIONS, true)
                .configure(Feature.ALLOW_COMMENTS, true)
                .configure(Feature.ALLOW_SINGLE_QUOTES, true); // consider `JsonMapper.Builder` instead

        final var readerOfKlass = om.readerFor(klass);
        try {
            final var value = readerOfKlass.<T>readValue(file);
            return Result.ok(value);
        } catch (final IOException ex) {
            return Result.err(
                    new UncheckedIOException("could not json-deserialize file %s: %s".formatted(path, ex), ex));
        }
    }
}
