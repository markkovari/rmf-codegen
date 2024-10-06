package io.vrap.codegen.languages.rust

import io.vrap.rmf.codegen.types.*

fun VrapType.rustTypeName(): String {
    return when (this) {
        is VrapAnyType -> this.baseType
        is VrapScalarType -> this.scalarType
        is VrapEnumType -> this.simpleClassName.exportName()
        is VrapObjectType -> this.simpleClassName.exportName()
        is VrapArrayType -> "[]${this.itemType.rustTypeName()}"
        is VrapNilType -> "nil"
    }
}

fun VrapType.simpleRustName(): String {
    return when (this) {
        is VrapAnyType -> this.baseType
        is VrapScalarType -> this.scalarType
        is VrapEnumType -> this.simpleClassName.exportName()
        is VrapObjectType -> this.simpleClassName.exportName()
        is VrapArrayType -> this.itemType.simpleRustName()
        is VrapNilType -> "nil"
    }
}

fun VrapType.flattenVrapType(): VrapType {
    return when (this) {
        is VrapArrayType -> {
            this.itemType.flattenVrapType()
        }

        else -> this
    }
}
