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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.graalvm.scriptagent.annotations.SchemaDoc;
import org.jspecify.annotations.NullMarked;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetType;
import org.springframework.samples.petclinic.owner.Visit;

/**
 * Query API for Spring PetClinic owners used by the Scripting Agent.
 *
 */
@SchemaDoc("Query API for Spring PetClinic owners.")
public final class OwnersApi {

	private final OwnerDataAccess ownerDataAccess;

	OwnersApi(OwnerRepository ownerRepository) {
		this(new RepositoryOwnerDataAccess(ownerRepository));
	}

	OwnersApi(OwnerDataAccess ownerDataAccess) {
		this.ownerDataAccess = ownerDataAccess;
	}

	@NullMarked
	public List<OwnerView> findByLastNameStartingWith(String lastNamePrefix) {
		return toOwnerArray(this.ownerDataAccess
			.findByLastNameStartingWithIgnoreCase(normalizeQueryValue(lastNamePrefix, "lastNamePrefix")));
	}

	@NullMarked
	public List<OwnerView> findByFirstAndLastNameStartingWith(String firstNamePrefix, String lastNamePrefix) {
		return toOwnerArray(this.ownerDataAccess.findByFirstNameStartingWithIgnoreCaseAndLastNameStartingWithIgnoreCase(
				normalizeQueryValue(firstNamePrefix, "firstNamePrefix"),
				normalizeQueryValue(lastNamePrefix, "lastNamePrefix")));
	}

	@NullMarked
	public List<OwnerView> findByCityStartingWith(String cityPrefix) {
		return toOwnerArray(
				this.ownerDataAccess.findByCityStartingWithIgnoreCase(normalizeQueryValue(cityPrefix, "cityPrefix")));
	}

	@NullMarked
	public List<OwnerView> findByPetTypeName(String petTypeName) {
		String normalizedPetTypeName = normalizeQueryValue(petTypeName, "petTypeName");
		if (normalizedPetTypeName.isEmpty()) {
			throw new IllegalArgumentException("petTypeName cannot be empty.");
		}
		return toOwnerArray(this.ownerDataAccess.findOwnersByPetTypeName(normalizedPetTypeName));
	}

	@NullMarked
	public List<OwnerView> findByPetNameStartingWith(String petNamePrefix) {
		return toOwnerArray(this.ownerDataAccess
			.findOwnersByPetNameStartingWith(normalizeQueryValue(petNamePrefix, "petNamePrefix")));
	}

	@NullMarked
	public List<OwnerView> findByVisitDescriptionContaining(String descriptionFragment) {
		return toOwnerArray(this.ownerDataAccess
			.findOwnersByVisitDescriptionContaining(normalizeQueryValue(descriptionFragment, "descriptionFragment")));
	}

