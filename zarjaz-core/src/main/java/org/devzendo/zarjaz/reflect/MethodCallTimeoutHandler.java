package org.devzendo.zarjaz.reflect;

import org.devzendo.zarjaz.transport.EndpointName;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

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
@FunctionalInterface
public interface MethodCallTimeoutHandler {
    /**
     * The timeout for this endpoint method has triggered. Process this appropriately by setting the future
     * (with a timeout exception for single return methods, or null for multiple return methods). Tidy up as
     * necessary.
     * @param future the future on which the client is blocking waiting for this method to complete.
     * @param endpointName the endpoint name under which this method is registered
     * @param method the method that has timed out.
     */
    void timeoutOccurred(CompletableFuture<Object> future, EndpointName endpointName, Method method);
}
