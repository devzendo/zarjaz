package org.devzendo.zarjaz.nio;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

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
public class TestPhantomByteBuffers {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void cannotWriteToFlippedWritableByteBuffer() {
        final WritableByteBuffer buffer = DefaultWritableByteBuffer.allocate(10);
        buffer.put((byte) 0x01);

        thrown.expect(IllegalStateException.class);
        buffer.flip();

        buffer.put((byte) 0x02);
    }
}
