package org.example.learn.netty.hello.util;

import io.netty.buffer.ByteBuf;

public class ByteBufUtils {

    public static void log(String info, ByteBuf buf) {
        int readerIndex = buf.readerIndex();
        int writerIndex = buf.writerIndex();
        int capacity = buf.capacity();
        int readableBytes = buf.readableBytes();
        int writableBytes = buf.writableBytes();

        System.out.printf("%s: capacity=%d, readerIndex=%d, writerIndex=%d, readableBytes=%d, writableBytes=%d\n", info, capacity, readerIndex, writerIndex, readableBytes, writableBytes);
    }

    public static String arr(ByteBuf buf) {
        byte[] arr = buf.array();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            sb.append(Integer.toHexString((byte) arr[i]));
            if (i < arr.length - 1) {
                sb.append(" ");
            }
        }

        return sb.toString();
    }
}
