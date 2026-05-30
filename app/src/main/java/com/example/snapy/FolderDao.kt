package com.example.snapy

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY createdAt DESC")
    fun getAllFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE name = :name LIMIT 1")
    suspend fun getFolderByName(name: String): Folder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder): Long

    @Update
    suspend fun updateFolder(folder: Folder)

    @Delete
    suspend fun deleteFolder(folder: Folder)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addPhotoToFolder(folderPhoto: FolderPhoto)

    @Query("DELETE FROM folder_photos WHERE folderId = :folderId AND photoUri = :photoUri")
    suspend fun removePhotoFromFolder(folderId: Long, photoUri: String)

    @Query("SELECT COUNT(*) > 0 FROM folder_photos WHERE folderId = :folderId AND photoUri = :photoUri")
    suspend fun isPhotoInFolder(folderId: Long, photoUri: String): Boolean

    @Query("SELECT photoUri FROM folder_photos WHERE folderId = :folderId")
    suspend fun getPhotoUrisInFolder(folderId: Long): List<String>

    @Query("SELECT photoUri FROM folder_photos WHERE folderId = :folderId")
    fun getPhotosInFolder(folderId: Long): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM folder_photos WHERE folderId = :folderId")
    suspend fun getPhotoCountInFolder(folderId: Long): Int
    
    @Query("SELECT photoUri FROM folder_photos WHERE folderId = :folderId LIMIT 1")
    suspend fun getFolderThumbnail(folderId: Long): String?
}
