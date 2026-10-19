package io.nekohasekai.sagernet.database;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * see: https://youtrack.jetbrains.com/issue/KT-19853
 */
public class ParcelizeBridge {

    public static <T> T create(Parcel parcel, Parcelable.Creator<T> creator) {
        return creator.createFromParcel(parcel);
    }
}
