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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import org.graalvm.scriptagent.Script;
import org.graalvm.scriptagent.ScriptAgent;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.ScriptingExtension;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ScriptGenerationService {

	private static final int MAX_GENERATION_SESSIONS = 128;

	private static final String AGENT_INSTRUCTIONS = """
			The script source evaluates to one ScriptingExtension implementation directly.
			Pet and visit data is only reachable from owner query results: first find owners, then call owner.getPets(), then call pet.getVisits(). In OwnerHierarchyResult, return pet entries from OwnerResultEntry.petsToDisplay() and visit entries from PetResultEntry.visitsToDisplay(); those lists, not shouldDisplayField, control whether nested pets and visits are included.
			When returning owners or owner-related data, preserve each owner's id together with firstName and lastName so the UI can render owner names as clickable links.
			Important: Try the best to fulfil the user's request even though the prompt may not specify everything precisely or there is no straightforward api to get the requested information/do the requested modifications.
			""";

	private final ScriptAgent scriptingAgent;

	private final Map<String, ScriptAgent.Session<ScriptingExtension>> generationSessions = new LinkedHashMap<>();

	private final Map<String, Object> generationSessionLocks = new LinkedHashMap<>();

	public ScriptGenerationService() {
		String apiKey = System.getenv("MODEL_API_KEY");
		if (!StringUtils.hasText(apiKey)) {
			this.scriptingAgent = null;
			return;
		}

		String reasoningEffort = System.getenv("MODEL_REASONING_EFFORT");
		OpenAiResponsesStreamingChatModel.Builder modelBuilder = OpenAiResponsesStreamingChatModel.builder()
			.apiKey(apiKey)
			.modelName(StringUtils.hasText(System.getenv("MODEL_NAME")) ? System.getenv("MODEL_NAME") : "gpt-5.5")
			.reasoningEffort(StringUtils.hasText(reasoningEffort) ? reasoningEffort : "low");
		String baseUrl = System.getenv("MODEL_BASE_URL");
		if (StringUtils.hasText(baseUrl)) {
			modelBuilder.baseUrl(baseUrl);
		}
		StreamingChatModel chatModel = modelBuilder.build();
		this.scriptingAgent = ScriptAgent.newBuilder(chatModel).language("js").instructions(AGENT_INSTRUCTIONS).build();
	}

	public GenerationResult generateScript(String prompt, String sessionId) {
		if (!StringUtils.hasText(prompt)) {
			throw new IllegalArgumentException("Prompt is required.");
		}
		if (this.scriptingAgent == null) {
			throw new IllegalArgumentException("MODEL_API_KEY environment variable is not set.");
		}

		String resolvedSessionId = StringUtils.hasText(sessionId) ? sessionId : UUID.randomUUID().toString();
		Object generationSessionLock = getGenerationSessionLock(resolvedSessionId);
		synchronized (generationSessionLock) {
			ScriptAgent.Session<ScriptingExtension> generationSession = getOrCreateSession(resolvedSessionId);
			try {
				Script<ScriptingExtension> generatedScript = generationSession.generate(prompt);
				return new GenerationResult(resolvedSessionId, generatedScript);
			}
			catch (RuntimeException ex) {
				clearGenerationSession(resolvedSessionId);
				throw new IllegalArgumentException("Script generation failed: " + ex.getMessage(), ex);
			}
		}
	}

	public void clearGenerationSession(String sessionId) {
		if (!StringUtils.hasText(sessionId)) {
			return;
		}
		ScriptAgent.Session<ScriptingExtension> removedSession;
		synchronized (this.generationSessions) {
			removedSession = this.generationSessions.remove(sessionId);
		}
		closeSession(removedSession);
	}

	private ScriptAgent.Session<ScriptingExtension> getOrCreateSession(String sessionId) {
		synchronized (this.generationSessions) {
			ScriptAgent.Session<ScriptingExtension> existingSession = this.generationSessions.get(sessionId);
			if (existingSession != null) {
				return existingSession;
			}
			evictOldestSessionIfNeeded();
			ScriptAgent.Session<ScriptingExtension> newSession = this.scriptingAgent
				.newSession(ScriptingExtension.class, PetClinicScriptExtensions.scriptSchema());
			this.generationSessions.put(sessionId, newSession);
			return newSession;
		}
	}

	private Object getGenerationSessionLock(String sessionId) {
		synchronized (this.generationSessionLocks) {
			return this.generationSessionLocks.computeIfAbsent(sessionId, ignored -> new Object());
		}
	}

	private void evictOldestSessionIfNeeded() {
		if (this.generationSessions.size() < MAX_GENERATION_SESSIONS) {
			return;
		}
		String eldestSessionId = this.generationSessions.keySet().iterator().next();
		ScriptAgent.Session<ScriptingExtension> removedSession = this.generationSessions.remove(eldestSessionId);
		closeSession(removedSession);
	}

	private void closeSession(ScriptAgent.Session<ScriptingExtension> session) {
		if (session == null) {
			return;
		}
		try {
			session.close();
		}
		catch (Exception ex) {
			// ScriptAgent.Session currently has no close-side effects; ignore defensive
			// cleanup failures.
		}
	}

	public record GenerationResult(String sessionId, Script<ScriptingExtension> script) {

		public GenerationResult {
			if (!StringUtils.hasText(sessionId)) {
				throw new IllegalArgumentException("Generation session id must not be empty.");
			}
			if (script == null) {
				throw new IllegalArgumentException("Generated script must not be null.");
			}
		}

	}

}
