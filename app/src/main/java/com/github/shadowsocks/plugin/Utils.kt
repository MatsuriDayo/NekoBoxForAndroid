@file:JvmName("Utils")

package com.github.shadowsocks.plugin

import android.os.Parcel
import android.os.Parcelable

class Empty : Parcelable {
    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = Unit

    companion object CREATOR : Parcelable.Creator<Empty> {
        override fun createFromParcel(source: Parcel) = Empty()

        override fun newArray(size: Int) = arrayOfNulls<Empty>(size)
    }
}
