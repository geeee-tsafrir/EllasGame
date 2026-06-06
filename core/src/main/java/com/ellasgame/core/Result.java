package com.ellasgame.core;

import java.util.Objects;
import java.util.function.Function;

public sealed interface Result<T, E> permits Result.Success, Result.Failure {
    static <T, E> Result<T, E> success(T value) {
        return new Success<>(value);
    }

    static <T, E> Result<T, E> failure(E error) {
        return new Failure<>(error);
    }

    boolean isSuccess();

    default boolean isFailure() {
        return !isSuccess();
    }

    <U> Result<U, E> map(Function<? super T, ? extends U> mapper);

    <U> Result<U, E> flatMap(Function<? super T, Result<U, E>> mapper);

    record Success<T, E>(T value) implements Result<T, E> {
        public Success {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public <U> Result<U, E> map(Function<? super T, ? extends U> mapper) {
            return Result.success(mapper.apply(value));
        }

        @Override
        public <U> Result<U, E> flatMap(Function<? super T, Result<U, E>> mapper) {
            return mapper.apply(value);
        }
    }

    record Failure<T, E>(E error) implements Result<T, E> {
        public Failure {
            Objects.requireNonNull(error, "error");
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public <U> Result<U, E> map(Function<? super T, ? extends U> mapper) {
            return Result.failure(error);
        }

        @Override
        public <U> Result<U, E> flatMap(Function<? super T, Result<U, E>> mapper) {
            return Result.failure(error);
        }
    }
}
