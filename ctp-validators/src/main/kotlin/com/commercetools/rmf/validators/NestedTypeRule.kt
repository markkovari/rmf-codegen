package com.commercetools.rmf.validators

import io.vrap.rmf.raml.model.types.ArrayType
import io.vrap.rmf.raml.model.types.BuiltinType
import io.vrap.rmf.raml.model.types.ObjectType
import io.vrap.rmf.raml.model.types.Property
import org.eclipse.emf.common.util.Diagnostic
import java.util.*

class NestedTypeRule(options: List<RuleOption>? = null) : TypesRule(options) {

    private val exclude: List<String> =
        (options?.filter { ruleOption -> ruleOption.type.lowercase(Locale.getDefault()) == RuleOptionType.EXCLUDE.toString() }?.map { ruleOption -> ruleOption.value }?.plus("") ?: defaultExcludes)

    override fun caseObjectType(type: ObjectType): List<Diagnostic> {
        val validationResults: MutableList<Diagnostic> = ArrayList()

        type.properties.filter { it.type is ObjectType || it.type is ArrayType }.map {
            val propertyType = it.type
            when (propertyType) {
                is ObjectType -> {
                    if ((propertyType.name == BuiltinType.OBJECT.getName() || propertyType.name.isNullOrBlank()) && propertyType.properties.isNotEmpty()) {
                        validationResults.add(error(it, "Type \"{0}\" must not use nested inline types for property \"{1}\"", type.name, it.name))
                    } else { }
                }
                is ArrayType -> {
                    if ((propertyType.items.name == BuiltinType.OBJECT.getName() || propertyType.items.name.isNullOrBlank())) {
                        validationResults.add(error(it, "Type \"{0}\" must not use nested inline types for property \"{1}\"", type.name, it.name))
                    } else if ((propertyType.items.name == BuiltinType.ARRAY.getName() || propertyType.items.name.isNullOrBlank())) {
                        validationResults.add(error(it, "Type \"{0}\" must not use nested inline types for property \"{1}\"", type.name, it.name))
                    } else { }
                }
                else -> {}
            }
        }
        return validationResults
    }

    companion object : ValidatorFactory<NestedTypeRule> {
        private val defaultExcludes by lazy { emptyList<String>() }

        @JvmStatic
        override fun create(options: List<RuleOption>?): NestedTypeRule {
            return NestedTypeRule(options)
        }
    }
}

