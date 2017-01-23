package org.devzendo.zarjaz.protocol;

import org.devzendo.zarjaz.reflect.DefaultInvocationHashGenerator;
import org.devzendo.zarjaz.reflect.InvocationHashGenerator;
import org.devzendo.zarjaz.transport.EndpointName;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

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
public class TestInvocationCodec {

    private static final byte[] fixedHash = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    private InvocationCodec codec = new DefaultInvocationCodec();

    private interface SampleInterface {
        void firstMethod(int integer, boolean bool, String string);
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void detectsHashExistence() throws NoSuchMethodException {
        final EndpointName endpointName = new EndpointName("endpoint");
        final InvocationHashGenerator gen = new DefaultInvocationHashGenerator(endpointName);
        final Map<Method, byte[]> methodMap = gen.generate(SampleInterface.class);

        assertThat(codec.registerHashes(endpointName, SampleInterface.class, methodMap), equalTo(Optional.empty()));

        final Optional<InvocationCodec.EndpointInterfaceMethod> secondRegistration = codec.registerHashes(endpointName, SampleInterface.class, methodMap);
        assertThat(secondRegistration, not(equalTo(Optional.empty())));
        final InvocationCodec.EndpointInterfaceMethod collision = secondRegistration.get();
        assertThat(collision.toString(), equalTo("Endpoint 'endpoint', Client interface 'SampleInterface', Method 'firstMethod'"));
    }
}
