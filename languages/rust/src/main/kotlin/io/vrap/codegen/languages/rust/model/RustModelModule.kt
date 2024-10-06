package io.vrap.codegen.languages.rust.model

import io.vrap.rmf.codegen.di.Module
import io.vrap.rmf.codegen.di.RamlGeneratorModule
import io.vrap.rmf.codegen.rendering.CodeGenerator
import io.vrap.rmf.codegen.rendering.FileGenerator


object RustModelModule : Module {
    override fun configure(generatorModule: RamlGeneratorModule) = setOf<CodeGenerator>(
        FileGenerator(
            setOf(
                RustFileProducer(
                    generatorModule.vrapTypeProvider(),
                    generatorModule.allAnyTypes(),
                    generatorModule.providePackageName()
                )
            )
        )
    )
}
