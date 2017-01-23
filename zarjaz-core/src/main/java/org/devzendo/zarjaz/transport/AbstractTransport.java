package org.devzendo.zarjaz.transport;

import org.devzendo.zarjaz.concurrency.DaemonThreadFactory;
import org.devzendo.zarjaz.reflect.CompletionInvocationHandler;
import org.devzendo.zarjaz.timeout.TimeoutScheduler;
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

    static protected Class[] objectsToClasses(final Object[] args) {
        if (args == null) {
            return null;
        }
        final Class[] out = new Class[args.length];
        for (int i=0; i<args.length; i++) {
            out[i] = args[i].getClass();
        }
        return out;
    }

    static protected String joinedClassNames(final Class[] argClasses) {
        final StringBuilder sb = new StringBuilder();
        sb.append('(');
        if (argClasses != null) {
            for (int i=0; i<argClasses.length; i++) {
                sb.append(argClasses[i].getSimpleName());
                if (i != (argClasses.length - 1)) {
                    sb.append(", ");
                }
            }

        }
        sb.append(')');
        return sb.toString();
    }

    static protected String[] classesToClassNames(final Class[] argClasses) {
        if (argClasses == null) {
            return null;
        }
        final String[] out = new String[argClasses.length];
        for (int i=0; i<argClasses.length; i++) {
            out[i] = argClasses[i].getSimpleName();
        }
        return out;
    }

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
                new ArrayBlockingQueue<Runnable>(10),
                new DaemonThreadFactory("zarjaz-" + transportName + "-transport-invoker-thread-"));
    }

    public final <T> void registerServerImplementation(final EndpointName name, final Class<T> interfaceClass, final T implementation) {
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

    private final <T> void registerClientEndpointInterface(final EndpointName endpointName, final Class<T> interfaceClass) {
        final NamedInterface reg = new NamedInterface(endpointName, interfaceClass);
        if (registeredEndpointInterfaces.contains(reg)) {
            // TODO change to RegistrationException
            throw new IllegalArgumentException("Cannot register the same EndpointName/Client interface more than once");
        }
        registeredEndpointInterfaces.add(reg);
    }

    public final <T> T createClientProxy(final EndpointName endpointName, final Class<T> interfaceClass, final long methodTimeoutMilliseconds) {
        // TODO validate for nulls
        logger.info("Creating client proxy of " + endpointName + " with interface " + interfaceClass.getName() + " with method timeout " + methodTimeoutMilliseconds);
        clientInterfaceValidator.validateClientInterface(interfaceClass);
        registerClientEndpointInterface(endpointName, interfaceClass);

        final TransportInvocationHandler transportInvocationHandler = createTransportInvocationHandler(endpointName, interfaceClass, methodTimeoutMilliseconds);
        final CompletionInvocationHandler cih = new CompletionInvocationHandler(timeoutScheduler, endpointName, interfaceClass, transportInvocationHandler, methodTimeoutMilliseconds);
        return (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                cih);
    }

    protected abstract <T> TransportInvocationHandler createTransportInvocationHandler(final EndpointName endpointName, final Class<T> interfaceClass, final long methodTimeoutMilliseconds);

    public void start() {
        timeoutScheduler.start();
    }

    public void stop() {
        if (timeoutScheduler.isStarted()) {
            timeoutScheduler.stop();
        }
    }
}
