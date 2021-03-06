package org.devzendo.zarjaz.transport;

import org.devzendo.commoncode.concurrency.DaemonThreadFactory;
import org.devzendo.commoncode.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.reflect.CompletionInvocationHandler;
import org.devzendo.zarjaz.reflect.CompletionMultipleReturnInvoker;
import org.devzendo.zarjaz.validation.ClientInterfaceValidator;
import org.devzendo.zarjaz.validation.ServerImplementationValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
public abstract class AbstractTransport {
    private static final Logger logger = LoggerFactory.getLogger(AbstractTransport.class);

    protected final ServerImplementationValidator serverImplementationValidator;
    protected final ClientInterfaceValidator clientInterfaceValidator;
    protected final TimeoutScheduler timeoutScheduler;
    protected final ThreadPoolExecutor executor;
    protected final Set<NamedInterface> registeredEndpointInterfaces = new HashSet<>();
    final Map<NamedInterface, Object> implementations = new HashMap<>();

    public AbstractTransport(final ServerImplementationValidator serverImplementationValidator, final ClientInterfaceValidator clientInterfaceValidator, final TimeoutScheduler timeoutScheduler, final String transportName) {
        this.serverImplementationValidator = serverImplementationValidator;
        this.clientInterfaceValidator = clientInterfaceValidator;
        this.timeoutScheduler = timeoutScheduler;
        this.executor = new ThreadPoolExecutor(5, 10, 2000L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(10),
                new DaemonThreadFactory("zarjaz-" + transportName + "-transport-invoker-thread-"));
    }

    public final <T> void registerServerImplementation(final EndpointName name, final Class<T> interfaceClass, final T implementation) {
        logger.info("Registering server implementation of " + name + " with interface " + interfaceClass.getName());
        clientInterfaceValidator.validateClientInterface(interfaceClass);
        serverImplementationValidator.validateServerImplementation(interfaceClass, implementation);

        registerTransportRequestDispatcher(name, interfaceClass);

        synchronized (implementations) {
            final NamedInterface namedInterface = new NamedInterface(name, interfaceClass);
            if (implementations.containsKey(namedInterface)) {
                throw new RegistrationException("The EndpointName '" + name + "' is already registered");
            }

            implementations.put(namedInterface, implementation);
        }
    }

    public final <T> T getImplementation(final NamedInterface namedInterface) {
        synchronized (implementations) {
            return (T) implementations.get(namedInterface);
        }
    }

    protected abstract <T> void registerTransportRequestDispatcher(final EndpointName endpointName, final Class<T> interfaceClass);

    private <T> void registerClientEndpointInterface(final EndpointName endpointName, final Class<T> interfaceClass) {
        final NamedInterface reg = new NamedInterface(endpointName, interfaceClass);
        synchronized (registeredEndpointInterfaces) {
            if (registeredEndpointInterfaces.contains(reg)) {
                return;
            }
            registeredEndpointInterfaces.add(reg);
        }
    }

    public final <T> T createClientProxy(final EndpointName endpointName, final Class<T> interfaceClass, final long defaultMethodTimeoutMilliseconds) {
        // TODO validate for nulls
        logger.info("Creating client proxy of " + endpointName + " with interface " + interfaceClass.getName() + " with default method timeout " + defaultMethodTimeoutMilliseconds + "ms");
        clientInterfaceValidator.validateClientInterface(interfaceClass);

        registerClientEndpointInterface(endpointName, interfaceClass);

        final TransportInvocationHandler transportInvocationHandler = createTransportInvocationHandler(endpointName, interfaceClass, defaultMethodTimeoutMilliseconds);
        final CompletionInvocationHandler cih = new CompletionInvocationHandler(timeoutScheduler, endpointName, interfaceClass, transportInvocationHandler, defaultMethodTimeoutMilliseconds);
        return (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                cih);
    }

    // TODO can have client proxy or multiple return, but not both at the moment. because of the exception in registerClientEndpointInterface
    public <T> MultipleReturnInvoker<T> createClientMultipleReturnInvoker(final EndpointName endpointName, final Class<T> interfaceClass, final long defaultMethodTimeoutMilliseconds) {
        // TODO validate for nulls
        logger.info("Creating client multiple return invoker for " + endpointName + " with interface " + interfaceClass.getName() + " with default method timeout " + defaultMethodTimeoutMilliseconds + "ms");
        clientInterfaceValidator.validateClientInterface(interfaceClass);
        validateMultipleReturn();

        registerClientEndpointInterface(endpointName, interfaceClass);

        final TransportInvocationHandler transportInvocationHandler = createTransportInvocationHandler(endpointName, interfaceClass, defaultMethodTimeoutMilliseconds);
        return new CompletionMultipleReturnInvoker(timeoutScheduler, endpointName, interfaceClass, transportInvocationHandler, defaultMethodTimeoutMilliseconds);
    }

    protected abstract void validateMultipleReturn();

    protected abstract <T> TransportInvocationHandler createTransportInvocationHandler(final EndpointName endpointName, final Class<T> interfaceClass, final long methodTimeoutMilliseconds);

    protected boolean hasClientProxiesBound() {
        synchronized (registeredEndpointInterfaces) {
            return registeredEndpointInterfaces.size() != 0;
        }
    }

    protected boolean hasServerImplementationsBound() {
        synchronized (implementations) {
            return implementations.size() != 0;
        }
    }

    public void start() {
        if (!hasClientProxiesBound() && !hasServerImplementationsBound()) {
            throw new IllegalStateException("No clients or server implementations bound");
        }
        timeoutScheduler.start();
    }

    public void stop() {
        if (timeoutScheduler.isStarted()) {
            timeoutScheduler.stop();
        }
    }
}
