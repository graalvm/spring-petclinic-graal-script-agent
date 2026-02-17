/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.script;

import java.util.List;

import org.graalvm.scriptagent.Schema;
import org.graalvm.scriptagent.Script;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.ExtensionSelector;

import static org.assertj.core.api.Assertions.assertThat;

class PetClinicScriptExtensionsTests {

	@Test
	void shouldCreateSchemaWithDateStringsAndDescriptions() {
		Schema schema = PetClinicScriptExtensions.scriptSchema();

		assertThat(schema.requireType("OwnersApi")).extracting(Schema.SchemaType::description)
			.isEqualTo("Query API for Spring PetClinic owners.");
		assertThat(schema.requireMethod("ExtensionSelector#choose"))
			.extracting(method -> method.returnType().targetId())
			.isEqualTo("ScriptingExtension");
		assertThat(schema.requireMethod("OwnerQueryHierarchyResultExtension#execute"))
			.extracting(method -> method.returnType().targetId())
			.isEqualTo("OwnerHierarchyResult");
		assertThat(schema.requireMethod("OwnerQueryHierarchyResultExtension#execute").parameters())
			.extracting(parameter -> parameter.type().targetId())
			.containsExactly("OwnersApi");
		assertThat(schema.requireMethod("OwnerQueryJsonResultExtension#execute"))
			.extracting(method -> method.returnType().targetId())
			.isEqualTo("JsonResult");
		assertThat(schema.requireMethod("OwnerQueryJsonResultExtension#execute").parameters())
			.extracting(parameter -> parameter.type().targetId())
			.containsExactly("OwnersApi");
		assertThat(schema.requireMethod("ModificationExtension#execute").parameters())
			.extracting(parameter -> parameter.type().targetId())
			.containsExactly("OwnersApi", "ModificationApi");
		Schema.TypeUse ownerEntriesType = schema.requireMethod("OwnerHierarchyResult#owners").returnType();
		assertThat(ownerEntriesType.targetId()).isEqualTo("List");
		assertThat(ownerEntriesType.arguments()).extracting(Schema.TypeUse::targetId)
			.containsExactly("OwnerResultEntry");
		assertThat(schema.requireMethod("OwnerResultEntry#owner")).extracting(method -> method.returnType().targetId())
			.isEqualTo("OwnerView");
		Schema.TypeUse petEntriesType = schema.requireMethod("OwnerResultEntry#petsToDisplay").returnType();
		assertThat(petEntriesType.targetId()).isEqualTo("List");
		assertThat(petEntriesType.arguments()).extracting(Schema.TypeUse::targetId).containsExactly("PetResultEntry");
		assertThat(schema.requireMethod("PetResultEntry#pet")).extracting(method -> method.returnType().targetId())
			.isEqualTo("PetView");
		Schema.TypeUse visitsType = schema.requireMethod("PetResultEntry#visitsToDisplay").returnType();
		assertThat(visitsType.targetId()).isEqualTo("List");
		assertThat(visitsType.arguments()).extracting(Schema.TypeUse::targetId).containsExactly("VisitResultEntry");
		assertThat(schema.requireMethod("VisitResultEntry#visit")).extracting(method -> method.returnType().targetId())
			.isEqualTo("VisitView");
		assertThat(schema.requireMethod("OwnerResultEntry#shouldDisplayField").parameters())
			.extracting(parameter -> parameter.type().targetId())
			.containsExactly("OwnerViewField");
		assertThat(schema.requireMethod("PetResultEntry#shouldDisplayField").parameters())
			.extracting(parameter -> parameter.type().targetId())
			.containsExactly("PetViewField");
		assertThat(schema.requireMethod("VisitResultEntry#shouldDisplayField").parameters())
			.extracting(parameter -> parameter.type().targetId())
			.containsExactly("VisitViewField");
		assertThat(schema.requireType("ScriptingExtension")).isNotNull();
		assertThat(schema.requireType("OwnerQueryHierarchyResultExtension")).isNotNull();
		assertThat(schema.requireType("OwnerQueryJsonResultExtension")).isNotNull();
		assertThat(schema.requireType("ModificationExtension")).isNotNull();
		assertThat(((Schema.ObjectType) schema.requireType("OwnerViewField")).enumConstants())
			.extracting(Schema.EnumConstant::name)
			.containsExactlyInAnyOrder("id", "firstName", "lastName", "address", "city", "telephone");
		assertThat(((Schema.ObjectType) schema.requireType("PetViewField")).enumConstants())
			.extracting(Schema.EnumConstant::name)
			.containsExactlyInAnyOrder("id", "name", "birthDate", "type");
		assertThat(schema.requireType("VisitViewField")).isNotNull();
		assertThat(schema.requireMethod("JsonResult#json")).extracting(method -> method.returnType().targetId())
			.isEqualTo("String");
		assertThat(schema.requireMethod("VisitView#getDate")).extracting(method -> method.returnType().targetId())
			.isEqualTo("String");
		assertThat(schema.requireMethod("PetView#getBirthDate")).extracting(method -> method.returnType().targetId())
			.isEqualTo("String");
		assertThat(schema.requireMethod("VisitView#getDate")).extracting(Schema.MethodElement::description)
			.isEqualTo(
					"Returns the visit date as a string in yyyy-MM-dd format, or null if the visit date is missing.");
		assertThat(schema.requireMethod("PetView#getBirthDate")).extracting(Schema.MethodElement::description)
			.isEqualTo(
					"Returns the pet birth date as a string in yyyy-MM-dd format, or null if the birth date is missing.");
		assertThat(schema.requireMethod("ModificationApi#addOwner"))
			.extracting(method -> method.parameters().stream().map(Schema.SchemaParameter::name).toList())
			.isEqualTo(List.of("firstName", "lastName", "address", "city", "telephone"));
	}

	@Test
	void shouldCreateScriptWithConfiguredProperties() {
		Script<ExtensionSelector> script = PetClinicScriptTestSupport.createScript(
				"() => util.implement(types.OwnerQueryJsonResultExtension, { execute: ownersApi => util.implement(types.JsonResult, { json: () => JSON.stringify(ownersApi.findByCityStartingWith('Madison').length) }) })",
				"Show Owners");

		assertThat(script.property(PetClinicScriptExtensions.CAPTION_PROPERTY)).isEqualTo("Show Owners");
	}

}
