package org.devzendo.zarjaz.transport;

import org.devzendo.zarjaz.concurrency.DaemonThreadFactory;
import org.devzendo.zarjaz.reflect.CompletionInvocationHandler;
import org.devzendo.zarjaz.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.validation.ClientInterfaceValidator;
import org.devzendo.zarjaz.validation.ServerImplementationValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

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
    private final ThreadPoolExecutor executor;

    final Map<NamedInterface, Object> implementations = new HashMap<>();

    // TODO not sure this ctor is needed - need one with everything supplied, and with nothing supplied?
    public NullTransport(ServerImplementationValidator serverImplementationValidator, ClientInterfaceValidator clientInterfaceValidator) {
        this(serverImplementationValidator, clientInterfaceValidator, new TimeoutScheduler());
    }

    public NullTransport(ServerImplementationValidator serverImplementationValidator, ClientInterfaceValidator clientInterfaceValidator, TimeoutScheduler timeoutScheduler) {
        super(serverImplementationValidator, clientInterfaceValidator, timeoutScheduler);
        this.executor = new ThreadPoolExecutor(5, 10, 2000L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(10),
                new DaemonThreadFactory("zarjaz-null-transport-invoker-thread-"));
    }

    public <T> void registerServerImplementation(final EndpointName name, final Class<T> interfaceClass, final T implementation) {
        logger.info("Registering server implementation of " + name + " with interface " + interfaceClass.getName());
        clientInterfaceValidator.validateClientInterface(interfaceClass);
        serverImplementationValidator.validateServerImplementation(interfaceClass, implementation);

        synchronized (implementations) {
            final NamedInterface namedInterface = new NamedInterface(name, interfaceClass);
            if (implementations.containsKey(namedInterface)) {
                throw new RegistrationException("The EndpointName '" + name + "' is already registered");
            }

            implementations.put(namedInterface, implementation);
        }
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
        public void invoke(final Method method, final Object[] args, final CompletableFuture<Object> future) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    String methodName = method.getName();
                    final Class[] argClasses = objectsToClasses(args);
                    if (logger.isDebugEnabled()) {
                        // TODO would be useful to log the endpoint name here
                        logger.debug("Invoking method: " + method.getReturnType().getSimpleName() + " " + methodName + joinedClassNames(argClasses));
                    }
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
                        final String msg = "No such method '" + methodName + "' with arg classes " + objectsToClasses(args) + ": " + e.getMessage();
                        logger.error(msg, e);
                        future.completeExceptionally(e);
                    } catch (final IllegalAccessException e) {
                        // TODO not sure how to test this
                        final String msg = "Illegal access exception when calling method '" + methodName + "' with arg classes " + objectsToClasses(args) + ": " + e.getMessage();
                        logger.error(msg, e);
                        future.completeExceptionally(e);
                    } catch (final InvocationTargetException e) {
                        // TODO not sure how to test this
                        final String msg = "Invocation target exception when calling method '" + methodName + "' with arg classes " + objectsToClasses(args) + ": " + e.getMessage();
                        logger.error(msg, e);
                        future.completeExceptionally(e);
                    } catch (final InterruptedException e) {
                        // TODO not sure how to test this
                        final String msg = "Interrupted exception when calling method '" + methodName + "' with arg classes " + objectsToClasses(args) + ": " + e.getMessage();
                        logger.error(msg, e);
                        future.completeExceptionally(e);
                    } catch (final ExecutionException e) {
                        // TODO not sure how to test this
                        final String msg = "Execution exception when calling method '" + methodName + "' with arg classes " + objectsToClasses(args) + ": " + e.getMessage();
                        logger.error(msg, e);
                        future.completeExceptionally(e);
                    }
                }
            });
            // TODO handle exhaustion
        }

    }

    // Plan is that the TransportInvocationHandler is the client-side part that varies here, so that this
    // createClientProxy method could be in an abstract base transport class?
    @Override
    public <T> T createClientProxy(EndpointName name, Class<T> interfaceClass, long methodTimeoutMilliseconds) {
        logger.info("Creating client proxy of " + name + " with interface " + interfaceClass.getName() + " with method timeout " + methodTimeoutMilliseconds);
        clientInterfaceValidator.validateClientInterface(interfaceClass);
        final TransportInvocationHandler transportInvocationHandler = createTransportInvocationHandler(name, interfaceClass);
        final CompletionInvocationHandler cih = new CompletionInvocationHandler(timeoutScheduler, name, interfaceClass, transportInvocationHandler, methodTimeoutMilliseconds);
        return createProxy(interfaceClass, cih);
    }

    private <T> T createProxy(Class<T> interfaceClass, final CompletionInvocationHandler cih) {
        // TODO not sure we need the new InvocationHandler here, isn't the CompletionInvocationHandler an InvocationHandler?
        return (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                new InvocationHandler() {
                    @Override
                    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
                        return cih.invoke(proxy, method, args);
                    }
                });
    }

    private <T> TransportInvocationHandler createTransportInvocationHandler(EndpointName name, Class<T> interfaceClass) {
        // TODO generally, how does a remote client know that a named interface exists?
        synchronized (implementations) {
            final NamedInterface namedInterface = new NamedInterface(name, interfaceClass);
            if (!implementations.containsKey(namedInterface)) {
                throw new RegistrationException("The EndpointName '" + name + "' is not registered");
            }

            return new NullTransportInvocationHandler(executor, implementations.get(namedInterface));
        }
    }

    public void start() {
        timeoutScheduler.start();
    }

    public void stop() {
        if (timeoutScheduler.isStarted()) {
            timeoutScheduler.stop();
        }
    }
}
