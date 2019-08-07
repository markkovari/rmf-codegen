package io.vrap.codegen.languages.postman.model

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.google.common.collect.Lists
import com.google.inject.Inject
import com.hypertino.inflector.English
import io.vrap.codegen.languages.extensions.EObjectExtensions
import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.FileProducer
import io.vrap.rmf.codegen.rendring.utils.escapeAll
import io.vrap.rmf.codegen.rendring.utils.keepIndentation
import io.vrap.rmf.codegen.types.VrapTypeProvider
import io.vrap.rmf.raml.model.modules.Api
import io.vrap.rmf.raml.model.resources.HttpMethod
import io.vrap.rmf.raml.model.resources.Method
import io.vrap.rmf.raml.model.resources.Parameter
import io.vrap.rmf.raml.model.resources.Resource
import io.vrap.rmf.raml.model.security.OAuth20Settings
import io.vrap.rmf.raml.model.types.*
import io.vrap.rmf.raml.model.util.StringCaseFormat
import org.apache.commons.lang3.StringEscapeUtils
import org.apache.commons.lang3.StringUtils
import org.eclipse.emf.ecore.EObject
import java.io.IOException
import java.net.URI
import java.util.*
import kotlin.reflect.KFunction2

class PostmanModuleRenderer @Inject constructor(val api: Api, override val vrapTypeProvider: VrapTypeProvider) : EObjectExtensions, FileProducer {

    override fun produceFiles(): List<TemplateFile> {
        return listOf(
                template(api),
                collection(api)
        )
    }

    private fun template(api: Api): TemplateFile {
        return TemplateFile(relativePath = "template.json",
                content = """
                    |{
                    |  "id": "<id>",
                    |  "name": "commercetools platform API (generated).template",
                    |  "values": [
                    |    {
                    |      "enabled": true,
                    |      "key": "host",
                    |      "value": "${api.baseUri}",
                    |      "type": "text"
                    |    },
                    |    {
                    |      "enabled": true,
                    |      "key": "auth_url",
                    |      "value": "<api.OAuth.uri.host>",
                    |      "type": "text"
                    |    },
                    |    {
                    |      "enabled": true,
                    |      "key": "ctp_client_id",
                    |      "value": "\<your-client-id\>",
                    |      "type": "text"
                    |    },
                    |    {
                    |      "enabled": true,
                    |      "key": "ctp_client_secret",
                    |      "value": "\<your-client-secret\>",
                    |      "type": "text"
                    |    },
                    |    {
                    |      "enabled": true,
                    |      "key": "ctp_access_token",
                    |      "value": "\<your_access_token\>",
                    |      "type": "text"
                    |    }
                    |  ]
                    |}
                """.trimMargin()
        )
    }

    private fun collection(api: Api): TemplateFile {
        return TemplateFile(relativePath = "collection.json",
                content = """
                    |{
                    |    "info": {
                    |        "_postman_id": "<id>",
                    |        "name": "commercetools platform API (generated)",
                    |        "description": "${StringEscapeUtils.escapeJson(readme())}",
                    |        "schema": "https://schema.getpostman.com/json/collection/v2.0.0/collection.json"
                    |    },
                    |    "auth":
                    |        <<${auth()}>>,
                    |    "item": [
                    |        <<${authorization(api.oauth())}>>,
                    |        <<${api.resources().joinToString(",") { folder(api.oauth(), it) }}>>
                    |    ]
                    |}
                """.trimMargin().keepIndentation("<<",">>"))
    }

    private fun folder(oauth: OAuth20Settings, resource: ResourceModel): String {
        return """
            |{
            |    "name": "${resource.name()}",
            |    "description": "${resource.description()}",
            |    "item": [
            |        <<${resource.items.joinToString(",") { it.template(oauth, it) } }>>
            |    ],
            |    "event": [
            |        {
            |            "listen": "test",
            |            "script": {
            |                "type": "text/javascript",
            |                "exec": [
            |                    <<${if (resource.items.isNotEmpty()) testScript(resource.items.first(), "") else ""}>>
            |                ]
            |            }
            |        }
            |    ]
            |}
        """.trimMargin()
    }

    private fun auth(): String {
        return """
            |{
            |    "type": "oauth2",
            |    "oauth2": {
            |        "accessToken": "{{ctp_access_token}}",
            |        "addTokenTo": "header",
            |        "tokenType": "Bearer"
            |    }
            |}
            """.trimMargin()
    }

    private fun testScript(item: ItemGenModel, param: String): String {
        return """
            |tests["Status code " + responseCode.code] = responseCode.code === 200 || responseCode.code === 201;
            |var data = JSON.parse(responseBody);
            |if(data.version){
            |    pm.environment.set("${item.resourcePathName.singularize()}-version", data.version);
            |}
            |if(data.id){
            |    pm.environment.set("${item.resourcePathName.singularize()}-id", data.id); 
            |}
            |if(data.key){
            |    pm.environment.set("${item.resourcePathName.singularize()}-key", data.key);
            |}
            |${if (param.isNotEmpty()) """
            |if(data.${param}){
            |    pm.environment.set("${item.resourcePathName.singularize()}-${param}", data.${param});
            |}
            """.trimMargin() else ""}
            |${if (item is ActionGenModel && item.testScript.isNullOrEmpty().not()) item.testScript!!.joinToString(",\n") else ""}
        """.trimMargin().split("\n").map { it.escapeJson().escapeAll() }.joinToString("\",\n\"", "\"", "\"")
    }

    private fun query(oauth: OAuth20Settings, item: ItemGenModel): String {
        return """
            |{
            |    "name": "Query ${item.name}",
            |    "event": [
            |        {
            |            "listen": "test",
            |            "script": {
            |                "type": "text/javascript",
            |                "exec": [
            |                    <<${testScript(item, "")}>>
            |                ]
            |            }
            |        }
            |    ],
            |    "request": {
            |        "auth":
            |            <<${auth()}>>,
            |        "method": "${item.method.methodName.toUpperCase()}",
            |        "header": [
            |            {
            |                "key": "Content-Type",
            |                "value": "application/json"
            |            }
            |        ],
            |        "body": {
            |            "mode": "raw",
            |            "raw": ""
            |        },
            |        "url": {
            |            "raw": "{{host}}/{{projectKey}}${item.resource.relativeUri.template}",
            |            "host": [
            |                "{{host}}"
            |            ],
            |            "path": [
            |                "{{projectKey}}",
            |                "${item.resource.resourcePathName}"
            |            ],
            |            "query": [
            |                <<${if (item.queryParameters.isNotEmpty()) item.queryParameters.joinToString(",\n") { it.queryParam() } else ""}>>
            |            ]
            |        },
            |        "description": "${item.description}"
            |    },
            |    "response": []
            |}
        """.trimMargin()
    }

    private fun create(oauth: OAuth20Settings, item: ItemGenModel): String {
        return """
            |{
            |    "name": "Create ${item.name.singularize()}",
            |    "event": [
            |        {
            |            "listen": "test",
            |            "script": {
            |                "type": "text/javascript",
            |                "exec": [
            |                    <<${testScript(item, "")}>>
            |                ]
            |            }
            |        }
            |    ],
            |    "request": {
            |        "auth":
            |           <<${auth()}>>,
            |        "method": "${item.method.methodName.toUpperCase()}",
            |        "header": [
            |            {
            |                "key": "Content-Type",
            |                "value": "application/json"
            |            }
            |        ],
            |        "body": {
            |            "mode": "raw",
            |            "raw": "${if (item.getExample().isNullOrEmpty().not()) item.getExample()!!.escapeJson() else ""}"
            |        },
            |        "url": {
            |            "raw": "{{host}}/{{projectKey}}${item.resource.relativeUri.template}",
            |            "host": [
            |                "{{host}}"
            |            ],
            |            "path": [
            |                "{{projectKey}}",
            |                "${item.resource.resourcePathName}"
            |            ],
            |            "query": [
            |                <<${if (item.queryParameters.isNotEmpty()) item.queryParameters.joinToString(",\n") { it.queryParam() } else ""}>>
            |            ]
            |        },
            |        "description": "${item.description}"
            |    },
            |    "response": []
            |}
        """.trimMargin()
    }

