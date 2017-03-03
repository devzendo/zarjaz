package org.devzendo.zarjaz.transceiver;

import java.nio.ByteBuffer;
import java.util.List;

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
public class DataReceived implements TransceiverObservableEvent {
    private final List<ByteBuffer> buffers;
    private final Transceiver.BufferWriter replyWriter;

    public DataReceived(final List<ByteBuffer> buffers, final Transceiver.BufferWriter replyWriter) {
        this.buffers = buffers;
        this.replyWriter = replyWriter;
    }

    @Override
    public boolean isFailure() {
        return false;
    }

    @Override
    public Exception getCause() {
        throw new UnsupportedOperationException("No failure in data");
    }

    @Override
    public List<ByteBuffer> getData() {
        return buffers;
    }

    @Override
    public Transceiver.BufferWriter getReplyWriter() {
        return replyWriter;
    }
}
