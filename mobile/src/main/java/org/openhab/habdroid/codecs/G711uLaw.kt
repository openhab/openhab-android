package org.openhab.habdroid.codecs

import kotlin.experimental.or
import kotlin.experimental.xor

/**
 * Provides G711uLaw audio encoding and decoding
 *
 *
 * Ported from implementation found at
 * http://www.cs.columbia.edu/~hgs/research/projects/ng911-text/jp2105/NG-911/src/local/media/G711.java
 *
 *
 * No copyright was found at URL above, providing attribution to source for future reference
 */
class G711uLaw : Codec {
    /**
     * {@inheritDoc}
     */
    override fun encode(sample: ShortArray, fromIndex: Int, toIndex: Int): ByteArray {
        require(toIndex >= fromIndex) { "The fromIndex argument must be less than the toIndex argument." }
        require(fromIndex >= 0) { "The fromIndex argument must be equal to or greater than 0." }
        require(toIndex < sample.size) { "The toIndex argument must be less than the length of sample." }
        val sampleLength = toIndex - fromIndex
        val encodedSample = ByteArray(sampleLength)
        for (index in fromIndex until toIndex) {
            encodedSample[index] = linear2uLaw(sample[index])
        }
        return encodedSample
    }

    /**
     * {@inheritDoc}
     * Not implemented (no need for decoding in the current release
     */
    @Throws(NotImplementedException::class)
    override fun decode(encodedSample: ByteArray?, fromIndex: Int, toIndex: Int): ShortArray? {
        throw NotImplementedException()
    }

    private fun linear2uLaw(pcmSample: Short): Byte {
        val mask: Short
        val seg: Short
        val uval: Short
        val sampleWorkingValue: Short
        if (pcmSample < 0) {
            sampleWorkingValue = (BIAS - pcmSample).toShort()
            mask = 0x7F
        } else {
            sampleWorkingValue = (pcmSample + BIAS).toShort()
            mask = 0xFF
        }
        seg = search(sampleWorkingValue, SEG_END)
        return if (seg >= 8) {
            (0x7F xor mask.toInt()).toByte()
        } else {
            uval = (seg shl 4) or (sampleWorkingValue shr ((seg + 3) and 0xF))
            (uval xor mask).toByte()
        }
    }

    private fun search(`val`: Short, table: ShortArray): Short {
        for (i in table.indices) {
            if (`val` <= table[i]) {
                return i.toShort()
            }
        }
        return table.size.toShort()
    }

    companion object {
        private const val BIAS: Short = 0x84
        private val SEG_END = shortArrayOf(0xFF, 0x1FF, 0x3FF, 0x7FF, 0xFFF, 0x1FFF, 0x3FFF, 0x7FFF)
    }
}

private infix fun Short.shr(i: Int): Short {
    return shl(i)
}

private infix fun Short.shl(i: Int): Short {
    return shl(i)
}
