package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.FocusTubeDao
import com.example.data.local.entity.AppPreferenceEntity
import com.example.data.local.entity.ChannelEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.SearchHistoryEntity
import com.example.data.local.entity.SubscriptionGroupEntity
import com.example.data.local.entity.VideoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        SubscriptionGroupEntity::class,
        ChannelEntity::class,
        PlaylistEntity::class,
        VideoEntity::class,
        AppPreferenceEntity::class,
        SearchHistoryEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class FocusTubeDatabase : RoomDatabase() {
    abstract fun dao(): FocusTubeDao

    companion object {
        @Volatile
        private var INSTANCE: FocusTubeDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): FocusTubeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FocusTubeDatabase::class.java,
                    "focustube_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(FocusTubeDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class FocusTubeDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateInitialData(database.dao())
                }
            }
        }

        private suspend fun populateInitialData(dao: FocusTubeDao) {
            // 1. Initial Channel Groups
            val group1 = SubscriptionGroupEntity(
                id = 1,
                name = "Science & Math Group",
                isPinnedHome = true
            )
            val group2 = SubscriptionGroupEntity(
                id = 2,
                name = "Coding & Technology Group",
                isPinnedHome = false
            )
            dao.insertGroups(listOf(group1, group2))

            // 2. Channels in Groups
            val channels = listOf(
                ChannelEntity(id = 1, name = "3Blue1Brown", handleOrUrl = "@3blue1brown", groupId = 1),
                ChannelEntity(id = 2, name = "Veritasium", handleOrUrl = "@veritasium", groupId = 1),
                ChannelEntity(id = 3, name = "freeCodeCamp", handleOrUrl = "@freecodecamp", groupId = 2),
                ChannelEntity(id = 4, name = "Philipp Lackner", handleOrUrl = "@philipplackner", groupId = 2)
            )
            dao.insertChannels(channels)

            // 3. Playlists
            val playlists = listOf(
                PlaylistEntity(
                    id = 1,
                    playlistId = "pl_linear_algebra",
                    title = "Linear Algebra Lessons",
                    isPinnedHome = false
                ),
                PlaylistEntity(
                    id = 2,
                    playlistId = "pl_coding_basics",
                    title = "Computer Science Foundations",
                    isPinnedHome = false
                )
            )
            dao.insertPlaylists(playlists)

            // 4. Sample Videos with Descriptions
            val videos = listOf(
                // Videos for Group 1 (Science & Math)
                VideoEntity(
                    id = 1,
                    videoId = "fNk_zzaMoSs",
                    groupId = 1,
                    channelDbId = 1,
                    title = "Vectors, what even are they? | Chapter 1",
                    channelName = "3Blue1Brown",
                    description = "What is a vector? The introduction to essence of linear algebra.\n\nTimestamps:\n0:00 - Introduction\n1:05 - Physics perspective\n3:20 - Computer Science perspective\n5:12 - Mathematics perspective\n8:10 - Vector addition and scalar multiplication.",
                    duration = "09:52",
                    thumbnailUrl = "https://img.youtube.com/vi/fNk_zzaMoSs/hqdefault.jpg"
                ),
                VideoEntity(
                    id = 2,
                    videoId = "k7RM-ot2NWY",
                    groupId = 1,
                    channelDbId = 1,
                    title = "Linear combinations, span, and basis vectors",
                    channelName = "3Blue1Brown",
                    description = "Fundamental concepts of linear algebra: basis vectors, span, and linear combinations.\n\nKey Concepts:\n- Standard basis vectors i-hat and j-hat\n- Linear combinations: a*v + b*w\n- Span: The set of all possible vectors reachable.",
                    duration = "09:59",
                    thumbnailUrl = "https://img.youtube.com/vi/k7RM-ot2NWY/hqdefault.jpg"
                ),
                VideoEntity(
                    id = 3,
                    videoId = "bHIhgxav9LY",
                    groupId = 1,
                    channelDbId = 2,
                    title = "How One Small Mistake Cost $125 Million",
                    channelName = "Veritasium",
                    description = "The Mars Climate Orbiter metric vs imperial unit mistake.\n\nIn 1999, NASA lost the Mars Climate Orbiter spacecraft because one engineering team used metric units while another used imperial units.",
                    duration = "14:22",
                    thumbnailUrl = "https://img.youtube.com/vi/bHIhgxav9LY/hqdefault.jpg"
                ),

                // Videos for Group 2 (Coding)
                VideoEntity(
                    id = 4,
                    videoId = "bbMsuI2P15M",
                    groupId = 2,
                    channelDbId = 3,
                    title = "Android Development Beginner Course",
                    channelName = "freeCodeCamp",
                    description = "Complete beginners course to modern Android app development with Kotlin and Jetpack Compose.\n\nTopics covered:\n1. Android Studio Setup\n2. Kotlin Basics\n3. Compose UI\n4. State Management\n5. Room Database",
                    duration = "11:38:00",
                    thumbnailUrl = "https://img.youtube.com/vi/bbMsuI2P15M/hqdefault.jpg"
                ),

                // Videos for Playlist 1
                VideoEntity(
                    id = 5,
                    videoId = "fNk_zzaMoSs",
                    playlistId = "pl_linear_algebra",
                    title = "Vectors, what even are they? | Chapter 1",
                    channelName = "3Blue1Brown",
                    description = "What is a vector? The introduction to essence of linear algebra.\n\nTimestamps:\n0:00 - Introduction\n1:05 - Physics perspective\n3:20 - Computer Science perspective\n5:12 - Mathematics perspective",
                    duration = "09:52",
                    thumbnailUrl = "https://img.youtube.com/vi/fNk_zzaMoSs/hqdefault.jpg"
                ),
                VideoEntity(
                    id = 6,
                    videoId = "kYB8IZa5AuE",
                    playlistId = "pl_linear_algebra",
                    title = "Linear transformations and matrices | Chapter 3",
                    channelName = "3Blue1Brown",
                    description = "Visualizing linear transformations and matrices geometrically.\n\nTransformations keep grid lines parallel and origin fixed.",
                    duration = "10:59",
                    thumbnailUrl = "https://img.youtube.com/vi/kYB8IZa5AuE/hqdefault.jpg"
                )
            )
            dao.insertVideos(videos)
        }
    }
}