    private fun getByID(oauth: OAuth20Settings, item: ItemGenModel): String {
        return getByParam(oauth, item, "", false)
    }

    private fun getByKey(oauth: OAuth20Settings, item: ItemGenModel): String {
        return getByParam(oauth, item, "key", true)
    }

    private fun getByParam(oauth: OAuth20Settings, item: ItemGenModel, param: String, by: Boolean): String {
        return """
            |{
            |    "name": "Get ${item.name.singularize()}${if (by) " by ${if (param.isNotEmpty()) param else "ID"}" else ""}",
            |    "event": [
            |        {
            |            "listen": "test",
            |            "script": {
            |                "type": "text/javascript",
            |                "exec": [
            |                    <<${testScript(item, param)}>>
            |                ]
            |            }
            |        }
            |    ],
            |    "request": {
            |        "auth":
            |            <<${auth()}>>,
            |        "method": "${item.method.methodName.toUpperCase()}",
            |        "header": [
            |            {
            |                "key": "Content-Type",
            |                "value": "application/json"
            |            }
            |        ],
            |        "body": {
            |            "mode": "raw",
            |            "raw": ""
            |        },
            |        "url": {
            |            ${if (param.isNotEmpty()) """
                            "raw": "{{host}}/{{projectKey}}${item.resource.relativeUri.template}/${param}={{${item.resource.resourcePathName.singularize()}-${param}}}",
                            """.trimIndent() else """
                            "raw": "{{host}}/{{projectKey}}${item.resource.relativeUri.template}/{{${item.resource.resourcePathName.singularize()}-id}}",
                            """.trimIndent()}
            |            "host": [
            |                "{{host}}"
            |            ],
            |            "path": [
            |                "{{projectKey}}",
            |                "${item.resource.resourcePathName}",
            |                "${if (param.isNotEmpty()) "${param}={{${item.resource.resourcePathName}-${param}}}" else "{{${item.resource.resourcePathName}-id}}"}"
            |            ],
            |            "query": [
            |                <<${if (item.queryParameters.isNotEmpty()) item.queryParameters.joinToString(",\n") { it.queryParam() } else ""}>>
            |            ]
            |        },
            |        "description": "${item.description}"
            |    },
            |    "response": []
            |}
        """.trimMargin()
    }

    private fun updateByID(oauth: OAuth20Settings, item: ItemGenModel): String {
        return updateByParam(oauth, item, "", false)
    }

    private fun updateByKey(oauth: OAuth20Settings, item: ItemGenModel): String {
        return updateByParam(oauth, item, "key", true)
    }

    private fun updateByParam(oauth: OAuth20Settings, item: ItemGenModel, param: String, by: Boolean): String {
        return """
            |{
            |    "name": "Update ${item.name.singularize()}${if (by) " by ${if (param.isNotEmpty()) param else "ID"}" else ""}",
            |    "event": [
            |        {
            |            "listen": "test",
            |            "script": {
            |                "type": "text/javascript",
            |                "exec": [
            |                    <<${testScript(item, param)}>>
            |                ]
            |            }
            |        }
            |    ],
            |    "request": {
            |        "auth":
            |            <<${auth()}>>,
            |        "method": "${item.method.methodName.toUpperCase()}",
            |        "header": [
            |            {
            |                "key": "Content-Type",
            |                "value": "application/json"
            |            }
            |        ],
            |        "body": {
            |            "mode": "raw",
            |            "raw": "${if (item.getExample().isNullOrEmpty().not()) item.getExample()!!.escapeJson() else ""}"
            |        },
            |        "url": {
            |            ${if (param.isNotEmpty()) """
                            "raw": "{{host}}/{{projectKey}}${item.resource.relativeUri.template}/${param}={{${item.resource.resourcePathName.singularize()}-${param}}}",
                            """.trimIndent() else """
                            "raw": "{{host}}/{{projectKey}}${item.resource.relativeUri.template}/{{${item.resource.resourcePathName.singularize()}-id}}",
                            """.trimIndent()}
            |            "host": [
            |                "{{host}}"
            |            ],
            |            "path": [
            |                "{{projectKey}}",
            |                "${item.resource.resourcePathName}",
            |                "${if (param.isNotEmpty()) "${param}={{${item.resource.resourcePathName}-${param}}}" else "{{${item.resource.resourcePathName}-id}}"}"
            |            ],
            |            "query": [
            |                <<${if (item.queryParameters.isNotEmpty()) item.queryParameters.joinToString(",\n") { it.queryParam() } else ""}>>
            |            ]
            |        },
            |        "description": "${item.description}"
            |    },
            |    "response": []
            |}
        """.trimMargin()
    }

    private fun deleteByID(oauth: OAuth20Settings, item: ItemGenModel): String {
        return deleteByParam(oauth, item, "", false)
    }

    private fun deleteByKey(oauth: OAuth20Settings, item: ItemGenModel): String {
        return deleteByParam(oauth, item, "key", true)
    }

    private fun deleteByParam(oauth: OAuth20Settings, item: ItemGenModel, param: String, by: Boolean): String {
        return """
            |{
            |    "name": "Delete ${item.name.singularize()}${if (by) " by ${if (param.isNotEmpty()) param else "ID"}" else ""}",
            |    "event": [
            |        {
            |            "listen": "test",
            |            "script": {
            |                "type": "text/javascript",
            |                "exec": [
            |                    <<${testScript(item, param)}>>
            |                ]
            |            }
            |        }
            |    ],
            |    "request": {
            |        "auth":
            |            <<${auth()}>>,
            |        "method": "${item.method.methodName.toUpperCase()}",
            |        "header": [
            |            {
            |                "key": "Content-Type",
            |                "value": "application/json"
            |            }
            |        ],
            |        "body": {
            |            "mode": "raw",
            |            "raw": "${if (item.getExample().isNullOrEmpty().not()) item.getExample()!!.escapeJson() else ""}"
            |        },
            |        "url": {
            |            ${if (param.isNotEmpty()) """
                            "raw": "{{host}}/{{projectKey}}${item.resource.relativeUri.template}/${param}={{${item.resource.resourcePathName.singularize()}-${param}}}",
                            """.trimIndent() else """
                            "raw": "{{host}}/{{projectKey}}${item.resource.relativeUri.template}/{{${item.resource.resourcePathName.singularize()}-id}}",
                            """.trimIndent()}
            |            "host": [
            |                "{{host}}"
            |            ],
            |            "path": [
            |                "{{projectKey}}",
            |                "${item.resource.resourcePathName}",
            |                "${if (param.isNotEmpty()) "${param}={{${item.resource.resourcePathName}-${param}}}" else "{{${item.resource.resourcePathName}-id}}"}"
            |            ],
            |            "query": [
            |                <<${if (item.queryParameters.isNotEmpty()) item.queryParameters.joinToString(",\n") { it.queryParam() } else ""}>>
            |            ]
            |        },
            |        "description": "${item.description}"
            |    },
            |    "response": []
            |}
        """.trimMargin()
    }

