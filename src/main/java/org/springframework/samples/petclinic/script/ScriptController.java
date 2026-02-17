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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;
import org.graalvm.scriptagent.Script;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.ExtensionSelector;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for the Scripting Agent page.
 *
 */
@Controller
class ScriptController {

	private static final String SCRIPTING_SESSIONS_ATTRIBUTE = ScriptController.class.getName() + ".sessions";

	private static final String SCRIPTING_RUNTIME_ATTRIBUTE = ScriptController.class.getName() + ".runtime";

	private static final String CURRENT_RUNTIME_ID = UUID.randomUUID().toString();

	private static final String NO_MODIFICATIONS_PREVIEW_MESSAGE = "No modifications to preview. The script did not request any changes.";

	private static final TypeReference<List<ChatSessionState>> SESSION_LIST_TYPE = new TypeReference<>() {
	};

	private final ScriptGenerationService scriptGenerationService;

	private final ScriptService scriptService;

	private final SavedOwnerQueryScriptService savedOwnerQueryScripts;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final ExecutorService generationExecutor = Executors.newCachedThreadPool(runnable -> {
		Thread thread = new Thread(runnable, "script-generation");
		thread.setDaemon(true);
		return thread;
	});

	ScriptController(ScriptGenerationService scriptGenerationService, ScriptService scriptService,
			SavedOwnerQueryScriptService savedOwnerQueryScripts) {
		this.scriptGenerationService = scriptGenerationService;
		this.scriptService = scriptService;
		this.savedOwnerQueryScripts = savedOwnerQueryScripts;
	}

	@PreDestroy
	void shutdownGenerationExecutor() throws InterruptedException {
		this.generationExecutor.shutdown();
		if (!this.generationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
			this.generationExecutor.shutdownNow();
		}
	}

	@InitBinder
	public void setAllowedFields(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id");
	}

	@ModelAttribute("scriptForm")
	public ScriptCreationForm scriptForm() {
		return new ScriptCreationForm();
	}

	@GetMapping("/scripting")
	public String initScriptForm(@ModelAttribute("scriptForm") ScriptCreationForm scriptForm, Model model,
			HttpSession httpSession) {
		ChatState chatState = loadChatState(scriptForm, httpSession);
		synchronized (chatState) {
			return renderPage(scriptForm, chatState, null, model, httpSession);
		}
	}

	@GetMapping("/scripting/state")
	public String refreshScriptForm(@ModelAttribute("scriptForm") ScriptCreationForm scriptForm,
			@RequestParam(value = "activeSessionId", required = false) String activeSessionId, Model model,
			HttpSession httpSession) {
		ChatState chatState = loadChatState(scriptForm, httpSession);
		synchronized (chatState) {
			return renderPage(scriptForm, chatState, activeSessionId, model, httpSession);
		}
	}

	@PostMapping("/scripting/select-session")
	public String selectSession(@ModelAttribute("scriptForm") ScriptCreationForm scriptForm,
			@RequestParam(value = "selectedSessionId", required = false) String selectedSessionId, Model model,
			HttpSession httpSession) {
		ChatState chatState = loadChatState(scriptForm, httpSession);
		synchronized (chatState) {
			return renderPage(scriptForm, chatState, resolveActiveSessionId(selectedSessionId, chatState.sessions()),
					model, httpSession);
		}
	}

	@PostMapping("/scripting/new-session")
	public String createSession(@ModelAttribute("scriptForm") ScriptCreationForm scriptForm, Model model,
			HttpSession httpSession) {
		ChatState chatState = loadChatState(scriptForm, httpSession);
		synchronized (chatState) {
			return renderPage(scriptForm, chatState, null, model, httpSession);
		}
	}

