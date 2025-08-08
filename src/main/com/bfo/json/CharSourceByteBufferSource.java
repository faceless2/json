package com.bfo.json;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;

class CharSourceByteBufferSource extends CharSourceCharBuffer {

    private static final int BUFSIZE = 8;
    private final AbstractReader owner;
    private ByteBuffer bbuf;
    private Object source;
    private CharsetDecoder decoder;

    CharSourceByteBufferSource(AbstractReader owner, InputStream in) {
        super(owner, (CharBuffer)CharBuffer.allocate(BUFSIZE).position(BUFSIZE));
        this.owner = owner;
        this.bbuf = (ByteBuffer)ByteBuffer.allocate(BUFSIZE).mark().limit(0);
        this.source = in;
    }

    CharSourceByteBufferSource(AbstractReader owner, ReadableByteChannel in) {
        super(owner, (CharBuffer)CharBuffer.allocate(BUFSIZE).position(BUFSIZE));
        this.owner = owner;
        this.bbuf = (ByteBuffer)ByteBuffer.allocate(BUFSIZE).mark().limit(0);
        this.source = in;
    }

    /**
     * Read from the supplied buffer directly, do not copy it
     */
    CharSourceByteBufferSource(AbstractReader owner, ByteBuffer in) {
        super(owner, (CharBuffer)CharBuffer.allocate(owner.isFinal() ? in.remaining() : BUFSIZE).position(owner.isFinal() ? in.remaining() : BUFSIZE));
        this.owner = owner;
        if (owner.isFinal()) {
            this.bbuf = in;
        } else {
            this.bbuf = (ByteBuffer)ByteBuffer.allocate(BUFSIZE).mark().limit(0);
            this.source = in;
        }
    }

    /**
     * These next three methods will add a new source. The presumption is that
     * reading from the existing source has hit EOF.
     */
    void setInput(InputStream in) {
        this.source = in;
    }
    void setInput(ReadableByteChannel in) {
        this.source = in;
    }
    void setInput(ByteBuffer in) {
        if (source == null) {
            throw new IllegalStateException("Can't reset final buffer");
        }
        this.source = in;
//        System.out.println("SETINPUT: src=[pos="+in.position()+" lim="+in.limit()+"] " + in);
    }

    /**
     * Fill the buffer (possibly).
     * On entry, position is where to read to and limit=capacity (probably)
     * On exit, position is set to 0 and limit is set to the end of the read data (or limit=position if nothing read)
     * @return true if bytes were added
     */
    boolean fill(ByteBuffer bbuf) throws IOException {
        bbuf.position(bbuf.limit());
        bbuf.limit(bbuf.capacity());
        int pos = bbuf.position();
        if (source instanceof InputStream) {
            int l = ((InputStream)source).read(bbuf.array(), bbuf.arrayOffset() + bbuf.position(), bbuf.remaining());
            bbuf.limit(bbuf.position() + (l < 0 ? 0 : l));
        } else if (source instanceof ReadableByteChannel) {
            ((ReadableByteChannel)source).read(bbuf);
            bbuf.limit(bbuf.position()).position(pos);
        } else if (source instanceof ByteBuffer) {
            ByteBuffer srcbuf = (ByteBuffer)source;
            int len = Math.min(bbuf.remaining(), srcbuf.remaining());
            int oldlimit = srcbuf.limit();
            if (len < srcbuf.remaining()) {
                srcbuf.limit(srcbuf.position() + len);
            }
//            System.out.println("    fill: copy min(" + bbuf.remaining()+","+srcbuf.remaining() + ") bytes from " + srcbuf.position()+" to " + bbuf.position());
            bbuf.put(srcbuf);
            srcbuf.limit(oldlimit);
            bbuf.limit(bbuf.position()).position(pos);
        }
        bbuf.position(0);
        return bbuf.limit() > pos;
    }

    @Override boolean nextBuffer() throws IOException {
        final CharBuffer cbuf = getBuffer();
//        System.out.println("nextBuffer: bbuf="+bbuf+" cbuf=[pos="+cbuf.position()+" lim="+cbuf.limit()+"]");
        final int shift = cbuf.position();
        if (cbuf.remaining() > 0 && shift > 0) {
//            System.out.println("  copy "+cbuf.remaining()+" chars from "+cbuf.position()+" to 0, set new cbuf position to " + cbuf.remaining());
            System.arraycopy(cbuf.array(), cbuf.arrayOffset() + shift, cbuf.array(), cbuf.arrayOffset(), cbuf.remaining());
            cbuf.position(cbuf.remaining()).limit(cbuf.capacity());
        } else {
            cbuf.clear();
        }
        cbuf.limit(cbuf.capacity());
        // Try and read at least one character. This may involve reading more than one byte
        // if we have a "source", on input bbuf mark is head of queue and position is end of queue.
        boolean eof = false;
        final int cpos0 = cbuf.position();
        do {
            eof = source == null || !fill(bbuf);
//            System.out.println("  postfill: bbuf=" + bbuf + " "+Json.hex(bbuf));
            Charset charset = owner.getCharset();
            if (charset == null) {
                owner.setCharset(charset = AbstractReader.sniffCharset(bbuf));
            }
            if (decoder == null) {
                decoder = charset.newDecoder();
                decoder.onMalformedInput(owner.getCodingErrorAction());
                decoder.onUnmappableCharacter(owner.getCodingErrorAction());
            }
            int cpos1 = cbuf.position();
            if (decoder.decode(bbuf, cbuf, eof && owner.isFinal()).isError()) {
                throw new IOException("Malformed " + charset);
            }
//            System.out.println("  postcvt: bbuf="+bbuf+" cbuf initpos was " + cpos0+" now [pos="+cbuf.position()+" lim="+cbuf.limit()+"] eof="+eof+"&&"+owner.isFinal());
        } while (cbuf.position() == cpos0 && !eof);     // Continue while nothing is read and !eof
        if (false && cbuf.position() == cpos0) {
            // Nothing was read, position bbuf at the end
            bbuf.position(bbuf.limit());
        } else if (source != null) {
            // Move remaining bytes from end to start for next pass
            int len = bbuf.remaining();
//            System.out.println("  copy "+len+" bytes from "+bbuf.position()+" to 0, set limit/pos to " + len);
            System.arraycopy(bbuf.array(), bbuf.arrayOffset() + bbuf.position(), bbuf.array(), bbuf.arrayOffset(), len);
            bbuf.position(len).limit(len);
        }
        cbuf.limit(cbuf.position()).position(cpos0);
        setBuffer(cbuf, shift);
        return cbuf.hasRemaining();
    }

}
