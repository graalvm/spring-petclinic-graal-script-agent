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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.graalvm.scriptagent.annotations.SchemaDoc;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetType;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.util.StringUtils;

@SchemaDoc("Modification API for Spring PetClinic owners, pets, and visits.")
public final class ModificationApi {

	private final ScriptService.ExecutionState state;

	ModificationApi(ScriptService.ExecutionState state) {
		this.state = state;
	}

	@SchemaDoc("Adds a new owner. In preview mode, records the planned modification only.")
	public void addOwner(String firstName, String lastName, String address, String city, String telephone) {
		String normalizedFirstName = normalizeValue(firstName);
		String normalizedLastName = normalizeValue(lastName);
		String normalizedAddress = normalizeValue(address);
		String normalizedCity = normalizeValue(city);
		String normalizedTelephone = normalizeValue(telephone);
		boolean hasAllRequiredFields = hasText(normalizedFirstName) && hasText(normalizedLastName)
				&& hasText(normalizedAddress) && hasText(normalizedCity) && hasText(normalizedTelephone);
		List<String> missingFields = missingFields("firstName", normalizedFirstName, "lastName", normalizedLastName,
				"address", normalizedAddress, "city", normalizedCity, "telephone", normalizedTelephone);
		boolean validTelephone = !hasText(normalizedTelephone) || normalizedTelephone.matches("\\d{10}");
		List<String> issues = new ArrayList<>();
		if (this.state.persistChanges() && !hasAllRequiredFields) {
			throw new IllegalArgumentException("All owner fields must be populated before execution.");
		}
		if (this.state.persistChanges() && !validTelephone) {
			throw new IllegalArgumentException("telephone must contain exactly 10 digits.");
		}

		Owner owner = new Owner();
		owner.setFirstName(normalizedFirstName);
		owner.setLastName(normalizedLastName);
		owner.setAddress(normalizedAddress);
		owner.setCity(normalizedCity);
		owner.setTelephone(normalizedTelephone);

		String description = "Add owner: " + displayValue(normalizedFirstName) + " " + displayValue(normalizedLastName)
				+ ", " + displayValue(normalizedAddress) + ", " + displayValue(normalizedCity) + ", "
				+ displayValue(normalizedTelephone);
		if (!hasAllRequiredFields) {
			issues.add("Missing required fields: " + String.join(", ", missingFields));
		}
		else if (!validTelephone) {
			issues.add("Invalid telephone");
		}
		description = finalizeDescription(description, issues);
		if (!this.state.persistChanges() && hasAllRequiredFields && validTelephone) {
			this.state.stageNewOwner(owner);
		}
		if (this.state.persistChanges()) {
			this.state.ownerRepository().save(owner);
		}
	}

	@SchemaDoc("""
			Adds a new pet to an existing owner identified by first and last name.
			birthDate must use yyyy-MM-dd.
			""")
	public void addPet(String ownerFirstName, String ownerLastName, String petName, String petTypeName,
			String birthDate) {
		String normalizedOwnerFirstName = normalizeValue(ownerFirstName);
		String normalizedOwnerLastName = normalizeValue(ownerLastName);
		String normalizedPetName = normalizeValue(petName);
		String normalizedPetTypeName = normalizeValue(petTypeName);
		String normalizedBirthDate = normalizeValue(birthDate);

		List<String> issues = new ArrayList<>();
		List<String> missingFields = missingFields("ownerFirstName", normalizedOwnerFirstName, "ownerLastName",
				normalizedOwnerLastName, "petName", normalizedPetName, "petTypeName", normalizedPetTypeName,
				"birthDate", normalizedBirthDate);
		if (!missingFields.isEmpty()) {
			issues.add("Missing required fields: " + String.join(", ", missingFields));
		}

		Owner owner = findOwner(normalizedOwnerFirstName, normalizedOwnerLastName, issues);
		PetType petType = findPetType(normalizedPetTypeName, issues);
		LocalDate parsedBirthDate = parseDate(normalizedBirthDate, "birthDate", issues);
		if (parsedBirthDate != null && parsedBirthDate.isAfter(LocalDate.now())) {
			issues.add("birthDate cannot be in the future");
		}
		if (owner != null && hasText(normalizedPetName) && owner.getPet(normalizedPetName, false) != null) {
			issues.add("Pet already exists for owner");
		}

		String description = "Add pet: " + displayValue(normalizedPetName) + ", type "
				+ displayValue(normalizedPetTypeName) + ", birth date " + displayValue(normalizedBirthDate) + ", owner "
				+ displayValue(normalizedOwnerFirstName) + " " + displayValue(normalizedOwnerLastName);
		if (this.state.persistChanges() && !issues.isEmpty()) {
			throw new IllegalArgumentException("Cannot add pet: " + String.join("; ", issues) + ".");
		}
		description = finalizeDescription(description, issues,
				ownerReference(owner, normalizedOwnerFirstName, normalizedOwnerLastName));

		if (this.state.persistChanges()) {
			Pet pet = new Pet();
			pet.setName(normalizedPetName);
			pet.setType(petType);
			pet.setBirthDate(parsedBirthDate);
			owner.addPet(pet);
			this.state.ownerRepository().save(owner);
		}
		else if (owner != null) {
			Pet pet = new Pet();
			pet.setName(normalizedPetName);
			pet.setType(petType);
			pet.setBirthDate(parsedBirthDate);
			owner.addPet(pet);
		}
	}