    private fun actionExample(item: ActionGenModel): String {
        return """
            |{
            |    "version": {{${item.resource.resourcePathName.singularize()}-version}},
            |    "actions": [
            |        <<${if (item.getExample().isNullOrEmpty().not()) item.getExample() else """
            |        |{
            |        |    "action": "${item.type.discriminatorValue}"
            |        |}""".trimMargin()}>>
            |    ]
            |}
        """.trimMargin().keepIndentation("<<", ">>")
    }

    private fun projectActionExample(item: ActionGenModel): String {
        if (item.getExample().isNullOrEmpty())
            return """
                |{
                |    "version": {{project-version}},
                |    "actions": []
                |}
            """.trimMargin()
        return item.getExample()!!
    }

    private fun action(oauth: OAuth20Settings, item: ItemGenModel): String {
        if (item is ActionGenModel)
            return """
                |{
                |    "name": "${item.type.discriminatorValue.capitalize()}",
                |    "event": [
                |        {
                |            "listen": "test",
                |            "script": {
                |                "type": "text/javascript",
                |                "exec": [
                |                    <<${testScript(item, "")}>>
                |                ]
                |            }
                |        }
                |    ],
                |    "request": {
                |        "auth":
                |            <<${auth()}>>,
                |        "method": "${item.method.methodName.toUpperCase()}",
                |        "header": [
                |            {
                |                "key": "Content-Type",
                |                "value": "application/json"
                |            }
                |        ],
                |        "body": {
                |            "mode": "raw",
                |            "raw": "${actionExample(item).escapeJson().escapeAll()}"
                |        },
                |        "url": {
                |            "raw": "{{host}}/{{projectKey}}${item.resource.relativeUri.template}/{{${item.resource.resourcePathName.singularize()}-id}}",
                |            "host": [
                |                "{{host}}"
                |            ],
                |            "path": [
                |                "{{projectKey}}",
                |                "${item.resource.resourcePathName}",
                |                "{{${item.resource.resourcePathName}-id}}"
                |            ],
                |            "query": [
                |                <<${if (item.queryParameters.isNotEmpty()) item.queryParameters.joinToString(",\n") { it.queryParam() } else ""}>>
                |            ]
                |        },
                |        "description": "${item.description}"
                |    },
                |    "response": []
                |}
            """.trimMargin()
        return ""
    }

    private fun getProject(oauth: OAuth20Settings, item: ItemGenModel): String {
        return """
            |{
            |    "name": "Get ${item.name.singularize()}",
            |    "event": [
            |        {
            |            "listen": "test",
            |            "script": {
            |                "type": "text/javascript",
            |                "exec": [
            |                    <<${testScript(item, "")}>>
            |                ]
            |            }
            |        }
            |    ],
            |    "request": {
            |        "auth":
            |            <<${auth()}>>,
            |        "method": "${item.method.methodName.toUpperCase()}",
            |        "header": [
            |            {
            |                "key": "Content-Type",
            |                "value": "application/json"
            |            }
            |        ],
            |        "body": {
            |            "mode": "raw",
            |            "raw": ""
            |        },
            |        "url": {
            |            "raw": "{{host}}/{{projectKey}}",
            |            "host": [
            |                "{{host}}"
            |            ],
            |            "path": [
            |                "{{projectKey}}"
            |            ],
            |            "query": [
            |                <<${if (item.queryParameters.isNotEmpty()) item.queryParameters.joinToString(",\n") { it.queryParam() } else ""}>>
            |            ]
            |        },
            |        "description": "${item.description}"
            |    },
            |    "response": []
            |}
        """.trimMargin()
    }

    private fun updateProject(oauth: OAuth20Settings, item: ItemGenModel): String {
        if (item is ActionGenModel)
            return """
                    |{
                    |    "name": "Update ${item.name.singularize()}",
                    |    "event": [
                    |        {
                    |            "listen": "test",
                    |            "script": {
                    |                "type": "text/javascript",
                    |                "exec": [
                    |                    <<${testScript(item, "")}>>
                    |                ]
                    |            }
                    |        }
                    |    ],
                    |    "request": {
                    |        "auth":
                    |            <<${auth()}>>,
                    |        "method": "${item.method.methodName.toUpperCase()}",
                    |        "header": [
                    |            {
                    |                "key": "Content-Type",
                    |                "value": "application/json"
                    |            }
                    |        ],
                    |        "body": {
                    |            "mode": "raw",
                    |            "raw": "${projectActionExample(item).escapeJson().escapeAll()}"
                    |        },
                    |        "url": {
                    |            "raw": "{{host}}/{{projectKey}}",
                    |            "host": [
                    |                "{{host}}"
                    |            ],
                    |            "path": [
                    |                "{{projectKey}}"
                    |            ],
                    |            "query": [
                    |                <<${if (item.queryParameters.isNotEmpty()) item.queryParameters.joinToString(",\n") { it.queryParam() } else ""}>>
                    |            ]
                    |        },
                    |        "description": "${item.description}"
                    |    },
                    |    "response": []
                    |}
                """.trimMargin()
        return ""
    }

    private fun projectAction(oauth: OAuth20Settings, item: ItemGenModel): String {
        if (item is ActionGenModel)
            return """
                |{
                |    "name": "${item.type.discriminatorValue.capitalize()}",
                |    "event": [
                |        {
                |            "listen": "test",
                |            "script": {
                |                "type": "text/javascript",
                |                "exec": [
                |                    <<${testScript(item, "")}>>
                |                ]
                |            }
                |        }
                |    ],
                |    "request": {
                |        "auth":
                |            <<${auth()}>>,
                |        "method": "${item.method.methodName.toUpperCase()}",
                |        "body": {
                |            "mode": "raw",
                |            "raw": "${projectActionExample(item).escapeJson().escapeAll()}"
                |        },
                |        "header": [
                |            {
                |                "key": "Content-Type",
                |                "value": "application/json"
                |            }
                |        ],
                |        "url": {
                |            "raw": "{{host}}/{{projectKey}}",
                |            "host": [
                |                "{{host}}"
                |            ],
                |            "path": [
                |                "{{projectKey}}"
                |            ],
                |            "query": [
                |                <<${if (item.queryParameters.isNotEmpty()) item.queryParameters.joinToString(",\n") { it.queryParam() } else ""}>>
                |            ]
                |        },
                |        "description": "${item.description}"
                |    },
                |    "response": []
                |}            
            """.trimMargin()
        return ""
    }

