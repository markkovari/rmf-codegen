package io.vrap.rmf.codegen.di

import io.vrap.rmf.codegen.core.ResourceCollection
import io.vrap.rmf.codegen.io.DataSink
import io.vrap.rmf.codegen.io.FileDataSink
import io.vrap.rmf.codegen.types.*
import io.vrap.rmf.raml.model.elements.NamedElement
import io.vrap.rmf.raml.model.modules.Api
import io.vrap.rmf.raml.model.resources.Method
import io.vrap.rmf.raml.model.resources.Resource
import io.vrap.rmf.raml.model.resources.Trait
import io.vrap.rmf.raml.model.types.*
import io.vrap.rmf.raml.model.types.util.TypesSwitch
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.ComposedSwitch
import org.slf4j.LoggerFactory
import java.net.URI


@SuppressWarnings("unused")
class RamlGeneratorModule constructor(
    override val apiProvider: RamlApiProvider,
    override val generatorConfig: io.vrap.rmf.codegen.CodeGeneratorConfig,
    override val languageBaseTypes: LanguageBaseTypes,
    override val defaultPackage: String = "io/vrap/rmf",
    override val dataSink: DataSink = FileDataSink(generatorConfig.outputFolder)
) : GeneratorModule {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(RamlGeneratorModule::class.java)
    }

    private val filterSwitch = UserProvidedResourcesFilterSwitch()


    fun vrapTypeProvider(): VrapTypeProvider {
        return VrapTypeProvider(
            PackageProvider(providePackageName(), provideModelPackageName(), provideClientPackageName()),
            languageBaseTypes,
            generatorConfig.customTypeMapping
        )
    }

    @BasePackageName
    fun providePackageName(): String {
        val defaultPackage = defaultPackage();
        val api = provideRamlModel();
        if (generatorConfig.basePackageName == null && api.baseUri == null) {
            LOGGER.warn("Could not find proper package name configuration. Using default $defaultPackage")
            return defaultPackage
        }
        return generatorConfig.basePackageName ?: return try {
            URI(api.baseUri.expand()).host.split(".").reversed().joinToString("/")
        } catch (e: Exception) {
            LOGGER.warn(
                "Error while trying to extract base package from url, resolving to default package '$defaultPackage'", e
            )
            defaultPackage
        }
    }

    @ModelPackageName
    fun provideModelPackageName(): String {
        val basePackageName = providePackageName()
        return generatorConfig.modelPackage ?: if (basePackageName.isBlank()) "models" else "$basePackageName/models"
    }

    @ClientPackageName
    fun provideClientPackageName(): String {
        val basePackageName = providePackageName()
        return generatorConfig.clientPackage ?: if (basePackageName.isBlank()) "client" else "$basePackageName/client"
    }

    @SharedPackageName
    fun provideSharedPackageName(): String {
        val basePackageName = providePackageName()
        return generatorConfig.sharedPackage ?: if (basePackageName.isBlank()) "shared" else "$basePackageName/shared"
    }

    @RamlApi
    fun provideRamlModel(): Api = apiProvider.api

    @AllAnyTypes
    fun allAnyTypes(): List<AnyType> {
        val result = mutableListOf<AnyType>()
        val ramlApi = provideRamlModel();
        ramlApi.types?.forEach { result.add(it) }
        ramlApi.uses?.flatMap { it.library.types }?.forEach { result.add(it) }
        return result.filter { filterSwitch.doSwitch(it) }
    }

    @AllObjectTypes
    fun allObjectTypes(): List<ObjectType> =
        allAnyTypes().filter { it is ObjectType && !it.deprecated() }.map { it as ObjectType }

    @AllUnionTypes
    fun allUnionTypes(): List<UnionType> =
        allAnyTypes().filter { it is UnionType && !it.deprecated() }.map { it as UnionType }

    @EnumStringTypes
    fun allEnumStringTypes(): List<StringType> =
        allAnyTypes().filter { it is StringType && !it.deprecated() && it.enum.isNotEmpty() }.map { it as StringType }

    @PatternStringTypes
    fun allPatternStringTypes(): List<StringType> =
        allAnyTypes().filter { it is StringType && !it.deprecated() && it.pattern != null }.map { it as StringType }

    @NamedScalarTypes
    fun allNamedScalarTypes(): List<StringType> = allAnyTypes().filter {
        it is StringType && !it.deprecated() && it.pattern == null && it.enum.isNullOrEmpty()
    }.map { it as StringType }

    @AllResources
    fun allResources(): List<Resource> = provideRamlModel().allContainedResources

    @AllResourceMethods
    fun allResourceMethods(): List<Method> = provideRamlModel().allContainedResources.flatMap { it.methods }

    @AllTraits
    fun allTraits(): List<Trait> {
        val result = mutableListOf<Trait>()
        val ramlApi = provideRamlModel()
        if (ramlApi.traits != null) result.addAll(ramlApi.traits)
        ramlApi.uses?.forEach { if (it.library.traits != null) result.addAll(it.library.traits) }
        return result
    }

    @AllResourceCollections
    fun resourceCollection(vrapTypeProvider: VrapTypeProvider): List<ResourceCollection> {
        val resources = allResources()
        return resources.groupBy { (vrapTypeProvider.doSwitch(it) as VrapObjectType).simpleClassName }
            .map { entry: Map.Entry<String, List<Resource>> ->
                ResourceCollection(vrapTypeProvider.doSwitch(entry.value[0]), entry.value)
            }.toList()
    }


    /**
     * This filter is used to filter files that are explicitly provided by the sdk developer
     */
    private inner class UserProvidedResourcesFilterSwitch : ComposedSwitch<Boolean>() {

        init {
            addSwitch(FilterTypeSwitch())
        }

        private inner class FilterTypeSwitch : TypesSwitch<Boolean>() {
            override fun caseNamedElement(`object`: NamedElement): Boolean =
                generatorConfig.customTypeMapping[`object`.name]?.let { false } ?: true

            override fun caseStringType(stringType: StringType): Boolean = true
            override fun defaultCase(`object`: EObject?): Boolean = false
        }
    }

    private fun AnyType.deprecated(): Boolean {
        val anno = this.getAnnotation("deprecated")
        return (anno != null && (anno.value as BooleanInstance).value)
    }
}