	@SchemaDoc("Edits an existing owner identified by the current first and last name.")
	public void editOwner(String currentFirstName, String currentLastName, String newFirstName, String newLastName,
			String address, String city, String telephone) {
		String normalizedCurrentFirstName = normalizeValue(currentFirstName);
		String normalizedCurrentLastName = normalizeValue(currentLastName);
		String normalizedNewFirstName = normalizeValue(newFirstName);
		String normalizedNewLastName = normalizeValue(newLastName);
		String normalizedAddress = normalizeValue(address);
		String normalizedCity = normalizeValue(city);
		String normalizedTelephone = normalizeValue(telephone);

		List<String> issues = new ArrayList<>();
		List<String> missingFields = missingFields("currentFirstName", normalizedCurrentFirstName, "currentLastName",
				normalizedCurrentLastName, "newFirstName", normalizedNewFirstName, "newLastName", normalizedNewLastName,
				"address", normalizedAddress, "city", normalizedCity, "telephone", normalizedTelephone);
		if (!missingFields.isEmpty()) {
			issues.add("Missing required fields: " + String.join(", ", missingFields));
		}
		if (hasText(normalizedTelephone) && !normalizedTelephone.matches("\\d{10}")) {
			issues.add("Invalid telephone");
		}

		Owner owner = findOwner(normalizedCurrentFirstName, normalizedCurrentLastName, issues);
		String description = "Edit owner: " + displayValue(normalizedCurrentFirstName) + " "
				+ displayValue(normalizedCurrentLastName) + " -> " + displayValue(normalizedNewFirstName) + " "
				+ displayValue(normalizedNewLastName) + ", " + displayValue(normalizedAddress) + ", "
				+ displayValue(normalizedCity) + ", " + displayValue(normalizedTelephone);
		if (this.state.persistChanges() && !issues.isEmpty()) {
			throw new IllegalArgumentException("Cannot edit owner: " + String.join("; ", issues) + ".");
		}
		description = finalizeDescription(description, issues,
				ownerReference(owner, normalizedCurrentFirstName, normalizedCurrentLastName),
				ownerReference(owner, normalizedNewFirstName, normalizedNewLastName));

		if (owner != null) {
			owner.setFirstName(normalizedNewFirstName);
			owner.setLastName(normalizedNewLastName);
			owner.setAddress(normalizedAddress);
			owner.setCity(normalizedCity);
			owner.setTelephone(normalizedTelephone);
			if (this.state.persistChanges()) {
				this.state.ownerRepository().save(owner);
			}
		}
	}

