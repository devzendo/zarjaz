package org.devzendo.zarjaz.validation;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

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
public class TestClientInterfaceValidation {
    final ClientInterfaceValidator validator = new DefaultClientInterfaceValidator();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private interface EmptyInterface {
        // nothing
    }
    @Test
    public void mustDetectLackOfMethods() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Client interfaces must contain methods");

        validator.validateClientInterface(EmptyInterface.class);
    }

    @Test
    public void nullInterfaceDisallowed() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Client interfaces must not be null");

        validator.validateClientInterface(null);
    }

    private interface SampleInterface {
        void firstMethod(int integer, boolean bool, String string);
        int secondMethod(int integer, boolean bool, String string);
        String thirdMethod();
    }
    @Test
    public void happyPath() {
        validator.validateClientInterface(SampleInterface.class);
        // no exception thrown
    }

    private interface DerivedSampleInterface extends TestClientInterfaceValidation.SampleInterface {
        // intentionally empty to check that getMethods, not getDerivedMethods, is used.
    }
    @Test
    public void interfacesCanBeInheritedFrom() {
        validator.validateClientInterface(DerivedSampleInterface.class);
        // no exception thrown
    }
}
