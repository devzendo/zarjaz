package org.devzendo.zarjaz.reflect;

import org.devzendo.zarjaz.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.transport.EndpointName;
import org.devzendo.zarjaz.transport.MultipleReturnInvoker;
import org.devzendo.zarjaz.transport.TransportInvocationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Consumer;

import static org.devzendo.zarjaz.util.ClassUtils.joinedClassNames;

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
public class CompletionMultipleReturnInvoker<T> extends AbstractCompletionHandler implements MultipleReturnInvoker<T> {
    private static final Logger logger = LoggerFactory.getLogger(CompletionMultipleReturnInvoker.class);

    public CompletionMultipleReturnInvoker(final TimeoutScheduler timeoutScheduler, final EndpointName endpointName, final Class<T> interfaceClass, final TransportInvocationHandler transportInvocationHandler, final long methodTimeoutMilliseconds) {
        super(timeoutScheduler, endpointName, interfaceClass, transportInvocationHandler, methodTimeoutMilliseconds);
    }

    @Override
    public <R> void invoke(final Method method, final Consumer<R> consumer, final long methodTimeoutMilliSeconds, final Object... args) {
        if (logger.isDebugEnabled()) {
            logger.debug("Invoking (multiple return) [" + endpointName + "] " + method.getDeclaringClass().getName() + "." + method.getName() + joinedClassNames(method.getParameterTypes()));
        }
        invokeRemote(method, args, Optional.of(consumer));
    }

    @Override
    protected MethodCallTimeoutHandler createTimeoutHandler() {
        return (f, en, m) -> {
            final String message = "Multiple return method call [" + en + "] '" + m.getName() + "' ended after " + methodTimeoutMilliseconds + "ms";
            if (logger.isDebugEnabled()) {
                logger.debug(message);
            }
            f.complete(null); // the result of the method call is not needed, the future is used to unblock the client
        };
    }
}