	@PostMapping("/scripting/generate-and-preview")
	public String generateAndPreviewScript(@ModelAttribute("scriptForm") ScriptCreationForm scriptForm,
			BindingResult result, Model model, HttpSession httpSession) {
		ChatState chatState = loadChatState(scriptForm, httpSession);
		GenerationTask generationTask;
		String viewName;
		synchronized (chatState) {
			List<ChatSessionState> sessions = chatState.sessions();
			ChatSessionState activeSession = findSession(sessions, scriptForm.getActiveSessionId());
			String activeSessionId = activeSession != null ? activeSession.getId() : scriptForm.getActiveSessionId();
			if (!StringUtils.hasText(scriptForm.getLlmPrompt())) {
				result.rejectValue("llmPrompt", "required", "is required");
				return renderPage(scriptForm, chatState, resolveActiveSessionId(activeSessionId, sessions), model,
						httpSession);
			}

			String llmPrompt = scriptForm.getLlmPrompt().trim();
			if (activeSession == null) {
				activeSession = newSessionState(StringUtils.hasText(activeSessionId) ? activeSessionId : null,
						llmPrompt);
				sessions.add(activeSession);
				activeSessionId = activeSession.getId();
			}
			if (!StringUtils.hasText(activeSession.getLlmSessionId())) {
				activeSession.setLlmSessionId(UUID.randomUUID().toString());
			}
			ChatEntryState entry = createPendingEntry(llmPrompt);
			activeSession.getEntries().add(entry);
			scriptForm.setLlmPrompt("");
			generationTask = new GenerationTask(chatState, entry.getId(), llmPrompt, activeSession.getLlmSessionId());
			viewName = renderPage(scriptForm, chatState, activeSessionId, model, httpSession);
		}
		this.generationExecutor.submit(() -> completeGeneration(generationTask));
		return viewName;
	}

	@PostMapping("/scripting/execute")
	public String executeScript(@ModelAttribute("scriptForm") ScriptCreationForm scriptForm,
			@RequestParam(value = "targetEntryId", required = false) String targetEntryId, Model model,
			HttpSession httpSession) {
		ChatState chatState = loadChatState(scriptForm, httpSession);
		Script<ExtensionSelector> script;
		EntryReference entryReference;
		synchronized (chatState) {
			List<ChatSessionState> sessions = chatState.sessions();
			entryReference = findEntryReference(sessions, targetEntryId);
			if (entryReference == null) {
				model.addAttribute("scriptExecutionError", "Generated script to execute was not found.");
				return renderPage(scriptForm, chatState,
						resolveActiveSessionId(scriptForm.getActiveSessionId(), sessions), model, httpSession);
			}

			ChatSessionState session = entryReference.session();
			ChatEntryState entry = entryReference.entry();
			if (!entry.isExecutable()) {
				model.addAttribute("scriptExecutionError",
						"Only scripts with previewed modifications can be executed.");
				return renderPage(scriptForm, chatState, session.getId(), model, httpSession);
			}
			script = entry.requireScript();
		}

		try {
			ScriptService.ExecutionResult executionResult = this.scriptService.execute(script);
			synchronized (chatState) {
				ChatSessionState session = entryReference.session();
				ChatEntryState entry = entryReference.entry();
				entry.setPreviewOperations(new ArrayList<>(executionResult.operations()));
				entry.setScriptKind(executionResult.scriptKind());
				applyScriptResult(entry, executionResult.scriptResult(), false);
				entry.setExecuted(true);
				entry.setExecutionSuccessMessage("Script executed successfully.");
				entry.setExecutionError(null);
				session.setLastExecutedScriptJson(entry.getScriptJson());
			}
		}
		catch (IllegalArgumentException ex) {
			synchronized (chatState) {
				ChatEntryState entry = entryReference.entry();
				entry.setExecutionSuccessMessage(null);
				entry.setExecutionError(ex.getMessage());
			}
		}

		synchronized (chatState) {
			return renderPage(scriptForm, chatState, entryReference.session().getId(), model, httpSession);
		}
	}

	@PostMapping("/scripting/refresh-preview")
	public String refreshPreview(@ModelAttribute("scriptForm") ScriptCreationForm scriptForm,
			@RequestParam(value = "targetEntryId", required = false) String targetEntryId, Model model,
			HttpSession httpSession) {
		ChatState chatState = loadChatState(scriptForm, httpSession);
		Script<ExtensionSelector> script;
		EntryReference entryReference;
		synchronized (chatState) {
			List<ChatSessionState> sessions = chatState.sessions();
			entryReference = findEntryReference(sessions, targetEntryId);
			if (entryReference == null) {
				model.addAttribute("scriptExecutionError", "Generated script to refresh was not found.");
				return renderPage(scriptForm, chatState,
						resolveActiveSessionId(scriptForm.getActiveSessionId(), sessions), model, httpSession);
			}
			script = entryReference.entry().requireScript();
		}

		try {
			ScriptService.PreviewResult preview = this.scriptService.preview(script);
			synchronized (chatState) {
				applyPreviewResult(entryReference.entry(), preview);
			}
		}
		catch (IllegalArgumentException ex) {
			synchronized (chatState) {
				applyPreviewError(entryReference.entry(), ex.getMessage());
			}
		}

		synchronized (chatState) {
			return renderPage(scriptForm, chatState, entryReference.session().getId(), model, httpSession);
		}
	}

