package org.devzendo.zarjaz.protocol;

import org.devzendo.zarjaz.transport.EndpointName;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

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
public interface InvocationCodec {

    class EndpointInterfaceMethod {
        private final EndpointName endpointName;
        private final Class<?> clientInterface;
        private final Method method;

        public EndpointInterfaceMethod(final EndpointName endpointName, final Class<?> clientInterface, final Method method) {
            this.endpointName = endpointName;
            this.clientInterface = clientInterface;
            this.method = method;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!EndpointInterfaceMethod.class.isAssignableFrom(obj.getClass())) {
                return false;
            }
            final EndpointInterfaceMethod other = (EndpointInterfaceMethod) obj;
            if (endpointName == null) {
                if (other.endpointName != null) {
                    return false;
                }
            } else if (!endpointName.equals(other.endpointName)) {
                return false;
            }
            if (clientInterface == null) {
                if (other.clientInterface != null) {
                    return false;
                }
            } else if (!clientInterface.equals(other.clientInterface)) {
                return false;
            }
            if (method == null) {
                if (other.method != null) {
                    return false;
                }
            } else if (!method.equals(other.method)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((endpointName == null) ? 0 : endpointName.hashCode());
            result = prime * result + ((clientInterface == null) ? 0 : clientInterface.hashCode());
            result = prime * result + ((method == null) ? 0 : method.hashCode());
            return result;
        }

        @Override
        public String toString() {
            return "Endpoint '" + endpointName.toString() + "', Client interface '" + clientInterface.getSimpleName() + "', Method '" + method.getName() + "'";
        }

        public EndpointName getEndpointName() {
            return endpointName;
        }

        public Class<?> getClientInterface() {
            return clientInterface;
        }

        public Method getMethod() {
            return method;
        }
    }

    /**
     * Detect any potential collisions, return details of the first collision, if there is one.
     * If no collisions, register the method hashes.
     *
     * @param endpointName
     * @param interfaceClass
     * @param methodMap the map of methods and their hashes.
     * @return an optional detected collision.
     */
    Optional<EndpointInterfaceMethod> registerHashes(EndpointName endpointName, Class<?> interfaceClass, Map<Method, byte[]> methodMap);

    /**
     * Retuan all method-hash pairs for a given endpoint/interface
     * @param endpointName
     * @param interfaceClass
     * @return the map of methods and their hashes.
     */
    Map<Method, byte[]> getMethodsToHashMap(EndpointName endpointName, Class<?> interfaceClass);
}
