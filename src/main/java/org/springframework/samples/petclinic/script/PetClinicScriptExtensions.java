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
import org.graalvm.scriptagent.annotations.SchemaDoc;
import org.graalvm.scriptagent.annotations.SchemaProperty;
import org.graalvm.scriptagent.host.JavaDiscovery;
import org.graalvm.scriptagent.nativeimage.DefaultSchemaDiscovery;
import org.springframework.util.StringUtils;

public final class PetClinicScriptExtensions {

	static final String CAPTION_PROPERTY = "caption";

	private static final String CAPTION_PROPERTY_PROMPT = "Provide a short human-readable script title.";

	private static final Schema SCRIPT_SCHEMA = JavaDiscovery.discover(ScriptingExtension.class);

	private PetClinicScriptExtensions() {
	}

	@DefaultSchemaDiscovery
	@SchemaProperty(name = PetClinicScriptExtensions.CAPTION_PROPERTY,
			prompt = PetClinicScriptExtensions.CAPTION_PROPERTY_PROMPT)
	public sealed interface ScriptingExtension
			permits OwnerQueryHierarchyResultExtension, OwnerQueryJsonResultExtension, ModificationExtension {

	}

	public non-sealed interface OwnerQueryHierarchyResultExtension extends ScriptingExtension {

		OwnerHierarchyResult execute(OwnersApi ownersApi);

	}

	public non-sealed interface OwnerQueryJsonResultExtension extends ScriptingExtension {

		JsonResult execute(OwnersApi ownersApi);

	}

	public non-sealed interface ModificationExtension extends ScriptingExtension {

		void execute(OwnersApi ownersApi, ModificationApi modificationApi);

	}

	public interface OwnerHierarchyResult {

		@SchemaDoc("Use an empty list when no owners match.")
		List<OwnerResultEntry> owners();

	}

	public interface JsonResult {

		@SchemaDoc("Returns valid JSON text. Produce it with JSON.stringify(...) instead of returning a JavaScript object.")
		String json();

	}

	public interface OwnerResultEntry {

		OwnersApi.OwnerView owner();

		@SchemaDoc("Use an empty list when pets are not part of the answer.")
		List<PetResultEntry> petsToDisplay();

		boolean shouldDisplayField(OwnerViewField field);

	}

	public enum OwnerViewField {

		id,

		firstName,

		lastName,

		address,

		city,

		telephone

	}

	public interface PetResultEntry {

		OwnersApi.PetView pet();

		@SchemaDoc("Use an empty list when visits are not part of the answer.")
		List<VisitResultEntry> visitsToDisplay();

		boolean shouldDisplayField(PetViewField field);

	}

	public enum PetViewField {

		id,

		name,

		birthDate,

		type

	}

	public interface VisitResultEntry {

		OwnersApi.VisitView visit();

		boolean shouldDisplayField(VisitViewField field);

	}

	public enum VisitViewField {

		id,

		date,

		description

	}

	static Schema scriptSchema() {
		return SCRIPT_SCHEMA;
	}

	static Script<ScriptingExtension> fromJson(String scriptJson) {
		if (!StringUtils.hasText(scriptJson)) {
			throw new IllegalArgumentException("Script JSON cannot be empty.");
		}
		return Script.fromJSON(ScriptingExtension.class, scriptJson, SCRIPT_SCHEMA);
	}

}
