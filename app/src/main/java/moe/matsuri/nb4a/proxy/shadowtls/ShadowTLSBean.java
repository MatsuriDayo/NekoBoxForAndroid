package moe.matsuri.nb4a.proxy.shadowtls;

import androidx.annotation.NonNull;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import org.jetbrains.annotations.NotNull;
import io.nekohasekai.sagernet.fmt.KryoConverters;
import io.nekohasekai.sagernet.fmt.v2ray.StandardV2RayBean;

public class ShadowTLSBean extends StandardV2RayBean {

    private int version = 3; // Use int instead of Integer
    private char[] password; // Use char[] for security

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        security = "tls";
        password = new char[0]; 
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        try {
            output.writeInt(0); 
            super.serialize(output);
            output.writeInt(version);
            output.writeString(new String(password)); // Convert char[] to String
        } catch (Exception e) {
            // Handles exceptions during serialization
            throw new RuntimeException("Error serializing ShadowTLSBean", e);
        }
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        try {
            int version_ = input.readInt(); 
            super.deserialize(input);
            version = input.readInt();
            password = input.readString().toCharArray(); // Convert String to char[]
        } catch (Exception e) {
            // Handles exceptions during deserialization
            throw new RuntimeException("Error deserializing ShadowTLSBean", e);
        }
    }

    @NotNull
    @Override
    public ShadowTLSBean clone() {
        return KryoConverters.deserialize(new ShadowTLSBean(), KryoConverters.serialize(this));
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        if (version < 0) {
            throw new IllegalArgumentException("The version cannot be negative.");
        }
        this.version = version;
    }

    public String getPassword() {
        return new String(password); // Returns the password as a String
    }

    public void setPassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("The password cannot be null or empty.");
        }
        this.password = password.toCharArray(); // Store as char[]
    }

    public static final Creator<ShadowTLSBean> CREATOR = new CREATOR<ShadowTLSBean>() {
        @NonNull
        @Override
        public ShadowTLSBean newInstance() {
            return new ShadowTLSBean();
        }

        @Override
        public ShadowTLSBean[] newArray(int size) {
            return new ShadowTLSBean[size];
        }
    };
}