    private fun authorization(oauth: OAuth20Settings): String {
        return """
            |{
            |    "name": "Authorization",
            |    "description": "Authorization",
            |    "item": [
            |        {
            |            "name": "Obtain access token",
            |            "event": [
            |                {
            |                    "listen": "test",
            |                    "script": {
            |                        "type": "text/javascript",
            |                        "exec": [
            |                            "tests[\"Status code is 200\"] = responseCode.code === 200;",
            |                            "var data = JSON.parse(responseBody);",
            |                            "if(data.access_token){",
            |                            "    pm.environment.set(\"ctp_access_token\", data.access_token);",
            |                            "}",
            |                            "if (data.scope) {",
            |                            "    parts = data.scope.split(\" \");",
            |                            "    if (parts.length > 0) {",
            |                            "        scopeParts = parts[0].split(\":\");",
            |                            "        if (scopeParts.length >= 2) {",
            |                            "            pm.environment.set(\"projectKey\", scopeParts[1]);",
            |                            "        }",
            |                            "    }",
            |                            "}"
            |                        ]
            |                    }
            |                }
            |            ],
            |            "request": {
            |                "auth": {
            |                    "type": "basic",
            |                    "basic": {
            |                        "username": "{{ctp_client_id}}",
            |                        "password": "{{ctp_client_secret}}"
            |                    }
            |                },
            |                "method": "POST",
            |                "header": [],
            |                "body": {
            |                    "mode": "raw",
            |                    "raw": ""
            |                },
            |                "url": {
            |                    "raw": "https://{{auth_url}}${oauth.uri().path}?grant_type=client_credentials",
            |                    "protocol": "https",
            |                    "host": [
            |                        "{{auth_url}}"
            |                    ],
            |                    "path": [
            |                        "${oauth.uri().pathElements().joinToString("\", \"")}"
            |                    ],
            |                    "query": [
            |                        {
            |                            "key": "grant_type",
            |                            "value": "client_credentials",
            |                            "equals": true
            |                        }
            |                    ]
            |                },
            |                "description": "Use this request to obtain an access token for your commercetools platform project via Client Credentials Flow. As a prerequisite you must have filled out environment variables in Postman for projectKey, client_id and client_secret to use this."
            |            },
            |            "response": []
            |        },
            |        {
            |            "name": "Obtain access token through password flow",
            |            "event": [
            |                {
            |                    "listen": "test",
            |                    "script": {
            |                        "type": "text/javascript",
            |                        "exec": [
            |                            "tests[\"Status code is 200\"] = responseCode.code === 200;"
            |                        ]
            |                    }
            |                }
            |            ],
            |            "request": {
            |                "auth": {
            |                    "type": "basic",
            |                    "basic": {
            |                        "username": "{{ctp_client_id}}",
            |                        "password": "{{ctp_client_secret}}"
            |                    }
            |                },
            |                "method": "POST",
            |                "header": [
            |                    {
            |                        "key": "",
            |                        "value": "",
            |                        "disabled": true
            |                    }
            |                ],
            |                "body": {
            |                    "mode": "raw",
            |                    "raw": ""
            |                },
            |                "url": {
            |                    "raw": "https://{{auth_url}}/oauth/{{projectKey}}/customers/token?grant_type=password&username={{user_email}}&password={{user_password}}",
            |                    "protocol": "https",
            |                    "host": [
            |                        "{{auth_url}}"
            |                    ],
            |                    "path": [
            |                        "oauth",
            |                        "{{projectKey}}",
            |                        "customers",
            |                        "token"
            |                    ],
            |                    "query": [
            |                        {
            |                            "key": "grant_type",
            |                            "value": "password",
            |                            "equals": true
            |                        },
            |                        {
            |                            "key": "username",
            |                            "value": "",
            |                            "equals": true
            |                        },
            |                        {
            |                            "key": "password",
            |                            "value": "",
            |                            "equals": true
            |                        },
            |                        {
            |                            "key": "scope",
            |                            "value": "manage_project:{{projectKey}}",
            |                            "equals": true
            |                        }
            |                    ]
            |                },
            |                "description": "Use this request to obtain an access token for your commercetools platform project via Password Flow. As a prerequisite you must have filled out environment variables in Postman for projectKey, client_id, client_secret, user_email and user_password to use this."
            |            },
            |            "response": []
            |        },
            |        {
            |            "name": "Token for Anonymous Sessions",
            |            "event": [
            |                {
            |                    "listen": "test",
            |                    "script": {
            |                        "type": "text/javascript",
            |                        "exec": [
            |                            "tests[\"Status code is 200\"] = responseCode.code === 200;"
            |                        ]
            |                    }
            |                }
            |            ],
            |            "request": {
            |                "auth": {
            |                    "type": "basic",
            |                    "basic": {
            |                        "username": "{{ctp_client_id}}",
            |                        "password": "{{ctp_client_secret}}"
            |                    }
            |                },
            |                "method": "POST",
            |                "header": [],
            |                "body": {
            |                    "mode": "raw",
            |                    "raw": ""
            |                },
            |                "url": {
            |                    "raw": "https://{{auth_url}}/oauth/{{projectKey}}/anonymous/token?grant_type=client_credentials&scope=manage_my_profile:{{projectKey}}",
            |                    "protocol": "https",
            |                    "host": [
            |                        "{{auth_url}}"
            |                    ],
            |                    "path": [
            |                        "oauth",
            |                        "{{projectKey}}",
            |                        "anonymous",
            |                        "token"
            |                    ],
            |                    "query": [
            |                        {
            |                            "key": "grant_type",
            |                            "value": "client_credentials",
            |                            "equals": true
            |                        },
            |                        {
            |                            "key": "scope",
            |                            "value": "manage_my_profile:{{projectKey}}",
            |                            "equals": true
            |                        }
            |                    ]
            |                },
            |                "description": "Use this request to obtain an access token for a anonymous session. As a prerequisite you must have filled out environment variables in Postman for projectKey, client_id and client_secret to use this."
            |            },
            |            "response": []
            |        },
            |        {
            |            "name": "Token Introspection",
            |            "event": [
            |                {
            |                    "listen": "test",
            |                    "script": {
            |                        "type": "text/javascript",
            |                        "exec": [
            |                            "tests[\"Status code is 200\"] = responseCode.code === 200;"
            |                        ]
            |                    }
            |                }
            |            ],
            |            "request": {
            |                "auth": {
            |                    "type": "basic",
            |                    "basic": {
            |                        "username": "{{ctp_client_id}}",
            |                        "password": "{{ctp_client_secret}}"
            |                    }
            |                },
            |                "method": "POST",
            |                "header": [
            |                    {
            |                        "key": "Content-Type",
            |                        "value": "application/json"
            |                    }
            |                ],
            |                "body": {
            |                    "mode": "raw",
            |                    "raw": ""
            |                },
            |                "url": {
            |                    "raw": "https://{{auth_url}}/oauth/introspect?token={{ctp_access_token}}",
            |                    "protocol": "https",
            |                    "host": [
            |                        "{{auth_url}}"
            |                    ],
            |                    "path": [
            |                        "oauth",
            |                        "introspect"
            |                    ],
            |                    "query": [
            |                        {
            |                            "key": "token",
            |                            "value": "{{ctp_access_token}}",
            |                            "equals": true
            |                        }
            |                    ]
            |                },
            |                "description": "Token introspection allows to determine the active state of an OAuth 2.0 access token and to determine meta-information about this accces token, such as the `scope`."
            |            },
            |            "response": []
            |        }
            |    ]
            |}
        """.trimMargin().escapeAll()
    }

    private fun readme(): String {
        return """
            # commercetools API Postman collection

            This Postman collection contains examples of requests and responses for most endpoints and commands of the commercetools platform API. For every command the smallest possible payload is given. Please find optional fields in the related official documentation. Additionally the collection provides example requests and responses for specific tasks and more complex data models.

            ## Disclaimer

            This is not the official commercetools platform API documentation. Please see [here](http://docs.commercetools.com/) for a complete and approved documentation of the commercetools platform API.

            To automate frequent tasks the collection automatically manages commonly required values and parameters such as resource ids, keys and versions in Postman environment variables for you.

            Please see http://docs.commercetools.com/ for further information about the commercetools Plattform.
        """.trimIndent()
    }

    class ResourceModel(val resource: Resource, val items: List<ItemGenModel>)

    fun Api.resources(): List<ResourceModel> {
        val resources = Lists.newArrayList<ResourceModel>()
        resources.add(ResourceModel(this.resources[0], this.resources[0].projectItems()))
        resources.addAll(this.resources[0].resources.map { ResourceModel(it, it.items()) })
        return resources
    }

