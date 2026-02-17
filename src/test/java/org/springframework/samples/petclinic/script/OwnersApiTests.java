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

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.Visit;

import static org.assertj.core.api.Assertions.assertThat;

class OwnersApiTests {

	@Test
	void shouldReturnPetBirthDateInIsoLocalDateFormat() {
		Pet pet = new Pet();
		pet.setBirthDate(LocalDate.of(2026, 3, 1));

		OwnersApi.PetView petView = new OwnersApi.PetView(pet);

		assertThat(petView.getBirthDate()).isEqualTo("2026-03-01");
	}

	@Test
	void shouldReturnVisitDateInIsoLocalDateFormat() {
		Visit visit = new Visit();
		visit.setDate(LocalDate.of(2026, 3, 1));

		OwnersApi.VisitView visitView = new OwnersApi.VisitView(visit);

		assertThat(visitView.getDate()).isEqualTo("2026-03-01");
	}

}
