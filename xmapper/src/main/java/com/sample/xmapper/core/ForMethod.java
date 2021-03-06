package com.sample.xmapper.core;



import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ForMethod implements SpreadWrapper<Object> {
    private final Method method;
    private final Object instance;

    public ForMethod(@NotNull Method method, @Nullable Object instance) {
        this.method = method;
        this.instance = instance;
    }

    @Override
    public Object call(Object[] args) throws InvocationTargetException, IllegalAccessException {
        return method.invoke(instance, args);
    }
}
