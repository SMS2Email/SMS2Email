package org.matrix.rustsdk

import android.content.Context

object Android {
  init {
    System.loadLibrary("matrix_sdk_ffi")
  }

  @JvmStatic external fun init(context: Context)
}