	@SchemaDoc("""
			Edits an existing pet for an owner identified by first and last name.
			birthDate must use yyyy-MM-dd.
			""")
	public void editPet(String ownerFirstName, String ownerLastName, String currentPetName, String newPetName,
			String petTypeName, String birthDate) {
		String normalizedOwnerFirstName = normalizeValue(ownerFirstName);
		String normalizedOwnerLastName = normalizeValue(ownerLastName);
		String normalizedCurrentPetName = normalizeValue(currentPetName);
		String normalizedNewPetName = normalizeValue(newPetName);
		String normalizedPetTypeName = normalizeValue(petTypeName);
		String normalizedBirthDate = normalizeValue(birthDate);

		List<String> issues = new ArrayList<>();
		List<String> missingFields = missingFields("ownerFirstName", normalizedOwnerFirstName, "ownerLastName",
				normalizedOwnerLastName, "currentPetName", normalizedCurrentPetName, "newPetName", normalizedNewPetName,
				"petTypeName", normalizedPetTypeName, "birthDate", normalizedBirthDate);
		if (!missingFields.isEmpty()) {
			issues.add("Missing required fields: " + String.join(", ", missingFields));
		}

		Owner owner = findOwner(normalizedOwnerFirstName, normalizedOwnerLastName, issues);
		Pet pet = findPet(owner, normalizedCurrentPetName, issues);
		PetType petType = findPetType(normalizedPetTypeName, issues);
		LocalDate parsedBirthDate = parseDate(normalizedBirthDate, "birthDate", issues);
		if (parsedBirthDate != null && parsedBirthDate.isAfter(LocalDate.now())) {
			issues.add("birthDate cannot be in the future");
		}
		if (owner != null && pet != null && hasText(normalizedNewPetName)) {
			Pet existingPet = owner.getPet(normalizedNewPetName, false);
			if (existingPet != null && existingPet != pet) {
				issues.add("Pet already exists for owner");
			}
		}

		String description = "Edit pet: " + displayValue(normalizedCurrentPetName) + " -> "
				+ displayValue(normalizedNewPetName) + ", type " + displayValue(normalizedPetTypeName) + ", birth date "
				+ displayValue(normalizedBirthDate) + ", owner " + displayValue(normalizedOwnerFirstName) + " "
				+ displayValue(normalizedOwnerLastName);
		if (this.state.persistChanges() && !issues.isEmpty()) {
			throw new IllegalArgumentException("Cannot edit pet: " + String.join("; ", issues) + ".");
		}
		description = finalizeDescription(description, issues,
				ownerReference(owner, normalizedOwnerFirstName, normalizedOwnerLastName));

		if (pet != null) {
			pet.setName(normalizedNewPetName);
			pet.setType(petType);
			pet.setBirthDate(parsedBirthDate);
			if (this.state.persistChanges()) {
				this.state.ownerRepository().save(owner);
			}
		}
	}

	@SchemaDoc("""
			Adds a new visit to an existing pet owned by an existing owner identified by name.
			visitDate must use yyyy-MM-dd.
			""")
	public void addVisit(String ownerFirstName, String ownerLastName, String petName, String visitDate,
			String description) {
		String normalizedOwnerFirstName = normalizeValue(ownerFirstName);
		String normalizedOwnerLastName = normalizeValue(ownerLastName);
		String normalizedPetName = normalizeValue(petName);
		String normalizedVisitDate = normalizeValue(visitDate);
		String normalizedDescription = normalizeValue(description);

		List<String> issues = new ArrayList<>();
		List<String> missingFields = missingFields("ownerFirstName", normalizedOwnerFirstName, "ownerLastName",
				normalizedOwnerLastName, "petName", normalizedPetName, "visitDate", normalizedVisitDate, "description",
				normalizedDescription);
		if (!missingFields.isEmpty()) {
			issues.add("Missing required fields: " + String.join(", ", missingFields));
		}

		Owner owner = findOwner(normalizedOwnerFirstName, normalizedOwnerLastName, issues);
		Pet pet = findPet(owner, normalizedPetName, issues);
		LocalDate parsedVisitDate = parseDate(normalizedVisitDate, "visitDate", issues);

		String operationDescription = "Add visit: " + displayValue(normalizedVisitDate) + ", "
				+ displayValue(normalizedDescription) + ", pet " + displayValue(normalizedPetName) + ", owner "
				+ displayValue(normalizedOwnerFirstName) + " " + displayValue(normalizedOwnerLastName);
		if (this.state.persistChanges() && !issues.isEmpty()) {
			throw new IllegalArgumentException("Cannot add visit: " + String.join("; ", issues) + ".");
		}
		operationDescription = finalizeDescription(operationDescription, issues,
				ownerReference(owner, normalizedOwnerFirstName, normalizedOwnerLastName));

		if (this.state.persistChanges()) {
			Visit visit = new Visit();
			visit.setDate(parsedVisitDate);
			visit.setDescription(normalizedDescription);
			pet.addVisit(visit);
			this.state.ownerRepository().save(owner);
		}
		else if (pet != null) {
			Visit visit = new Visit();
			visit.setDate(parsedVisitDate);
			visit.setDescription(normalizedDescription);
			pet.addVisit(visit);
		}
	}

