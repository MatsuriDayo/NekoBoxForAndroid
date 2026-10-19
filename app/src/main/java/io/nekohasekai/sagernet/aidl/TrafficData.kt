package io.nekohasekai.sagernet.aidl

import android.os.Parcel
import android.os.Parcelable

data class TrafficData(
    var id: Long = 0L,
    var tx: Long = 0L,
    var rx: Long = 0L,
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
    )

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(id)
        dest.writeLong(tx)
        dest.writeLong(rx)
    }

    companion object CREATOR : Parcelable.Creator<TrafficData> {
        override fun createFromParcel(source: Parcel) = TrafficData(source)

        override fun newArray(size: Int) = arrayOfNulls<TrafficData>(size)
    }
}
