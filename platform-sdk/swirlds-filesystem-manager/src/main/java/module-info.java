import com.swirlds.filesystem.manager.FileManagerFactory;
import com.swirlds.filesystem.manager.internal.FileManagerFactoryImpl;

module com.swirlds.filesystem.manager {
    requires transitive com.swirlds.config.api;
    requires com.swirlds.base;
    requires com.swirlds.common;
    requires com.swirlds.config.extensions;
    requires static com.github.spotbugs.annotations;
    requires static com.google.auto.service;

    exports com.swirlds.filesystem.manager;

    provides FileManagerFactory with
            FileManagerFactoryImpl;
}
