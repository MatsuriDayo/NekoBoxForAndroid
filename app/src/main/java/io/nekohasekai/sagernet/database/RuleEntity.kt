package io.nekohasekai.sagernet.database

import android.os.Parcel
import android.os.Parcelable
import androidx.room.*
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.app
import java.util.LinkedHashSet

@Entity(tableName = "rules")
@TypeConverters(StringCollectionConverter::class)
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) var id: Long = 0L,
    var name: String = "",
    @ColumnInfo(defaultValue = "")
    var config: String = "",
    var userOrder: Long = 0L,
    var enabled: Boolean = false,
    var domains: String = "",
    var ip: String = "",
    var port: String = "",
    var sourcePort: String = "",
    var network: String = "",
    var source: String = "",
    var protocol: String = "",
    var outbound: Long = 0,
    var packages: Set<String> = emptySet(),
) : Parcelable {

    override fun describeContents() = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(name)
        parcel.writeString(config)
        parcel.writeLong(userOrder)
        parcel.writeInt(if (enabled) 1 else 0)
        parcel.writeString(domains)
        parcel.writeString(ip)
        parcel.writeString(port)
        parcel.writeString(sourcePort)
        parcel.writeString(network)
        parcel.writeString(source)
        parcel.writeString(protocol)
        parcel.writeLong(outbound)
        parcel.writeInt(packages.size)
        packages.forEach(parcel::writeString)
    }

    fun displayName(): String {
        return name.takeIf { it.isNotBlank() } ?: "Rule $id"
    }

    fun mkSummary(): String {
        var summary = ""
        if (config.isNotBlank()) summary += "[config]\n"
        if (domains.isNotBlank()) summary += "$domains\n"
        if (ip.isNotBlank()) summary += "$ip\n"
        if (source.isNotBlank()) summary += "src ip: $source\n"
        if (sourcePort.isNotBlank()) summary += "src port: $sourcePort\n"
        if (port.isNotBlank()) summary += "dst port: $port\n"
        if (network.isNotBlank()) summary += "network: $network\n"
        if (protocol.isNotBlank()) summary += "protocol: $protocol\n"
        if (packages.isNotEmpty()) summary += app.getString(
            R.string.apps_message, packages.size
        ) + "\n"
        val lines = summary.trim().split("\n")
        return if (lines.size > 3) {
            lines.subList(0, 3).joinToString("\n", postfix = "\n...")
        } else {
            summary.trim()
        }
    }

    fun displayOutbound(): String {
        return when (outbound) {
            0L -> app.getString(R.string.route_proxy)
            -1L -> app.getString(R.string.route_bypass)
            -2L -> app.getString(R.string.route_block)
            else -> ProfileManager.getProfile(outbound)?.displayName()
                ?: app.getString(R.string.error_title)
        }
    }

    @androidx.room.Dao
    interface Dao {

        @Query("SELECT * from rules WHERE (packages != '') AND enabled = 1")
        fun checkVpnNeeded(): List<RuleEntity>

        @Query("SELECT * FROM rules ORDER BY userOrder")
        fun allRules(): List<RuleEntity>

        @Query("SELECT * FROM rules WHERE enabled = :enabled ORDER BY userOrder")
        fun enabledRules(enabled: Boolean = true): List<RuleEntity>

        @Query("SELECT MAX(userOrder) + 1 FROM rules")
        fun nextOrder(): Long?

        @Query("SELECT * FROM rules WHERE id = :ruleId")
        fun getById(ruleId: Long): RuleEntity?

        @Query("DELETE FROM rules WHERE id = :ruleId")
        fun deleteById(ruleId: Long): Int

        @Delete
        fun deleteRule(rule: RuleEntity)

        @Delete
        fun deleteRules(rules: List<RuleEntity>)

        @Insert
        fun createRule(rule: RuleEntity): Long

        @Update
        fun updateRule(rule: RuleEntity)

        @Update
        fun updateRules(rules: List<RuleEntity>)

        @Query("DELETE FROM rules")
        fun reset()

        @Insert
        fun insert(rules: List<RuleEntity>)

    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<RuleEntity> {
            override fun createFromParcel(parcel: Parcel): RuleEntity {
                val id = parcel.readLong()
                val name = requireNotNull(parcel.readString())
                val config = requireNotNull(parcel.readString())
                val userOrder = parcel.readLong()
                val enabled = parcel.readInt() != 0
                val domains = requireNotNull(parcel.readString())
                val ip = requireNotNull(parcel.readString())
                val port = requireNotNull(parcel.readString())
                val sourcePort = requireNotNull(parcel.readString())
                val network = requireNotNull(parcel.readString())
                val source = requireNotNull(parcel.readString())
                val protocol = requireNotNull(parcel.readString())
                val outbound = parcel.readLong()
                val packageCount = parcel.readInt()
                val packages = LinkedHashSet<String>(packageCount)
                repeat(packageCount) {
                    packages += requireNotNull(parcel.readString())
                }
                return RuleEntity(
                    id, name, config, userOrder, enabled, domains, ip, port, sourcePort,
                    network, source, protocol, outbound, packages
                )
            }

            override fun newArray(size: Int): Array<RuleEntity?> = arrayOfNulls(size)
        }
    }

}
