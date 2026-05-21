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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.graalvm.polyglot.Context;
import org.graalvm.scriptagent.Sandbox;
import org.graalvm.scriptagent.Script;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetTypeRepository;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.JsonResult;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.ModificationExtension;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.OwnerHierarchyResult;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.OwnerQueryHierarchyResultExtension;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.OwnerQueryJsonResultExtension;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.OwnerResultEntry;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.OwnerViewField;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.PetResultEntry;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.PetViewField;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.ScriptingExtension;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.VisitResultEntry;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.VisitViewField;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ScriptService {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private static final Sandbox SCRIPT_SANDBOX = Sandbox.newBuilder(Sandbox.UNTRUSTED).configureContext(builder -> {
		builder.option("sandbox.MaxCPUTime", "5s");
		builder.option("sandbox.MaxHeapMemory", "134217728B");
		builder.option("sandbox.MaxASTDepth", "64");
		builder.option("sandbox.MaxThreads", "1");
		builder.option("sandbox.MaxOutputStreamSize", "1048576B");
		builder.option("sandbox.MaxErrorStreamSize", "1048576B");
	}).build();

	private final OwnerRepository ownerRepository;

	private final PetTypeRepository petTypeRepository;

	public ScriptService(OwnerRepository ownerRepository, PetTypeRepository petTypeRepository) {
		this.ownerRepository = ownerRepository;
		this.petTypeRepository = petTypeRepository;
	}

	public PreviewResult preview(Script<ScriptingExtension> script) {
		ExecutionState state = new ExecutionState(false, this.ownerRepository, this.petTypeRepository);
		ScriptExecution execution = execute(script, state);
		return new PreviewResult(state.previewOperations(), state.hasIncompleteOperations(), execution.scriptResult(),
				execution.scriptKind());
	}

	public ExecutionResult execute(Script<ScriptingExtension> script) {
		ExecutionState state = new ExecutionState(true, this.ownerRepository, this.petTypeRepository);
		ScriptExecution execution = execute(script, state);
		return new ExecutionResult(state.previewOperations(), execution.scriptResult(), execution.scriptKind());
	}

	private ScriptExecution execute(Script<ScriptingExtension> script, ExecutionState state) {
		if (script == null) {
			throw new IllegalArgumentException("Script cannot be null.");
		}
		if (!StringUtils.hasText(script.source().getCharacters().toString())) {
			throw new IllegalArgumentException("Script text cannot be empty.");
		}
		if (!StringUtils.hasText(script.property(PetClinicScriptExtensions.CAPTION_PROPERTY))) {
			throw new IllegalArgumentException("Script caption cannot be empty.");
		}

		try (Context context = SCRIPT_SANDBOX.newContextBuilder(script.source().getLanguage()).build()) {
			return executeSelectedExtension(script.bind(context), state);
		}
		catch (RuntimeException ex) {
			throw new IllegalArgumentException("Script execution failed: " + ex.getMessage(), ex);
		}
	}

	private ScriptExecution executeSelectedExtension(ScriptingExtension extension, ExecutionState state) {
		OwnersApi ownersApi = new OwnersApi(new ExecutionStateOwnerDataAccess(state));
		if (extension instanceof OwnerQueryHierarchyResultExtension ownerQueryExtension) {
			return new ScriptExecution(ScriptKind.OWNER_QUERY,
					extractScriptResultData(ownerQueryExtension.execute(ownersApi)));
		}
		if (extension instanceof OwnerQueryJsonResultExtension ownerQueryExtension) {
			return new ScriptExecution(ScriptKind.OWNER_QUERY,
					extractScriptResultData(ownerQueryExtension.execute(ownersApi)));
		}
		if (extension instanceof ModificationExtension modificationExtension) {
			modificationExtension.execute(ownersApi, new ModificationApi(state));
			return new ScriptExecution(ScriptKind.MODIFICATION, ScriptResultData.absent());
		}
		throw new IllegalArgumentException("Script did not select a supported extension.");
	}

	static ScriptResultData extractScriptResultData(OwnerHierarchyResult result) {
		if (result == null) {
			return ScriptResultData.absent();
		}
		return ScriptResultData.present(normalizeOwnerHierarchyResult(result.owners()));
	}

	static ScriptResultData extractScriptResultData(JsonResult result) {
		if (result == null) {
			return ScriptResultData.absent();
		}
		return extractScriptResultData(result.json());
	}

	static ScriptResultData extractScriptResultData(String resultJson) {
		if (!StringUtils.hasText(resultJson)) {
			return ScriptResultData.absent();
		}
		Object parsedResult = parseScriptResult(resultJson);
		validateScriptResult(parsedResult);
		Object displayResult = unwrapDisplayResult(parsedResult);
		if (displayResult == null) {
			return ScriptResultData.absent();
		}
		return ScriptResultData.present(displayResult);
	}

	private static Object parseScriptResult(String resultJson) {
		try {
			return OBJECT_MAPPER.readValue(resultJson, Object.class);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("Script returned invalid JSON: " + ex.getOriginalMessage(), ex);
		}
	}

	static void validateScriptResult(Object result) {
		if (!(result instanceof Map<?, ?> map)) {
			return;
		}
		Object ok = map.get("ok");
		if (ok instanceof Boolean booleanValue && !booleanValue) {
			throw new IllegalArgumentException("Script returned an error result: " + extractErrorMessage(map));
		}
	}

	private static String extractErrorMessage(Map<?, ?> result) {
		Object error = result.get("error");
		if (error == null) {
			return "unknown error";
		}
		return String.valueOf(error);
	}

	private static Object unwrapDisplayResult(Object result) {
		if (result == null) {
			return result;
		}
		if (result instanceof Map<?, ?> map && map.containsKey("ok") && map.containsKey("result")) {
			return map.get("result");
		}
		return result;
	}

	static boolean looksLikeOwnerListResult(Object value) {
		if (isStructuredOwnerDisplayResult(value)) {
			return extractOwnerIdsFromResult(value).isPresent();
		}
		return extractOwnerIdsFromResult(value).filter(ownerIds -> !ownerIds.isEmpty()).isPresent();
	}

	static Optional<List<Integer>> extractOwnerIdsFromResult(Object value) {
		if (value instanceof Map<?, ?> map && map.containsKey("owners")) {
			return extractOwnerIdsFromResult(map.get("owners"));
		}
		if (!(value instanceof List<?> list)) {
			return Optional.empty();
		}
		List<Integer> ownerIds = new ArrayList<>();
		for (Object item : list) {
			if (!(item instanceof Map<?, ?> itemMap)) {
				return Optional.empty();
			}
			Integer ownerId = extractOwnerId(itemMap);
			if (ownerId == null || !hasOwnerIdentity(itemMap)) {
				return Optional.empty();
			}
			ownerIds.add(ownerId);
		}
		return Optional.of(List.copyOf(ownerIds));
	}

	private static Integer extractOwnerId(Map<?, ?> itemMap) {
		Object ownerId = itemMap.get("id");
		if (ownerId instanceof Number number) {
			return number.intValue();
		}
		return null;
	}

	private static boolean hasOwnerIdentity(Map<?, ?> itemMap) {
		Object firstName = itemMap.get("firstName");
		Object lastName = itemMap.get("lastName");
		return firstName instanceof String first && StringUtils.hasText(first) && lastName instanceof String last
				&& StringUtils.hasText(last);
	}

	private static boolean isStructuredOwnerDisplayResult(Object value) {
		return value instanceof Map<?, ?> map && map.containsKey("owners") && map.size() == 1;
	}

	private static Map<String, Object> normalizeOwnerHierarchyResult(List<OwnerResultEntry> ownerEntries) {
		List<Object> owners = new ArrayList<>();
		if (ownerEntries != null) {
			for (Object ownerEntryValue : ownerEntries) {
				OwnerResultEntry ownerEntry = requireOwnerResultEntry(ownerEntryValue,
						"OwnerHierarchyResult owner entry");
				OwnersApi.OwnerView owner = requireOwnerView(ownerEntry.owner(), "OwnerHierarchyResult owner");
				List<?> petEntries = requireList(ownerEntry.petsToDisplay(), "OwnerHierarchyResult pet entries");
				owners.add(ownerDisplayMap(owner, ownerEntry, petEntries));
			}
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("owners", owners);
		return result;
	}

	private static Map<String, Object> ownerDisplayMap(OwnersApi.OwnerView owner, OwnerResultEntry ownerEntry,
			List<?> petEntries) {
		Map<String, Object> display = new LinkedHashMap<>();
		boolean displayFirstName = ownerEntry.shouldDisplayField(OwnerViewField.firstName);
		boolean displayLastName = ownerEntry.shouldDisplayField(OwnerViewField.lastName);
		Integer ownerId = owner.getId();
		if (displayFirstName && displayLastName && ownerId != null) {
			display.put("id", ownerId);
		}
		if (displayFirstName) {
			display.put("firstName", owner.getFirstName());
		}
		if (displayLastName) {
			display.put("lastName", owner.getLastName());
		}
		if (ownerEntry.shouldDisplayField(OwnerViewField.address)) {
			display.put("address", owner.getAddress());
		}
		if (ownerEntry.shouldDisplayField(OwnerViewField.city)) {
			display.put("city", owner.getCity());
		}
		if (ownerEntry.shouldDisplayField(OwnerViewField.telephone)) {
			display.put("telephone", owner.getTelephone());
		}
		List<Object> pets = new ArrayList<>();
		for (Object petEntryValue : petEntries) {
			PetResultEntry petEntry = requirePetResultEntry(petEntryValue, "OwnerHierarchyResult pet entry");
			OwnersApi.PetView pet = requirePetView(petEntry.pet(), "OwnerHierarchyResult pet");
			List<?> visits = requireList(petEntry.visitsToDisplay(), "OwnerHierarchyResult visit list");
			pets.add(petDisplayMap(pet, petEntry, visits));
		}
		if (!pets.isEmpty()) {
			display.put("pets", pets);
		}
		return display;
	}

	private static Map<String, Object> petDisplayMap(OwnersApi.PetView pet, PetResultEntry petEntry, List<?> visits) {
		Map<String, Object> display = new LinkedHashMap<>();
		Integer petId = pet.getId();
		if (petEntry.shouldDisplayField(PetViewField.id) && petId != null) {
			display.put("id", petId);
		}
		if (petEntry.shouldDisplayField(PetViewField.name)) {
			display.put("name", pet.getName());
		}
		if (petEntry.shouldDisplayField(PetViewField.birthDate)) {
			display.put("birthDate", pet.getBirthDate());
		}
		if (petEntry.shouldDisplayField(PetViewField.type)) {
			OwnersApi.PetTypeView type = pet.getType();
			display.put("type", type != null ? petTypeDisplayMap(type) : null);
		}
		List<Object> visitValues = new ArrayList<>();
		for (Object visitEntryValue : visits) {
			VisitResultEntry visitEntry = requireVisitResultEntry(visitEntryValue, "OwnerHierarchyResult visit entry");
			OwnersApi.VisitView visit = requireVisitView(visitEntry.visit(), "OwnerHierarchyResult visit");
			visitValues.add(visitDisplayMap(visit, visitEntry));
		}
		if (!visitValues.isEmpty()) {
			display.put("visits", visitValues);
		}
		return display;
	}

	private static Map<String, Object> petTypeDisplayMap(OwnersApi.PetTypeView type) {
		Map<String, Object> display = new LinkedHashMap<>();
		display.put("id", type.getId());
		display.put("name", type.getName());
		return display;
	}

	private static Map<String, Object> visitDisplayMap(OwnersApi.VisitView visit, VisitResultEntry visitEntry) {
		Map<String, Object> display = new LinkedHashMap<>();
		Integer visitId = visit.getId();
		if (visitEntry.shouldDisplayField(VisitViewField.id) && visitId != null) {
			display.put("id", visitId);
		}
		if (visitEntry.shouldDisplayField(VisitViewField.date)) {
			display.put("date", visit.getDate());
		}
		if (visitEntry.shouldDisplayField(VisitViewField.description)) {
			display.put("description", visit.getDescription());
		}
		return display;
	}

	private static OwnerResultEntry requireOwnerResultEntry(Object value, String role) {
		if (value instanceof OwnerResultEntry ownerEntry) {
			return ownerEntry;
		}
		throw invalidOwnerHierarchyResult(role, "OwnerResultEntry", value);
	}

	private static PetResultEntry requirePetResultEntry(Object value, String role) {
		if (value instanceof PetResultEntry petEntry) {
			return petEntry;
		}
		throw invalidOwnerHierarchyResult(role, "PetResultEntry", value);
	}

	private static VisitResultEntry requireVisitResultEntry(Object value, String role) {
		if (value instanceof VisitResultEntry visitEntry) {
			return visitEntry;
		}
		throw invalidOwnerHierarchyResult(role, "VisitResultEntry", value);
	}

	private static OwnersApi.OwnerView requireOwnerView(Object value, String role) {
		if (value instanceof OwnersApi.OwnerView owner) {
			return owner;
		}
		throw invalidOwnerHierarchyResult(role, "OwnerView", value);
	}

	private static OwnersApi.PetView requirePetView(Object value, String role) {
		if (value instanceof OwnersApi.PetView pet) {
			return pet;
		}
		throw invalidOwnerHierarchyResult(role, "PetView", value);
	}

	private static OwnersApi.VisitView requireVisitView(Object value, String role) {
		if (value instanceof OwnersApi.VisitView visit) {
			return visit;
		}
		throw invalidOwnerHierarchyResult(role, "VisitView", value);
	}

	private static List<?> requireList(Object value, String role) {
		if (value instanceof List<?> list) {
			return list;
		}
		throw invalidOwnerHierarchyResult(role, "List", value);
	}

	private static IllegalArgumentException invalidOwnerHierarchyResult(String role, String expectedType,
			Object value) {
		String actualType = value != null ? value.getClass().getName() : "null";
		return new IllegalArgumentException(role + " must be a " + expectedType + " but was " + actualType + ".");
	}

	static final class ExecutionState {

		private final boolean persistChanges;

		private final OwnerRepository ownerRepository;

		private final PetTypeRepository petTypeRepository;

		private final List<String> operations = new ArrayList<>();

		private final List<PreviewOperation> previewOperations = new ArrayList<>();

		private final LinkedHashMap<Integer, Owner> stagedOwnersById = new LinkedHashMap<>();

		private final List<Owner> stagedNewOwners = new ArrayList<>();

		private boolean hasIncompleteOperations;

		ExecutionState(boolean persistChanges, OwnerRepository ownerRepository, PetTypeRepository petTypeRepository) {
			this.persistChanges = persistChanges;
			this.ownerRepository = ownerRepository;
			this.petTypeRepository = petTypeRepository;
		}

		boolean persistChanges() {
			return this.persistChanges;
		}

		OwnerRepository ownerRepository() {
			return this.ownerRepository;
		}

		PetTypeRepository petTypeRepository() {
			return this.petTypeRepository;
		}

		List<String> operations() {
			return this.operations;
		}

		List<PreviewOperation> previewOperations() {
			return this.previewOperations;
		}

		void addOperation(String description, String warningText, OwnerReference... ownerReferences) {
			this.operations.add(description);
			List<OwnerReference> resolvedOwnerReferences = new ArrayList<>();
			for (OwnerReference ownerReference : ownerReferences) {
				if (ownerReference != null) {
					resolvedOwnerReferences.add(ownerReference);
				}
			}
			this.previewOperations.add(createPreviewOperation(description, warningText, resolvedOwnerReferences));
		}

		List<Owner> findOwnersByExactName(String firstName, String lastName) {
			List<Owner> matches = new ArrayList<>();
			for (Owner owner : this.stagedOwnersById.values()) {
				if (matchesExactName(owner, firstName, lastName)) {
					matches.add(owner);
				}
			}
			for (Owner owner : this.stagedNewOwners) {
				if (matchesExactName(owner, firstName, lastName)) {
					matches.add(owner);
				}
			}
			for (Owner owner : this.ownerRepository
				.findByFirstNameStartingWithIgnoreCaseAndLastNameStartingWithIgnoreCase(firstName, lastName)) {
				if (owner.getId() != null && this.stagedOwnersById.containsKey(owner.getId())) {
					continue;
				}
				if (matchesExactName(owner, firstName, lastName)) {
					matches.add(owner);
				}
			}
			return matches;
		}

		List<Owner> allOwnersSnapshot() {
			List<Owner> owners = new ArrayList<>();
			owners.addAll(this.stagedOwnersById.values());
			owners.addAll(this.stagedNewOwners);
			for (Owner owner : this.ownerRepository.findAll()) {
				if (owner.getId() != null && this.stagedOwnersById.containsKey(owner.getId())) {
					continue;
				}
				owners.add(owner);
			}
			return owners;
		}

		Owner stageExistingOwner(Owner owner) {
			Integer ownerId = owner.getId();
			if (ownerId == null) {
				return owner;
			}
			return this.stagedOwnersById.computeIfAbsent(ownerId, ignored -> copyOwner(owner));
		}

		void stageNewOwner(Owner owner) {
			if (!this.stagedNewOwners.contains(owner)) {
				this.stagedNewOwners.add(owner);
			}
		}

		boolean hasIncompleteOperations() {
			return this.hasIncompleteOperations;
		}

		void setHasIncompleteOperations(boolean hasIncompleteOperations) {
			this.hasIncompleteOperations = hasIncompleteOperations;
		}

		private boolean matchesExactName(Owner owner, String firstName, String lastName) {
			return firstName.equalsIgnoreCase(normalize(owner.getFirstName()))
					&& lastName.equalsIgnoreCase(normalize(owner.getLastName()));
		}

		private boolean startsWithIgnoreCase(String value, String prefix) {
			String normalizedValue = normalize(value);
			String normalizedPrefix = normalize(prefix);
			return normalizedValue != null && normalizedPrefix != null
					&& normalizedValue.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix.toLowerCase(Locale.ROOT));
		}

		private boolean containsIgnoreCase(String value, String fragment) {
			String normalizedValue = normalize(value);
			String normalizedFragment = normalize(fragment);
			return normalizedValue != null && normalizedFragment != null
					&& normalizedValue.toLowerCase(Locale.ROOT).contains(normalizedFragment.toLowerCase(Locale.ROOT));
		}

		private boolean petHasType(Owner owner, String petTypeName) {
			return owner.getPets()
				.stream()
				.anyMatch(pet -> pet.getType() != null && pet.getType().getName() != null
						&& pet.getType().getName().equalsIgnoreCase(petTypeName));
		}

		private boolean petNameStartsWith(Owner owner, String petNamePrefix) {
			return owner.getPets().stream().anyMatch(pet -> startsWithIgnoreCase(pet.getName(), petNamePrefix));
		}

		private boolean visitDescriptionContains(Owner owner, String descriptionFragment) {
			return owner.getPets()
				.stream()
				.flatMap(pet -> pet.getVisits().stream())
				.anyMatch(visit -> containsIgnoreCase(visit.getDescription(), descriptionFragment));
		}

		private boolean visitDateBetween(Owner owner, LocalDate startDate, LocalDate endDate) {
			return owner.getPets()
				.stream()
				.flatMap(pet -> pet.getVisits().stream())
				.anyMatch(visit -> visit.getDate() != null && !visit.getDate().isBefore(startDate)
						&& !visit.getDate().isAfter(endDate));
		}

		private String normalize(String value) {
			return value != null ? value.trim() : null;
		}

		private Owner copyOwner(Owner source) {
			Owner copy = new Owner();
			copy.setId(source.getId());
			copy.setFirstName(source.getFirstName());
			copy.setLastName(source.getLastName());
			copy.setAddress(source.getAddress());
			copy.setCity(source.getCity());
			copy.setTelephone(source.getTelephone());
			for (Pet pet : source.getPets()) {
				copy.getPets().add(copyPet(pet));
			}
			return copy;
		}

		private Pet copyPet(Pet source) {
			Pet copy = new Pet();
			copy.setId(source.getId());
			copy.setName(source.getName());
			copy.setBirthDate(source.getBirthDate());
			copy.setType(source.getType());
			for (Visit visit : source.getVisits()) {
				copy.addVisit(copyVisit(visit));
			}
			return copy;
		}

		private Visit copyVisit(Visit source) {
			Visit copy = new Visit();
			copy.setId(source.getId());
			copy.setDate(source.getDate());
			copy.setDescription(source.getDescription());
			return copy;
		}

		private PreviewOperation createPreviewOperation(String description, String warningText,
				List<OwnerReference> ownerReferences) {
			String baseDescription = warningText != null && description.endsWith(" " + warningText)
					? description.substring(0, description.length() - warningText.length() - 1) : description;
			List<PreviewSegment> segments = new ArrayList<>();
			int currentIndex = 0;
			for (OwnerReference ownerReference : ownerReferences) {
				if (ownerReference == null || ownerReference.ownerId() == null
						|| !StringUtils.hasText(ownerReference.displayName())) {
					continue;
				}
				int ownerNameIndex = baseDescription.indexOf(ownerReference.displayName(), currentIndex);
				if (ownerNameIndex < 0) {
					continue;
				}
				if (ownerNameIndex > currentIndex) {
					segments.add(new PreviewSegment(baseDescription.substring(currentIndex, ownerNameIndex), null));
				}
				segments.add(new PreviewSegment(ownerReference.displayName(), ownerReference.ownerId()));
				currentIndex = ownerNameIndex + ownerReference.displayName().length();
			}
			if (currentIndex < baseDescription.length() || segments.isEmpty()) {
				segments.add(new PreviewSegment(baseDescription.substring(currentIndex), null));
			}
			return new PreviewOperation(segments, warningText);
		}

	}

	static final class ExecutionStateOwnerDataAccess implements OwnersApi.OwnerDataAccess {

		private final ExecutionState state;

		ExecutionStateOwnerDataAccess(ExecutionState state) {
			this.state = state;
		}

		@Override
		public List<Owner> findByLastNameStartingWithIgnoreCase(String lastNamePrefix) {
			return this.state.allOwnersSnapshot()
				.stream()
				.filter(owner -> this.state.startsWithIgnoreCase(owner.getLastName(), lastNamePrefix))
				.toList();
		}

		@Override
		public List<Owner> findByFirstNameStartingWithIgnoreCaseAndLastNameStartingWithIgnoreCase(
				String firstNamePrefix, String lastNamePrefix) {
			return this.state.allOwnersSnapshot()
				.stream()
				.filter(owner -> this.state.startsWithIgnoreCase(owner.getFirstName(), firstNamePrefix)
						&& this.state.startsWithIgnoreCase(owner.getLastName(), lastNamePrefix))
				.toList();
		}

		@Override
		public List<Owner> findByCityStartingWithIgnoreCase(String cityPrefix) {
			return this.state.allOwnersSnapshot()
				.stream()
				.filter(owner -> this.state.startsWithIgnoreCase(owner.getCity(), cityPrefix))
				.toList();
		}

		@Override
		public List<Owner> findOwnersByPetTypeName(String petTypeName) {
			return this.state.allOwnersSnapshot()
				.stream()
				.filter(owner -> this.state.petHasType(owner, petTypeName))
				.toList();
		}

		@Override
		public List<Owner> findOwnersByPetNameStartingWith(String petNamePrefix) {
			return this.state.allOwnersSnapshot()
				.stream()
				.filter(owner -> this.state.petNameStartsWith(owner, petNamePrefix))
				.toList();
		}

		@Override
		public List<Owner> findOwnersByVisitDescriptionContaining(String descriptionFragment) {
			return this.state.allOwnersSnapshot()
				.stream()
				.filter(owner -> this.state.visitDescriptionContains(owner, descriptionFragment))
				.toList();
		}

		@Override
		public List<Owner> findOwnersByVisitDateBetween(LocalDate startDate, LocalDate endDate) {
			return this.state.allOwnersSnapshot()
				.stream()
				.filter(owner -> this.state.visitDateBetween(owner, startDate, endDate))
				.toList();
		}

	}

	record OwnerReference(String displayName, Integer ownerId) {
	}

	public record PreviewSegment(String text, Integer ownerId) {

		public PreviewSegment {
			text = text != null ? text : "";
		}

	}

	public record PreviewOperation(List<PreviewSegment> segments, String warningText) {

		public PreviewOperation {
			segments = List.copyOf(segments);
		}

		public static PreviewOperation plain(String text) {
			return new PreviewOperation(List.of(new PreviewSegment(text, null)), null);
		}

		public static PreviewOperation plain(String text, String warningText) {
			return new PreviewOperation(List.of(new PreviewSegment(text, null)), warningText);
		}

	}

	public record ScriptResultData(boolean present, Object value) {

		static ScriptResultData absent() {
			return new ScriptResultData(false, null);
		}

		static ScriptResultData present(Object value) {
			return new ScriptResultData(true, value);
		}

	}

	public enum ScriptKind {

		OWNER_QUERY, MODIFICATION

	}

	private record ScriptExecution(ScriptKind scriptKind, ScriptResultData scriptResult) {
	}

	public record PreviewResult(List<PreviewOperation> operations, boolean hasIncompleteOperations,
			ScriptResultData scriptResult, ScriptKind scriptKind) {

		public PreviewResult {
			operations = List.copyOf(operations);
			scriptResult = scriptResult != null ? scriptResult : ScriptResultData.absent();
			scriptKind = scriptKind != null ? scriptKind : inferScriptKind(operations, scriptResult);
		}

		public PreviewResult(List<PreviewOperation> operations, boolean hasIncompleteOperations,
				ScriptResultData scriptResult) {
			this(operations, hasIncompleteOperations, scriptResult, inferScriptKind(operations, scriptResult));
		}

		public PreviewResult(List<PreviewOperation> operations, boolean hasIncompleteOperations) {
			this(operations, hasIncompleteOperations, ScriptResultData.absent());
		}

	}

	public record ExecutionResult(List<PreviewOperation> operations, ScriptResultData scriptResult,
			ScriptKind scriptKind) {

		public ExecutionResult {
			operations = List.copyOf(operations);
			scriptResult = scriptResult != null ? scriptResult : ScriptResultData.absent();
			scriptKind = scriptKind != null ? scriptKind : inferScriptKind(operations, scriptResult);
		}

		public ExecutionResult(List<PreviewOperation> operations, ScriptResultData scriptResult) {
			this(operations, scriptResult, inferScriptKind(operations, scriptResult));
		}

		public ExecutionResult(List<PreviewOperation> operations) {
			this(operations, ScriptResultData.absent());
		}

	}

	private static ScriptKind inferScriptKind(List<PreviewOperation> operations, ScriptResultData scriptResult) {
		if (operations != null && !operations.isEmpty()) {
			return ScriptKind.MODIFICATION;
		}
		return ScriptKind.OWNER_QUERY;
	}

}
