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
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptResultFormatterTests {

	@Test
	void shouldNotDisplayEmptyListFields() {
		String html = ScriptResultFormatter.formatHtml(Map.of("owners",
				List.of(Map.of("id", 1, "firstName", "George", "lastName", "Franklin", "pets", List.of()))));

		assertThat(html).contains("George Franklin");
		assertThat(html).doesNotContain("pets");
		assertThat(html).doesNotContain("Empty list");
	}

	@Test
	void shouldStillDisplayTopLevelEmptyList() {
		String html = ScriptResultFormatter.formatHtml(List.of());

		assertThat(html).contains("Empty list");
	}

}
