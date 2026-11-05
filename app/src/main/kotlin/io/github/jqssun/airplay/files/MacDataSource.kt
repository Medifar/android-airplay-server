package io.github.jqssun.airplay.files

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.common.PlaybackException
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.smbj.share.File
import java.io.IOException

/** Streams a file from the mac share straight into exoplayer; uri is mirra-smb:///<path in share>. */
@UnstableApi
class MacDataSource : BaseDataSource(/* isNetwork = */ true) {

    private var uri: Uri? = null
    private var file: File? = null
    private var position = 0L
    private var remaining = 0L

    // extractors ask for a few kb at a time; one smb round trip per call would crawl, so read ahead
    private val buffer = ByteArray(CHUNK)
    private var bufferStart = 0L
    private var bufferLen = 0

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        val f = try {
            MacShare.open(pathOf(dataSpec.uri))
        } catch (e: Exception) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        }
        file = f
        val size = f.getFileInformation(FileStandardInformation::class.java).endOfFile
        if (dataSpec.position > size) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        position = dataSpec.position
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else size - position
        bufferLen = 0
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val f = file ?: throw IOException("not open")
        if (position < bufferStart || position >= bufferStart + bufferLen) {
            val n = try {
                f.read(buffer, position, 0, CHUNK)
            } catch (e: Exception) {
                throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
            }
            if (n <= 0) return C.RESULT_END_OF_INPUT
            bufferStart = position
            bufferLen = n
        }
        val inBuffer = (bufferStart + bufferLen - position).toInt()
        val count = minOf(length.toLong(), remaining, inBuffer.toLong()).toInt()
        System.arraycopy(buffer, (position - bufferStart).toInt(), target, offset, count)
        position += count
        remaining -= count
        bytesTransferred(count)
        return count
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        bufferLen = 0
        runCatching { file?.close() }
        if (file != null) {
            file = null
            transferEnded()
        }
    }

    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = MacDataSource()
    }

    companion object {
        const val SCHEME = "mirra-smb"
        private const val CHUNK = 256 * 1024

        fun uriFor(path: String): Uri = Uri.Builder().scheme(SCHEME).path("/$path").build()

        private fun pathOf(uri: Uri): String = uri.path.orEmpty().removePrefix("/")
    }
}
