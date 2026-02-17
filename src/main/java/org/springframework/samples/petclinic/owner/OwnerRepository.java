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
package org.springframework.samples.petclinic.owner;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository class for <code>Owner</code> domain objects. All method names are compliant
 * with Spring Data naming conventions so this interface can easily be extended for Spring
 * Data. See:
 * https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#repositories.query-methods.query-creation
 *
 * @author Ken Krebs
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Michael Isvy
 * @author Wick Dynex
 */
public interface OwnerRepository extends JpaRepository<Owner, Integer> {

	/**
	 * Retrieve {@link Owner}s from the data store by last name, returning all owners
	 * whose last name <i>starts</i> with the given name.
	 * @param lastName Value to search for
	 * @return a Collection of matching {@link Owner}s (or an empty Collection if none
	 * found)
	 */
	Page<Owner> findByLastNameStartingWith(String lastName, Pageable pageable);

	/**
	 * Retrieve {@link Owner}s from the data store by first and last name, returning all
	 * owners whose first and last names <i>start</i> with the given values.
	 * @param lastName Value to search for in the last name
	 * @param firstName Value to search for in the first name
	 * @return a Collection of matching {@link Owner}s (or an empty Collection if none
	 * found)
	 */
	Page<Owner> findByLastNameStartingWithAndFirstNameStartingWith(String lastName, String firstName,
			Pageable pageable);

	/**
	 * Retrieve owners by a last-name prefix (case-insensitive).
	 * @param lastNamePrefix value to search for in the last name
	 * @return matching owners
	 */
	List<Owner> findByLastNameStartingWithIgnoreCase(String lastNamePrefix);

	/**
	 * Retrieve owners by first and last-name prefixes (case-insensitive).
	 * @param firstNamePrefix value to search for in the first name
	 * @param lastNamePrefix value to search for in the last name
	 * @return matching owners
	 */
	List<Owner> findByFirstNameStartingWithIgnoreCaseAndLastNameStartingWithIgnoreCase(String firstNamePrefix,
			String lastNamePrefix);

	/**
	 * Retrieve owners by city prefix (case-insensitive).
	 * @param cityPrefix value to search for in city
	 * @return matching owners
	 */
	List<Owner> findByCityStartingWithIgnoreCase(String cityPrefix);

	/**
	 * Retrieve owners with pets whose type has the specified name (case-insensitive).
	 * @param petTypeName pet type name to match
	 * @return matching owners
	 */
	@Query("select distinct o from Owner o join o.pets p join p.type t where lower(t.name) = lower(:petTypeName)")
	List<Owner> findOwnersByPetTypeName(@Param("petTypeName") String petTypeName);

	/**
	 * Retrieve owners with pets whose name starts with the provided prefix
	 * (case-insensitive).
	 * @param petNamePrefix pet-name prefix to match
	 * @return matching owners
	 */
	@Query("select distinct o from Owner o join o.pets p where lower(p.name) like lower(concat(:petNamePrefix, '%'))")
	List<Owner> findOwnersByPetNameStartingWith(@Param("petNamePrefix") String petNamePrefix);

	/**
	 * Retrieve owners with visits whose description contains the provided fragment
	 * (case-insensitive).
	 * @param descriptionFragment visit description fragment to match
	 * @return matching owners
	 */
	@Query("select distinct o from Owner o join o.pets p join p.visits v "
			+ "where lower(v.description) like lower(concat('%', :descriptionFragment, '%'))")
	List<Owner> findOwnersByVisitDescriptionContaining(@Param("descriptionFragment") String descriptionFragment);

	/**
	 * Retrieve owners that have at least one visit date in the provided inclusive range.
	 * @param startDate start date (inclusive)
	 * @param endDate end date (inclusive)
	 * @return matching owners
	 */
	@Query("select distinct o from Owner o join o.pets p join p.visits v where v.date between :startDate and :endDate")
	List<Owner> findOwnersByVisitDateBetween(@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	/**
	 * Retrieve an {@link Owner} from the data store by id.
	 * <p>
	 * This method returns an {@link Optional} containing the {@link Owner} if found. If
	 * no {@link Owner} is found with the provided id, it will return an empty
	 * {@link Optional}.
	 * </p>
	 * @param id the id to search for
	 * @return an {@link Optional} containing the {@link Owner} if found, or an empty
	 * {@link Optional} if not found.
	 * @throws IllegalArgumentException if the id is null (assuming null is not a valid
	 * input for id)
	 */
	Optional<Owner> findById(Integer id);

}
