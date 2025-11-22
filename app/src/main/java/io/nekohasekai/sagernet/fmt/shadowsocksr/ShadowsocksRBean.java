package io.nekohasekai.sagernet.fmt.shadowsocksr;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import moe.matsuri.nb4a.utils.JavaUtil;

public class ShadowsocksRBean extends AbstractBean {

    public String method;
    public String password;
    public String protocol;
    public String protocolParam;
    public String obfs;
    public String obfsParam;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();

        if (JavaUtil.isNullOrBlank(method)) method = "none";
        if (password == null) password = "";
        if (JavaUtil.isNullOrBlank(protocol)) protocol = "origin";
        if (protocolParam == null) protocolParam = "";
        if (JavaUtil.isNullOrBlank(obfs)) obfs = "plain";
        if (obfsParam == null) obfsParam = "";
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(0);
        super.serialize(output);
        output.writeString(method);
        output.writeString(password);
        output.writeString(protocol);
        output.writeString(protocolParam);
        output.writeString(obfs);
        output.writeString(obfsParam);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        method = input.readString();
        password = input.readString();
        protocol = input.readString();
        protocolParam = input.readString();
        obfs = input.readString();
        obfsParam = input.readString();
    }

    @NotNull
    @Override
    public ShadowsocksRBean clone() {
        return KryoConverters.deserialize(new ShadowsocksRBean(), KryoConverters.serialize(this));
    }

    public static final Creator<ShadowsocksRBean> CREATOR = new CREATOR<ShadowsocksRBean>() {
        @NonNull
        @Override
        public ShadowsocksRBean newInstance() {
            return new ShadowsocksRBean();
        }

        @Override
        public ShadowsocksRBean[] newArray(int size) {
            return new ShadowsocksRBean[size];
        }
    };
}

