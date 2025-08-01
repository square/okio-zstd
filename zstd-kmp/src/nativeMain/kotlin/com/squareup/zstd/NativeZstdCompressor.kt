/*
 * Copyright (C) 2025 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:OptIn(ExperimentalForeignApi::class)

package com.squareup.zstd

import com.squareup.zstd.internal.ZSTD_CCtx_setParameter
import com.squareup.zstd.internal.ZSTD_compressStream2
import com.squareup.zstd.internal.ZSTD_createCCtx
import com.squareup.zstd.internal.ZSTD_freeCCtx
import com.squareup.zstd.internal.ZSTD_inBuffer
import com.squareup.zstd.internal.ZSTD_outBuffer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned

internal class NativeZstdCompressor : ZstdCompressor() {
  private var cctx = ZSTD_createCCtx()
    .also {
      if (it == null) throw OutOfMemoryError("ZSTD_createCCtx failed")
    }

  override fun setParameter(param: Int, value: Int): Long = ZSTD_CCtx_setParameter(cctx, param.toUInt(), value).toLong()

  override fun compressStream2(
    outputByteArray: ByteArray,
    outputEnd: Int,
    outputStart: Int,
    inputByteArray: ByteArray,
    inputEnd: Int,
    inputStart: Int,
    mode: Int,
  ): Long {
    memScoped {
      outputByteArray.usePinned { outputDataPinned ->
        inputByteArray.usePinned { inputDataPinned ->
          val outputStart = outputStart.toULong()
          val outputEnd = outputEnd.toULong()
          val zstdOutput = alloc<ZSTD_outBuffer>()
          zstdOutput.dst = when {
            outputStart < outputEnd -> outputDataPinned.addressOf(0)
            else -> null
          }
          zstdOutput.pos = outputStart
          zstdOutput.size = outputEnd

          val inputStart = inputStart.toULong()
          val inputEnd = inputEnd.toULong()
          val zstdInput = alloc<ZSTD_inBuffer>()
          zstdInput.src = when {
            inputStart < inputEnd -> inputDataPinned.addressOf(0)
            else -> null
          }
          zstdInput.pos = inputStart
          zstdInput.size = inputEnd

          val result = ZSTD_compressStream2(
            cctx = cctx,
            output = zstdOutput.ptr,
            input = zstdInput.ptr,
            endOp = mode.toUInt(),
          ).toLong()

          outputBytesProcessed = (zstdOutput.pos - outputStart).toInt()
          inputBytesProcessed = (zstdInput.pos - inputStart).toInt()

          return result
        }
      }
    }
  }

  override fun close() {
    val cctxToClose = cctx ?: return
    cctx = null
    ZSTD_freeCCtx(cctxToClose)
  }
}
