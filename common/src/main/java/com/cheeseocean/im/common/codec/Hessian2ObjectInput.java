package com.cheeseocean.im.common.codec;

import com.alibaba.com.caucho.hessian.io.Hessian2Input;
import org.apache.dubbo.common.serialize.Cleanable;
import org.apache.dubbo.common.serialize.hessian2.dubbo.Hessian2FactoryInitializer;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * @author xxxcrel
 * @date 2023/5/16 17:09
 */
public class Hessian2ObjectInput implements Cleanable {
    private final Hessian2Input mH2i;
    private final Hessian2FactoryInitializer hessian2FactoryInitializer;

    public Hessian2ObjectInput(InputStream is) {
        mH2i = new Hessian2Input(is);
        hessian2FactoryInitializer = Hessian2FactoryInitializer.getInstance();
        mH2i.setSerializerFactory(hessian2FactoryInitializer.getSerializerFactory());
    }

    public boolean readBool() throws IOException {
        return mH2i.readBoolean();
    }

    public byte readByte() throws IOException {
        return (byte) mH2i.readInt();
    }

    public short readShort() throws IOException {
        return (short) mH2i.readInt();
    }

    public int readInt() throws IOException {
        return mH2i.readInt();
    }

    public long readLong() throws IOException {
        return mH2i.readLong();
    }

    public float readFloat() throws IOException {
        return (float) mH2i.readDouble();
    }

    public double readDouble() throws IOException {
        return mH2i.readDouble();
    }

    public byte[] readBytes() throws IOException {
        return mH2i.readBytes();
    }

    public String readUTF() throws IOException {
        return mH2i.readString();
    }

    public Object readObject() throws IOException {
        if (!mH2i.getSerializerFactory().getClassLoader().equals(Thread.currentThread().getContextClassLoader())) {
            mH2i.setSerializerFactory(hessian2FactoryInitializer.getSerializerFactory());
        }
        return mH2i.readObject();
    }

    @SuppressWarnings("unchecked")
    public <T> T readObject(Class<T> cls) throws IOException,
            ClassNotFoundException {
        if (!mH2i.getSerializerFactory().getClassLoader().equals(Thread.currentThread().getContextClassLoader())) {
            mH2i.setSerializerFactory(hessian2FactoryInitializer.getSerializerFactory());
        }
        return (T) mH2i.readObject(cls);
    }

    public <T> T readObject(Class<T> cls, Type type) throws IOException, ClassNotFoundException {
        if (!mH2i.getSerializerFactory().getClassLoader().equals(Thread.currentThread().getContextClassLoader())) {
            mH2i.setSerializerFactory(hessian2FactoryInitializer.getSerializerFactory());
        }
        return readObject(cls);
    }

    public InputStream readInputStream() throws IOException {
        return mH2i.readInputStream();
    }

    @Override
    public void cleanup() {
        if(mH2i != null) {
            mH2i.reset();
        }
    }
}

