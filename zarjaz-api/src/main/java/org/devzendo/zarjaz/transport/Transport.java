package org.devzendo.zarjaz.transport;

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
public interface Transport {

    <T> void registerServerImplementation(EndpointName name, Class<T> interfaceClass, T implementation);

    <T> T createClientProxy(EndpointName name, Class<T> interfaceClass, long methodTimeoutMilliseconds);
    // TODO need a mechanism for setting/overriding timeouts for specific methods.

    <T> MultipleReturnInvoker createClientMultipleReturnInvoker(EndpointName name, Class<T> interfaceClass, long methodTimeoutMilliSeconds);

    /**
     * Start allowing calls to server implementations.
     */
    void start();

    /**
     * Shut down all server implementations and client proxies.
     */
    void stop();

}
