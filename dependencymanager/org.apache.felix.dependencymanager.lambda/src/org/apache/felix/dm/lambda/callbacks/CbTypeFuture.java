package org.apache.felix.dm.lambda.callbacks;

import java.util.Objects;

/**
 * Represents a callback that accepts the result of a CompletableFuture operation. The callback is invoked on a Component implementation class. 
 * The type of the class on which the callback is invoked on is represented by the T generic parameter.
 * The type of the result of the CompletableFuture is represented by the F generic parameter.
 * 
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
@FunctionalInterface
public interface CbTypeFuture<T, F> extends SerializableLambda {
    /**
     * Handles the given arguments.
     * @param instance the Component implementation instance on which the callback is invoked on. 
     * @param future the result of a CompletableFuture operation.
     */
    void accept(T instance, F future);

    default CbTypeFuture<T, F> andThen(CbTypeFuture<? super T, F> after) {
        Objects.requireNonNull(after);
        return (T instance, F future) -> {
            accept(instance, future);
            after.accept(instance, future);
        };
    }
}