	private String normalizeValue(String value) {
		return value != null ? value.trim() : null;
	}

	private boolean hasText(String value) {
		return StringUtils.hasText(value);
	}

	private String displayValue(String value) {
		return hasText(value) ? value : "(missing)";
	}

	private String finalizeDescription(String description, List<String> issues,
			ScriptService.OwnerReference... ownerReferences) {
		String warningText = null;
		if (!issues.isEmpty()) {
			this.state.setHasIncompleteOperations(true);
			warningText = "[" + String.join("; ", issues) + "]";
			description += " " + warningText;
		}
		this.state.addOperation(description, warningText, ownerReferences);
		return description;
	}

	private ScriptService.OwnerReference ownerReference(Owner owner, String firstName, String lastName) {
		if (owner == null || owner.getId() == null || !hasText(firstName) || !hasText(lastName)) {
			return null;
		}
		return new ScriptService.OwnerReference(firstName + " " + lastName, owner.getId());
	}

	private Owner findOwner(String firstName, String lastName, List<String> issues) {
		if (!hasText(firstName) || !hasText(lastName)) {
			return null;
		}
		List<Owner> matches = this.state.findOwnersByExactName(firstName, lastName);
		if (matches.isEmpty()) {
			issues.add("Owner not found");
			return null;
		}
		if (matches.size() > 1) {
			issues.add("Owner lookup is ambiguous");
			return null;
		}
		Owner owner = matches.get(0);
		if (!this.state.persistChanges() && owner.getId() != null) {
			return this.state.stageExistingOwner(owner);
		}
		return owner;
	}

	private PetType findPetType(String petTypeName, List<String> issues) {
		if (!hasText(petTypeName)) {
			return null;
		}
		return this.state.petTypeRepository()
			.findPetTypes()
			.stream()
			.filter(type -> petTypeName.equalsIgnoreCase(normalizeValue(type.getName())))
			.findFirst()
			.orElseGet(() -> {
				issues.add("Unknown pet type");
				return null;
			});
	}

	private Pet findPet(Owner owner, String petName, List<String> issues) {
		if (owner == null || !hasText(petName)) {
			return null;
		}
		List<Pet> matches = owner.getPets()
			.stream()
			.filter(pet -> petName.equalsIgnoreCase(normalizeValue(pet.getName())))
			.toList();
		if (matches.isEmpty()) {
			issues.add("Pet not found for owner");
			return null;
		}
		if (matches.size() > 1) {
			issues.add("Pet lookup is ambiguous");
			return null;
		}
		return matches.get(0);
	}

	private LocalDate parseDate(String value, String fieldName, List<String> issues) {
		if (!hasText(value)) {
			return null;
		}
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException ex) {
			issues.add(fieldName + " must use yyyy-MM-dd");
			return null;
		}
	}

	private List<String> missingFields(String... fieldNamesAndValues) {
		LinkedHashSet<String> missingFields = new LinkedHashSet<>();
		for (int i = 0; i < fieldNamesAndValues.length; i += 2) {
			String fieldName = fieldNamesAndValues[i];
			String value = fieldNamesAndValues[i + 1];
			if (!hasText(value)) {
				missingFields.add(fieldName);
			}
		}
		return List.copyOf(missingFields);
	}

}
