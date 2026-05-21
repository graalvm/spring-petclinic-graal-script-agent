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
import org.graalvm.scriptagent.Script;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.ScriptingExtension;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Persistence and execution support for owner-list query scripts shown on the Find Owners
 * page.
 *
 */
@Service
public class SavedOwnerQueryScriptService {

	private final SavedOwnerQueryScriptRepository savedOwnerQueryScripts;

	private final ScriptService scriptService;

	public SavedOwnerQueryScriptService(SavedOwnerQueryScriptRepository savedOwnerQueryScripts,
			ScriptService scriptService) {
		this.savedOwnerQueryScripts = savedOwnerQueryScripts;
		this.scriptService = scriptService;
	}

	public List<SavedOwnerQueryScript> findAll() {
		return this.savedOwnerQueryScripts.findAllByOrderByIdDesc();
	}

	public boolean isAlreadySaved(String name, String scriptJson) {
		if (!StringUtils.hasText(name) || !StringUtils.hasText(scriptJson)) {
			return false;
		}
		return this.savedOwnerQueryScripts.existsByNameAndScriptJson(name, scriptJson);
	}

	public SavedOwnerQueryScript save(String name, String scriptJson) {
		if (!StringUtils.hasText(name)) {
			throw new IllegalArgumentException("Saved owner query name must not be empty.");
		}
		if (!StringUtils.hasText(scriptJson)) {
			throw new IllegalArgumentException("Saved owner query script JSON must not be empty.");
		}

		SavedOwnerQueryScript savedQuery = new SavedOwnerQueryScript();
		savedQuery.setName(name);
		savedQuery.setScriptJson(scriptJson);
		return this.savedOwnerQueryScripts.save(savedQuery);
	}

	public void delete(Integer id) {
		if (id == null) {
			throw new IllegalArgumentException("Saved owner query id must not be null.");
		}
		if (!this.savedOwnerQueryScripts.existsById(id)) {
			throw new IllegalArgumentException("Saved owner query was not found.");
		}
		this.savedOwnerQueryScripts.deleteById(id);
	}

	public SavedOwnerQueryExecution execute(Integer id) {
		if (id == null) {
			throw new IllegalArgumentException("Saved owner query id must not be null.");
		}
		SavedOwnerQueryScript savedQuery = this.savedOwnerQueryScripts.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("Saved owner query was not found."));
		Script<ScriptingExtension> script = PetClinicScriptExtensions.fromJson(savedQuery.getScriptJson());
		ScriptService.PreviewResult preview = this.scriptService.preview(script);
		if (preview.scriptKind() != ScriptService.ScriptKind.OWNER_QUERY) {
			throw new IllegalArgumentException("Saved owner query must not modify data.");
		}
		if (!preview.scriptResult().present()
				|| ScriptService.extractOwnerIdsFromResult(preview.scriptResult().value()).isEmpty()) {
			throw new IllegalArgumentException("Saved owner query did not return an owner list.");
		}
		return new SavedOwnerQueryExecution(savedQuery, preview.scriptResult().value());
	}

	public record SavedOwnerQueryExecution(SavedOwnerQueryScript savedQuery, Object scriptResultValue) {

	}

}