    fun Resource.projectItems(): List<ItemGenModel> {
        val items = Lists.newArrayList<ItemGenModel>()
        val resource = this
        if (resource.getMethod(HttpMethod.GET) != null) {
            items.add(ItemGenModel(resource, ::getProject, resource.getMethod(HttpMethod.GET)))
        }
        if (resource.getMethod(HttpMethod.POST) != null) {
            items.add(ItemGenModel(resource, ::updateProject, resource.getMethod(HttpMethod.POST)))
        }
        if (resource.getMethod(HttpMethod.POST) != null) {
            items.addAll(getActionItems(resource.getMethod(HttpMethod.POST), ::projectAction))
        }
        return items
    }

    fun Resource.items(): List<ItemGenModel> {
        val items = Lists.newArrayList<ItemGenModel>()

        if (this.getMethod(HttpMethod.GET) != null) {
            items.add(ItemGenModel(this, ::query, this.getMethod(HttpMethod.GET)))
        }
        if (this.getMethod(HttpMethod.POST) != null) {
            items.add(ItemGenModel(this, ::create, this.getMethod(HttpMethod.POST)))
        }
        val byId = this.resources.stream().filter { resource1 -> resource1.getUriParameter("ID") != null }.findFirst().orElse(null)
        val byKey = this.resources.stream().filter { resource1 -> resource1.getUriParameter("key") != null }.findFirst().orElse(null)
        if (byId?.getMethod(HttpMethod.GET) != null) {
            items.add(ItemGenModel(this, ::getByID, byId.getMethod(HttpMethod.GET)))
        }
        if (byKey?.getMethod(HttpMethod.GET) != null) {
            items.add(ItemGenModel(this, ::getByKey, byKey.getMethod(HttpMethod.GET)))
        }
        if (byId?.getMethod(HttpMethod.POST) != null) {
            items.add(ItemGenModel(this, ::updateByID, byId.getMethod(HttpMethod.POST)))
        }
        if (byKey?.getMethod(HttpMethod.POST) != null) {
            items.add(ItemGenModel(this, ::updateByKey, byKey.getMethod(HttpMethod.POST)))
        }
        if (byId?.getMethod(HttpMethod.DELETE) != null) {
            items.add(ItemGenModel(this, ::deleteByID, byId.getMethod(HttpMethod.DELETE)))
        }
        if (byKey?.getMethod(HttpMethod.DELETE) != null) {
            items.add(ItemGenModel(this, ::deleteByKey, byKey.getMethod(HttpMethod.DELETE)))
        }
        if (byId?.getMethod(HttpMethod.POST) != null) {
            items.addAll(getActionItems(byId.getMethod(HttpMethod.POST)))
        }
        return items
    }

    fun Resource.getActionItems(method: Method): List<ActionGenModel> {
        return this.getActionItems(method, ::action)
    }

    fun Resource.getActionItems(method: Method, template: KFunction2<OAuth20Settings, ItemGenModel, String>): List<ActionGenModel> {
        val actionItems = Lists.newArrayList<ActionGenModel>()

        val body = method.getBody("application/json")
        if (body != null && body.type is ObjectType) {
            val actions = (body.type as ObjectType).getProperty("actions")
            if (actions != null) {
                val actionsType = actions.type as ArrayType
                val updateActions: List<AnyType>
                if (actionsType.items is UnionType) {
                    updateActions = (actionsType.items as UnionType).oneOf[0].subTypes
                } else {
                    updateActions = actionsType.items.subTypes
                }
                for (action in updateActions) {
                    actionItems.add(ActionGenModel(action as ObjectType, this, template, method))
                }
                actionItems.sortBy { actionGenModel -> actionGenModel.discriminatorValue }
            }
        }

        return actionItems
    }

    fun ResourceModel.description(): String? {
        return this.resource.description?.value?.escapeJson()
    }

    fun String.singularize(): String {
        return English.singular(this)
    }

    fun String.escapeJson(): String {
        return StringEscapeUtils.escapeJson(this)
    }
    class ActionGenModel(val type: ObjectType, resource: Resource, template: KFunction2<OAuth20Settings, ItemGenModel, String>, method: Method) : ItemGenModel(resource, template, method) {
        val testScript: List<String>?
        private val example: String?
        val discriminatorValue: String
            get() = type.discriminatorValue

        override val description: String
            get() {
                val description = Optional.ofNullable(type.description).map<String> { it.value }.orElse(type.name)
                return StringEscapeUtils.escapeJson(description)
            }

        init {
            var example: String? = null
            var instance: Instance? = null

            if (type.getAnnotation("postman-example") != null) {
                instance = type.getAnnotation("postman-example").value
            } else if (type.examples.size > 0) {
                instance = type.examples[0].value
            }

            if (instance != null) {
                example = instance.toJson()
                try {
                    val mapper = ObjectMapper()
                    val nodes = mapper.readTree(example) as ObjectNode
                    nodes.put("action", type.discriminatorValue)

                    example = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(nodes)
                            .split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray().map { s -> "  $s" }
                            .joinToString("\n")
                            .trim { it <= ' ' }
                } catch (e: IOException) {
                }

            }

            this.example = example
            val t = type.getAnnotation("postman-test-script")
            if (t != null) {
                this.testScript = (t.value as StringInstance).value.split("\n".toRegex()).dropLastWhile { it.isEmpty() }
            } else {
                this.testScript = null
            }
        }


        override fun getExample(): String? {
            return example
        }
    }

    open class ItemGenModel(val resource: Resource, val template: KFunction2<OAuth20Settings, ItemGenModel, String>, val method: Method) {

        val name: String
            get() =
                StringCaseFormat.UPPER_CAMEL_CASE.apply(Optional.ofNullable(resource.displayName).map<String> { it.value }.orElse(resource.resourcePathName))

        open val description: String
            get() {
                val description = Optional.ofNullable(method.description).map<String> { it.value }.orElse(method.method.getName() + " " + name)
                return StringEscapeUtils.escapeJson(description)
            }

        val resourcePathName: String
            get() {
                val resourcePathName = resource.resourcePathName

                return if (resourcePathName.isEmpty()) {
                    resource.displayName.value.toLowerCase()
                } else resourcePathName
            }

        val queryParameters: List<QueryParameter>
            get() =
                method.queryParameters

        open fun getExample(): String? {
            val s = method.bodies?.
                    getOrNull(0)?.
                    type?.
                    examples?.
                    getOrNull(0)?.
                    value
            return StringEscapeUtils.escapeJson(s?.toJson())
        }
    }

    fun ResourceModel.name(): String {
        return StringCaseFormat.UPPER_CAMEL_CASE.apply(Optional.ofNullable(this.resource.displayName).map<String> { it.value }.orElse(this.resource.resourcePathName))
    }

    private fun URI.pathElements(): List<String> {
        return this.path.split("/")
    }
    private fun OAuth20Settings.uri(): URI {
        return URI.create(this.accessTokenUri)
    }

    private fun QueryParameter.queryParam() : String {
        return """
            |{
            |    "key": "${this.name}",
            |    "value": "${this.defaultValue()}",
            |    "equals": true,
            |    "disabled": ${this.required.not()}
            |}
        """.trimMargin()
    }

    fun Api.oauth(): OAuth20Settings {
        return this.securitySchemes.stream()
                .filter { securityScheme -> securityScheme.settings is OAuth20Settings }
                .map { securityScheme -> securityScheme.settings as OAuth20Settings }
                .findFirst().orElse(null)
    }

