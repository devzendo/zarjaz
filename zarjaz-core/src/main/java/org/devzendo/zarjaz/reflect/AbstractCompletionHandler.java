package org.devzendo.zarjaz.reflect;

import org.devzendo.commoncode.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.transport.EndpointName;
import org.devzendo.zarjaz.transport.MethodInvocationTimeoutException;
import org.devzendo.zarjaz.transport.TransportInvocationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

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
public abstract class AbstractCompletionHandler<T> {
    private static final Logger logger = LoggerFactory.getLogger(AbstractCompletionHandler.class);
    protected final TimeoutScheduler timeoutScheduler;
    protected final EndpointName endpointName;
    protected final Class<T> interfaceClass;
    protected final TransportInvocationHandler transportInvocationHandler;
    protected final long methodTimeoutMilliseconds;

    AbstractCompletionHandler(final TimeoutScheduler timeoutScheduler, final EndpointName endpointName, final Class<T> interfaceClass, final TransportInvocationHandler transportInvocationHandler, final long methodTimeoutMilliseconds) {
        this.timeoutScheduler = timeoutScheduler;
        this.endpointName = endpointName;
        this.interfaceClass = interfaceClass;
        this.transportInvocationHandler = transportInvocationHandler;
        this.methodTimeoutMilliseconds = methodTimeoutMilliseconds;
    }

    Object invokeRemote(final Method method, final Object[] args, final Optional<Consumer<Object>> consumer) {
        return invokeRemote(method, args, consumer, methodTimeoutMilliseconds);
    }

    Object invokeRemote(final Method method, final Object[] args, final Optional<Consumer<Object>> consumer, final long methodTimeoutMilliseconds) {
        final CompletableFuture<Object> future = new CompletableFuture<>();

        // Possible behaviours on timeout:
        // Complete the future exceptionally
        // Process some transport-specific detail (clearing outstanding method call count?)
        //
        // These timeout handlers need access to (from this layer):
        // * Future
        // * Endpoint name
        // * Method
        //
        // So there are two timeout handlers, both of which can be replaced:
        // The actual timeout exceptional future setter
        // The transport-specific handler
        //
        // Pass the context object for the timeout handlers to the transport invocation handler so it can provide its
        // own timeout behaviour for the method invocation.
        final MethodCallTimeoutHandlers timeoutHandlers = new MethodCallTimeoutHandlers();
        final MethodCallTimeoutHandler timeoutHandler = createTimeoutHandler();
        timeoutHandlers.setTimeoutOccurredHandler(timeoutHandler);
        timeoutScheduler.schedule(methodTimeoutMilliseconds, () -> {
            // TODO create list of handlers, flatMap over?
            try {
                timeoutHandlers.getTimeoutTransportHandler().ifPresent(handler -> handler.timeoutOccurred(future, endpointName, method));
            } catch (final Exception e) {
                logger.warn("Method call timeout handler threw exception: " + e.getMessage());
            }
            try {
                timeoutHandlers.getTimeoutOccurredHandler().ifPresent(handler -> handler.timeoutOccurred(future, endpointName, method));
            } catch (final Exception e) {
                logger.warn("Method call timeout handler threw exception: " + e.getMessage());
            }
        });

        // Note that the NullTransport could block indefinitely here, so runs the invocation and remote code on a separate thread.
        try {
            transportInvocationHandler.invoke(method, args, future, consumer, timeoutHandlers);
        } catch (final Exception e) {
            final String message = "Method call [" + endpointName + "] '" + method.getName() + "' invocation failed: " + e.getMessage();
            logger.warn(message, e);
            timeoutHandlers.removeTimeoutOccurredHandler();
            future.completeExceptionally(e);
        }

        if (method.getReturnType().isAssignableFrom(Future.class)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Returning Future");
            }
            return future;
            // TODO how to cancel the timeout handler on a correct method invocation return? nested future?
        } else {
            try {
                if (logger.isDebugEnabled()) {
                    logger.debug("Waiting on Future");
                }
                final Object o = future.get();
                if (logger.isDebugEnabled()) {
                    logger.debug("Wait over; removing timeout handler; returning value");
                }
                timeoutHandlers.removeTimeoutOccurredHandler();
                return o;
            } catch (final InterruptedException e) {
                final String msg = "Invocation of " + method.getDeclaringClass().getName() + "." + method.getName() + " interrupted";
                logger.warn(msg);
                throw new InvocationException(msg, e);
            } catch (final ExecutionException e) {
                // If the cause is one of our timeouts, rethrow rather than embedding it in a thicket of stuff.
                final Throwable cause = e.getCause();
                if (cause instanceof MethodInvocationTimeoutException) {
                    throw (MethodInvocationTimeoutException) cause;
                }
                // Timeouts are already logged by the timeout handler; log other faults now.
                final String msg = "Invocation of " + method.getDeclaringClass().getName() + "." + method.getName() + " threw an " +
                        e.getCause().getClass().getName() + ": " + e.getCause().getMessage();
                logger.warn(msg);
                throw new InvocationException(msg, e);
            }
        }
    }

    protected abstract MethodCallTimeoutHandler createTimeoutHandler();
}
