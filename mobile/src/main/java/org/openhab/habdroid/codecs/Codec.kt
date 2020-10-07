package org.openhab.habdroid.codecs

/**
 * Defines Codec behavior
 */
interface Codec {
    /**
     * Encodes a sample.
     *
     * @param sample    the array of samples to be encoded
     * @param fromIndex the index of the first element (inclusive) to be encoded, value must be 0 or greater and less than toIndex
     * @param toIndex   the index of the last element (inclusive) to be encoded, value must be greater than fromIndex and less than size of sample
     * @return The encoded sample
     */
    fun encode(sample: ShortArray, fromIndex: Int, toIndex: Int): ByteArray

    /**
     * Decodes an encoded sample
     *
     * @param encodedSample the array of encoded samples to be decoded
     * @param fromIndex     the index of the first element (inclusive) to be decoded, value must be 0 or greater and less than toIndex
     * @param toIndex       the index of the last element (inclusive) to be decoded, value must be greater than fromIndex and less than size of sample
     * @return The decoded sample
     */
    @Throws(NotImplementedException::class)
    fun decode(encodedSample: ByteArray?, fromIndex: Int, toIndex: Int): ShortArray?
}
