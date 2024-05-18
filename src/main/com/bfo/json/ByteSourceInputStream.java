package com.bfo.json;

import java.io.*;
import java.nio.*;
import java.util.*;

/**
 * A ByteSource that wraps an InputStream, and does not use
 * any intermediate buffers - when the read has finished,
 * the InputStream cursor is at the end of the data.
 */
class ByteSourceInputStream implements ByteSource {

    private static final int BUFSIZE = 8192;

    private InputStream in;
    private long tell;
    private int holdlen, holdhead, holdtail;
    private byte[] hold;

    ByteSourceInputStream(AbstractReader owner, InputStream in) {
        this.in = in;
        this.hold = new byte[BUFSIZE];
    }

    void setInput(InputStream in) {
//        System.out.println("---- NEW SOURCE: this="+this);
        this.in = in;
    }

    @Override public void initializePosition(long byteNumber) {
        tell += byteNumber;
    }

    @Override public int available() throws IOException {
        int v = (holdtail - holdhead) + in.available();
//        System.out.println("# available " + this+" = " + v);
        return v;
    }

    @Override public int get() throws IOException {
//        System.out.print("# get " + this);
        if (holdlen > 0) {
            if (holdtail == holdlen) {
                holdtail = holdhead = holdlen = 0;
            } else if (holdhead == holdtail) {
                int v = in.read();
                if (v >= 0) {
                    hold[holdtail++] = (byte)v;
                    holdhead++;
                    tell++;
                }
//                System.out.println(" = "+v);
                return v;
            } else {
                int v = hold[holdhead++] & 0xFF;
                tell++;
//                System.out.println(" = "+v);
                return v;
            }
        }
        int v = in.read();
        if (v >= 0) {
            tell++;
        }
//        System.out.println(" = "+v);
        return v;
    }

    @Override public void mark(int num) throws IOException {
//        System.out.println("# mark(" + num + ") " + this);
        // For an input [0 1 2 3 4 5 6 7 8]
        // read=0
        // mark(4)  hold=[x x x x] head=0 tail=0 len=4
        // read=1   hold=[1 x x x] head=1 tail=1 len=4
        // read=2   hold=[1 2 x x] head=2 tail=2 len=4
        // reset    hold=[1 2 x x] head=0 tail=2 len=4
        // read=1   hold=[1 2 x x] head=1 tail=2 len=4
        // mark(3)  hold=[2 x x x] head=0 tail=1 len=3
        // read=2   hold=[2 x x x] head=1 tail=1 len=3
        // read=3   hold=[2 3 x x] head=2 tail=2 len=3
        // read=4   hold=[2 3 4 x] head=3 tail=3 len=3
        // read=5   hold=[x x x x] head=0 tail=0 len=0
        //
        int rem = holdtail - holdhead;
        if (holdhead > 0) {
            System.arraycopy(hold, holdhead, hold, 0, holdtail - holdhead);
            holdtail -= holdhead;
            holdhead = 0;
        }
        if (num > hold.length) {
            hold = Arrays.copyOf(hold, num);
        }
        holdlen = num;
    }

    @Override public void reset() throws IOException {
//        System.out.println("# reset() " + this);
        if (holdlen == 0) {
            throw new IOException("Expired mark");
        }
        tell -= holdhead;
        holdhead = 0;
    }

    @Override public ByteBuffer get(int len) throws IOException {
//        System.out.print("# get(" + len + ") " + this);
        ByteBuffer buf;
        if (len > 0 && holdhead + len <= holdlen) {
            buf = ByteBuffer.wrap(hold, holdhead, len);
            int l = len - holdtail + holdhead;
            while (l > 0) {
                int q = in.read(hold, holdtail, l);
                if (q < 0) {
                    throw new EOFException();
                }
                holdtail += q;
                l -= q;
            }
            buf = ByteBuffer.wrap(hold, holdhead, len);
            holdhead += len;
        } else {
            byte[] a = new byte[len];
            int l = 0;
            if (holdhead < holdtail) {
                l = holdtail - holdhead;
                System.arraycopy(hold, holdhead, a, 0, l);
                holdtail = holdhead = holdlen = 0;
            }
            do {
                int q = in.read(a, l, a.length - l);
                if (q < 0) {
                    throw new EOFException();
                }
                l -= q;
            } while (l > 0);
            buf = ByteBuffer.wrap(a);
        }
//        System.out.println(" out="+Json.hex(buf));
        return buf;
    }

    @Override public long getByteNumber() {
        return tell;
    }

    public String toString() {
        return "{inputstream: " + (holdlen == 0 ? "}" : " hold: head=" + holdhead + " tail=" + holdtail + " buf=" + Json.hex(hold, 0, holdlen) + "}");
    }

}
