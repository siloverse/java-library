package io.github.siloverse.openapi.springdoc

import io.github.siloverse.selector.core.spec.FieldType
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.BooleanSchema
import io.swagger.v3.oas.models.media.DateTimeSchema
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.media.UUIDSchema
import io.swagger.v3.oas.models.parameters.Parameter

/** The OpenAPI schema for a selector value type, by the type's name. Anything unknown is a string. */
internal fun schemaFor(type: FieldType<*>): Schema<*> = when (type.name) {
    "int" -> IntegerSchema()
    "long" -> IntegerSchema().format("int64")
    "boolean" -> BooleanSchema()
    "uuid" -> UUIDSchema()
    "instant", "date-time" -> DateTimeSchema()
    else -> StringSchema()
}

internal fun arrayOf(items: Schema<*>): ArraySchema = ArraySchema().items(items)

internal fun queryParameter(name: String, description: String, schema: Schema<*>): Parameter =
    Parameter().`in`("query").name(name).description(description).required(false).schema(schema)

/** A repeatable query parameter: `?name=a&name=b`. */
internal fun repeatableQueryParameter(name: String, description: String, items: Schema<*>): Parameter =
    queryParameter(name, description, arrayOf(items)).style(Parameter.StyleEnum.FORM).explode(true)
