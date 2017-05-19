package org.devzendo.zarjaz.reflect;

import org.devzendo.zarjaz.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.transport.EndpointName;
import org.devzendo.zarjaz.transport.TransportInvocationHandler;

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
public class AbstractCompletionHandler<T> {
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
}