    fun QueryParameter.defaultValue(): String {
        if (this.name == "version") {
            return "{{" + English.singular(this.getParent(Resource::class.java)?.resourcePathName) + "-version}}"
        }
        val defaultValue = this.getAnnotation("postman-default-value")
        if (defaultValue != null && defaultValue.value is StringInstance) {
            val value = (defaultValue.value.value as String).replace("{{", "").replace("}}", "")

            return "{{" + English.singular(this.getParent(Resource::class.java)?.resourcePathName) + "-" + value + "}}"
        }

        return ""
    }

    fun <T> EObject.getParent(parentClass: Class<T>): T? {
        if (this.eContainer() == null) {
            return null
        }
        return if (parentClass.isInstance(this.eContainer())) {
            this.eContainer() as T
        } else this.eContainer().getParent(parentClass)
    }

    /**
folder(oauth, resource) ::=<<

{
    "name": "<resource.name>",
    "description": "<resource.description>",
    "item": [
        <resource.items: {item |<(item.template)(oauth, item)>}; separator=",">
    ],
    "event": [
        {
            "listen": "test",
            "script": {
                "type": "text/javascript",
                "exec": [
                    <test(first(resource.items), false)>
                ]
            }
        }
    ]
}
>>

query(oauth, item) ::=<<

{
    "name": "Query <item.name>",
    "event": [
        {
            "listen": "test",
            "script": {
                "type": "text/javascript",
                "exec": [
                    "tests[\"Status code is 200\"] = responseCode.code === 200;",
                    "var data = JSON.parse(responseBody);",
                    "if(data.results && data.results[0] && data.results[0].id && data.results[0].version){",
                    "    pm.environment.set(\"<item.resource.resourcePathName; format="singularize">-id\", data.results[0].id); ",
                    "    pm.environment.set(\"<item.resource.resourcePathName; format="singularize">-version\", data.results[0].version);",
                    "}",
                    "if(data.results && data.results[0] && data.results[0].key){",
                    "    pm.environment.set(\"<item.resource.resourcePathName; format="singularize">-key\", data.results[0].key); ",
                    "}"
                ]
            }
        }
    ],
    "request": {
        "auth":
            <auth(oauth)>,
        "method": "<item.method.methodName; format="uppercase">",
        "header": [
            {
                "key": "Content-Type",
                "value": "application/json"
            }
        ],
        "body": {
            "mode": "raw",
            "raw": ""
        },
        "url": {
            "raw": "{{host}}/{{projectKey}}<item.resource.relativeUri.template>",
            "host": [
                "{{host}}"
            ],
            "path": [
                "{{projectKey}}",
                "<item.resource.resourcePathName>"
            <if (item.queryParameters)>
            ],
            "query": [
                <item.queryParameters: {param |<queryParam(param)>}; separator=",">
            <endif>
            ]
        },
        "description": "<item.description>"
    },
    "response": []
}
>>

create(oauth, item) ::=<<

{
    "name": "Create <item.name; format="singularize">",
    "event": [
        {
            "listen": "test",
            "script": {
                "type": "text/javascript",
                "exec": [
                    <test(item, false)>
                ]
            }
        }
    ],
    "request": {
        "auth": <auth(oauth)>,
        "method": "<item.method.methodName; format="uppercase">",
        "header": [
            {
                "key": "Content-Type",
                "value": "application/json"
            }
        ],
        "body": {
            "mode": "raw",
            "raw": "<if (item.example)><item.example><endif>"
        },
        "url": {
            "raw": "{{host}}/{{projectKey}}<item.resource.relativeUri.template>",
            "host": [
                "{{host}}"
            ],
            "path": [
                "{{projectKey}}",
                "<item.resource.resourcePathName>"
            <if (item.queryParameters)>
            ],
            "query": [
                <item.queryParameters: {param |<queryParam(param)>}; separator=",">
            <endif>
            ]
        },
        "description": "<item.description>"
    },
    "response": []
}
>>

get(oauth, item) ::=<<
<getByParam(oauth, item, false, false)>
>>


getByID(oauth, item) ::=<<
<getByParam(oauth, item, false, true)>
>>

getByKey(oauth, item) ::=<<
<getByParam(oauth, item, "key", true)>
>>

getByParam(oauth, item, param, by) ::=<<
{
    "name": "Get <item.name; format="singularize"><if(by)> by <if(param)><param><else>ID<endif><endif>",
    "event": [
        {
            "listen": "test",
            "script": {
                "type": "text/javascript",
                "exec": [
                    <test(item, param)>
                ]
            }
        }
    ],
    "request": {
        "auth":
            <auth(oauth)>,
        "method": "<item.method.methodName; format="uppercase">",
        "header": [
            {
                "key": "Content-Type",
                "value": "application/json"
            }
        ],
        "body": {
            "mode": "raw",
            "raw": ""
        },
        "url": {
            <if (param)>
            "raw": "{{host}}/{{projectKey}}<item.resource.relativeUri.template>/<param>={{<item.resource.resourcePathName; format="singularize">-<param>}}",
            <else>
            "raw": "{{host}}/{{projectKey}}<item.resource.relativeUri.template>/{{<item.resource.resourcePathName; format="singularize">-id}}",
            <endif>
            "host": [
                "{{host}}"
            ],
            "path": [
                "{{projectKey}}",
                "<item.resource.resourcePathName>",
                <if (param)>
                "<param>={{<item.resource.resourcePathName; format="singularize">-<param>}}"
                <else>
                "{{<item.resource.resourcePathName; format="singularize">-id}}"
                <endif>
            <if (item.queryParameters)>
            ],
            "query": [
                <item.queryParameters: {param |<queryParam(param)>}; separator=",">
            <endif>
            ]
        },
        "description": "<item.description>"
    },
    "response": []
}
>>

getProject(oauth, item) ::=<<
{
    "name": "Get <item.name; format="singularize">",
    "event": [
        {
            "listen": "test",
            "script": {
                "type": "text/javascript",
                "exec": [
                    <test(item, false)>
                ]
            }
        }
    ],
    "request": {
        "auth":
            <auth(oauth)>,
        "method": "<item.method.methodName; format="uppercase">",
        "header": [
            {
                "key": "Content-Type",
                "value": "application/json"
            }
        ],
        "body": {
            "mode": "raw",
            "raw": ""
        },
        "url": {
            "raw": "{{host}}/{{projectKey}}",
            "host": [
                "{{host}}"
            ],
            "path": [
                "{{projectKey}}"
            <if (item.queryParameters)>
            ],
            "query": [
                <item.queryParameters: {param |<queryParam(param)>}; separator=",">
            <endif>
            ]
        },
        "description": "<item.description>"
    },
    "response": []
}
>>

updateByID(oauth, item) ::=<<
<updateByParam(oauth, item, false)>
>>

updateByKey(oauth, item) ::=<<
<updateByParam(oauth, item, "key")>
>>

updateProject(oauth, item) ::=<<
{
    "name": "Update <item.name; format="singularize">",
    "event": [
        {
            "listen": "test",
            "script": {
                "type": "text/javascript",
                "exec": [
                    <test(item, false)>
                ]
            }
        }
    ],
    "request": {
        "auth":
            <auth(oauth)>,
        "method": "<item.method.methodName; format="uppercase">",
        "header": [
            {
                "key": "Content-Type",
                "value": "application/json"
            }
        ],
        "body": {
            "mode": "raw",
            "raw": "<if (item.example)><item.example><else>{\n  \"version\": {{project-version}},\n  \"actions\": [\n  ]\n}<endif>"
        },
        "url": {
            "raw": "{{host}}/{{projectKey}}",
            "host": [
                "{{host}}"
            ],
            "path": [
                "{{projectKey}}"
            <if (item.queryParameters)>
            ],
            "query": [
                <item.queryParameters: {param |<queryParam(param)>}; separator=",">
            <endif>
            ]
        },
        "description": "<item.description>"
    },
    "response": []
}
>>

updateByParam(oauth, item, param) ::=<<
{
    "name": "Update <item.name; format="singularize"> by <if(param)><param><else>ID<endif>",
    "event": [
        {
            "listen": "test",
            "script": {
                "type": "text/javascript",
                "exec": [
                    <test(item, param)>
                ]
            }
        }
    ],
    "request": {
        "auth":
            <auth(oauth)>,
        "method": "<item.method.methodName; format="uppercase">",
        "header": [
            {
                "key": "Content-Type",
                "value": "application/json"
            }
        ],
        "body": {
            "mode": "raw",
            "raw": "<if (item.example)><item.example><else>{\n  \"version\": {{<item.resource.resourcePathName; format="singularize">-version}},\n  \"actions\": [\n  ]\n}<endif>"
        },
        "url": {
            <if (param)>
            "raw": "{{host}}/{{projectKey}}<item.resource.relativeUri.template>/<param>={{<item.resource.resourcePathName; format="singularize">-<param>}}",
            <else>
            "raw": "{{host}}/{{projectKey}}<item.resource.relativeUri.template>/{{<item.resource.resourcePathName; format="singularize">-id}}",
            <endif>
            "host": [
                "{{host}}"
            ],
            "path": [
                "{{projectKey}}",
                "<item.resource.resourcePathName>",
                <if (param)>
                "<param>={{<item.resource.resourcePathName; format="singularize">-<param>}}"
                <else>
                "{{<item.resource.resourcePathName; format="singularize">-id}}"
                <endif>
            <if (item.queryParameters)>
            ],
            "query": [
                <item.queryParameters: {param |<queryParam(param)>}; separator=",">
            <endif>
            ]
        },
        "description": "<item.description>"
    },
    "response": []
}
>>

deleteByID(oauth, item) ::=<<
<deleteByParam(oauth, item, false)>
>>

deleteByKey(oauth, item) ::=<<
<deleteByParam(oauth, item, "key")>
>>

deleteByParam(oauth, item, param) ::=<<
{
    "name": "Delete <item.name; format="singularize"> by <if(param)><param><else>ID<endif>",
    "event": [
        {
            "listen": "test",
            "script": {
                "type": "text/javascript",
                "exec": [
                    <test(item, param)>
                ]
            }
        }
    ],
    "request": {
        "auth":
            <auth(oauth)>,
        "method": "<item.method.methodName; format="uppercase">",
        "header": [
            {
                "key": "Content-Type",
                "value": "application/json"
            }
        ],
        "body": {
            "mode": "raw",
            "raw": ""
        },
        "url": {
            <if (param)>
            "raw": "{{host}}/{{projectKey}}<item.resource.relativeUri.template>/<param>={{<item.resource.resourcePathName; format="singularize">-<param>}}?version={{<item.resource.resourcePathName; format="singularize">-version}}",
            <else>
            "raw": "{{host}}/{{projectKey}}<item.resource.relativeUri.template>/{{<item.resource.resourcePathName; format="singularize">-id}}?version={{<item.resource.resourcePathName; format="singularize">-version}}",
            <endif>
            "host": [
                "{{host}}"
            ],
            "path": [
                "{{projectKey}}",
                "<item.resource.resourcePathName>",
                <if (param)>
                "<param>={{<item.resource.resourcePathName; format="singularize">-<param>}}"
                <else>
                "{{<item.resource.resourcePathName; format="singularize">-id}}"
                <endif>
            <if (item.queryParameters)>
            ],
            "query": [
                <item.queryParameters: {param |<queryParam(param)>}; separator=",">
            <endif>
            ]
        },
        "description": "<item.description>"
    },
    "response": []
}
>>

action(oauth, action) ::=<<
{
    "name": "<action.type.discriminatorValue; format="capitalize">",
    "event": [
        {
            "listen": "test",
            "script": {
                "type": "text/javascript",
                "exec": [
                    <test(item, false)>
                ]
            }
        }
    ],
    "request": {
        "auth":
            <auth(oauth)>,
        "method": "<item.method.methodName; format="uppercase">",
        "body": {
            "mode": "raw",
            "raw": "{\n  \"version\": {{<item.resource.resourcePathName; format="singularize">-version}},\n  \"actions\": [<if (item.example)><item.example><else>{\n    \"action\": \"<action.type.discriminatorValue>\"\n  }<endif>]\n}"
        },
        "header": [
            {
                "key": "Content-Type",
                "value": "application/json"
            }
        ],
        "url": {
            "raw": "{{host}}/{{projectKey}}/<item.resource.relativeUri.template>/{{<item.resource.resourcePathName; format="singularize">-id}}",
            "host": [
                "{{host}}"
            ],
            "path": [
                "{{projectKey}}",
                "<item.resource.resourcePathName>",
                "{{<item.resource.resourcePathName; format="singularize">-id}}"
            <if (item.queryParameters)>
            ],
            "query": [
                <item.queryParameters: {param |<queryParam(param)>}; separator=",">
            <endif>
            ]
        },
        "description": "<item.description>"
    },
    "response": []
}
>>

projectAction(oauth, action) ::=<<
{
    "name": "<action.type.discriminatorValue; format="capitalize">",
    "event": [
        {
            "listen": "test",
            "script": {
                "type": "text/javascript",
                "exec": [
                    <test(item, false)>
                ]
            }
        }
    ],
    "request": {
        "auth":
            <auth(oauth)>,
        "method": "<item.method.methodName; format="uppercase">",
        "body": {
            "mode": "raw",
            "raw": "{\n  \"version\": {{project-version}},\n  \"actions\": [<if (item.example)><item.example><else>{\n    \"action\": \"<action.type.discriminatorValue>\"\n  }<endif>]\n}"
        },
        "header": [
            {
                "key": "Content-Type",
                "value": "application/json"
            }
        ],
        "url": {
            "raw": "{{host}}/{{projectKey}}",
            "host": [
                "{{host}}"
            ],
            "path": [
                "{{projectKey}}"
            <if (item.queryParameters)>
            ],
            "query": [
                <item.queryParameters: {param |<queryParam(param)>}; separator=",">
            <endif>
            ]
        },
        "description": "<item.description>"
    },
    "response": []
}
>>

test(item, param) ::=<<
"tests[\"Status code \" + responseCode.code] = responseCode.code === 200 || responseCode.code === 201;",
"var data = JSON.parse(responseBody);",
"if(data.version){",
"    pm.environment.set(\"<item.resourcePathName; format="singularize">-version\", data.version);",
"}",
"if(data.id){",
"    pm.environment.set(\"<item.resourcePathName; format="singularize">-id\", data.id); ",
"}",
"if(data.key){",
"    pm.environment.set(\"<item.resourcePathName; format="singularize">-key\", data.key); ",
"}",
<if (param)>
"if(data.<param>){",
"    pm.environment.set(\"<item.resourcePathName; format="singularize">-<param>\", data.<param>); ",
"}",
<endif>
<if (item.testScript)>
<item.testScript: {t |"<t; format="jsonescape">"}; separator=",\n">,
<endif>
""
>>

auth(oauth) ::=<<

{
    "type": "oauth2",
    "oauth2": {
        "accessToken": "{{ctp_access_token}}",
        "addTokenTo": "header",
        "tokenType": "Bearer"
    }
}
>>

authorization(oauth) ::=<<

{
    "name": "Authorization",
    "description": "Authorization",
    "item": [
        {
            "name": "Obtain access token",
            "event": [
                {
                    "listen": "test",
                    "script": {
                        "type": "text/javascript",
                        "exec": [
                            "tests[\"Status code is 200\"] = responseCode.code === 200;",
                            "var data = JSON.parse(responseBody);",
                            "if(data.access_token){",
                            "    pm.environment.set(\"ctp_access_token\", data.access_token);",
                            "}",
                            "if (data.scope) {",
                            "    parts = data.scope.split(\" \");",
                            "    if (parts.length > 0) {",
                            "        scopeParts = parts[0].split(\":\");",
                            "        if (scopeParts.length >= 2) {",
                            "            pm.environment.set(\"projectKey\", scopeParts[1]);",
                            "        }",
                            "    }",
                            "}"
                        ]
                    }
                }
            ],
            "request": {
                "auth": {
                    "type": "basic",
                    "basic": {
                        "username": "{{ctp_client_id}}",
                        "password": "{{ctp_client_secret}}"
                    }
                },
                "method": "POST",
                "header": [],
                "body": {
                    "mode": "raw",
                    "raw": ""
                },
                "url": {
                    "raw": "https://{{auth_url}}<oauth.uri.path>?grant_type=client_credentials",
                    "protocol": "https",
                    "host": [
                        "{{auth_url}}"
                    ],
                    "path": [
                        "<oauth.uri.pathElements; separator="\",\"">"
                    ],
                    "query": [
                        {
                            "key": "grant_type",
                            "value": "client_credentials",
                            "equals": true
                        }
                    ]
                },
                "description": "Use this request to obtain an access token for your commercetools platform project via Client Credentials Flow. As a prerequisite you must have filled out environment variables in Postman for projectKey, client_id and client_secret to use this."
            },
            "response": []
        },
        {
            "name": "Obtain access token through password flow",
            "event": [
                {
                    "listen": "test",
                    "script": {
                        "type": "text/javascript",
                        "exec": [
                            "tests[\"Status code is 200\"] = responseCode.code === 200;"
                        ]
                    }
                }
            ],
            "request": {
                "auth": {
                    "type": "basic",
                    "basic": {
                        "username": "{{ctp_client_id}}",
                        "password": "{{ctp_client_secret}}"
                    }
                },
                "method": "POST",
                "header": [
                    {
                        "key": "",
                        "value": "",
                        "disabled": true
                    }
                ],
                "body": {
                    "mode": "raw",
                    "raw": ""
                },
                "url": {
                    "raw": "https://{{auth_url}}/oauth/{{projectKey}}/customers/token?grant_type=password&username={{user_email}}&password={{user_password}}",
                    "protocol": "https",
                    "host": [
                        "{{auth_url}}"
                    ],
                    "path": [
                        "oauth",
                        "{{projectKey}}",
                        "customers",
                        "token"
                    ],
                    "query": [
                        {
                            "key": "grant_type",
                            "value": "password",
                            "equals": true
                        },
                        {
                            "key": "username",
                            "value": "",
                            "equals": true
                        },
                        {
                            "key": "password",
                            "value": "",
                            "equals": true
                        },
                        {
                            "key": "scope",
                            "value": "manage_project:{{projectKey}}",
                            "equals": true
                        }
                    ]
                },
                "description": "Use this request to obtain an access token for your commercetools platform project via Password Flow. As a prerequisite you must have filled out environment variables in Postman for projectKey, client_id, client_secret, user_email and user_password to use this."
            },
            "response": []
        },
        {
            "name": "Token for Anonymous Sessions",
            "event": [
                {
                    "listen": "test",
                    "script": {
                        "type": "text/javascript",
                        "exec": [
                            "tests[\"Status code is 200\"] = responseCode.code === 200;"
                        ]
                    }
                }
            ],
            "request": {
                "auth": {
                    "type": "basic",
                    "basic": {
                        "username": "{{ctp_client_id}}",
                        "password": "{{ctp_client_secret}}"
                    }
                },
                "method": "POST",
                "header": [],
                "body": {
                    "mode": "raw",
                    "raw": ""
                },
                "url": {
                    "raw": "https://{{auth_url}}/oauth/{{projectKey}}/anonymous/token?grant_type=client_credentials&scope=manage_my_profile:{{projectKey}}",
                    "protocol": "https",
                    "host": [
                        "{{auth_url}}"
                    ],
                    "path": [
                        "oauth",
                        "{{projectKey}}",
                        "anonymous",
                        "token"
                    ],
                    "query": [
                        {
                            "key": "grant_type",
                            "value": "client_credentials",
                            "equals": true
                        },
                        {
                            "key": "scope",
                            "value": "manage_my_profile:{{projectKey}}",
                            "equals": true
                        }
                    ]
                },
                "description": "Use this request to obtain an access token for a anonymous session. As a prerequisite you must have filled out environment variables in Postman for projectKey, client_id and client_secret to use this."
            },
            "response": []
        },
        {
            "name": "Token Introspection",
            "event": [
                {
                    "listen": "test",
                    "script": {
                        "type": "text/javascript",
                        "exec": [
                            "tests[\"Status code is 200\"] = responseCode.code === 200;"
                        ]
                    }
                }
            ],
            "request": {
                "auth": {
                    "type": "basic",
                    "basic": {
                        "username": "{{ctp_client_id}}",
                        "password": "{{ctp_client_secret}}"
                    }
                },
                "method": "POST",
                "header": [
                    {
                        "key": "Content-Type",
                        "value": "application/json"
                    }
                ],
                "body": {
                    "mode": "raw",
                    "raw": ""
                },
                "url": {
                    "raw": "https://{{auth_url}}/oauth/introspect?token={{ctp_access_token}}",
                    "protocol": "https",
                    "host": [
                        "{{auth_url}}"
                    ],
                    "path": [
                        "oauth",
                        "introspect"
                    ],
                    "query": [
                        {
                            "key": "token",
                            "value": "{{ctp_access_token}}",
                            "equals": true
                        }
                    ]
                },
                "description": "Token introspection allows to determine the active state of an OAuth 2.0 access token and to determine meta-information about this accces token, such as the `scope`."
            },
            "response": []
        }
    ]
}
>>

     */
}

