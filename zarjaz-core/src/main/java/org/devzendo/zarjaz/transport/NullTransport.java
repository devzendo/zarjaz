package org.devzendo.zarjaz.transport;

import org.devzendo.zarjaz.reflect.CompletionInvocationHandler;
import org.devzendo.zarjaz.validation.ClientInterfaceValidator;
import org.devzendo.zarjaz.validation.ServerImplementationValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

    final Map<NamedInterface, Object> implementations = new HashMap<>();
    private final ServerImplementationValidator serverImplementationValidator;
    private final ClientInterfaceValidator clientInterfaceValidator;

    public NullTransport(ServerImplementationValidator serverImplementationValidator, ClientInterfaceValidator clientInterfaceValidator) {
        this.serverImplementationValidator = serverImplementationValidator;
        this.clientInterfaceValidator = clientInterfaceValidator;
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

        private final Object impl;

        public NullTransportInvocationHandler(final Object impl) {
            this.impl = impl;
        }

        @Override
        public void invoke(final Method method, final Object[] args, final CompletableFuture<Object> future) {
            String methodName = method.getName();
            final Class[] argClasses = objectsToClasses(args);
            if (logger.isDebugEnabled()) {
                // TODO would be useful to log the endpoint name here
                logger.debug("Invoking method: " + method.getReturnType().getSimpleName() + " " + methodName + joinedClassNames(argClasses));
            }
            try {
                final Method implMethod = impl.getClass().getMethod(methodName, argClasses);
                final Object returnValue = implMethod.invoke(impl, args);
                if (logger.isDebugEnabled()) {
                    logger.debug("Invocation completed with return: '" + returnValue + "'");
                }
                future.complete(returnValue);
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
            }
        }

    }

    // Plan is that the TransportInvocationHandler is the client-side part that varies here, so that this
    // createClientProxy method could be in an abstract base transport class?
    public <T> T createClientProxy(final EndpointName name, final Class<T> interfaceClass) {
        logger.info("Creating client proxy of " + name + " with interface " + interfaceClass.getName());
        // TODO validate the client interface?
        final TransportInvocationHandler transportInvocationHandler = createTransportInvocationHandler(name, interfaceClass);
        final CompletionInvocationHandler cih = new CompletionInvocationHandler(name, interfaceClass, transportInvocationHandler);
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
                return cih.invoke(proxy, method, args);
            }
        };
        T proxy = (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                handler);
        return proxy;
    }

    private <T> TransportInvocationHandler createTransportInvocationHandler(EndpointName name, Class<T> interfaceClass) {
        // TODO generally, how does a remote client know that a named interface exists?
        synchronized (implementations) {
            final NamedInterface namedInterface = new NamedInterface(name, interfaceClass);
            if (!implementations.containsKey(namedInterface)) {
                throw new RegistrationException("The EndpointName '" + name + "' is not registered");
            }

            return new NullTransportInvocationHandler(implementations.get(namedInterface));
        }
    }

    public void start() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void stop() {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
