package io.vrap.codegen.languages.rust.client

import io.vrap.codegen.languages.rust.client.RequestBuilder
import io.vrap.rmf.codegen.di.Module
import io.vrap.rmf.codegen.di.RamlGeneratorModule
import io.vrap.rmf.codegen.rendering.CodeGenerator
import io.vrap.rmf.codegen.rendering.FileGenerator
import io.vrap.rmf.codegen.rendering.MethodGenerator
import io.vrap.rmf.codegen.rendering.ResourceGenerator


object RustClientModule : Module {

    override fun configure(generatorModule: RamlGeneratorModule) = setOf<CodeGenerator>(
        ResourceGenerator(
            setOf(
                RequestBuilder(
                    generatorModule.clientConstants(),
                    generatorModule.provideRamlModel(),
                    generatorModule.vrapTypeProvider(),
                    generatorModule.providePackageName()
                )
            ), generatorModule.allResources()
        ), MethodGenerator(
            setOf(
                RustMethodRenderer(
                    generatorModule.vrapTypeProvider(), generatorModule.providePackageName()
                )
            ), generatorModule.allResourceMethods()
        ), FileGenerator(
            setOf(
                ClientFileProducer(
                    generatorModule.provideRamlModel(), generatorModule.providePackageName()
                )
            )
        )
    )

    private fun RamlGeneratorModule.clientConstants() = ClientConstants(
        this.provideSharedPackageName(), this.provideClientPackageName(), this.providePackageName()
    )
}
