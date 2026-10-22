package com.lumalink.g100d.protocol

object LumixInfoParser {

    fun parse(body: String): CameraModel {
        val fields = XmlFields.extract(body)
        val model = XmlFields.value(
            fields,
            "model",
            "productname",
            "product",
            "camera",
            "devicename",
            "modelname",
        ) ?: "LUMIX"
        val firmware = XmlFields.value(
            fields,
            "firmware",
            "firmwareversion",
            "software",
            "version",
        )
        val serial = XmlFields.value(fields, "serial", "serialnumber", "cameraseiral", "cameraid")
        return CameraModel(model = model, firmware = firmware, serial = serial)
    }
}