	@PostMapping("/scripting/save-owner-query")
	public String saveOwnerQuery(@ModelAttribute("scriptForm") ScriptCreationForm scriptForm,
			@RequestParam(value = "targetEntryId", required = false) String targetEntryId, Model model,
			HttpSession httpSession) {
		ChatState chatState = loadChatState(scriptForm, httpSession);
		String scriptName;
		String scriptJson;
		EntryReference entryReference;
		synchronized (chatState) {
			List<ChatSessionState> sessions = chatState.sessions();
			entryReference = findEntryReference(sessions, targetEntryId);
			if (entryReference == null) {
				model.addAttribute("ownerQuerySaveError", "Generated script to save was not found.");
				return renderPage(scriptForm, chatState,
						resolveActiveSessionId(scriptForm.getActiveSessionId(), sessions), model, httpSession);
			}

			ChatSessionState session = entryReference.session();
			ChatEntryState entry = entryReference.entry();
			if (!canAddToFindOwnersPage(entry)) {
				model.addAttribute("ownerQuerySaveError",
						"Only query-only scripts that return an owner list can be added to the Find Owners page.");
				return renderPage(scriptForm, chatState, session.getId(), model, httpSession);
			}
			scriptName = entry.getScriptName();
			scriptJson = entry.getScriptJson();
		}

		try {
			this.savedOwnerQueryScripts.save(scriptName, scriptJson);
			synchronized (chatState) {
				entryReference.entry().setOwnerQuerySaveSuccessMessage("Added to Find Owners page.");
			}
		}
		catch (IllegalArgumentException ex) {
			model.addAttribute("ownerQuerySaveError", ex.getMessage());
		}

		synchronized (chatState) {
			return renderPage(scriptForm, chatState, entryReference.session().getId(), model, httpSession);
		}
	}

	private String renderPage(ScriptCreationForm scriptForm, ChatState chatState, String activeSessionId, Model model,
			HttpSession httpSession) {
		List<ChatSessionState> sessions = chatState.sessions();
		ChatSessionState activeSession = findSession(sessions, activeSessionId);
		storeChatState(httpSession, chatState);
		scriptForm.setActiveSessionId(activeSession != null ? activeSession.getId() : null);
		scriptForm.setSessionsState(writeSessions(sessions));
		scriptForm.setRuntimeId(CURRENT_RUNTIME_ID);
		model.addAttribute("sessions", sessions);
		model.addAttribute("activeSession", activeSession);
		model.addAttribute("activeChatEntries", activeSession != null ? toChatEntryViews(activeSession) : List.of());
		model.addAttribute("hasPendingGenerations", hasPendingGenerations(sessions));
		return "scripts/scriptForm";
	}

	private ChatEntryState createPendingEntry(String prompt) {
		ChatEntryState entry = new ChatEntryState();
		entry.setId(UUID.randomUUID().toString());
		entry.setPrompt(prompt);
		entry.setPending(true);
		return entry;
	}

	private void completeGeneration(GenerationTask task) {
		try {
			ScriptGenerationService.GenerationResult generationResult = this.scriptGenerationService
				.generateScript(task.prompt(), task.llmSessionId());
			ChatEntryState completedEntry = createPreviewedEntry(task.prompt(), generationResult.script());
			synchronized (task.chatState()) {
				EntryReference entryReference = findEntryReference(task.chatState().sessions(), task.entryId());
				if (entryReference == null) {
					return;
				}
				ChatSessionState session = entryReference.session();
				ChatEntryState entry = entryReference.entry();
				copyGeneratedEntry(completedEntry, entry);
				session.setLlmSessionId(generationResult.sessionId());
				if (task.prompt().equals(session.getName()) && StringUtils.hasText(entry.getScriptName())) {
					session.setName(entry.getScriptName());
				}
			}
		}
		catch (RuntimeException ex) {
			synchronized (task.chatState()) {
				EntryReference entryReference = findEntryReference(task.chatState().sessions(), task.entryId());
				if (entryReference == null) {
					return;
				}
				String errorMessage = StringUtils.hasText(ex.getMessage()) ? ex.getMessage()
						: "Script generation failed.";
				copyGeneratedEntry(createGenerationFailedEntry(task.prompt(), errorMessage), entryReference.entry());
			}
		}
	}

