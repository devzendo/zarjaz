package org.devzendo.zarjaz.transport;

import org.devzendo.zarjaz.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.validation.ClientInterfaceValidator;
import org.devzendo.zarjaz.validation.ServerImplementationValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class TransceiverTransport extends AbstractTransport implements Transport {
    private static final Logger logger = LoggerFactory.getLogger(TransceiverTransport.class);

    public TransceiverTransport(ServerImplementationValidator serverImplementationValidator, ClientInterfaceValidator clientInterfaceValidator, TimeoutScheduler timeoutScheduler) {
        this(serverImplementationValidator, clientInterfaceValidator, timeoutScheduler, "transceiver");
    }

    public TransceiverTransport(ServerImplementationValidator serverImplementationValidator, ClientInterfaceValidator clientInterfaceValidator, TimeoutScheduler timeoutScheduler, String transportName) {
        super(serverImplementationValidator, clientInterfaceValidator, timeoutScheduler, transportName);
    }

    @Override
    public <T> T createClientProxy(EndpointName name, Class<T> interfaceClass, long methodTimeoutMilliseconds) {
        return null;
    }

    @Override
    public void start() {
        super.start();
        // TODO start transceiver?
    }

    @Override
    public void stop() {
        // TODO stop transceiver?
        super.stop();
    }
}
