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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetType;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.JsonResult;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.OwnerHierarchyResult;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.OwnerResultEntry;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.OwnerViewField;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.PetResultEntry;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.PetViewField;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.VisitResultEntry;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.VisitViewField;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScriptServiceTests {

	@Test
	void shouldRejectErrorScriptResult() {
		assertThatThrownBy(() -> ScriptService.validateScriptResult(Map.of("ok", false, "error", "Owner not found")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Script returned an error result: Owner not found");
	}

	@Test
	void shouldAcceptSuccessfulScriptResult() {
		assertThatCode(() -> ScriptService.validateScriptResult(Map.of("ok", true, "result", "done")))
			.doesNotThrowAnyException();
	}

	@Test
	void shouldIgnoreScriptResultWithoutOkField() {
		assertThatCode(() -> ScriptService.validateScriptResult(Map.of("result", "done"))).doesNotThrowAnyException();
	}

	@Test
	void shouldExtractWrappedScriptResultValue() {
		ScriptService.ScriptResultData scriptResult = ScriptService.extractScriptResultData("""
				{"ok":true,"result":[{"id":1,"firstName":"George","lastName":"Franklin"},{"telephone":"6085551023"}]}
				""");

		assertThat(scriptResult.present()).isTrue();
		assertThat(scriptResult.value()).isEqualTo(List
			.of(Map.of("id", 1, "firstName", "George", "lastName", "Franklin"), Map.of("telephone", "6085551023")));
	}

	@Test
	void shouldTreatNullScriptResultAsAbsent() {
		ScriptService.ScriptResultData scriptResult = ScriptService.extractScriptResultData("null");

		assertThat(scriptResult.present()).isFalse();
		assertThat(scriptResult.value()).isNull();
	}

	@Test
	void shouldExtractJsonResultValue() {
		ScriptService.ScriptResultData scriptResult = ScriptService
			.extractScriptResultData((JsonResult) () -> "{\"name\":\"Unda\"}");

		assertThat(scriptResult.present()).isTrue();
		assertThat(scriptResult.value()).isEqualTo(Map.of("name", "Unda"));
	}

	@Test
	void shouldRejectInvalidJsonResult() {
		assertThatThrownBy(() -> ScriptService.extractScriptResultData("not-json"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Script returned invalid JSON");
	}

	@Test
	void shouldNormalizeOwnerHierarchyResultForDisplay() {
		OwnersApi.OwnerView owner = new OwnersApi.OwnerView(owner(1, "George", "Franklin"));
		OwnersApi.PetView pet = new OwnersApi.PetView(pet(2, "Leo", "cat", LocalDate.of(2020, 5, 1)));
		OwnersApi.VisitView visit = new OwnersApi.VisitView(visit(3, LocalDate.of(2026, 3, 1), "annual checkup"));

		ScriptService.ScriptResultData scriptResult = ScriptService
			.extractScriptResultData((OwnerHierarchyResult) () -> List.of(new OwnerResultEntry() {
				@Override
				public OwnersApi.OwnerView owner() {
					return owner;
				}

				@Override
				public List<PetResultEntry> petsToDisplay() {
					return List.of(new PetResultEntry() {
						@Override
						public OwnersApi.PetView pet() {
							return pet;
						}

						@Override
						public List<VisitResultEntry> visitsToDisplay() {
							return List.of(new VisitResultEntry() {
								@Override
								public OwnersApi.VisitView visit() {
									return visit;
								}

								@Override
								public boolean shouldDisplayField(VisitViewField field) {
									return true;
								}
							});
						}

						@Override
						public boolean shouldDisplayField(PetViewField field) {
							return true;
						}
					});
				}

				@Override
				public boolean shouldDisplayField(OwnerViewField field) {
					return true;
				}
			}));

		assertThat(scriptResult.present()).isTrue();
		assertThat(scriptResult.value()).isEqualTo(Map.of("owners",
				List.of(Map.of("id", 1, "firstName", "George", "lastName", "Franklin", "address", "110 W. Liberty St.",
						"city", "Madison", "telephone", "6085551023", "pets",
						List.of(Map.of("id", 2, "name", "Leo", "birthDate", "2020-05-01", "type",
								Map.of("id", 4, "name", "cat"), "visits",
								List.of(Map.of("id", 3, "date", "2026-03-01", "description", "annual checkup"))))))));
	}

	@Test
	void shouldOmitHiddenOwnerHierarchyResultFields() {
		OwnersApi.OwnerView owner = new OwnersApi.OwnerView(owner(1, "George", "Franklin"));
		OwnersApi.PetView pet = new OwnersApi.PetView(pet(2, "Leo", "cat", LocalDate.of(2020, 5, 1)));
		OwnersApi.VisitView visit = new OwnersApi.VisitView(visit(3, LocalDate.of(2026, 3, 1), "annual checkup"));

		ScriptService.ScriptResultData scriptResult = ScriptService
			.extractScriptResultData((OwnerHierarchyResult) () -> List.of(new OwnerResultEntry() {
				@Override
				public OwnersApi.OwnerView owner() {
					return owner;
				}

				@Override
				public List<PetResultEntry> petsToDisplay() {
					return List.of(new PetResultEntry() {
						@Override
						public OwnersApi.PetView pet() {
							return pet;
						}

						@Override
						public List<VisitResultEntry> visitsToDisplay() {
							return List.of(new VisitResultEntry() {
								@Override
								public OwnersApi.VisitView visit() {
									return visit;
								}

								@Override
								public boolean shouldDisplayField(VisitViewField field) {
									return field == VisitViewField.date;
								}
							});
						}

						@Override
						public boolean shouldDisplayField(PetViewField field) {
							return field == PetViewField.name;
						}
					});
				}

				@Override
				public boolean shouldDisplayField(OwnerViewField field) {
					return field == OwnerViewField.firstName || field == OwnerViewField.lastName;
				}
			}));

		assertThat(scriptResult.present()).isTrue();
		assertThat(scriptResult.value())
			.isEqualTo(Map.of("owners", List.of(Map.of("id", 1, "firstName", "George", "lastName", "Franklin", "pets",
					List.of(Map.of("name", "Leo", "visits", List.of(Map.of("date", "2026-03-01"))))))));
	}

	@Test
	void shouldOmitOwnerIdUnlessFirstAndLastNameAreDisplayed() {
		OwnersApi.OwnerView owner = new OwnersApi.OwnerView(owner(1, "George", "Franklin"));

		ScriptService.ScriptResultData scriptResult = ScriptService
			.extractScriptResultData((OwnerHierarchyResult) () -> List.of(new OwnerResultEntry() {
				@Override
				public OwnersApi.OwnerView owner() {
					return owner;
				}

				@Override
				public List<PetResultEntry> petsToDisplay() {
					return List.of();
				}

				@Override
				public boolean shouldDisplayField(OwnerViewField field) {
					return field == OwnerViewField.firstName;
				}
			}));

		assertThat(scriptResult.present()).isTrue();
		assertThat(scriptResult.value()).isEqualTo(Map.of("owners", List.of(Map.of("firstName", "George"))));
	}

	@Test
	void shouldOmitNullIdsFromOwnerHierarchyResult() {
		OwnersApi.OwnerView owner = new OwnersApi.OwnerView(owner(null, "John", "Doe"));
		OwnersApi.PetView pet = new OwnersApi.PetView(pet(null, "Leo", "cat", LocalDate.of(2020, 5, 1)));
		OwnersApi.VisitView visit = new OwnersApi.VisitView(visit(null, LocalDate.of(2026, 3, 1), "annual checkup"));

		ScriptService.ScriptResultData scriptResult = ScriptService
			.extractScriptResultData((OwnerHierarchyResult) () -> List.of(new OwnerResultEntry() {
				@Override
				public OwnersApi.OwnerView owner() {
					return owner;
				}

				@Override
				public List<PetResultEntry> petsToDisplay() {
					return List.of(new PetResultEntry() {
						@Override
						public OwnersApi.PetView pet() {
							return pet;
						}

						@Override
						public List<VisitResultEntry> visitsToDisplay() {
							return List.of(new VisitResultEntry() {
								@Override
								public OwnersApi.VisitView visit() {
									return visit;
								}

								@Override
								public boolean shouldDisplayField(VisitViewField field) {
									return true;
								}
							});
						}

						@Override
						public boolean shouldDisplayField(PetViewField field) {
							return true;
						}
					});
				}

				@Override
				public boolean shouldDisplayField(OwnerViewField field) {
					return true;
				}
			}));

		assertThat(scriptResult.present()).isTrue();
		assertThat(scriptResult.value())
			.isEqualTo(Map.of("owners",
					List.of(Map.of("firstName", "John", "lastName", "Doe", "address", "110 W. Liberty St.", "city",
							"Madison", "telephone", "6085551023", "pets",
							List.of(Map.of("name", "Leo", "birthDate", "2020-05-01", "type",
									Map.of("id", 4, "name", "cat"), "visits",
									List.of(Map.of("date", "2026-03-01", "description", "annual checkup"))))))));
	}

	@Test
	void shouldDetectOwnerListResult() {
		Object ownerList = List.of(Map.of("id", 1, "firstName", "George", "lastName", "Franklin"),
				Map.of("id", 2, "firstName", "Betty", "lastName", "Davis"));

		assertThat(ScriptService.looksLikeOwnerListResult(ownerList)).isTrue();
		assertThat(ScriptService.extractOwnerIdsFromResult(ownerList)).contains(List.of(1, 2));
	}

	@Test
	void shouldNotDetectNonOwnerListResult() {
		Object result = List.of(Map.of("id", 1), Map.of("id", 2));

		assertThat(ScriptService.looksLikeOwnerListResult(result)).isFalse();
		assertThat(ScriptService.extractOwnerIdsFromResult(result)).isEmpty();
	}

	@Test
	void shouldDetectStructuredOwnerDisplayResult() {
		Object result = Map.of("owners",
				List.of(Map.of("id", 1, "firstName", "George", "lastName", "Franklin", "pets", List.of())));

		assertThat(ScriptService.looksLikeOwnerListResult(result)).isTrue();
		assertThat(ScriptService.extractOwnerIdsFromResult(result)).contains(List.of(1));
	}

	private Owner owner(Integer id, String firstName, String lastName) {
		Owner owner = new Owner();
		owner.setId(id);
		owner.setFirstName(firstName);
		owner.setLastName(lastName);
		owner.setAddress("110 W. Liberty St.");
		owner.setCity("Madison");
		owner.setTelephone("6085551023");
		return owner;
	}

	private Pet pet(Integer id, String name, String typeName, LocalDate birthDate) {
		Pet pet = new Pet();
		pet.setId(id);
		pet.setName(name);
		pet.setBirthDate(birthDate);
		PetType petType = new PetType();
		petType.setId(4);
		petType.setName(typeName);
		pet.setType(petType);
		return pet;
	}

	private Visit visit(Integer id, LocalDate date, String description) {
		Visit visit = new Visit();
		visit.setId(id);
		visit.setDate(date);
		visit.setDescription(description);
		return visit;
	}

}