	private ChatEntryState createPreviewedEntry(String prompt, Script<ExtensionSelector> generatedScript) {
		ChatEntryState entry = new ChatEntryState();
		entry.setId(UUID.randomUUID().toString());
		entry.setPrompt(prompt);
		entry.setPending(false);
		entry.setScript(generatedScript);
		try {
			ScriptService.PreviewResult preview = this.scriptService.preview(generatedScript);
			applyPreviewResult(entry, preview);
		}
		catch (IllegalArgumentException ex) {
			applyPreviewError(entry, ex.getMessage());
		}
		return entry;
	}

	private ChatEntryState createGenerationFailedEntry(String prompt, String errorMessage) {
		ChatEntryState entry = new ChatEntryState();
		entry.setId(UUID.randomUUID().toString());
		entry.setPrompt(prompt);
		entry.setPending(false);
		entry.setPreviewOperations(new ArrayList<>());
		entry.setPreviewHasWarnings(false);
		entry.setScriptKind(null);
		entry.setScriptResultPresent(false);
		entry.setScriptResultValue(null);
		entry.setExecutable(false);
		entry.setPreviewMessage(null);
		entry.setPreviewError(errorMessage);
		return entry;
	}

	private void copyGeneratedEntry(ChatEntryState source, ChatEntryState target) {
		target.setPending(source.isPending());
		target.setScriptJson(source.getScriptJson());
		target.setPreviewOperations(new ArrayList<>(source.getPreviewOperations()));
		target.setPreviewHasWarnings(source.isPreviewHasWarnings());
		target.setScriptKind(source.getScriptKind());
		target.setScriptResultPresent(source.isScriptResultPresent());
		target.setScriptResultValue(source.getScriptResultValue());
		target.setExecutable(source.isExecutable());
		target.setExecutionSuccessMessage(source.getExecutionSuccessMessage());
		target.setOwnerQuerySaveSuccessMessage(source.getOwnerQuerySaveSuccessMessage());
		target.setExecutionError(source.getExecutionError());
		target.setPreviewMessage(source.getPreviewMessage());
		target.setPreviewError(source.getPreviewError());
	}

	private void applyPreviewResult(ChatEntryState entry, ScriptService.PreviewResult preview) {
		entry.setPending(false);
		entry.setPreviewOperations(new ArrayList<>(preview.operations()));
		entry.setPreviewHasWarnings(preview.hasIncompleteOperations());
		entry.setScriptKind(preview.scriptKind());
		applyScriptResult(entry, preview.scriptResult(), true);
		entry.setExecutable(
				preview.scriptKind() == ScriptService.ScriptKind.MODIFICATION && !preview.operations().isEmpty());
		if (preview.scriptKind() == ScriptService.ScriptKind.MODIFICATION && preview.operations().isEmpty()) {
			entry.setPreviewMessage(NO_MODIFICATIONS_PREVIEW_MESSAGE);
		}
		else {
			entry.setPreviewMessage(null);
		}
		entry.setExecutionSuccessMessage(null);
		entry.setOwnerQuerySaveSuccessMessage(null);
		entry.setExecutionError(null);
		entry.setPreviewError(null);
	}

	private void applyPreviewError(ChatEntryState entry, String errorMessage) {
		entry.setPending(false);
		entry.setPreviewOperations(new ArrayList<>());
		entry.setPreviewHasWarnings(false);
		entry.setScriptKind(null);
		entry.setScriptResultPresent(false);
		entry.setScriptResultValue(null);
		entry.setExecutable(false);
		entry.setExecutionSuccessMessage(null);
		entry.setOwnerQuerySaveSuccessMessage(null);
		entry.setExecutionError(null);
		entry.setPreviewMessage(null);
		entry.setPreviewError(errorMessage);
	}

	private void applyScriptResult(ChatEntryState entry, ScriptService.ScriptResultData scriptResult,
			boolean clearWhenAbsent) {
		if (scriptResult != null && scriptResult.present()) {
			entry.setScriptResultPresent(true);
			entry.setScriptResultValue(scriptResult.value());
			return;
		}
		if (clearWhenAbsent) {
			entry.setScriptResultPresent(false);
			entry.setScriptResultValue(null);
		}
	}

