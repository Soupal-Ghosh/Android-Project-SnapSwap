package com.example.snapy

sealed class GalleryItem {
    data class Header(val title: String) : GalleryItem()
    data class PhotoItem(val photo: Photo) : GalleryItem()
}
