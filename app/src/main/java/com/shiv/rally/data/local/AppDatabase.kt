package com.shiv.rally.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ChannelEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
}