	private List<ChatEntryView> toChatEntryViews(ChatSessionState activeSession) {
		List<ChatEntryView> views = new ArrayList<>();
		for (ChatEntryState entry : activeSession.getEntries()) {
			String scriptResultHtml = null;
			String scriptResultJson = null;
			if (entry.isScriptResultPresent()) {
				scriptResultHtml = formatScriptResultHtml(entry.getScriptResultValue());
				scriptResultJson = writePrettyJson(entry.getScriptResultValue());
			}
			boolean repeatedExecution = entry.isExecuted()
					|| (StringUtils.hasText(activeSession.getLastExecutedScriptJson())
							&& activeSession.getLastExecutedScriptJson().equals(entry.getScriptJson()));
			boolean repeatedOwnerQuerySave = canAddToFindOwnersPage(entry)
					&& this.savedOwnerQueryScripts.isAlreadySaved(entry.getScriptName(), entry.getScriptJson());
			boolean generatedScript = entry.hasScript();
			views.add(new ChatEntryView(entry.getId(), entry.getPrompt(), entry.isPending(), entry.getScriptName(),
					entry.getScriptText(), generatedScript, entry.getPreviewOperations(), entry.isPreviewHasWarnings(),
					entry.getScriptKind(), isOwnerQueryScript(entry), isModificationScript(entry), scriptResultHtml,
					scriptResultJson, entry.isExecutable(), canAddToFindOwnersPage(entry), entry.isExecuted(),
					repeatedExecution, repeatedOwnerQuerySave, entry.getExecutionSuccessMessage(),
					entry.getOwnerQuerySaveSuccessMessage(), entry.getExecutionError(), entry.getPreviewMessage(),
					entry.getPreviewError()));
		}
		return views;
	}

	private boolean canAddToFindOwnersPage(ChatEntryState entry) {
		return !entry.isPending() && entry.hasScript() && isOwnerQueryScript(entry)
				&& !StringUtils.hasText(entry.getPreviewError())
				&& ScriptService.looksLikeOwnerListResult(entry.getScriptResultValue());
	}

	private boolean isOwnerQueryScript(ChatEntryState entry) {
		return entry.getScriptKind() == ScriptService.ScriptKind.OWNER_QUERY;
	}

	private boolean isModificationScript(ChatEntryState entry) {
		return entry.getScriptKind() == ScriptService.ScriptKind.MODIFICATION;
	}

	private boolean hasPendingGenerations(List<ChatSessionState> sessions) {
		for (ChatSessionState session : sessions) {
			for (ChatEntryState entry : session.getEntries()) {
				if (entry.isPending()) {
					return true;
				}
			}
		}
		return false;
	}

	private ChatState loadChatState(ScriptCreationForm scriptForm, HttpSession httpSession) {
		synchronized (httpSession) {
			String storedRuntimeId = storedRuntimeId(httpSession);
			if (StringUtils.hasText(storedRuntimeId) && !CURRENT_RUNTIME_ID.equals(storedRuntimeId)) {
				clearStoredSessions(httpSession);
			}
			Object storedState = httpSession.getAttribute(SCRIPTING_SESSIONS_ATTRIBUTE);
			if (storedState instanceof ChatState chatState) {
				return chatState;
			}
			ChatState chatState = new ChatState(loadInitialSessions(scriptForm, httpSession, storedState));
			storeChatState(httpSession, chatState);
			return chatState;
		}
	}

	private List<ChatSessionState> loadInitialSessions(ScriptCreationForm scriptForm, HttpSession httpSession,
			Object storedSessions) {
		String storedRuntimeId = storedRuntimeId(httpSession);
		if (StringUtils.hasText(storedRuntimeId) && !CURRENT_RUNTIME_ID.equals(storedRuntimeId)) {
			clearStoredSessions(httpSession);
			return new ArrayList<>();
		}
		if (storedSessions instanceof String rawSessions) {
			return parseSessions(rawSessions);
		}
		if (!CURRENT_RUNTIME_ID.equals(scriptForm.getRuntimeId())) {
			return new ArrayList<>();
		}
		return parseSessions(scriptForm.getSessionsState());
	}

	private void storeChatState(HttpSession httpSession, ChatState chatState) {
		httpSession.setAttribute(SCRIPTING_SESSIONS_ATTRIBUTE, chatState);
		httpSession.setAttribute(SCRIPTING_RUNTIME_ATTRIBUTE, CURRENT_RUNTIME_ID);
	}

	private String storedRuntimeId(HttpSession httpSession) {
		Object runtimeId = httpSession.getAttribute(SCRIPTING_RUNTIME_ATTRIBUTE);
		return runtimeId instanceof String value ? value : null;
	}

