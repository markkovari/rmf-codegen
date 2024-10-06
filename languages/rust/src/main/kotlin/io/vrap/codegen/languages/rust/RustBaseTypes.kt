package io.vrap.codegen.languages.rust

import io.vrap.rmf.codegen.types.LanguageBaseTypes
import io.vrap.rmf.codegen.types.VrapScalarType

object RustBaseTypes : LanguageBaseTypes(
    // this might cause some trouble mapping
    anyType = nativeRustType("()"),

    // this might cause some trouble mapping
    objectType = nativeRustType("Trait"),


    integerType = nativeRustType("int"),
    longType = nativeRustType("i64"),
    doubleType = nativeRustType("f64"),
    stringType = nativeRustType("String"),
    booleanType = nativeRustType("bool"),
    // this might cause some trouble mapping
    // ISO 8601 ??
    dateTimeType = nativeRustType("chrono::NaiveTime"),
    dateOnlyType = nativeRustType("chrono::NaiveDate"),
    timeOnlyType = nativeRustType("chrono::DateTime"),
    file = nativeRustType("std::fs::File")
)

fun nativeRustType(typeName: String): VrapScalarType = VrapScalarType(typeName)
