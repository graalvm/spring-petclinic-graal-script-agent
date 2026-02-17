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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetTypeRepository;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.ExtensionSelector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ScriptServiceIntegrationTests {

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private PetTypeRepository petTypeRepository;

	@Test
	void shouldAddPetToExistingOwner() {
		ScriptService.ExecutionState state = new ScriptService.ExecutionState(true, this.ownerRepository,
				this.petTypeRepository);
		ModificationApi api = new ModificationApi(state);

		api.addPet("George", "Franklin", "Pixel", "dog", "2020-05-01");

		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPet("Pixel");
		assertThat(state.operations())
			.containsExactly("Add pet: Pixel, type dog, birth date 2020-05-01, owner George Franklin");
		assertThat(pet).isNotNull();
		assertThat(pet.getBirthDate()).isEqualTo(LocalDate.of(2020, 5, 1));
		assertThat(pet.getType()).isNotNull();
		assertThat(pet.getType().getName()).isEqualTo("dog");
	}

	@Test
	void shouldAddVisitToExistingPet() {
		ScriptService.ExecutionState state = new ScriptService.ExecutionState(true, this.ownerRepository,
				this.petTypeRepository);
		ModificationApi api = new ModificationApi(state);

		api.addVisit("Jean", "Coleman", "Samantha", "2026-03-01", "annual checkup");

		Owner owner = this.ownerRepository.findById(6).orElseThrow();
		Pet pet = owner.getPet("Samantha");
		assertThat(state.operations())
			.containsExactly("Add visit: 2026-03-01, annual checkup, pet Samantha, owner Jean Coleman");
		assertThat(pet).isNotNull();
		assertThat(pet.getVisits()).anySatisfy(visit -> {
			assertThat(visit.getDate()).isEqualTo(LocalDate.of(2026, 3, 1));
			assertThat(visit.getDescription()).isEqualTo("annual checkup");
		});
	}

	@Test
	void shouldEditExistingOwner() {
		ScriptService.ExecutionState state = new ScriptService.ExecutionState(true, this.ownerRepository,
				this.petTypeRepository);
		ModificationApi api = new ModificationApi(state);

		api.editOwner("George", "Franklin", "Georgia", "Franklin", "120 W. Main St.", "Monona", "6085551111");

		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		assertThat(state.operations())
			.containsExactly("Edit owner: George Franklin -> Georgia Franklin, 120 W. Main St., Monona, 6085551111");
		assertThat(owner.getFirstName()).isEqualTo("Georgia");
		assertThat(owner.getLastName()).isEqualTo("Franklin");
		assertThat(owner.getAddress()).isEqualTo("120 W. Main St.");
		assertThat(owner.getCity()).isEqualTo("Monona");
		assertThat(owner.getTelephone()).isEqualTo("6085551111");
	}

	@Test
	void shouldEditExistingPet() {
		ScriptService.ExecutionState state = new ScriptService.ExecutionState(true, this.ownerRepository,
				this.petTypeRepository);
		ModificationApi api = new ModificationApi(state);

		api.editPet("Jean", "Coleman", "Samantha", "Luna", "dog", "2020-05-01");

		Owner owner = this.ownerRepository.findById(6).orElseThrow();
		Pet pet = owner.getPet("Luna");
		assertThat(state.operations())
			.containsExactly("Edit pet: Samantha -> Luna, type dog, birth date 2020-05-01, owner Jean Coleman");
		assertThat(owner.getPet("Samantha")).isNull();
		assertThat(pet).isNotNull();
		assertThat(pet.getBirthDate()).isEqualTo(LocalDate.of(2020, 5, 1));
		assertThat(pet.getType()).isNotNull();
		assertThat(pet.getType().getName()).isEqualTo("dog");
	}

	@Test
	void shouldMarkPetPreviewAsIncompleteWhenOwnerOrTypeCannotBeResolved() {
		ScriptService.ExecutionState state = new ScriptService.ExecutionState(false, this.ownerRepository,
				this.petTypeRepository);
		ModificationApi api = new ModificationApi(state);

		api.addPet("Missing", "Owner", "Pixel", "dragon", "2020-05-01");

		assertThat(state.hasIncompleteOperations()).isTrue();
		assertThat(state.operations()).singleElement().satisfies(description -> {
			assertThat(description).contains("Owner not found");
			assertThat(description).contains("Unknown pet type");
		});
		assertThat(this.ownerRepository.findById(1).orElseThrow().getPet("Pixel")).isNull();
	}

	@Test
	void shouldPreviewVisitForPetAddedEarlierInSameScriptWithoutWarning() {
		ScriptService.ExecutionState state = new ScriptService.ExecutionState(false, this.ownerRepository,
				this.petTypeRepository);
		ModificationApi api = new ModificationApi(state);

		api.addPet("George", "Franklin", "Unda", "dog", "2020-05-01");
		api.addVisit("George", "Franklin", "Unda", "2026-03-01", "annual checkup");

		assertThat(state.hasIncompleteOperations()).isFalse();
		assertThat(state.operations()).containsExactly(
				"Add pet: Unda, type dog, birth date 2020-05-01, owner George Franklin",
				"Add visit: 2026-03-01, annual checkup, pet Unda, owner George Franklin");
		assertThat(this.ownerRepository.findById(1).orElseThrow().getPet("Unda")).isNull();
	}

	@Test
	void shouldExposeExistingOwnerAsClickablePreviewSegment() {
		ScriptService.ExecutionState state = new ScriptService.ExecutionState(false, this.ownerRepository,
				this.petTypeRepository);
		ModificationApi api = new ModificationApi(state);

		api.addPet("George", "Franklin", "Unda", "dog", "2020-05-01");

		assertThat(state.previewOperations()).hasSize(1);
		assertThat(state.previewOperations().get(0).segments()).containsExactly(
				new ScriptService.PreviewSegment("Add pet: Unda, type dog, birth date 2020-05-01, owner ", null),
				new ScriptService.PreviewSegment("George Franklin", 1));
	}

	@Test
	void shouldPreviewVisitForRenamedPetWithoutWarning() {
		ScriptService.ExecutionState state = new ScriptService.ExecutionState(false, this.ownerRepository,
				this.petTypeRepository);
		ModificationApi api = new ModificationApi(state);

		api.editPet("Jean", "Coleman", "Samantha", "Unda", "cat", "2012-09-04");
		api.addVisit("Jean", "Coleman", "Unda", "2026-03-01", "annual checkup");

		assertThat(state.hasIncompleteOperations()).isFalse();
		assertThat(state.operations()).containsExactly(
				"Edit pet: Samantha -> Unda, type cat, birth date 2012-09-04, owner Jean Coleman",
				"Add visit: 2026-03-01, annual checkup, pet Unda, owner Jean Coleman");
	}

	@Test
	void shouldPreviewPetAndVisitForOwnerAddedEarlierInSameScript() {
		ScriptService.ExecutionState state = new ScriptService.ExecutionState(false, this.ownerRepository,
				this.petTypeRepository);
		ModificationApi api = new ModificationApi(state);

		api.addOwner("Taylor", "Preview", "1 Preview St.", "Madison", "6085550000");
		api.addPet("Taylor", "Preview", "Nova", "cat", "2022-01-15");
		api.addVisit("Taylor", "Preview", "Nova", "2026-03-01", "first checkup");

		assertThat(state.hasIncompleteOperations()).isFalse();
		assertThat(state.operations()).containsExactly("Add owner: Taylor Preview, 1 Preview St., Madison, 6085550000",
				"Add pet: Nova, type cat, birth date 2022-01-15, owner Taylor Preview",
				"Add visit: 2026-03-01, first checkup, pet Nova, owner Taylor Preview");
		assertThat(this.ownerRepository.findByFirstNameStartingWithIgnoreCaseAndLastNameStartingWithIgnoreCase("Taylor",
				"Preview"))
			.isEmpty();
	}

	@Test
	void shouldPreviewPetForRenamedOwnerWithoutWarning() {
		ScriptService.ExecutionState state = new ScriptService.ExecutionState(false, this.ownerRepository,
				this.petTypeRepository);
		ModificationApi api = new ModificationApi(state);

		api.editOwner("George", "Franklin", "Georgia", "Franklin", "110 W. Liberty St.", "Madison", "6085551023");
		api.addPet("Georgia", "Franklin", "Nova", "dog", "2020-05-01");

		assertThat(state.hasIncompleteOperations()).isFalse();
		assertThat(state.operations()).containsExactly(
				"Edit owner: George Franklin -> Georgia Franklin, 110 W. Liberty St., Madison, 6085551023",
				"Add pet: Nova, type dog, birth date 2020-05-01, owner Georgia Franklin");
	}

	@Test
	void shouldAllowQueriesToSeePreviewStateChanges() {
		ScriptService.ExecutionState state = new ScriptService.ExecutionState(false, this.ownerRepository,
				this.petTypeRepository);
		ModificationApi modificationApi = new ModificationApi(state);
		OwnersApi ownersApi = new OwnersApi(new ScriptService.ExecutionStateOwnerDataAccess(state));

		modificationApi.editOwner("George", "Franklin", "Georgia", "Franklin", "110 W. Liberty St.", "Madison",
				"6085551023");
		modificationApi.addPet("Georgia", "Franklin", "Nova", "dog", "2020-05-01");

		assertThat(ownersApi.findByFirstAndLastNameStartingWith("George", "Franklin")).isEmpty();
		assertThat(ownersApi.findByFirstAndLastNameStartingWith("Georgia", "Frank")).singleElement()
			.satisfies(owner -> assertThat(owner.getFirstName()).isEqualTo("Georgia"));
		assertThat(ownersApi.findByPetNameStartingWith("No")).singleElement()
			.satisfies(owner -> assertThat(owner.getLastName()).isEqualTo("Franklin"));
	}

	@Test
	void shouldPreviewJsonResultExtensionWithoutAgent() {
		ScriptService service = new ScriptService(this.ownerRepository, this.petTypeRepository);

		ScriptService.PreviewResult previewResult = service.preview(script("""
				() => util.implement(types.OwnerQueryJsonResultExtension, {
					execute: ownersApi => {
						const owner = ownersApi.findByFirstAndLastNameStartingWith('George', 'Franklin')[0];
						return util.implement(types.JsonResult, {
							json: () => JSON.stringify({
								firstName: owner.getFirstName(),
								lastName: owner.getLastName(),
								address: owner.getAddress(),
								city: owner.getCity(),
								telephone: owner.getTelephone()
							})
						});
					}
				})
				""", "Top Level API"));

		assertThat(previewResult.operations()).isEmpty();
		assertThat(previewResult.hasIncompleteOperations()).isFalse();
		assertThat(previewResult.scriptKind()).isEqualTo(ScriptService.ScriptKind.OWNER_QUERY);
		assertThat(previewResult.scriptResult().present()).isTrue();
		assertThat(castResultMap(previewResult.scriptResult().value())).containsEntry("firstName", "George")
			.containsEntry("lastName", "Franklin")
			.containsEntry("address", "110 W. Liberty St.")
			.containsEntry("city", "Madison")
			.containsEntry("telephone", "6085551023");
	}

	@Test
	void shouldPreviewOwnerHierarchyResult() {
		ScriptService service = new ScriptService(this.ownerRepository, this.petTypeRepository);

		ScriptService.PreviewResult previewResult = service.preview(script("""
				() => util.implement(types.OwnerQueryHierarchyResultExtension, {
					execute: ownersApi => util.implement(types.OwnerHierarchyResult, {
						owners: () => {
							const result = [];
							for (const owner of ownersApi.findByCityStartingWith('Madison')) {
								result.push(util.implement(types.OwnerResultEntry, {
									owner: () => owner,
									petsToDisplay: () => [],
									shouldDisplayField: field => true
								}));
							}
							return result;
						}
					})
				})
				""", "Owner Hierarchy Result"));

		assertThat(previewResult.operations()).isEmpty();
		assertThat(previewResult.scriptKind()).isEqualTo(ScriptService.ScriptKind.OWNER_QUERY);
		assertThat(previewResult.scriptResult().present()).isTrue();
		assertThat(ScriptService.looksLikeOwnerListResult(previewResult.scriptResult().value())).isTrue();
		assertThat(ScriptService.extractOwnerIdsFromResult(previewResult.scriptResult().value()))
			.contains(List.of(1, 5, 8, 9));
	}

	@Test
	void shouldPreviewModificationExtension() {
		ScriptService service = new ScriptService(this.ownerRepository, this.petTypeRepository);

		ScriptService.PreviewResult previewResult = service.preview(script("""
				() => util.implement(types.ModificationExtension, {
					execute: (ownersApi, modificationApi) => {
						modificationApi.addOwner('John', 'Doe', '5520 Lacy Road', 'Fitchburg', '6085558763');
					}
				})
				""", "Add Owner"));

		assertThat(previewResult.operations()).singleElement()
			.satisfies(operation -> assertThat(operation.segments()).extracting(ScriptService.PreviewSegment::text)
				.containsExactly("Add owner: John Doe, 5520 Lacy Road, Fitchburg, 6085558763"));
		assertThat(previewResult.scriptKind()).isEqualTo(ScriptService.ScriptKind.MODIFICATION);
		assertThat(previewResult.scriptResult().present()).isFalse();
		assertThat(previewResult.scriptResult().value()).isNull();
		assertThat(this.ownerRepository.findByFirstNameStartingWithIgnoreCaseAndLastNameStartingWithIgnoreCase("John",
				"Doe"))
			.isEmpty();
	}

	@Test
	void shouldPreviewNoOpModificationExtensionAsModification() {
		ScriptService service = new ScriptService(this.ownerRepository, this.petTypeRepository);

		ScriptService.PreviewResult previewResult = service.preview(script("""
				() => util.implement(types.ModificationExtension, {
					execute: (ownersApi, modificationApi) => {
					}
				})
				""", "No-op Modification"));

		assertThat(previewResult.operations()).isEmpty();
		assertThat(previewResult.scriptKind()).isEqualTo(ScriptService.ScriptKind.MODIFICATION);
		assertThat(previewResult.scriptResult().present()).isFalse();
	}

	@Test
	void shouldPreviewNestedOwnerHierarchyResult() {
		ScriptService service = new ScriptService(this.ownerRepository, this.petTypeRepository);

		ScriptService.PreviewResult previewResult = service.preview(script("""
				() => util.implement(types.OwnerQueryHierarchyResultExtension, {
					execute: ownersApi => util.implement(types.OwnerHierarchyResult, {
						owners: () => {
							const owner = ownersApi.findByFirstAndLastNameStartingWith('Jean', 'Coleman')[0];
							const pets = [];
							for (const pet of owner.getPets()) {
								const visits = [];
								for (const visit of pet.getVisits()) {
									visits.push(util.implement(types.VisitResultEntry, {
										visit: () => visit,
										shouldDisplayField: field => true
									}));
								}
								pets.push(util.implement(types.PetResultEntry, {
									pet: () => pet,
									visitsToDisplay: () => visits,
									shouldDisplayField: field => true
								}));
							}
							return [
								util.implement(types.OwnerResultEntry, {
									owner: () => owner,
									petsToDisplay: () => pets,
									shouldDisplayField: field => true
								})
							];
						}
					})
				})
				""", "Nested Owner Hierarchy Result"));

		assertThat(previewResult.scriptResult().present()).isTrue();
		Map<String, Object> result = castResultMap(previewResult.scriptResult().value());
		List<?> owners = (List<?>) result.get("owners");
		assertThat(owners).singleElement().satisfies(ownerValue -> {
			Map<String, Object> owner = castResultMap(ownerValue);
			assertThat(owner).containsEntry("firstName", "Jean").containsEntry("lastName", "Coleman");
			assertThat((List<?>) owner.get("pets")).hasSize(2).anySatisfy(petValue -> {
				Map<String, Object> pet = castResultMap(petValue);
				assertThat((List<?>) pet.get("visits")).anySatisfy(
						visitValue -> assertThat(castResultMap(visitValue)).containsEntry("description", "rabies shot")
							.containsEntry("date", "2013-01-01"));
			});
		});
	}

	@Test
	void shouldNotExposeScriptResultForModificationScript() {
		ScriptService service = new ScriptService(this.ownerRepository, this.petTypeRepository);

		ScriptService.PreviewResult previewResult = service.preview(script(
				"() => util.implement(types.ModificationExtension, { execute: (ownersApi, modificationApi) => { modificationApi.addOwner('Taylor', 'Preview', '1 Preview St.', 'Madison', '6085550000'); } })",
				"Mutation Only"));

		assertThat(previewResult.operations()).singleElement()
			.satisfies(operation -> assertThat(operation.segments()).extracting(ScriptService.PreviewSegment::text)
				.containsExactly("Add owner: Taylor Preview, 1 Preview St., Madison, 6085550000"));
		assertThat(previewResult.scriptKind()).isEqualTo(ScriptService.ScriptKind.MODIFICATION);
		assertThat(previewResult.scriptResult().present()).isFalse();
		assertThat(previewResult.scriptResult().value()).isNull();
	}

	@Test
	void shouldRejectVisitExecutionWhenPetCannotBeResolved() {
		ScriptService.ExecutionState state = new ScriptService.ExecutionState(true, this.ownerRepository,
				this.petTypeRepository);
		ModificationApi api = new ModificationApi(state);

		assertThatThrownBy(() -> api.addVisit("George", "Franklin", "Unknown Pet", "2026-03-01", "annual checkup"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Cannot add visit")
			.hasMessageContaining("Pet not found for owner");
		assertThat(state.operations()).isEmpty();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> castResultMap(Object value) {
		return (Map<String, Object>) value;
	}

	private org.graalvm.scriptagent.Script<ExtensionSelector> script(String scriptText, String caption) {
		return PetClinicScriptTestSupport.createScript(scriptText, caption);
	}

}