	private void clearStoredSessions(HttpSession httpSession) {
		httpSession.removeAttribute(SCRIPTING_SESSIONS_ATTRIBUTE);
		httpSession.removeAttribute(SCRIPTING_RUNTIME_ATTRIBUTE);
	}

	private List<ChatSessionState> parseSessions(String rawSessions) {
		if (!StringUtils.hasText(rawSessions)) {
			return new ArrayList<>();
		}
		try {
			List<ChatSessionState> sessions = this.objectMapper.readValue(rawSessions, SESSION_LIST_TYPE);
			if (sessions == null || sessions.isEmpty()) {
				return new ArrayList<>();
			}
			List<ChatSessionState> normalizedSessions = new ArrayList<>();
			for (int i = 0; i < sessions.size(); i++) {
				ChatSessionState normalizedSession = normalizeSession(sessions.get(i), i + 1);
				if (!normalizedSession.getEntries().isEmpty()) {
					normalizedSessions.add(normalizedSession);
				}
			}
			return normalizedSessions;
		}
		catch (JsonProcessingException ex) {
			return new ArrayList<>();
		}
	}

	private ChatSessionState normalizeSession(ChatSessionState session, int index) {
		ChatSessionState normalized = new ChatSessionState();
		normalized.setId(StringUtils.hasText(session.getId()) ? session.getId() : UUID.randomUUID().toString());
		normalized.setLlmSessionId(session.getLlmSessionId());
		normalized.setLastExecutedScriptJson(session.getLastExecutedScriptJson());
		List<ChatEntryState> normalizedEntries = new ArrayList<>();
		if (session.getEntries() != null) {
			for (ChatEntryState entry : session.getEntries()) {
				normalizedEntries.add(normalizeEntry(entry));
			}
		}
		normalized.setEntries(normalizedEntries);
		normalized.setName(resolveSessionName(session.getName(), normalizedEntries, index));
		return normalized;
	}

	private String resolveSessionName(String currentName, List<ChatEntryState> entries, int index) {
		if (StringUtils.hasText(currentName) && !currentName.matches("Session\\s+\\d+")) {
			return currentName;
		}
		for (ChatEntryState entry : entries) {
			if (StringUtils.hasText(entry.getScriptName())) {
				return entry.getScriptName();
			}
		}
		for (ChatEntryState entry : entries) {
			if (StringUtils.hasText(entry.getPrompt())) {
				return entry.getPrompt();
			}
		}
		return "Generated Script " + index;
	}

	private ChatEntryState normalizeEntry(ChatEntryState entry) {
		ChatEntryState normalized = new ChatEntryState();
		normalized.setId(StringUtils.hasText(entry.getId()) ? entry.getId() : UUID.randomUUID().toString());
		normalized.setPrompt(entry.getPrompt());
		normalized.setPending(entry.isPending());
		normalized.setScriptJson(entry.getScriptJson());
		normalized.setPreviewOperations(entry.getPreviewOperations() != null
				? new ArrayList<>(entry.getPreviewOperations()) : new ArrayList<>());
		normalized.setPreviewHasWarnings(entry.isPreviewHasWarnings());
		normalized.setScriptKind(resolveScriptKind(entry));
		normalized.setScriptResultPresent(entry.isScriptResultPresent());
		normalized.setScriptResultValue(entry.getScriptResultValue());
		normalized.setExecutable(normalized.getScriptKind() == ScriptService.ScriptKind.MODIFICATION
				&& normalized.getPreviewOperations() != null && !normalized.getPreviewOperations().isEmpty()
				&& !StringUtils.hasText(entry.getPreviewError()));
		normalized.setExecuted(entry.isExecuted());
		normalized.setExecutionSuccessMessage(entry.getExecutionSuccessMessage());
		normalized.setOwnerQuerySaveSuccessMessage(entry.getOwnerQuerySaveSuccessMessage());
		normalized.setExecutionError(entry.getExecutionError());
		normalized.setPreviewMessage(entry.getPreviewMessage());
		normalized.setPreviewError(entry.getPreviewError());
		return normalized;
	}

	private ScriptService.ScriptKind resolveScriptKind(ChatEntryState entry) {
		if (entry.getScriptKind() != null) {
			return entry.getScriptKind();
		}
		if (entry.getPreviewOperations() != null && !entry.getPreviewOperations().isEmpty()) {
			return ScriptService.ScriptKind.MODIFICATION;
		}
		if (entry.isScriptResultPresent()) {
			return ScriptService.ScriptKind.OWNER_QUERY;
		}
		return null;
	}

