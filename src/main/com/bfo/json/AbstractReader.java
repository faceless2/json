package com.bfo.json;

import java.util.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;

/**
 * The base reader class for any Json file formats
 */
public abstract class AbstractReader {
    
    private Object source;
    private Charset charset;
    private Object input;
    private boolean draining = true , partial = false, lineNumberTracking = false;
    private CodingErrorAction codingErrorAction = CodingErrorAction.REPLACE;

    public abstract boolean hasNext() throws IOException;

    public abstract JsonStream.Event next() throws IOException;

    void setSource(CharSource source) {
        throw new IllegalArgumentException("Not character based");
    }

    void setSource(ByteSource source) {
        throw new IllegalArgumentException("Not byte based");
    }

    public String toString() {
        return "{JsonReader " + source + "}";
    }

    final boolean isFinal() {
        return !partial;
    }
    final boolean isDraining() {
        return draining;
    }

    /**
     * Indicate that the current input is the final input.
     * This can be set after the read is started. For situations
     * where the end of a file format is ambiguous this is required.
     * Currently that is just Json, where the top-level item is a
     * number, true, false or null.
     */
    public AbstractReader setFinal() {
        partial = false;
        notifyUpdated();
        return this;
    }

    /**
     * Indicate that the input may be followed by more input when it runs out.
     * Reading cannot be changed from final to partial after the read has started.
     */
    public AbstractReader setPartial() {
        if (source != null && !partial) {
             System.out.println("Cannot be set to partial mode after read has started");
        }
        partial = true;
        notifyUpdated();
        return this;
    }

    /**
     * Indicate that after the read completes, the reader should verify there is no
     * more content following the input. 
     */
    public AbstractReader setDraining() {
        draining = true;
        notifyUpdated();
        return this;
    }

    public AbstractReader setNonDraining() {
        draining = false;
        notifyUpdated();
        return this;
    }

    public AbstractReader setCodingErrorAction(CodingErrorAction action) {
        codingErrorAction = action;
        notifyUpdated();
        return this;
    }

    CodingErrorAction getCodingErrorAction() {
        return codingErrorAction;
    }

    public AbstractReader setLineCounting() {
        lineNumberTracking = true;
        notifyUpdated();
        return this;
    }

    public AbstractReader setNoLineCounting() {
        lineNumberTracking = false;
        notifyUpdated();
        return this;
    }
    boolean isLineCounting() {
        return lineNumberTracking;
    }

    public AbstractReader setCharset(Charset charset) {
        if (charset == null) {
            this.charset = charset;
        }
        return this;
    }

    Charset getCharset() {
        return charset;
    }

