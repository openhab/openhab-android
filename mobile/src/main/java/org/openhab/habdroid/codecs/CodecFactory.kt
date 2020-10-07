package org.openhab.habdroid.codecs

/**
 * Instantiates and returns appropriate codecs
 */
object CodecFactory {
    fun getCodec(targetChannel: CodecChannels?): Codec {
        return when (targetChannel) {
            CodecChannels.VideoDoorBellNBAudio -> G711uLaw()
            else -> G711uLaw()
        }
    }
}