	private String writeSessions(List<ChatSessionState> sessions) {
		try {
			return this.objectMapper.writeValueAsString(sessions);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("Could not store scripting sessions.", ex);
		}
	}

	private String resolveActiveSessionId(String activeSessionId, List<ChatSessionState> sessions) {
		if (sessions.isEmpty()) {
			return null;
		}
		if (StringUtils.hasText(activeSessionId)) {
			for (ChatSessionState session : sessions) {
				if (activeSessionId.equals(session.getId())) {
					return session.getId();
				}
			}
		}
		return sessions.get(0).getId();
	}

	private ChatSessionState findSession(List<ChatSessionState> sessions, String sessionId) {
		if (!StringUtils.hasText(sessionId)) {
			return null;
		}
		for (ChatSessionState session : sessions) {
			if (sessionId.equals(session.getId())) {
				return session;
			}
		}
		return null;
	}

	private EntryReference findEntryReference(List<ChatSessionState> sessions, String entryId) {
		if (!StringUtils.hasText(entryId)) {
			return null;
		}
		for (ChatSessionState session : sessions) {
			for (ChatEntryState entry : session.getEntries()) {
				if (entryId.equals(entry.getId())) {
					return new EntryReference(session, entry);
				}
			}
		}
		return null;
	}

	private ChatSessionState newSessionState(String sessionName) {
		return newSessionState(null, sessionName);
	}

	private ChatSessionState newSessionState(String sessionId, String sessionName) {
		ChatSessionState session = new ChatSessionState();
		session.setId(StringUtils.hasText(sessionId) ? sessionId : UUID.randomUUID().toString());
		session.setName(sessionName);
		session.setEntries(new ArrayList<>());
		return session;
	}

	private String writePrettyJson(Object value) {
		return ScriptResultFormatter.writePrettyJson(value);
	}

	private String formatScriptResultHtml(Object value) {
		return ScriptResultFormatter.formatHtml(value);
	}

	private record EntryReference(ChatSessionState session, ChatEntryState entry) {
	}

	private record GenerationTask(ChatState chatState, String entryId, String prompt, String llmSessionId) {
	}

	private record ChatState(List<ChatSessionState> sessions) {

		private ChatState {
			sessions = sessions != null ? sessions : new ArrayList<>();
		}

	}

