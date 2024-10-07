package io.vrap.codegen.languages

import io.vrap.codegen.languages.rust.RustBaseTypes
import io.vrap.codegen.languages.rust.client.RustClientModule
import io.vrap.codegen.languages.rust.model.RustModelModule
import io.vrap.rmf.codegen.CodeGeneratorConfig
import io.vrap.rmf.codegen.di.*
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.nio.file.Paths

class TestRustCodeGenerator {

    companion object {
        private val userProvidedPath = System.getenv("TEST_RAML_FILE")
        private val userProvidedOutputPath = System.getenv("OUTPUT_FOLDER")

        private val apiPath: Path = Paths.get(userProvidedPath ?: "../../api-spec/api.raml")
        private val outputFolder: Path = Paths.get(userProvidedOutputPath ?: "build/gensrc")

        val apiProvider: RamlApiProvider = RamlApiProvider(apiPath)
        val generatorConfig = CodeGeneratorConfig(
            basePackageName = "", outputFolder = Paths.get("$outputFolder")
        )
    }

    @Test
    fun generateRustModels() {
        val generatorModule = RamlGeneratorModule(apiProvider, generatorConfig, RustBaseTypes)
        val generatorComponent = RamlGeneratorComponent(generatorModule, RustModelModule, RustClientModule)
        generatorComponent.generateFiles()
//        cleanGenTestFolder()
    }

//    private fun cleanGenTestFolder() {
//        cleanFolder("build/gensrc")
//    }
//
//    private fun cleanFolder(path: String) {
//        Paths.get(path).toFile().deleteRecursively()
//    }
}
