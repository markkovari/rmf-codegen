package io.vrap.rmf.codegen.core

import io.vrap.rmf.codegen.types.VrapType
import io.vrap.rmf.raml.model.resources.Resource

class ResourceCollection(val className: VrapType, val resources: List<Resource>) {

    val sample: Resource
        get() = resources.first()

    init {
        if (resources.isEmpty()) {
            throw IllegalArgumentException("The resource collection is supposed to be non empty")
        }
    }

    override fun toString(): String {
        return "ResourceCollection(className=$className, resources=$resources)"
    }


}