	record ChatEntryView(String id, String prompt, boolean pending, String scriptName, String scriptText,
			boolean generatedScript, List<ScriptService.PreviewOperation> previewOperations, boolean previewHasWarnings,
			ScriptService.ScriptKind scriptKind, boolean ownerQueryScript, boolean modificationScript,
			String scriptResultHtml, String scriptResultJson, boolean executable, boolean canAddToFindOwnersPage,
			boolean executed, boolean repeatedExecution, boolean repeatedOwnerQuerySave, String executionSuccessMessage,
			String ownerQuerySaveSuccessMessage, String executionError, String previewMessage, String previewError) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class ChatSessionState {

		private String id;

		private String name;

		private String llmSessionId;

		private String lastExecutedScriptJson;

		private List<ChatEntryState> entries = new ArrayList<>();

		public String getId() {
			return this.id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getName() {
			return this.name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getLlmSessionId() {
			return this.llmSessionId;
		}

		public void setLlmSessionId(String llmSessionId) {
			this.llmSessionId = llmSessionId;
		}

		public String getLastExecutedScriptJson() {
			return this.lastExecutedScriptJson;
		}

		public void setLastExecutedScriptJson(String lastExecutedScriptJson) {
			this.lastExecutedScriptJson = lastExecutedScriptJson;
		}

		public List<ChatEntryState> getEntries() {
			return this.entries;
		}

		public void setEntries(List<ChatEntryState> entries) {
			this.entries = entries != null ? entries : new ArrayList<>();
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class ChatEntryState {

		private String id;

		private String prompt;

		private String scriptJson;

		private transient Script<ExtensionSelector> script;

		private boolean pending;

		private List<ScriptService.PreviewOperation> previewOperations = new ArrayList<>();

		private boolean previewHasWarnings;

		private ScriptService.ScriptKind scriptKind;

		private boolean scriptResultPresent;

		private Object scriptResultValue;

		private boolean executable;

		private boolean executed;

		private String executionSuccessMessage;

		private String ownerQuerySaveSuccessMessage;

		private String executionError;

		private String previewMessage;

		private String previewError;

		public String getId() {
			return this.id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getPrompt() {
			return this.prompt;
		}

		public void setPrompt(String prompt) {
			this.prompt = prompt;
		}

		public String getScriptJson() {
			return this.scriptJson;
		}

		public void setScriptJson(String scriptJson) {
			this.scriptJson = scriptJson;
			this.script = null;
		}

		public boolean isPending() {
			return this.pending;
		}

		public void setPending(boolean pending) {
			this.pending = pending;
		}

		@JsonIgnore
		public String getScriptText() {
			Script<ExtensionSelector> resolvedScript = scriptOrNull();
			return resolvedScript != null ? resolvedScript.source().getCharacters().toString() : null;
		}

		@JsonIgnore
		public String getScriptName() {
			return getName();
		}

		@JsonIgnore
		public String getName() {
			Script<ExtensionSelector> resolvedScript = scriptOrNull();
			if (resolvedScript == null) {
				return null;
			}
			String caption = resolvedScript.property(PetClinicScriptExtensions.CAPTION_PROPERTY);
			return StringUtils.hasText(caption) ? caption : "Generated Script";
		}

		@JsonIgnore
		public boolean hasScript() {
			return scriptOrNull() != null;
		}

		@JsonIgnore
		public Script<ExtensionSelector> requireScript() {
			Script<ExtensionSelector> resolvedScript = scriptOrNull();
			if (resolvedScript == null) {
				throw new IllegalArgumentException("Generated script is missing.");
			}
			return resolvedScript;
		}

		void setScript(Script<ExtensionSelector> script) {
			this.script = script;
			this.scriptJson = script != null ? script.toJSON() : null;
		}

		private Script<ExtensionSelector> scriptOrNull() {
			if (this.script != null) {
				return this.script;
			}
			if (!StringUtils.hasText(this.scriptJson)) {
				return null;
			}
			try {
				this.script = PetClinicScriptExtensions.fromJson(this.scriptJson);
			}
			catch (IllegalArgumentException ex) {
				return null;
			}
			return this.script;
		}

		public List<ScriptService.PreviewOperation> getPreviewOperations() {
			return this.previewOperations;
		}

		public void setPreviewOperations(List<ScriptService.PreviewOperation> previewOperations) {
			this.previewOperations = previewOperations != null ? previewOperations : new ArrayList<>();
		}

		public boolean isPreviewHasWarnings() {
			return this.previewHasWarnings;
		}

		public void setPreviewHasWarnings(boolean previewHasWarnings) {
			this.previewHasWarnings = previewHasWarnings;
		}

		public ScriptService.ScriptKind getScriptKind() {
			return this.scriptKind;
		}

		public void setScriptKind(ScriptService.ScriptKind scriptKind) {
			this.scriptKind = scriptKind;
		}

		public boolean isScriptResultPresent() {
			return this.scriptResultPresent;
		}

		public void setScriptResultPresent(boolean scriptResultPresent) {
			this.scriptResultPresent = scriptResultPresent;
		}

		public Object getScriptResultValue() {
			return this.scriptResultValue;
		}

		public void setScriptResultValue(Object scriptResultValue) {
			this.scriptResultValue = scriptResultValue;
		}

		public boolean isExecutable() {
			return this.executable;
		}

		public void setExecutable(boolean executable) {
			this.executable = executable;
		}

		public boolean isExecuted() {
			return this.executed;
		}

		public void setExecuted(boolean executed) {
			this.executed = executed;
		}

		public String getExecutionSuccessMessage() {
			return this.executionSuccessMessage;
		}

		public void setExecutionSuccessMessage(String executionSuccessMessage) {
			this.executionSuccessMessage = executionSuccessMessage;
		}

		public String getExecutionError() {
			return this.executionError;
		}

		public void setExecutionError(String executionError) {
			this.executionError = executionError;
		}

		public String getOwnerQuerySaveSuccessMessage() {
			return this.ownerQuerySaveSuccessMessage;
		}

		public void setOwnerQuerySaveSuccessMessage(String ownerQuerySaveSuccessMessage) {
			this.ownerQuerySaveSuccessMessage = ownerQuerySaveSuccessMessage;
		}

		public String getPreviewError() {
			return this.previewError;
		}

		public String getPreviewMessage() {
			return this.previewMessage;
		}

		public void setPreviewMessage(String previewMessage) {
			this.previewMessage = previewMessage;
		}

		public void setPreviewError(String previewError) {
			this.previewError = previewError;
		}

	}

}