fun Instance.toJson(): String {
    var example = ""
    val mapper = ObjectMapper()

    val module = SimpleModule()
    module.addSerializer(ObjectInstance::class.java, ObjectInstanceSerializer())
    module.addSerializer<Instance>(ArrayInstance::class.java, InstanceSerializer())
    module.addSerializer<Instance>(IntegerInstance::class.java, InstanceSerializer())
    module.addSerializer<Instance>(BooleanInstance::class.java, InstanceSerializer())
    module.addSerializer<Instance>(StringInstance::class.java, InstanceSerializer())
    module.addSerializer<Instance>(NumberInstance::class.java, InstanceSerializer())
    mapper.registerModule(module)

    if (this is StringInstance) {
        example = this.value
    } else if (this is ObjectInstance) {
        try {
            example = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this)
        } catch (e: JsonProcessingException) {
        }

    }

    return example
}

class InstanceSerializer @JvmOverloads constructor(t: Class<Instance>? = null) : StdSerializer<Instance>(t) {

    @Throws(IOException::class)
    override fun serialize(value: Instance, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeObject(value.value)
    }
}

class ObjectInstanceSerializer @JvmOverloads constructor(t: Class<ObjectInstance>? = null) : StdSerializer<ObjectInstance>(t) {

    @Throws(IOException::class)
    override fun serialize(value: ObjectInstance, gen: JsonGenerator, provider: SerializerProvider) {
        val properties = value.value
        gen.writeStartObject()
        for (v in properties) {
            gen.writeObjectField(v.name, v.value)
        }
        gen.writeEndObject()
    }
}
