package com.example.snapy

/**
 * Singleton to safely pass large lists of photos to the viewer activity
 * without hitting Binder transaction limits (TransactionTooLargeException).
 */
object PhotoViewerData {
    var currentPhotos: List<Photo> = emptyList()
}
