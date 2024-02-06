package com.swirlds.baseapi.config.internal;

import com.swirlds.config.extensions.sources.AbstractConfigSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class ClasspathConfigSource extends AbstractConfigSource {
    private final Map<String, String> internalProperties;


    public ClasspathConfigSource(@NonNull Path filePath) throws IOException {
        Objects.requireNonNull(filePath, "filePath can not be null");
        this.internalProperties = new HashMap<>();
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(filePath.toString());
        assert inputStream != null;
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        try {
            Properties loadedProperties = new Properties();
            loadedProperties.load(reader);
            loadedProperties.stringPropertyNames().forEach((name) -> {
                this.internalProperties.put(name, loadedProperties.getProperty(name));
            });
        } catch (Throwable var7) {
            try {
                reader.close();
            } catch (Throwable var6) {
                var7.addSuppressed(var6);
            }

            throw var7;
        }

        reader.close();

    }

    @Override
    protected Map<String, String> getInternalProperties() {
        return internalProperties;
    }
}
