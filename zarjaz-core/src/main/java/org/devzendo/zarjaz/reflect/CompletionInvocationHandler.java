package org.devzendo.zarjaz.reflect;

import org.devzendo.zarjaz.transport.EndpointName;
import org.devzendo.zarjaz.transport.TransportInvocationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

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
public class CompletionInvocationHandler<T> {
    private static final Logger logger = LoggerFactory.getLogger(CompletionInvocationHandler.class);

    private final EndpointName name;
    private final Class<T> interfaceClass;
    private final TransportInvocationHandler transportHandler;

    public CompletionInvocationHandler(final EndpointName name, final Class<T> interfaceClass, final TransportInvocationHandler transportHandler) {
        this.name = name;
        this.interfaceClass = interfaceClass;
        this.transportHandler = transportHandler;
    }

    public Object invoke(final Object proxy, final Method method, final Object[] args) {
        if (logger.isDebugEnabled()) {
            logger.debug("Invoking " + method.getDeclaringClass().getName() + "." + method.getName());
        }

        // TODO this needs replacing with a timeout scheduler
        // And every response needs to reuse this logic. Synchronous calls can get.

        final CompletableFuture<Object> future = new CompletableFuture<Object>();

        // TODO could block indefinitely?
        transportHandler.invoke(method, args, future);

        if (method.getReturnType().isAssignableFrom(Future.class)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Returning Future");
            }
            return future;
        } else {
            try {
                if (logger.isDebugEnabled()) {
                    logger.debug("Waiting on Future");
                }
                final Object o = future.get();
                if (logger.isDebugEnabled()) {
                    logger.debug("Wait over; returning value");
                }
                return o;
            } catch (final InterruptedException e) {
                final String msg = "Invocation of " + method.getDeclaringClass().getName() + "." + method.getName() + " interrupted";
                logger.warn(msg);
                throw new InvocationException(msg, e);
            } catch (final ExecutionException e) {
                final String msg = "Invocation of " + method.getDeclaringClass().getName() + "." + method.getName() + " threw an " +
                        e.getCause().getClass().getName() + ": " + e.getCause().getMessage();
                logger.warn(msg);
                throw new InvocationException(msg, e);
            }
        }
    }
}
