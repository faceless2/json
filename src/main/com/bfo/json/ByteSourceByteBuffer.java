package com.bfo.json;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;

class ByteSourceByteBuffer implements ByteSource {

    private static final int BUFSIZE = 4;
    private Object source;
    private ByteBuffer bbuf;
    private int mark;
    private int bufstart;
    private long tell;

    ByteSourceByteBuffer(AbstractReader owner, ByteBuffer bbuf) {
        if (owner.isFinal()) {
            this.bbuf = bbuf;
            this.bufstart = bbuf.position();
            this.source = null;
        } else {
            this.bbuf = ByteBuffer.allocate(BUFSIZE).limit(0);
            this.source = bbuf;
        }
    }

    ByteSourceByteBuffer(AbstractReader owner, ReadableByteChannel input) {
        this.bbuf = ByteBuffer.allocate(BUFSIZE).limit(0);
        this.source = input;
    }

    @Override public void initializePosition(long byteNumber) {
        tell += byteNumber;
    }

    void setInput(ByteBuffer input) throws IOException {
        if (source == null) {
            throw new IllegalStateException("Final");
        }
        this.source = input;
        nextBuffer();
    }

    void setInput(ReadableByteChannel input) throws IOException {
        if (source == null) {
            throw new IllegalStateException("Final");
        }
        this.source = input;
        nextBuffer();
    }

    boolean nextBuffer() throws IOException {
        if (source != null) {
            int len = bbuf.remaining();
//            System.out.println("  copy "+len+" bytes from "+bbuf.position()+" to 0, set limit/pos to " + len);
            System.arraycopy(bbuf.array(), bbuf.arrayOffset() + bbuf.position(), bbuf.array(), bbuf.arrayOffset(), len);
            bbuf.position(len).limit(bbuf.capacity());
            if (source instanceof InputStream) {
                int l = ((InputStream)source).read(bbuf.array(), bbuf.arrayOffset() + bbuf.position(), bbuf.remaining());
                bbuf.limit(bbuf.position() + (l < 0 ? 0 : l));
            } else if (source instanceof ReadableByteChannel) {
                ((ReadableByteChannel)source).read(bbuf);
                bbuf.limit(bbuf.position());
            } else if (source instanceof ByteBuffer) {
                ByteBuffer srcbuf = (ByteBuffer)source;
                len = Math.min(bbuf.remaining(), srcbuf.remaining());
                int oldlimit = srcbuf.limit();
                if (len < srcbuf.remaining()) {
                    srcbuf.limit(srcbuf.position() + len);
                }
                bbuf.put(srcbuf);
                srcbuf.limit(oldlimit);
                bbuf.limit(bbuf.position());
            }
            bbuf.position(0);
            return bbuf.hasRemaining();
        }
        return false;
    }

    @Override public int available() throws IOException {
        return bbuf.remaining();
    }

    @Override public int get() throws IOException {
        if (!bbuf.hasRemaining() && !nextBuffer()) {
            return -1;
        }
        return bbuf.get() & 0xFF;
    }

    @Override public void mark(int len) throws IOException {
        mark = bbuf.position();
    }

    @Override public void reset() throws IOException {
        if (mark < 0) {
            throw new IOException("Expired mark");
        }
        bbuf.position(mark);
    }

    @Override public ByteBuffer get(int len) throws IOException {
        ByteBuffer dup = bbuf.duplicate().limit(bbuf.position() + len);
        bbuf.position(bbuf.position() + len);
        return dup;
    }

    @Override public long getByteNumber() {
        return bbuf.position() - bufstart;
    }

    public String toString() {
        return "{bytebuffer: next="+(bbuf.hasRemaining() ? "0x" + Integer.toHexString(bbuf.get(bbuf.position())) : "EOF") + " bbuf="+bufstart+"/"+bbuf.position()+"/"+bbuf.limit()+" b=" + Json.hex(bbuf.array(), bbuf.arrayOffset(), bbuf.limit()) + "}";
    }

}
