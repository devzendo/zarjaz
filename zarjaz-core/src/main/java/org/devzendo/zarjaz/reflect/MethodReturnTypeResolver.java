package org.devzendo.zarjaz.reflect;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.concurrent.Future;

/**
 * Copyright (C) 2008-2016 Matt Gumbley, DevZendo.org http://devzendo.org
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class MethodReturnTypeResolver {
    private static final Logger logger = LoggerFactory.getLogger(MethodReturnTypeResolver.class);
    private final TypeResolver typeResolver = new TypeResolver();

    public Class getReturnType(final Method method) {
        final Class<?> returnType = method.getReturnType();
        if (returnType.isAssignableFrom(Future.class)) {
            final Type genericReturnType = method.getGenericReturnType();
            final ResolvedType rawArgument = typeResolver.resolve(genericReturnType);
            final Class<?> erasedType = rawArgument.getTypeParameters().get(0).getErasedType();
            logger.debug("The resolved return type of the method " + method + " is " + erasedType);
            return erasedType;
        } else {
            logger.debug("The normal return type of the method " + method + " is " + returnType);
            return returnType;
        }
    }
}
