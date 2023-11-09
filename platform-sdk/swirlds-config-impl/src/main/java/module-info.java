import com.swirlds.config.api.spi.ConfigurationBuilderFactory;
import com.swirlds.config.impl.internal.ConfigurationBuilderFactoryImpl;

module com.swirlds.config.impl {
    exports com.swirlds.config.impl.converters;
    exports com.swirlds.config.impl.validators;

    requires transitive com.swirlds.config.api;
    requires com.swirlds.base;
    requires com.swirlds.common;
    requires static com.github.spotbugs.annotations;

    provides ConfigurationBuilderFactory with
            ConfigurationBuilderFactoryImpl;
}
