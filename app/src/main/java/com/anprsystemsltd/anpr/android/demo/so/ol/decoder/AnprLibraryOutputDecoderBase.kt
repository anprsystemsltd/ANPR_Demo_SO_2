package com.anprsystemsltd.anpr.android.demo.so.ol.decoder

abstract class AnprLibraryOutputDecoderBase {

    var isValid = false

    lateinit var buffer: ByteArray

    var structSize: Int = 0
    var charBuffer: CharArray = CharArray(12)
    var numberOfChars: Int = 0
    var confidence: Int = 0;
    var plateX0: Int = 0; val plateY0: Int = 0
    var plateX1: Int = 0; val plateY1: Int = 0
    var plateX2: Int = 0; val plateY2: Int = 0
    var plateX3: Int = 0; val plateY3: Int = 0
    var plateWidth: Int = 0
    var plateHeight: Int = 0
    var country: Int = 0
    var avgCharHeight: Int = 0
    var syntaxWeight: Int = 0
    var syntaxCode: Int = 0
    var syntaxName: CharArray = CharArray(8)

    fun unsignedByte(b: Byte) : Int = b.toInt() and 0xFF

    fun copyChars(chars: CharArray, offset: Int, count: Int) {
        for (i in 0 until count) {
            chars[i] = buffer.get(offset + i).toChar()
        }
    }

    fun parseInteger(offset: Int): Int = unsignedByte(buffer[offset])

    abstract fun refreshFromBuffer(buffer: ByteArray)

}