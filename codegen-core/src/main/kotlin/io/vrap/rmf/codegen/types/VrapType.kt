package io.vrap.rmf.codegen.types

sealed class VrapType

open class VrapObjectType(val `package`: String, val simpleClassName: String) : VrapType() {


    override fun toString(): String {
        return "VrapObjectType(`package`='$`package`', simpleClassName='$simpleClassName')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VrapObjectType

        if (`package` != other.`package`) return false
        if (simpleClassName != other.simpleClassName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = `package`.hashCode()
        result = 31 * result + simpleClassName.hashCode()
        return result
    }
}

enum class DateTimeTypes(val format: String) {
    DateTime("date-time"), DateOnly("date"), TimeOnly("time")
}

class VrapDateTimeType(`package`: String, simpleClassName: String, val dateTimeType: DateTimeTypes) :
    VrapObjectType(`package`, simpleClassName) {

    override fun toString(): String {
        return "VrapDateTimeType(format='${dateTimeType.format}')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as VrapDateTimeType

        if (dateTimeType != other.dateTimeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + dateTimeType.hashCode()
        return result
    }
}

class VrapLibraryType(`package`: String, simpleClassName: String) : VrapObjectType(`package`, simpleClassName)

class VrapEnumType(val `package`: String, val simpleClassName: String) : VrapType() {


    override fun toString(): String {
        return "VrapEnumType(`package`='$`package`', simpleClassName='$simpleClassName')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VrapEnumType

        if (`package` != other.`package`) return false
        if (simpleClassName != other.simpleClassName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = `package`.hashCode()
        result = 31 * result + simpleClassName.hashCode()
        return result
    }
}

/**
 * Represent a type that comes from the default package
 */
class VrapScalarType constructor(val scalarType: String, val primitiveType: String) : VrapType() {

    constructor(scalarType: String) : this(scalarType, scalarType)

    override fun toString(): String {
        return "VrapScalarType(scalarType='$scalarType')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VrapScalarType

        if (scalarType != other.scalarType) return false
        if (primitiveType != other.primitiveType) return false

        return true
    }

    override fun hashCode(): Int = 31 * scalarType.hashCode() + primitiveType.hashCode()

}

class VrapArrayType(val itemType: VrapType) : VrapType() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VrapArrayType

        if (itemType != other.itemType) return false

        return true
    }

    override fun hashCode(): Int {
        return itemType.hashCode()
    }
}

class VrapNilType : VrapType() {

    val name = "void"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

}

/**
 * Represent a type that comes from the default package
 */
class VrapAnyType(val baseType: String) : VrapType() {


    override fun toString(): String {
        return "VrapAnyType(baseType='$baseType')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VrapAnyType

        return baseType == other.baseType
    }

    override fun hashCode(): Int {
        return baseType.hashCode()
    }
}