	public List<OwnerView> findByVisitDateBetween(long startEpochMillis, long endEpochMillis) {
		if (endEpochMillis < startEpochMillis) {
			throw new IllegalArgumentException("endEpochMillis must be greater than or equal to startEpochMillis.");
		}
		LocalDate startDate = Instant.ofEpochMilli(startEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate();
		LocalDate endDate = Instant.ofEpochMilli(endEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate();
		return toOwnerArray(this.ownerDataAccess.findOwnersByVisitDateBetween(startDate, endDate));
	}

	private List<OwnerView> toOwnerArray(List<Owner> owners) {
		return owners.stream().map(OwnerView::new).toList();
	}

	private String normalizeQueryValue(String value, String fieldName) {
		if (value == null) {
			throw new IllegalArgumentException(fieldName + " cannot be null.");
		}
		return value.strip();
	}

	interface OwnerDataAccess {

		List<Owner> findByLastNameStartingWithIgnoreCase(String lastNamePrefix);

		List<Owner> findByFirstNameStartingWithIgnoreCaseAndLastNameStartingWithIgnoreCase(String firstNamePrefix,
				String lastNamePrefix);

		List<Owner> findByCityStartingWithIgnoreCase(String cityPrefix);

		List<Owner> findOwnersByPetTypeName(String petTypeName);

		List<Owner> findOwnersByPetNameStartingWith(String petNamePrefix);

		List<Owner> findOwnersByVisitDescriptionContaining(String descriptionFragment);

		List<Owner> findOwnersByVisitDateBetween(LocalDate startDate, LocalDate endDate);

	}

	private static final class RepositoryOwnerDataAccess implements OwnerDataAccess {

		private final OwnerRepository ownerRepository;

		private RepositoryOwnerDataAccess(OwnerRepository ownerRepository) {
			this.ownerRepository = ownerRepository;
		}

		@Override
		public List<Owner> findByLastNameStartingWithIgnoreCase(String lastNamePrefix) {
			return this.ownerRepository.findByLastNameStartingWithIgnoreCase(lastNamePrefix);
		}

		@Override
		public List<Owner> findByFirstNameStartingWithIgnoreCaseAndLastNameStartingWithIgnoreCase(
				String firstNamePrefix, String lastNamePrefix) {
			return this.ownerRepository.findByFirstNameStartingWithIgnoreCaseAndLastNameStartingWithIgnoreCase(
					firstNamePrefix, lastNamePrefix);
		}

		@Override
		public List<Owner> findByCityStartingWithIgnoreCase(String cityPrefix) {
			return this.ownerRepository.findByCityStartingWithIgnoreCase(cityPrefix);
		}

		@Override
		public List<Owner> findOwnersByPetTypeName(String petTypeName) {
			return this.ownerRepository.findOwnersByPetTypeName(petTypeName);
		}

		@Override
		public List<Owner> findOwnersByPetNameStartingWith(String petNamePrefix) {
			return this.ownerRepository.findOwnersByPetNameStartingWith(petNamePrefix);
		}

		@Override
		public List<Owner> findOwnersByVisitDescriptionContaining(String descriptionFragment) {
			return this.ownerRepository.findOwnersByVisitDescriptionContaining(descriptionFragment);
		}

		@Override
		public List<Owner> findOwnersByVisitDateBetween(LocalDate startDate, LocalDate endDate) {
			return this.ownerRepository.findOwnersByVisitDateBetween(startDate, endDate);
		}

	}

	public static final class OwnerView {

		private final Owner owner;

		OwnerView(Owner owner) {
			this.owner = owner;
		}

		public Integer getId() {
			return this.owner.getId();
		}

		public String getFirstName() {
			return this.owner.getFirstName();
		}

		public String getLastName() {
			return this.owner.getLastName();
		}

		@SchemaDoc("Returns the owner's street address.")
		public String getAddress() {
			return this.owner.getAddress();
		}

		public String getCity() {
			return this.owner.getCity();
		}

		public String getTelephone() {
			return this.owner.getTelephone();
		}

		public List<PetView> getPets() {
			return this.owner.getPets().stream().map(PetView::new).toList();
		}

	}

	public static final class PetView {

		private final Pet pet;

		PetView(Pet pet) {
			this.pet = pet;
		}

		public Integer getId() {
			return this.pet.getId();
		}

		public String getName() {
			return this.pet.getName();
		}

		@SchemaDoc("Returns the pet birth date as a string in yyyy-MM-dd format, or null if the birth date is missing.")
		public String getBirthDate() {
			LocalDate birthDate = this.pet.getBirthDate();
			return birthDate != null ? birthDate.toString() : null;
		}

		public PetTypeView getType() {
			PetType petType = this.pet.getType();
			return petType != null ? new PetTypeView(petType) : null;
		}

		public List<VisitView> getVisits() {
			return this.pet.getVisits().stream().map(VisitView::new).toList();
		}

	}

	public static final class PetTypeView {

		private final PetType petType;

		PetTypeView(PetType petType) {
			this.petType = petType;
		}

		public int getId() {
			return this.petType.getId();
		}

		public String getName() {
			return this.petType.getName();
		}

	}

	public static final class VisitView {

		private final Visit visit;

		VisitView(Visit visit) {
			this.visit = visit;
		}

		public Integer getId() {
			return this.visit.getId();
		}

		@SchemaDoc("Returns the visit date as a string in yyyy-MM-dd format, or null if the visit date is missing.")
		public String getDate() {
			LocalDate visitDate = this.visit.getDate();
			return visitDate != null ? visitDate.toString() : null;
		}

		public String getDescription() {
			return this.visit.getDescription();
		}

	}

}
