package org.devzendo.zarjaz.transport;

import org.devzendo.commoncode.timeout.DefaultTimeoutScheduler;
import org.devzendo.commoncode.timeout.TimeoutScheduler;
import org.devzendo.zarjaz.protocol.DefaultInvocationCodec;
import org.devzendo.zarjaz.protocol.InvocationCodec;
import org.devzendo.zarjaz.reflect.DefaultInvocationHashGenerator;
import org.devzendo.zarjaz.reflect.InvocationHashGenerator;
import org.devzendo.zarjaz.transceiver.Transceiver;
import org.devzendo.zarjaz.validation.DefaultClientInterfaceValidator;
import org.devzendo.zarjaz.validation.DefaultServerImplementationValidator;

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
public class TransceiverTransportFactory implements TransportFactory {

    private final DefaultServerImplementationValidator serverImplementationValidator = new DefaultServerImplementationValidator();
    private final DefaultClientInterfaceValidator clientInterfaceValidator = new DefaultClientInterfaceValidator();
    private final TimeoutScheduler timeoutScheduler = new DefaultTimeoutScheduler();
    private final InvocationCodec invocationCodec = new DefaultInvocationCodec();
    private final InvocationHashGenerator invocationHashGenerator = new DefaultInvocationHashGenerator();

    @Override
    public Transport create() {
        throw new UnsupportedOperationException("TransceiverTransportFactory cannot create Transports without a Transceiver");
    }

    @Override
    public Transport create(final Transceiver transceiver) {
        return new TransceiverTransport(serverImplementationValidator, clientInterfaceValidator, timeoutScheduler, transceiver, invocationHashGenerator, invocationCodec);
    }
}
