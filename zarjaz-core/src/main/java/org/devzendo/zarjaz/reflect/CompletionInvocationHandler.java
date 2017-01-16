package org.devzendo.zarjaz.reflect;

import org.devzendo.zarjaz.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.transport.EndpointName;
import org.devzendo.zarjaz.transport.MethodInvocationTimeoutException;
import org.devzendo.zarjaz.transport.TransportInvocationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
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
public class CompletionInvocationHandler<T> implements InvocationHandler {
    private static final Logger logger = LoggerFactory.getLogger(CompletionInvocationHandler.class);

    private final TimeoutScheduler timeoutScheduler;
    private final EndpointName name;
    private final Class<T> interfaceClass;
    private final TransportInvocationHandler transportHandler;
    private final long methodTimeoutMs;

    public CompletionInvocationHandler(final TimeoutScheduler timeoutScheduler, final EndpointName name, final Class<T> interfaceClass, final TransportInvocationHandler transportHandler, final long methodTimeoutMs) {
        this.timeoutScheduler = timeoutScheduler;
        this.name = name;
        this.interfaceClass = interfaceClass;
        this.transportHandler = transportHandler;
        this.methodTimeoutMs = methodTimeoutMs;
    }

    public Object invoke(final Object proxy, final Method method, final Object[] args) {
        if (logger.isDebugEnabled()) {
            logger.debug("Invoking [" + name + "] " + method.getDeclaringClass().getName() + "." + method.getName());
        }

        // TODO this needs replacing with a timeout scheduler
        // And every response needs to reuse this logic. Synchronous calls can get.

        final CompletableFuture<Object> future = new CompletableFuture<Object>();
        timeoutScheduler.schedule(methodTimeoutMs, new Runnable() {
            @Override
            public void run() {
                final String message = "method call [" + name + "] '" + method.getName() + "' timed out after " + methodTimeoutMs + "ms";
                if (logger.isDebugEnabled()) {
                    logger.debug(message);
                }
                future.completeExceptionally(new MethodInvocationTimeoutException(message));
            }
        });

        // Note that the NullTransport can block indefinitely here.
        // This will run the remote code on a separate thread.
        // TODO need to pass a Runnable that cancels the timeout on completion/exception.
        transportHandler.invoke(method, args, future);

        // TODO cancel timeout

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
                // If the cause is one of our timeouts, rethrow rather than embedding it in a thicket of stuff.
                final Throwable cause = e.getCause();
                if (cause instanceof MethodInvocationTimeoutException) {
                    throw (MethodInvocationTimeoutException) cause;
                }
                throw new InvocationException(msg, e);
            }
        }
    }
}
