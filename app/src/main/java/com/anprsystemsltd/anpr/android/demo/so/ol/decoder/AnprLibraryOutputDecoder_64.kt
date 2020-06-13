package com.anprsystemsltd.anpr.android.demo.so.ol.decoder

class AnprLibraryOutputDecoder_64 : AnprLibraryOutputDecoderBase() {

    override fun refreshFromBuffer(buffer: ByteArray) {
        this.buffer = buffer;
        structSize = parseInteger( 0);
        copyChars(charBuffer, 8, 12);
        numberOfChars = parseInteger(20);
        isValid = structSize == 248 && numberOfChars <= 12;
        confidence = parseInteger(32);
        plateX0 = parseInteger(84);
        plateX0 = parseInteger(88);
        plateX1 = parseInteger(92);
        plateX1 = parseInteger(96);
        plateX2 = parseInteger(100);
        plateX2 = parseInteger(104);
        plateX3 = parseInteger(108);
        plateX3 = parseInteger(112);
        plateWidth = parseInteger(116);
        plateHeight = parseInteger(120);
        country = parseInteger(164);
        avgCharHeight = parseInteger(168);
        syntaxWeight = parseInteger(176);
        syntaxCode = parseInteger(184);
        copyChars(syntaxName, 216, 8);
    }

}