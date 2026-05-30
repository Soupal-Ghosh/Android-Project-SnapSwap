package com.example.snapy

import android.content.Context
import kotlinx.coroutines.flow.Flow

class PhotoRepository(context: Context) {
    private val folderDao = AppDatabase.getDatabase(context).folderDao()

    val allFolders: Flow<List<Folder>> = folderDao.getAllFolders()

    suspend fun createFolder(name: String): Long {
        return folderDao.insertFolder(Folder(name = name))
    }

    suspend fun getOrCreateFolderId(name: String): Long {
        return folderDao.getFolderByName(name)?.id ?: createFolder(name)
    }

    suspend fun updateFolder(folder: Folder) {
        folderDao.updateFolder(folder)
    }

    suspend fun addPhotosToFolder(folderId: Long, photoUris: List<String>) {
        photoUris.forEach { uri ->
            folderDao.addPhotoToFolder(FolderPhoto(folderId, uri))
        }
    }

    suspend fun removePhotosFromFolder(folderId: Long, photoUris: List<String>) {
        photoUris.forEach { uri ->
            folderDao.removePhotoFromFolder(folderId, uri)
        }
    }

    suspend fun isPhotoInFolder(folderId: Long, photoUri: String): Boolean {
        return folderDao.isPhotoInFolder(folderId, photoUri)
    }

    suspend fun deleteFolder(folder: Folder) {
        folderDao.deleteFolder(folder)
    }

    fun getPhotosInFolder(folderId: Long): Flow<List<String>> {
        return folderDao.getPhotosInFolder(folderId)
    }
    
    suspend fun getPhotoUrisInFolder(folderId: Long): List<String> {
        return folderDao.getPhotoUrisInFolder(folderId)
    }

    suspend fun getPhotoCount(folderId: Long): Int {
        return folderDao.getPhotoCountInFolder(folderId)
    }
    
    suspend fun getFolderThumbnail(folderId: Long): String? {
        return folderDao.getFolderThumbnail(folderId)
    }
}
