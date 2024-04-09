import com.swirlds.filesystem.manager.FileManagerFactory;
import com.swirlds.filesystem.manager.internal.FileManagerFactoryImpl;

module com.swirlds.filesystem.manager {
    requires transitive com.swirlds.common;
    requires transitive com.swirlds.config.api;
    requires static com.github.spotbugs.annotations;
    requires static com.google.auto.service;

    exports com.swirlds.filesystem.manager;

    provides FileManagerFactory with
            FileManagerFactoryImpl;
}
