package io.vrap.codegen.languages.rust.client


import io.vrap.codegen.languages.rust.*
import io.vrap.rmf.codegen.di.BasePackageName
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendering.FileProducer
import io.vrap.rmf.codegen.rendering.utils.keepIndentation
import io.vrap.rmf.raml.model.modules.Api

class ClientFileProducer(
    val api: Api, @BasePackageName val basePackageName: String
) : FileProducer {

    override fun produceFiles(): List<TemplateFile> {

        return listOf(
            produceClientFile(), produceClientApiRoot(api), produceErrorsFile(), produceUtilsFile(), produceDateFile()
        )
    }

    fun produceClientFile(): TemplateFile {
        return TemplateFile(
            relativePath = "src/$basePackageName/client/mod.rs", content = """|$rustGeneratedComment
                |
                |use reqwest::{Client as HttpClient, Url};
                |
                |pub struct Client {
                |   http_client: HttpClient,
                |   url: Url,
                |}
            """.trimMargin()
        )
    }

    fun produceClientApiRoot(type: Api): TemplateFile {
        return TemplateFile(
            relativePath = "src/$basePackageName/client_api_root/mod.rs", content = """|$rustGeneratedComment
                |
            """.trimMargin().keepIndentation()
        )
    }

    fun produceErrorsFile(): TemplateFile {
        return TemplateFile(
            relativePath = "src/$basePackageName/errors/mod.rs", content = """|$rustGeneratedComment
               |
            """.trimMargin().keepIndentation()
        )
    }

    fun produceUtilsFile(): TemplateFile {
        return TemplateFile(
            relativePath = "src/$basePackageName/utils/mod.rs", content = """|$rustGeneratedComment
                |
            """.trimMargin().keepIndentation()
        )
    }

    fun produceDateFile(): TemplateFile {
        return TemplateFile(
            relativePath = "src/$basePackageName/date/mod.rs", content = """|$rustGeneratedComment
                |
            """.trimMargin().keepIndentation()
        )
    }
}
