package com.example.snapy

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Photo(
    val id: Int,
    val imageResId: Int = 0,
    val imageUri: Uri? = null,
    val dateTaken: Long=0L,
    var isLiked: Boolean = false,
    var isDisliked: Boolean = false
) : Parcelable 