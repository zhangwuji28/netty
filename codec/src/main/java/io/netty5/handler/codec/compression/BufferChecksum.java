/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec.compression;

import static java.util.Objects.requireNonNull;

import io.netty5.buffer.ByteBuf;
import io.netty5.util.ByteProcessor;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/**
 * {@link Checksum} implementation which can directly act on a {@link Buffer}.
 *
 * Implementations may optimize access patterns depending on if the {@link Buffer} is backed by a
 * byte array or not.
 */
abstract class BufferChecksum implements Checksum {

    private final ByteProcessor updateProcessor = value -> {
        update(value);
        return true;
    };

    static BufferChecksum wrapChecksum(Checksum checksum) {
        requireNonNull(checksum, "checksum");
        if (checksum instanceof BufferChecksum) {
            return (BufferChecksum) checksum;
        }
        if (checksum instanceof Adler32) {
            return new OptimizedByteBufChecksum<Adler32>((Adler32) checksum) {
                @Override
                public void update(ByteBuffer b) {
                    checksum.update(b);
                }
            };
        }
        if (checksum instanceof CRC32) {
            return new OptimizedByteBufChecksum<CRC32>((CRC32) checksum) {
                @Override
                public void update(ByteBuffer b) {
                    checksum.update(b);
                }
            };
        }
        return new SlowByteBufChecksum<>(checksum);
    }

    /**
     * @see #update(byte[], int, int)
     */
    public void update(Buffer b, int off, int len) {
        if (b.hasArray()) {
            update(b.array(), b.arrayOffset() + off, len);
        } else {
            b.forEachByte(off, len, updateProcessor);
        }
    }

    private abstract static class OptimizedByteBufChecksum<T extends Checksum> extends SlowByteBufChecksum<T> {
        OptimizedByteBufChecksum(T checksum) {
            super(checksum);
        }

        @Override
        public void update(Buffer b, int off, int len) {
            if (b.hasArray()) {
                update(b.array(), b.arrayOffset() + off, len);
            } else {
                try {
                    update(CompressionUtil.safeNioBuffer(b, off, len));
                } catch (Throwable cause) {
                    throw new Error();
                }
            }
        }

        public abstract void update(ByteBuffer b);
    }

    private static class SlowByteBufChecksum<T extends Checksum> extends BufferChecksum {

        protected final T checksum;

        SlowByteBufChecksum(T checksum) {
            this.checksum = checksum;
        }

        @Override
        public void update(int b) {
            checksum.update(b);
        }

        @Override
        public void update(byte[] b, int off, int len) {
            checksum.update(b, off, len);
        }

        @Override
        public long getValue() {
            return checksum.getValue();
        }

        @Override
        public void reset() {
            checksum.reset();
        }
    }
}
