package io.nekohasekai.sagernet.fmt.juicity;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class JuicityBean extends AbstractBean {

    public String uuid;
    public String password;
    public String sni;
    public String pinnedCertchainSha256;
    public Boolean allowInsecure;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (uuid == null) uuid = "";
        if (password == null) password = "";
        if (sni == null) sni = "";
        if (pinnedCertchainSha256 == null) pinnedCertchainSha256 = "";
        if (allowInsecure == null) allowInsecure = false;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        super.serialize(output);
        output.writeString(uuid);
        output.writeString(password);
        output.writeString(sni);
        output.writeString(pinnedCertchainSha256);
        output.writeBoolean(allowInsecure);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        uuid = input.readString();
        password = input.readString();
        sni = input.readString();
        pinnedCertchainSha256 = input.readString();
        allowInsecure = input.readBoolean();
    }

    @Override
    public boolean canTCPing() {
        return false;
    }

    @NotNull
    @Override
    public JuicityBean clone() {
        return KryoConverters.deserialize(new JuicityBean(), KryoConverters.serialize(this));
    }

    public static final Creator<JuicityBean> CREATOR = new CREATOR<JuicityBean>() {
        @NonNull
        @Override
        public JuicityBean newInstance() {
            return new JuicityBean();
        }

        @Override
        public JuicityBean[] newArray(int size) {
            return new JuicityBean[size];
        }
    };
}