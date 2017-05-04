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
import java.util.LinkedList;
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
    private final EndpointName endpointName;
    private final Class<T> interfaceClass;
    private final TransportInvocationHandler transportHandler;
    private final long methodTimeoutMs;

    public CompletionInvocationHandler(final TimeoutScheduler timeoutScheduler, final EndpointName endpointName, final Class<T> interfaceClass, final TransportInvocationHandler transportHandler, final long methodTimeoutMs) {
        this.timeoutScheduler = timeoutScheduler;
        this.endpointName = endpointName;
        this.interfaceClass = interfaceClass;
        this.transportHandler = transportHandler;
        this.methodTimeoutMs = methodTimeoutMs;
    }

    public Object invoke(final Object proxy, final Method method, final Object[] args) {
        if (logger.isDebugEnabled()) {
            logger.debug("Invoking [" + endpointName + "] " + method.getDeclaringClass().getName() + "." + method.getName());
        }

        String name = method.getName();
        if (name.equals("hashCode")) {
            return hashCode();
        }
        else if (name.equals("equals")) {
            final Object other = args[0];
            return (proxy == other) ||
                   (other != null && Proxy.isProxyClass(other.getClass()) && this.equals(Proxy.getInvocationHandler(other)));
        }
        else if (name.equals("toString")) {
            return "Client for endpoint '" + name + "', interface class '" + interfaceClass + "'";
        }
        else {
            return invokeRemote(method, args);
        }
    }

    private Object invokeRemote(final Method method, final Object[] args) {
        // And every response needs to reuse this logic. Synchronous calls can get.

        final CompletableFuture<Object> future = new CompletableFuture<Object>();


        // Possible behaviours on timeout:
        // Complete the future exceptionally
        // Process some transport-specific detail (clearing outstanding method call count?)
        // These timeout handlers need access to (from this layer):
        // Future
        // Endpoint name, method
        // So there are two timeout handlers, both of which can be replaced:
        // The actual timeout exceptional future setter
        // The transport-specific handler
        //
        // This list is passed to the transport invocation handler so it can provide its own timeout behaviour for the
        // method invocation.
        final LinkedList<MethodCallTimeoutHandler> timeoutHandlers = new LinkedList<>();
        final MethodCallTimeoutHandler timeoutHandler = (f, en, m) -> {
            final String message = "Method call [" + en + "] '" + m.getName() + "' timed out after " + methodTimeoutMs + "ms";
            if (logger.isDebugEnabled()) {
                logger.debug(message);
            }
            f.completeExceptionally(new MethodInvocationTimeoutException(message));
        };
        timeoutHandlers.addFirst(timeoutHandler);
        timeoutScheduler.schedule(methodTimeoutMs, () -> timeoutHandlers.forEach((handler) -> {
            try {
                handler.timeoutOccurred(future, endpointName, method);
            } catch (final Exception e) {
                logger.warn("Method call timeout handler threw exception: " + e.getMessage());
            }
        }));

        // Note that the NullTransport could block indefinitely here, so runs the invocation and remote code on a separate thread.
        try {
            transportHandler.invoke(method, args, future, timeoutHandlers);
        } catch (final Exception e) {
            final String message = "Method call [" + endpointName + "] '" + method.getName() + "' invocation failed: " + e.getMessage();
            logger.warn(message, e);
            timeoutHandlers.remove(timeoutHandler);
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
                timeoutHandlers.remove(timeoutHandler);
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
}
