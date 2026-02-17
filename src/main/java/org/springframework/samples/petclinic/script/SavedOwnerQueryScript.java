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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.model.BaseEntity;

/**
 * Persisted query-only script that can repopulate the Find Owners page.
 *
 */
@Entity
@Table(name = "saved_owner_query_scripts")
public class SavedOwnerQueryScript extends BaseEntity {

	@Column(nullable = false)
	private String name;

	@Lob
	@Column(nullable = false)
	private String scriptJson;

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getScriptJson() {
		return this.scriptJson;
	}

	public void setScriptJson(String scriptJson) {
		this.scriptJson = scriptJson;
	}

}
