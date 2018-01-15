package org.devzendo.zarjaz.transport;

import org.devzendo.commoncode.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.reflect.MethodCallTimeoutHandlers;
import org.devzendo.zarjaz.validation.ClientInterfaceValidator;
import org.devzendo.zarjaz.validation.ServerImplementationValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

import static org.devzendo.zarjaz.util.ClassUtils.joinedClassNames;
import static org.devzendo.zarjaz.util.ClassUtils.objectsToClasses;

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
class NullTransport extends AbstractTransport implements Transport {
    private static final Logger logger = LoggerFactory.getLogger(NullTransport.class);

    // TODO not sure this ctor is needed - need one with everything supplied, and with nothing supplied?
    public NullTransport(final ServerImplementationValidator serverImplementationValidator, final ClientInterfaceValidator clientInterfaceValidator) {
        this(serverImplementationValidator, clientInterfaceValidator, new TimeoutScheduler());
    }

    public NullTransport(final ServerImplementationValidator serverImplementationValidator, final ClientInterfaceValidator clientInterfaceValidator, final TimeoutScheduler timeoutScheduler) {
        super(serverImplementationValidator, clientInterfaceValidator, timeoutScheduler, "null");
    }

    // TODO this should be useful for all server-side transports, surely?
    private static class NullTransportInvocationHandler implements TransportInvocationHandler {

        private final ThreadPoolExecutor executor;
        private final Object impl;

        public NullTransportInvocationHandler(final ThreadPoolExecutor executor, final Object impl) {
            this.executor = executor;
            this.impl = impl;
        }

        @Override
        public void invoke(final Method method, final Object[] args, final CompletableFuture<Object> future, final Optional<Consumer<Object>> consumer, final MethodCallTimeoutHandlers timeoutHandlers) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    final String methodName = method.getName();
                    final Class[] argClasses = method.getParameterTypes();
                    try {
                        // Serialisation, transfer, setting a completion handler would happen here.
                        final Method implMethod = impl.getClass().getMethod(methodName, argClasses);
                        final Object returnValue = implMethod.invoke(impl, args);
                        if (logger.isDebugEnabled()) {
                            // TODO logging the return could violate security... we don't know what we could be logging.
                            logger.debug("Invocation completed with return: '" + returnValue + "'");
                        }

                        // Handle methods that are declared asynchronously.
                        if (method.getReturnType().isAssignableFrom(Future.class)) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Method was asynchronous, passing future contents on...");
                            }
                            final Future<?> thing = (Future)returnValue;
                            future.complete(thing.get());
                        } else {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Method was synchronous, returning value on...");
                            }
                            future.complete(returnValue);
                        }
                    } catch (final NoSuchMethodException e) {
                        // TODO not sure how to test this
                        final String msg = "No such method '" + methodName + "' with arg classes " + joinedClassNames(objectsToClasses(args)) + ": " + e.getMessage();
                        logger.error(msg, e);
                        future.completeExceptionally(e);
                    } catch (final IllegalAccessException e) {
                        // TODO not sure how to test this
                        final String msg = "Illegal access exception when calling method '" + methodName + "' with arg classes " + joinedClassNames(objectsToClasses(args)) + ": " + e.getMessage();
                        logger.error(msg, e);
                        future.completeExceptionally(e);
                    } catch (final InvocationTargetException e) {
                        // TODO not sure how to test this
                        final String msg = "Invocation target exception when calling method '" + methodName + "' with arg classes " + joinedClassNames(objectsToClasses(args)) + ": " + e.getMessage();
                        logger.error(msg, e);
                        future.completeExceptionally(e);
                    } catch (final InterruptedException e) {
                        // TODO not sure how to test this
                        final String msg = "Interrupted exception when calling method '" + methodName + "' with arg classes " + joinedClassNames(objectsToClasses(args)) + ": " + e.getMessage();
                        logger.error(msg, e);
                        future.completeExceptionally(e);
                    } catch (final ExecutionException e) {
                        // TODO not sure how to test this
                        final String msg = "Execution exception when calling method '" + methodName + "' with arg classes " + joinedClassNames(objectsToClasses(args)) + ": " + e.getMessage();
                        logger.error(msg, e);
                        future.completeExceptionally(e);
                    }
                }
            });
            // TODO handle exhaustion
        }
    }

    @Override
    protected <T> void registerTransportRequestDispatcher(final EndpointName endpointName, final Class<T> interfaceClass) {
        // Nothing is needed here in the NullTransport.
    }

    @Override
    protected void validateMultipleReturn() {
        throw new IllegalStateException("The NullTransport does not support multiple return invocation");
    }

    // The TransportInvocationHandler is the client-side part that varies between transports.
    @Override
    protected <T> TransportInvocationHandler createTransportInvocationHandler(final EndpointName endpointName, final Class<T> interfaceClass, final long methodTimeoutMilliseconds) {
        // TODO generally, how does a remote client know that a named interface exists?
        synchronized (implementations) {
            final NamedInterface namedInterface = new NamedInterface(endpointName, interfaceClass);
            if (!implementations.containsKey(namedInterface)) {
                throw new RegistrationException("The EndpointName '" + endpointName + "' is not registered");
            }

            return new NullTransportInvocationHandler(executor, implementations.get(namedInterface));
        }
    }
}
