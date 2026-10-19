package io.nekohasekai.sagernet.aidl

import android.os.Parcel
import android.os.Parcelable

data class SpeedDisplayData(
    // Bytes per second
    var txRateProxy: Long = 0L,
    var rxRateProxy: Long = 0L,
    var txRateDirect: Long = 0L,
    var rxRateDirect: Long = 0L,

    // Bytes for the current session
    // Outbound "bypass" usage is not counted
    var txTotal: Long = 0L,
    var rxTotal: Long = 0L,
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
    )

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(txRateProxy)
        dest.writeLong(rxRateProxy)
        dest.writeLong(txRateDirect)
        dest.writeLong(rxRateDirect)
        dest.writeLong(txTotal)
        dest.writeLong(rxTotal)
    }

    companion object CREATOR : Parcelable.Creator<SpeedDisplayData> {
        override fun createFromParcel(source: Parcel) = SpeedDisplayData(source)

        override fun newArray(size: Int) = arrayOfNulls<SpeedDisplayData>(size)
    }
}
