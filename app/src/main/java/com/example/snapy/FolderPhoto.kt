package com.example.snapy

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "folder_photos",
    primaryKeys = ["folderId", "photoUri"],
    foreignKeys = [
        ForeignKey(
            entity = Folder::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["folderId"])]
)
data class FolderPhoto(
    val folderId: Long,
    val photoUri: String // Use URI as the unique identifier for external photos
)
