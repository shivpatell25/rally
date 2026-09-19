package com.shiv.rally.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val number: String,
    val name: String,
    val category: String,
    val logoUrl: String?,
    val streamUrl: String?,
    val supportsCatchUp: Boolean = false,
    val archiveDurationHours: Int? = null
)
