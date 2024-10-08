package io.vrap.codegen.languages.rust.client

import io.vrap.codegen.languages.rust.rustName
import io.vrap.codegen.languages.rust.snakeCase
import io.vrap.rmf.raml.model.types.ObjectInstance
import io.vrap.rmf.raml.model.types.QueryParameter
import io.vrap.rmf.raml.model.types.StringInstance

fun QueryParameter.template(): String {
    val anno = this.getAnnotation("placeholderParam", true)

    if (anno != null) {
        val o = anno.value as ObjectInstance
        val template = o.value.stream().filter { propertyValue -> propertyValue.name == "template" }.findFirst()
            .orElse(null).value as StringInstance
        val paramName = o.value.stream().filter { propertyValue -> propertyValue.name == "paramName" }.findFirst()
            .orElse(null).value as StringInstance
        val placeholder = o.value.stream().filter { propertyValue -> propertyValue.name == "placeholder" }.findFirst()
            .orElse(null).value as StringInstance

        val replace = template.value.replace("<" + placeholder.value + ">", "{k}")
        return "{\"$replace\": v for k, v in ${paramName.value.snakeCase()}.items()}"
    }
    return "'" + this.name + "'"
}

fun QueryParameter.paramName(): String {
    val anno = this.getAnnotation("placeholderParam", true)

    if (anno != null) {
        val o = anno.value as ObjectInstance
        val paramName = o.value.stream().filter { propertyValue -> propertyValue.name == "paramName" }.findFirst()
            .orElse(null).value as StringInstance
        return paramName.value.rustName()
    }
    return this.name.rustName()
}
