package io.nekohasekai.sagernet.fmt.hysteria;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class HysteriaBean extends AbstractBean {

    public static final int TYPE_NONE = 0;
    public static final int TYPE_STRING = 1;
    public static final int TYPE_BASE64 = 2;

    public Integer authPayloadType;
    public String authPayload;

    public static final int PROTOCOL_UDP = 0;
    public static final int PROTOCOL_FAKETCP = 1;
    public static final int PROTOCOL_WECHAT_VIDEO = 2;

    public Integer protocol;

    public String obfuscation;
    public String sni;
    public String alpn;
    public String caText;

    public Integer uploadMbps;
    public Integer downloadMbps;
    public Boolean allowInsecure;
    public Integer streamReceiveWindow;
    public Integer connectionReceiveWindow;
    public Boolean disableMtuDiscovery;
    public Integer hopInterval;

    @Override
    public boolean canMapping() {
        return protocol != PROTOCOL_FAKETCP;
    }

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (authPayloadType == null) authPayloadType = TYPE_NONE;
        if (authPayload == null) authPayload = "";
        if (protocol == null) protocol = PROTOCOL_UDP;
        if (obfuscation == null) obfuscation = "";
        if (sni == null) sni = "";
        if (alpn == null) alpn = "";
        if (caText == null) caText = "";

        if (uploadMbps == null) uploadMbps = 10;
        if (downloadMbps == null) downloadMbps = 50;
        if (allowInsecure == null) allowInsecure = false;

        if (streamReceiveWindow == null) streamReceiveWindow = 0;
        if (connectionReceiveWindow == null) connectionReceiveWindow = 0;
        if (disableMtuDiscovery == null) disableMtuDiscovery = false;
        if (hopInterval == null) hopInterval = 10;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(5);
        super.serialize(output);
        output.writeInt(authPayloadType);
        output.writeString(authPayload);
        output.writeInt(protocol);
        output.writeString(obfuscation);
        output.writeString(sni);
        output.writeString(alpn);

        output.writeInt(uploadMbps);
        output.writeInt(downloadMbps);
        output.writeBoolean(allowInsecure);

        output.writeString(caText);
        output.writeInt(streamReceiveWindow);
        output.writeInt(connectionReceiveWindow);
        output.writeBoolean(disableMtuDiscovery);
        output.writeInt(hopInterval);

    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        authPayloadType = input.readInt();
        authPayload = input.readString();
        if (version >= 3) {
            protocol = input.readInt();
        }
        obfuscation = input.readString();
        sni = input.readString();
        if (version >= 2) {
            alpn = input.readString();
        }
        uploadMbps = input.readInt();
        downloadMbps = input.readInt();
        allowInsecure = input.readBoolean();
        if (version >= 1) {
            caText = input.readString();
            streamReceiveWindow = input.readInt();
            connectionReceiveWindow = input.readInt();
            if (version != 4) disableMtuDiscovery = input.readBoolean(); // note: skip 4
        }
        if (version >= 5) {
            hopInterval = input.readInt();
        }
    }

    @Override
    public void applyFeatureSettings(AbstractBean other) {
        if (!(other instanceof HysteriaBean)) return;
        HysteriaBean bean = ((HysteriaBean) other);
        bean.uploadMbps = uploadMbps;
        bean.downloadMbps = downloadMbps;
        bean.allowInsecure = allowInsecure;
        bean.disableMtuDiscovery = disableMtuDiscovery;
        bean.hopInterval = hopInterval;
    }

    @Override
    public String displayAddress() {
        if (HysteriaFmtKt.isMultiPort(this)) {
            return serverAddress;
        }
        return super.displayAddress();
    }

    @NotNull
    @Override
    public HysteriaBean clone() {
        return KryoConverters.deserialize(new HysteriaBean(), KryoConverters.serialize(this));
    }

    public static final Creator<HysteriaBean> CREATOR = new CREATOR<HysteriaBean>() {
        @NonNull
        @Override
        public HysteriaBean newInstance() {
            return new HysteriaBean();
        }

        @Override
        public HysteriaBean[] newArray(int size) {
            return new HysteriaBean[size];
        }
    };
} 
