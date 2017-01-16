package org.devzendo.zarjaz.protocol;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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
public class TestProtocol {
    @Test
    public void unknownProtocolFrame() {
        final Optional<Protocol.InitialFrameType> opt = Protocol.InitialFrameType.decodeInitialFrameType((byte) 127);// does not exist currently, unlikely to do for some time
        assertThat(opt.isPresent(), is(false));
    }

    @Test
    public void knownProtocolFrame() {
        final Optional<Protocol.InitialFrameType> opt = Protocol.InitialFrameType.decodeInitialFrameType(Protocol.InitialFrameType.EXTENSION.getInitialFrameType());
        // this form (if ispresent / get) shuts IntelliJ's warning up.
        if (opt.isPresent()) {
            assertThat(opt.get(), Matchers.equalTo(Protocol.InitialFrameType.EXTENSION));
        } else {
            Assert.fail("Should not be empty");
        }
    }
}
