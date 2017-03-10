package org.devzendo.zarjaz.reflect;

import org.devzendo.zarjaz.transport.EndpointName;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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
public class TestInvocationHashGenerator {
    private static final EndpointName endpointName = new EndpointName("sampleInterface");

    private interface SampleInterface {
        void firstMethod(int integer, boolean bool, String string);
        int secondMethod(int integer, boolean bool, String string);
        String thirdMethod();
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private InvocationHashGenerator gen = new DefaultInvocationHashGenerator();

    // MD5 is 16 bytes (128 bits), and when the MessageDigest is constructed, the digest is reset.

    private interface EmptyInterface {
        // nothing
    }
    @Test
    // not sure about this - what's the point of an empty method map/interface?
    // the validator will throw it out.
    public void generateEmptyMapForEmptyInterface() throws NoSuchMethodException {
        final Map<Method, byte[]> map = gen.generate(endpointName, EmptyInterface.class);
        assertThat(map.size(), equalTo(0));
    }

    @Test
    public void throwOnNullInterface() throws NoSuchMethodException {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Cannot generate hashes for 'null");
        gen.generate(endpointName, null);
    }

    @Test
    public void nullEndpointNameNotAllowed() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Cannot generate hashes for endpoint name 'null");
        gen.generate(null, SampleInterface.class);
    }

    @Test
    public void generateHashes() throws NoSuchMethodException {
        final Map<Method, byte[]> map = gen.generate(endpointName, SampleInterface.class);
        assertThat(map.size(), equalTo(3));
        final Method firstMethod = SampleInterface.class.getMethod("firstMethod", int.class, boolean.class, String.class);
        final Method secondMethod = SampleInterface.class.getMethod("secondMethod", int.class, boolean.class, String.class);
        final Method thirdMethod = SampleInterface.class.getMethod("thirdMethod");
        assertThat(map.keySet(), containsInAnyOrder(firstMethod, secondMethod, thirdMethod));
        final byte[] firstMethodHash = map.get(firstMethod);
        final byte[] secondMethodHash = map.get(secondMethod);
        final byte[] thirdMethodHash = map.get(thirdMethod);
        // no collisions...
        assertThat(firstMethodHash, not(anyOf(equalTo(secondMethodHash), equalTo(thirdMethodHash))));
        assertThat(secondMethodHash, not(anyOf(equalTo(firstMethodHash), equalTo(thirdMethodHash))));
        assertThat(thirdMethodHash, not(anyOf(equalTo(firstMethodHash), equalTo(secondMethodHash))));
    }

    @Test
    public void differentEndpointNamesGenerateDifferentHashesForSameMethods() throws NoSuchMethodException {
        final Method firstMethod = SampleInterface.class.getMethod("firstMethod", int.class, boolean.class, String.class);

        final Map<Method, byte[]> endpoint1Map = gen.generate(endpointName, SampleInterface.class);
        final byte[] firstMethodHash = endpoint1Map.get(firstMethod);

        final EndpointName endpoint2Name = new EndpointName("sampleInterfaceWithDifferentName");
        final InvocationHashGenerator gen2 = new DefaultInvocationHashGenerator();
        final Map<Method, byte[]> endpoint2Map = gen2.generate(endpoint2Name, SampleInterface.class);
        final byte[] firstMethodHash2 = endpoint2Map.get(firstMethod);

        assertThat(firstMethodHash, not(equalTo(firstMethodHash2)));
    }

    private interface DerivedSampleInterface extends SampleInterface {
        void fourthMethod();
    }
    @Test
    public void clientInterfacesAreReflectedUponWithInheritance() throws NoSuchMethodException {
        final Method fourthMethod = DerivedSampleInterface.class.getMethod("fourthMethod");

        final Map<Method, byte[]> endpointMap = gen.generate(endpointName, DerivedSampleInterface.class);
        assertThat(endpointMap.keySet().size(), equalTo(4));
        final byte[] fourthMethodHash = endpointMap.get(fourthMethod);
        assertThat(fourthMethodHash, not(equalTo(null)));
    }
}