    static Charset sniffCharset(ByteBuffer bbuf) {
        Charset charset = StandardCharsets.UTF_8;
        if (bbuf.remaining() >= 3) {
            int pos = bbuf.position();
            int b0 = bbuf.get() & 0xFF;
            int b1 = bbuf.get() & 0xFF;
            int b2 = bbuf.get() & 0xFF;
            if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
                charset = StandardCharsets.UTF_8;
            } else if (b0 == 0xFE && b1 == 0xFF) {
                charset = StandardCharsets.UTF_16BE;
                bbuf.position(pos + 2);
            } else if (b0 == 0xFF && b1 == 0xFE) {
                charset = StandardCharsets.UTF_16LE;
                bbuf.position(pos + 2);
            } else if (b0 == 0 && b1 >= 0x20 && b1 <= 0x7f) {
                charset = StandardCharsets.UTF_16BE;
                bbuf.position(pos);
            } else if (b1 == 0 && b0 >= 0x20 && b0 <= 0x7f) {
                charset = StandardCharsets.UTF_16LE;
                bbuf.position(pos);
            } else {
                bbuf.position(pos);
            }
        }
        return charset;
    }

    public long getByteNumber() {
        return source instanceof ByteSource ? ((ByteSource)source).getByteNumber() : ((CharSource)source).getByteNumber();
    }

    public long getCharNumber() {
        return source instanceof CharSource ? ((CharSource)source).getCharNumber() : 0;
    }

    public long getLineNumber() {
        return source instanceof CharSource ? ((CharSource)source).getLineNumber() : 0;
    }

    public long getColumnNumber() {
        return source instanceof CharSource ? ((CharSource)source).getColumnNumber() : 0;
    }

    public void close() throws IOException {
        if (input instanceof Closeable) {
            ((Closeable)input).close();
        }
    }

    public AbstractReader setInput(InputStream in) {
        if (in == null) {
            throw new IllegalArgumentException("Input is null");
        }
        input = in;
        notifyUpdated();
        return this;
    }

    public AbstractReader setInput(ReadableByteChannel in) {
        if (in == null) {
            throw new IllegalArgumentException("Input is null");
        }
        input = in;
        notifyUpdated();
        return this;
    }

    public AbstractReader setInput(ByteBuffer in) {
        if (in == null) {
            throw new IllegalArgumentException("Input is null");
        }
        input = in;
        notifyUpdated();
        return this;
    }

    public AbstractReader setInput(Readable in) {
        if (in == null) {
            throw new IllegalArgumentException("Input is null");
        }
        input = in;
        notifyUpdated();
        return this;
    }

    public AbstractReader setInput(CharSequence in) {
        if (in == null) {
            throw new IllegalArgumentException("Input is null");
        }
        input = in;
        notifyUpdated();
        return this;
    }

    public boolean write(JsonStream out) throws IOException {
        while (hasNext()) {
            JsonStream.Event e = next();
            if (out.event(e)) {
                return true;
            }
        }
        return false;
    }

    void notifyUpdated() {
    }

    boolean requestCharSource() throws IOException {
        if (input == null) {
            return false;
        }
        if (source instanceof CharSourceByteBufferSource) {
            // We have to append specially because we may have unread bytes that are mid-character
            CharSourceByteBufferSource charsource = (CharSourceByteBufferSource)source;
            if (input instanceof InputStream) {
                charsource.setInput((InputStream)input);
            } else if (input instanceof ReadableByteChannel) {
                charsource.setInput((ReadableByteChannel)input);
            } else if (input instanceof ByteBuffer) {
                charsource.setInput((ByteBuffer)input);
            } else {
                throw new UnsupportedOperationException("Unable to append \"" + input.getClass().getName() + "\" to existing input");
            }
            setSource(charsource);
            input = null;
        } else {
            CharSource charsource = null;
            if (input instanceof String) {
                charsource = new CharSourceString(this, (String)input);
            } else if (input instanceof CharBuffer) {
                charsource = new CharSourceCharBuffer(this, (CharBuffer)input);
            } else if (input instanceof CharSequence) {
                charsource = new CharSourceCharBuffer(this, CharBuffer.wrap((CharSequence)input));
            } else if (input instanceof Readable) {
                charsource = new CharSourceReadable(this, (Readable)input);
            } else if (input instanceof InputStream) {
                charsource = new CharSourceByteBufferSource(this, (InputStream)input);
            } else if (input instanceof ReadableByteChannel) {
                charsource = new CharSourceByteBufferSource(this, (ReadableByteChannel)input);
            } else if (input instanceof ByteBuffer) {
                charsource = new CharSourceByteBufferSource(this, (ByteBuffer)input);
            }
            if (charsource == null) {
                throw new UnsupportedOperationException("Unsupported source \"" + input.getClass().getName() + "\"");
            }
            if (source != null) {
                try {
                    int available = ((CharSource)source).available();
                    if (available > 0) {
                        // If the existing source has chars left (probably only one), create a union
                        // When the union completes the first half, CharSourceUnion calls setSource
                        // to replace itself with the second half
                        charsource = new CharSourceUnion(this, (CharSource)source, charsource);
                    }
                } catch (IOException e) {
                    // Not really going to happen?
                }
            }
            source = charsource;
            setSource(charsource);
        }
        input = null;
        return true;
    }

    boolean requestByteSource() throws IOException {
        if (input == null) {
            return false;
        }
        if (source instanceof ByteSourceByteBuffer) {
            ByteSourceByteBuffer bytesource = (ByteSourceByteBuffer)source;
            if (input instanceof ReadableByteChannel) {
                bytesource.setInput((ReadableByteChannel)input);
            } else if (input instanceof ByteBuffer) {
                bytesource.setInput((ByteBuffer)input);
            } else if (input instanceof InputStream) {
//                bytesource.setInput((InputStream)input);
            } else {
                throw new UnsupportedOperationException("Unable to append \"" + input.getClass().getName() + "\" to existing input");
            }
            setSource(bytesource);
        } else if (source instanceof ByteSourceInputStream) {
            ByteSourceInputStream bytesource = (ByteSourceInputStream)source;
            if (input instanceof InputStream) {
                bytesource.setInput((InputStream)input);
            } else {
                throw new UnsupportedOperationException("Unable to append \"" + input.getClass().getName() + "\" to existing input");
            }
        } else if (source != null) {
            throw new UnsupportedOperationException("Unable to append \"" + input.getClass().getName() + "\" to existing input");
        } else {
            ByteSource bytesource = null;
            if (input instanceof ByteBuffer) {
                bytesource = new ByteSourceByteBuffer(this, (ByteBuffer)input);
            } else if (input instanceof ReadableByteChannel) {
                bytesource = new ByteSourceByteBuffer(this, (ReadableByteChannel)input);
            } else if (input instanceof InputStream) {
                bytesource = new ByteSourceInputStream(this, (InputStream)input);
            }
            if (bytesource == null) {
                throw new UnsupportedOperationException("Unsupported source \"" + input.getClass().getName() + "\"");
            }
            source = bytesource;
            setSource(bytesource);
        }
        input = null;
        return true;
    }

}
