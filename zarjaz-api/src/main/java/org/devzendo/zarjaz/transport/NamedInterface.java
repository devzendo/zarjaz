package org.devzendo.zarjaz.transport;

/**
 * Copyright (C) 2008-2015 Matt Gumbley, DevZendo.org <http://devzendo.org>
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
public class NamedInterface <T> {
    private final EndpointName endpointName;
    private final Class<T> interfaceClass;

    public NamedInterface(final EndpointName endpointName, final Class<T> interfaceClass) {
        this.endpointName = endpointName;
        this.interfaceClass = interfaceClass;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!NamedInterface.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        final NamedInterface other = (NamedInterface) obj;
        if (endpointName == null) {
            if (other.endpointName != null) {
                return false;
            }
        } else if (!endpointName.equals(other.endpointName)) {
            return false;
        }
        if (interfaceClass == null) {
            if (other.interfaceClass != null) {
                return false;
            }
        } else if (!interfaceClass.equals(other.interfaceClass)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((endpointName == null) ? 0 : endpointName.hashCode());
        result = prime * result + ((interfaceClass == null) ? 0 : interfaceClass.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return endpointName.toString() + " => " + interfaceClass.getSimpleName();
    }
}
