package com.swirlds.config.api;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * Class for capturing and passing generic type information at runtime.
 * This is similar to Jackson's TypeReference but simplified for our use case.
 * <p>
 * Example usage:
 * <pre>
 * TypeReference&lt;List&lt;String&gt;&gt; typeRef = new TypeReference&lt;List&lt;String&gt;&gt;() {};
 * Type type = typeRef.getType();
 * </pre>
 *
 * @param <T> the type parameter to capture
 */
public abstract class TypeReference<T> {
    private final Type type;

    /**
     * Constructs a new TypeReference, capturing the generic type information.
     *
     * @throws IllegalArgumentException if the TypeReference is created without actual type information
     */
    protected TypeReference() {
        // Get the superclass's generic type (the T in TypeReference<T>)
        Type superClass = getClass().getGenericSuperclass();
        if (!(superClass instanceof ParameterizedType)) {
            throw new IllegalArgumentException("TypeReference created without actual type information");
        }

        // Extract the actual type argument
        if (((ParameterizedType) superClass).getActualTypeArguments().length == 0) {
            throw new IllegalArgumentException("TypeReference created without actual type information");
        }
        this.type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
        validateType(this.type);
    }

    public TypeReference(final Type type) {
        Objects.requireNonNull(type, "type cannot be null");
        this.type = type;
        validateType(this.type);
    }

    /**
     * Gets the captured type.
     *
     * @return the captured type
     */
    public Type getType() {
        return type;
    }

    /**
     * Gets the raw class of the captured type, if available.
     *
     * @return the raw class of the captured type
     * @throws IllegalStateException if the raw class cannot be determined
     */
    @SuppressWarnings("unchecked")
    public Class<T> getRawClass() {
        if (type instanceof Class<?>) {
            return (Class<T>) type;
        }
        if (type instanceof ParameterizedType) {
            return (Class<T>) ((ParameterizedType) type).getRawType();
        }
        throw new IllegalStateException("Cannot get raw class for type: " + type);
    }

    /**
     * Gets the argument types of the captured type, if available.
     *
     * @return an array of argument types
     */
    public Type[] getArgumentTypes() {
        if (type instanceof ParameterizedType) {
            return ((ParameterizedType) type).getActualTypeArguments();
        }
        return new Type[0];
    }

    /**
     * Validates the captured type.
     *
     * @param type the type to validate
     * @throws IllegalArgumentException if the type is invalid
     */
    private void validateType(Type type) {
        if (type == null) {
            throw new IllegalArgumentException("Captured type cannot be null");
        }
        if (type instanceof ParameterizedType) {
            for (Type argType : ((ParameterizedType) type).getActualTypeArguments()) {
                if (argType == null) {
                    throw new IllegalArgumentException("Type argument cannot be null");
                }
            }
        }
    }
}

