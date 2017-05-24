package org.devzendo.zarjaz.reflect;

import org.devzendo.zarjaz.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.transport.EndpointName;
import org.devzendo.zarjaz.transport.MethodInvocationTimeoutException;
import org.devzendo.zarjaz.transport.TransportInvocationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;

import static org.devzendo.zarjaz.util.ClassUtils.joinedClassNames;

/**
 * Copyright (C) 2008-2015 Matt Gumbley, DevZendo.org http://devzendo.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class CompletionInvocationHandler<T> extends AbstractCompletionHandler implements InvocationHandler {
    private static final Logger logger = LoggerFactory.getLogger(CompletionInvocationHandler.class);

    public CompletionInvocationHandler(final TimeoutScheduler timeoutScheduler, final EndpointName endpointName, final Class<T> interfaceClass, final TransportInvocationHandler transportHandler, final long methodTimeoutMs) {
        super(timeoutScheduler, endpointName, interfaceClass, transportHandler, methodTimeoutMs);
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) {
        if (logger.isDebugEnabled()) {
            logger.debug("Invoking [" + endpointName + "] " + method.getDeclaringClass().getName() + "." + method.getName() + joinedClassNames(method.getParameterTypes()));
        }

        final String name = method.getName();
        switch (name) {
            case "hashCode":
                return hashCode();
            case "equals":
                final Object other = args[0];
                return (proxy == other) ||
                        (other != null && Proxy.isProxyClass(other.getClass()) && this.equals(Proxy.getInvocationHandler(other)));
            case "toString":
                return "Client for endpoint '" + name + "', interface class '" + interfaceClass + "'";
            default:
                return invokeRemote(method, args, Optional.empty());
        }
    }

    @Override
    protected MethodCallTimeoutHandler createTimeoutHandler() {
        return (f, en, m) -> {
            final String message = "Method call [" + en + "] '" + m.getName() + "' timed out after " + methodTimeoutMilliseconds + "ms";
            if (logger.isDebugEnabled()) {
                logger.debug(message);
            }
            f.completeExceptionally(new MethodInvocationTimeoutException(message));
        };
    }
}
