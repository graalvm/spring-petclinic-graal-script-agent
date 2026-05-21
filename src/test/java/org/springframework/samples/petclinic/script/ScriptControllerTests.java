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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.graalvm.scriptagent.Script;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.samples.petclinic.script.PetClinicScriptExtensions.ScriptingExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ScriptController.class)
@DisabledInNativeImage
@DisabledInAotMode
class ScriptControllerTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ScriptGenerationService scriptGenerationService;

	@MockitoBean
	private ScriptService scriptService;

	@MockitoBean
	private SavedOwnerQueryScriptService savedOwnerQueryScripts;

	@Test
	void testInitScriptingForm() throws Exception {
		this.mockMvc.perform(get("/scripting"))
			.andExpect(status().isOk())
			.andExpect(model().attributeExists("scriptForm"))
			.andExpect(model().attribute("sessions", hasSize(0)))
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString(">New Session</button>")))
			.andExpect(content().string(not(containsString("No history yet."))));
	}

	@Test
	void testNewGetRequestAddsSessionWithoutDroppingExistingSessions() throws Exception {
		MockHttpSession httpSession = new MockHttpSession();
		Script<ScriptingExtension> generatedScript = mockGeneratedScript("Show Owners",
				"api.ownersApi.findByCityStartingWith('Madison');");
		given(this.scriptGenerationService.generateScript(eq("show owners in Madison"), any()))
			.willReturn(new ScriptGenerationService.GenerationResult("session-1", generatedScript));
		given(this.scriptService.preview(generatedScript))
			.willReturn(new ScriptService.PreviewResult(List.of(), false));

		this.mockMvc.perform(get("/scripting").session(httpSession)).andExpect(status().isOk());
		MvcResult pendingResult = this.mockMvc
			.perform(post("/scripting/generate-and-preview").session(httpSession)
				.param("llmPrompt", "show owners in Madison"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("sessions", hasSize(1)))
			.andExpect(content().string(containsString("Thinking")))
			.andReturn();
		refreshUntilContains(httpSession, activeSessionId(pendingResult), "Show Owners");

		this.mockMvc.perform(get("/scripting").session(httpSession))
			.andExpect(status().isOk())
			.andExpect(model().attribute("sessions", hasSize(1)))
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString("Show Owners")))
			.andExpect(content().string(containsString(">New Session</button>")))
			.andExpect(content().string(containsString("1 messages")));
	}

	@Test
	void testRepeatedGetWithoutMessagesKeepsNoSessions() throws Exception {
		MockHttpSession httpSession = new MockHttpSession();

		this.mockMvc.perform(get("/scripting").session(httpSession))
			.andExpect(status().isOk())
			.andExpect(model().attribute("sessions", hasSize(0)))
			.andExpect(content().string(containsString(">New Session</button>")))
			.andExpect(content().string(not(containsString("No history yet."))));

		this.mockMvc.perform(get("/scripting").session(httpSession))
			.andExpect(status().isOk())
			.andExpect(model().attribute("sessions", hasSize(0)))
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString(">New Session</button>")))
			.andExpect(content().string(not(containsString("1 messages"))));
	}

	@Test
	void testNewSessionAddsAnotherChatSession() throws Exception {
		String runtimeId = currentRuntimeId();
		String sessionsState = sessionsStateJson(session("session-a", "Show Owners", null, null,
				List.of(entry("entry-1", "show owners", "Show Owners",
						"api.ownersApi.findByCityStartingWith('Madison');", List.of(), false, true,
						List.of(Map.of("id", 1, "firstName", "George", "lastName", "Franklin")), false, null, null,
						null))));

		this.mockMvc
			.perform(post("/scripting/new-session").param("sessionsState", sessionsState)
				.param("activeSessionId", "session-a")
				.param("runtimeId", runtimeId)
				.param("llmPrompt", "draft prompt"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("sessions", hasSize(1)))
			.andExpect(model().attribute("scriptForm", hasProperty("llmPrompt", is("draft prompt"))))
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString("Show Owners")))
			.andExpect(content().string(containsString(">New Session</button>")));
	}

	@Test
	void testSelectSessionShowsChosenHistory() throws Exception {
		String runtimeId = currentRuntimeId();
		String sessionsState = sessionsStateJson(
				session("session-a", "Show Owners", null, null,
						List.of(entry("entry-1", "show owners", "Show Owners",
								"api.ownersApi.findByCityStartingWith('Madison');", List.of(), false, false, null,
								false, null, null, null))),
				session("session-b", "Add Owner", null, null,
						List.of(entry("entry-2", "add owner", "Add Owner",
								"api.modificationApi.addOwner('John', 'Doe', '1 Main St.', 'Madison', '6085558763');",
								List.of(ScriptService.PreviewOperation
									.plain("Add owner: John Doe, 1 Main St., Madison, 6085558763")),
								false, false, null, false, null, null, null))));

		this.mockMvc
			.perform(post("/scripting/select-session").param("sessionsState", sessionsState)
				.param("activeSessionId", "session-a")
				.param("runtimeId", runtimeId)
				.param("selectedSessionId", "session-b"))
			.andExpect(status().isOk())
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString(">Add Owner</span>")))
			.andExpect(content().string(containsString("add owner")));
	}

	@Test
	void testGenerateAndPreviewScriptingScript() throws Exception {
		MockHttpSession httpSession = new MockHttpSession();
		Script<ScriptingExtension> generatedScript = mockGeneratedScript("Show Owners",
				"api.ownersApi.findByCityStartingWith('Madison');");
		given(this.scriptGenerationService.generateScript(eq("show owners in Madison"), any()))
			.willReturn(new ScriptGenerationService.GenerationResult("session-1", generatedScript));
		given(this.scriptService.preview(generatedScript))
			.willReturn(new ScriptService.PreviewResult(List.of(), false, ScriptService.ScriptResultData
				.present(List.of(Map.of("id", 1, "firstName", "George", "lastName", "Franklin")))));

		MvcResult pendingResult = this.mockMvc
			.perform(post("/scripting/generate-and-preview").session(httpSession)
				.param("llmPrompt", "show owners in Madison"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("sessions", hasSize(1)))
			.andExpect(model().attribute("activeChatEntries", hasSize(1)))
			.andExpect(model().attribute("scriptForm", hasProperty("llmPrompt", is(""))))
			.andExpect(
					model().attribute("scriptForm", hasProperty("sessionsState", containsString("\"pending\":true"))))
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString("Thinking")))
			.andReturn();

		refreshUntilContains(httpSession, activeSessionId(pendingResult), "Show Owners")
			.andExpect(model().attribute("scriptForm",
					hasProperty("sessionsState", containsString("\"llmSessionId\":\"session-1\""))))
			.andExpect(model().attribute("scriptForm", hasProperty("sessionsState", containsString("\"scriptJson\""))))
			.andExpect(
					model().attribute("scriptForm", hasProperty("sessionsState", containsString("\"pending\":false"))))
			.andExpect(model().attribute("scriptForm",
					hasProperty("sessionsState", not(containsString("\"scriptName\"")))))
			.andExpect(model().attribute("scriptForm",
					hasProperty("sessionsState", not(containsString("\"scriptText\"")))))
			.andExpect(model().attribute("scriptForm",
					hasProperty("sessionsState", not(containsString("\"usedMemberIds\"")))))
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString("Show Owners")))
			.andExpect(content().string(containsString("href=\"/owners/1\">George Franklin</a>")))
			.andExpect(content().string(containsString("Add to Find Owners page")))
			.andExpect(content().string(not(containsString(">Execute</button>"))));
	}

	@Test
	void testGenerateAndPreviewShowsGenerationFailureInChat() throws Exception {
		MockHttpSession httpSession = new MockHttpSession();
		given(this.scriptGenerationService.generateScript(eq("make a script"), any()))
			.willThrow(new IllegalArgumentException("Script generation failed: model unavailable"));

		MvcResult pendingResult = this.mockMvc
			.perform(post("/scripting/generate-and-preview").session(httpSession).param("llmPrompt", "make a script"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("sessions", hasSize(1)))
			.andExpect(model().attribute("activeChatEntries", hasSize(1)))
			.andExpect(model().attribute("scriptForm", hasProperty("llmPrompt", is(""))))
			.andExpect(model().attributeDoesNotExist("generationError"))
			.andExpect(model().attribute("scriptForm",
					hasProperty("sessionsState", containsString("\"prompt\":\"make a script\""))))
			.andExpect(
					model().attribute("scriptForm", hasProperty("sessionsState", containsString("\"pending\":true"))))
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString("make a script")))
			.andExpect(content().string(containsString("Thinking")))
			.andReturn();

		refreshUntilContains(httpSession, activeSessionId(pendingResult), "Script generation failed: model unavailable")
			.andExpect(model().attribute("scriptForm",
					hasProperty("sessionsState",
							containsString("\"previewError\":\"Script generation failed: model unavailable\""))))
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString("make a script")))
			.andExpect(content().string(containsString("Script generation failed: model unavailable")))
			.andExpect(content().string(not(containsString("<button type=\"button\" class=\"script-name-toggle\""))))
			.andExpect(content().string(not(containsString(">Execute</button>"))));
	}

	@Test
	void testGenerateAndPreviewUsesExistingAgentSessionHistory() throws Exception {
		MockHttpSession httpSession = new MockHttpSession();
		String runtimeId = currentRuntimeId();
		String sessionsState = sessionsStateJson(session("session-a", "Show Owners", "session-1", null,
				List.of(entry("entry-1", "show all owners", "Show Owners",
						"api.ownersApi.findByLastNameStartingWith('');", List.of(), false, false, null, false, null,
						null, null))));
		Script<ScriptingExtension> generatedScript = mockGeneratedScript("Refined Query",
				"api.ownersApi.findByLastNameStartingWith('F');");
		given(this.scriptGenerationService.generateScript(eq("improve this"), eq("session-1")))
			.willReturn(new ScriptGenerationService.GenerationResult("session-1", generatedScript));
		given(this.scriptService.preview(any())).willReturn(new ScriptService.PreviewResult(List.of(), false));

		MvcResult pendingResult = this.mockMvc
			.perform(post("/scripting/generate-and-preview").session(httpSession)
				.param("llmPrompt", "improve this")
				.param("activeSessionId", "session-a")
				.param("runtimeId", runtimeId)
				.param("sessionsState", sessionsState))
			.andExpect(status().isOk())
			.andExpect(model().attribute("activeChatEntries", hasSize(2)))
			.andExpect(model().attribute("scriptForm",
					hasProperty("sessionsState", containsString("\"prompt\":\"show all owners\""))))
			.andExpect(model().attribute("scriptForm",
					hasProperty("sessionsState", containsString("\"prompt\":\"improve this\""))))
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString("show all owners")))
			.andExpect(content().string(containsString("Thinking")))
			.andReturn();
		refreshUntilContains(httpSession, activeSessionId(pendingResult), "Refined Query")
			.andExpect(content().string(containsString("show all owners")));
	}

	@Test
	void testGenerateAndPreviewScriptingScriptWithoutPrompt() throws Exception {
		this.mockMvc.perform(post("/scripting/generate-and-preview").param("llmPrompt", ""))
			.andExpect(status().isOk())
			.andExpect(model().attributeHasFieldErrors("scriptForm", "llmPrompt"))
			.andExpect(view().name("scripts/scriptForm"));
	}

	@Test
	void testGenerateAndPreviewAllowsMultiplePendingPromptsInSameSession() throws Exception {
		MockHttpSession httpSession = new MockHttpSession();
		CountDownLatch generationStarted = new CountDownLatch(1);
		CountDownLatch releaseGeneration = new CountDownLatch(1);
		Script<ScriptingExtension> generatedScript = mockGeneratedScript("Show Owners",
				"api.ownersApi.findByCityStartingWith('Madison');");
		given(this.scriptGenerationService.generateScript(any(), any())).willAnswer(invocation -> {
			generationStarted.countDown();
			releaseGeneration.await(2, TimeUnit.SECONDS);
			return new ScriptGenerationService.GenerationResult("session-1", generatedScript);
		});

		try {
			MvcResult firstResult = this.mockMvc
				.perform(
						post("/scripting/generate-and-preview").session(httpSession).param("llmPrompt", "first prompt"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("activeChatEntries", hasSize(1)))
				.andExpect(content().string(containsString("first prompt")))
				.andExpect(content().string(containsString("Thinking")))
				.andReturn();
			generationStarted.await(2, TimeUnit.SECONDS);

			this.mockMvc
				.perform(post("/scripting/generate-and-preview").session(httpSession)
					.param("activeSessionId", activeSessionId(firstResult))
					.param("llmPrompt", "second prompt"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("activeChatEntries", hasSize(2)))
				.andExpect(content().string(containsString("first prompt")))
				.andExpect(content().string(containsString("second prompt")))
				.andExpect(content().string(containsString("Thinking")));
		}
		finally {
			releaseGeneration.countDown();
		}
	}

	@Test
	void testStateRefreshPreservesEmptyNewSessionWhileAnotherSessionIsPending() throws Exception {
		MockHttpSession httpSession = new MockHttpSession();
		CountDownLatch releaseGeneration = new CountDownLatch(1);
		Script<ScriptingExtension> generatedScript = mockGeneratedScript("Show Owners",
				"api.ownersApi.findByCityStartingWith('Madison');");
		given(this.scriptGenerationService.generateScript(any(), any())).willAnswer(invocation -> {
			releaseGeneration.await(2, TimeUnit.SECONDS);
			return new ScriptGenerationService.GenerationResult("session-1", generatedScript);
		});

		try {
			this.mockMvc
				.perform(post("/scripting/generate-and-preview").session(httpSession).param("llmPrompt", "show owners"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("activeChatEntries", hasSize(1)))
				.andExpect(content().string(containsString("Thinking")));

			this.mockMvc.perform(post("/scripting/new-session").session(httpSession))
				.andExpect(status().isOk())
				.andExpect(model().attribute("activeSession", nullValue()))
				.andExpect(model().attribute("activeChatEntries", hasSize(0)));

			this.mockMvc.perform(get("/scripting/state").session(httpSession))
				.andExpect(status().isOk())
				.andExpect(model().attribute("activeSession", nullValue()))
				.andExpect(model().attribute("activeChatEntries", hasSize(0)));
		}
		finally {
			releaseGeneration.countDown();
		}
	}

	@Test
	void testGenerateAndPreviewRendersWarningOnlyOnceInRed() throws Exception {
		MockHttpSession httpSession = new MockHttpSession();
		Script<ScriptingExtension> generatedScript = mockGeneratedScript("Add Owner",
				"api.modificationApi.addOwner('John', 'Doe', '', 'Fitchburg', '6085558763');");
		given(this.scriptGenerationService.generateScript(eq("add john doe"), any()))
			.willReturn(new ScriptGenerationService.GenerationResult("session-1", generatedScript));
		given(this.scriptService.preview(any())).willReturn(new ScriptService.PreviewResult(
				List.of(ScriptService.PreviewOperation.plain("Add owner: John Doe, (missing), Fitchburg, 6085558763",
						"[Missing required fields: address]")),
				true));

		MvcResult pendingResult = this.mockMvc
			.perform(post("/scripting/generate-and-preview").session(httpSession).param("llmPrompt", "add john doe"))
			.andExpect(status().isOk())
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString("Thinking")))
			.andReturn();

		refreshUntilContains(httpSession, activeSessionId(pendingResult),
				"Add owner: John Doe, (missing), Fitchburg, 6085558763")
			.andExpect(content().string(containsString("Add owner: John Doe, (missing), Fitchburg, 6085558763")))
			.andExpect(content().string(
					containsString("<span class=\"script-warning-text\">[Missing required fields: address]</span>")))
			.andExpect(content().string(not(containsString(
					"Add owner: John Doe, (missing), Fitchburg, 6085558763 [Missing required fields: address] [Missing required fields: address]"))))
			.andExpect(content().string(containsString(">Execute</button>")))
			.andExpect(content().string(containsString("Preview may be incomplete.")));
	}

	@Test
	void testGenerateAndPreviewNoOpModificationShowsMessageWithoutExecuteButton() throws Exception {
		MockHttpSession httpSession = new MockHttpSession();
		Script<ScriptingExtension> generatedScript = mockGeneratedScript("No-op Modification",
				"util.implement(types.ModificationExtension, { execute: (ownersApi, modificationApi) => {} })");
		given(this.scriptGenerationService.generateScript(eq("do nothing"), any()))
			.willReturn(new ScriptGenerationService.GenerationResult("session-1", generatedScript));
		given(this.scriptService.preview(any())).willReturn(new ScriptService.PreviewResult(List.of(), false,
				ScriptService.ScriptResultData.absent(), ScriptService.ScriptKind.MODIFICATION));

		MvcResult pendingResult = this.mockMvc
			.perform(post("/scripting/generate-and-preview").session(httpSession).param("llmPrompt", "do nothing"))
			.andExpect(status().isOk())
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString("Thinking")))
			.andReturn();

		refreshUntilContains(httpSession, activeSessionId(pendingResult),
				"No modifications to preview. The script did not request any changes.")
			.andExpect(model().attribute("scriptForm",
					hasProperty("sessionsState", containsString("\"scriptKind\":\"MODIFICATION\""))))
			.andExpect(content().string(containsString("Modifies data")))
			.andExpect(content().string(not(containsString(">Execute</button>"))))
			.andExpect(content().string(not(containsString("Add to Find Owners page"))));
	}

	@Test
	void testGenerateAndPreviewShowsCompactPetResultForModificationScript() throws Exception {
		MockHttpSession httpSession = new MockHttpSession();
		Script<ScriptingExtension> generatedScript = mockGeneratedScript("Add Pet",
				"api.modificationApi.addPet('George', 'Franklin', 'Unda', 'dog', '2020-05-01'); [{ name: 'Unda' }];");
		given(this.scriptGenerationService.generateScript(eq("add pet"), any()))
			.willReturn(new ScriptGenerationService.GenerationResult("session-1", generatedScript));
		given(this.scriptService.preview(any())).willReturn(new ScriptService.PreviewResult(
				List.of(ScriptService.PreviewOperation
					.plain("Add pet: Unda, type dog, birth date 2020-05-01, owner George Franklin", null)),
				false, ScriptService.ScriptResultData.present(List.of(Map.of("name", "Unda", "type", "dog", "owner",
						Map.of("id", 1, "firstName", "George", "lastName", "Franklin"))))));

		MvcResult pendingResult = this.mockMvc
			.perform(post("/scripting/generate-and-preview").session(httpSession).param("llmPrompt", "add pet"))
			.andExpect(status().isOk())
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString("Thinking")))
			.andReturn();

		refreshUntilContains(httpSession, activeSessionId(pendingResult),
				"Unda, dog, owner: <a href=\"/owners/1\">George Franklin</a>")
			.andExpect(content().string(containsString("Unda, dog, owner: <a href=\"/owners/1\">George Franklin</a>")))
			.andExpect(content().string(containsString(">Execute</button>")))
			.andExpect(content().string(not(containsString("table table-condensed"))));
	}

	@Test
	void testGenerateAndPreviewShowsErrorWhenScriptReturnsErrorResult() throws Exception {
		MockHttpSession httpSession = new MockHttpSession();
		Script<ScriptingExtension> generatedScript = mockGeneratedScript("Add Pet",
				"api.modificationApi.addPet('Missing', 'Owner', 'Unda', 'dog', '2020-05-01');");
		given(this.scriptGenerationService.generateScript(eq("add pet"), any()))
			.willReturn(new ScriptGenerationService.GenerationResult("session-1", generatedScript));
		given(this.scriptService.preview(any()))
			.willThrow(new IllegalArgumentException("Script returned an error result: Owner not found"));

		MvcResult pendingResult = this.mockMvc
			.perform(post("/scripting/generate-and-preview").session(httpSession).param("llmPrompt", "add pet"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("activeChatEntries", hasSize(1)))
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString("Thinking")))
			.andReturn();

		refreshUntilContains(httpSession, activeSessionId(pendingResult),
				"Script returned an error result: Owner not found")
			.andExpect(content().string(containsString("Script returned an error result: Owner not found")))
			.andExpect(content().string(not(containsString(">Execute</button>"))));
	}

	@Test
	void testExecuteScriptingScriptMarksLastExecutedSignature() throws Exception {
		String runtimeId = currentRuntimeId();
		String scriptName = "Add Owner";
		String scriptText = "api.modificationApi.addOwner('John', 'Doe', '1 Main St.', 'Madison', '6085558763');";
		String sessionsState = sessionsStateJson(session("session-a", "Add Owner", "session-1", null,
				List.of(entry("entry-1", "add owner", scriptName, scriptText,
						List.of(ScriptService.PreviewOperation
							.plain("Add owner: John Doe, 1 Main St., Madison, 6085558763")),
						false, false, null, false, null, null, null))));

		given(this.scriptService.execute(any())).willReturn(new ScriptService.ExecutionResult(
				List.of(ScriptService.PreviewOperation.plain("Add owner: John Doe, 1 Main St., Madison, 6085558763"))));

		this.mockMvc
			.perform(post("/scripting/execute").param("sessionsState", sessionsState)
				.param("activeSessionId", "session-a")
				.param("runtimeId", runtimeId)
				.param("targetEntryId", "entry-1"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("scriptForm",
					hasProperty("sessionsState", containsString("\"lastExecutedScriptJson\""))))
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString("Script executed successfully.")))
			.andExpect(content().string(containsString("data-repeated-execution=\"true\"")));
	}

	@Test
	void testExecuteScriptingScriptWhileGenerationIsPending() throws Exception {
		MockHttpSession httpSession = new MockHttpSession();
		CountDownLatch releaseGeneration = new CountDownLatch(1);
		String runtimeId = currentRuntimeId();
		String scriptName = "Add Owner";
		String scriptText = "api.modificationApi.addOwner('John', 'Doe', '1 Main St.', 'Madison', '6085558763');";
		String sessionsState = sessionsStateJson(session("session-a", "Add Owner", "session-1", null,
				List.of(entry("entry-1", "add owner", scriptName, scriptText,
						List.of(ScriptService.PreviewOperation
							.plain("Add owner: John Doe, 1 Main St., Madison, 6085558763")),
						false, false, null, false, null, null, null))));
		Script<ScriptingExtension> generatedScript = mockGeneratedScript("Show Owners",
				"api.ownersApi.findByCityStartingWith('Madison');");
		given(this.scriptGenerationService.generateScript(eq("show owners"), eq("session-1")))
			.willAnswer(invocation -> {
				releaseGeneration.await(2, TimeUnit.SECONDS);
				return new ScriptGenerationService.GenerationResult("session-1", generatedScript);
			});
		given(this.scriptService.execute(any())).willReturn(new ScriptService.ExecutionResult(
				List.of(ScriptService.PreviewOperation.plain("Add owner: John Doe, 1 Main St., Madison, 6085558763"))));

		try {
			this.mockMvc
				.perform(post("/scripting/generate-and-preview").session(httpSession)
					.param("sessionsState", sessionsState)
					.param("activeSessionId", "session-a")
					.param("runtimeId", runtimeId)
					.param("llmPrompt", "show owners"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("activeChatEntries", hasSize(2)))
				.andExpect(content().string(containsString("Thinking")));

			this.mockMvc
				.perform(post("/scripting/execute").session(httpSession)
					.param("activeSessionId", "session-a")
					.param("targetEntryId", "entry-1"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Script executed successfully.")))
				.andExpect(content().string(containsString("Thinking")));
		}
		finally {
			releaseGeneration.countDown();
		}
	}

	@Test
	void testRepeatedExecutionWarningSurvivesSessionSwitch() throws Exception {
		MockHttpSession httpSession = new MockHttpSession();
		String runtimeId = currentRuntimeId();
		String scriptName = "Add Owner";
		String scriptText = "api.modificationApi.addOwner('John', 'Doe', '1 Main St.', 'Madison', '6085558763');";
		Map<String, Object> addOwnerSession = session("session-a", "Add Owner", "session-1", null,
				List.of(entry("entry-1", "add owner", scriptName, scriptText,
						List.of(ScriptService.PreviewOperation
							.plain("Add owner: John Doe, 1 Main St., Madison, 6085558763")),
						false, false, null, false, null, null, null)));
		Map<String, Object> showOwnersSession = session("session-b", "Show Owners", "session-2", null,
				List.of(entry("entry-2", "show owners", "Show Owners",
						"api.ownersApi.findByCityStartingWith('Madison');", List.of(), false, true,
						List.of(Map.of("id", 1, "firstName", "George", "lastName", "Franklin")), false, null, null,
						null)));
		String sessionsState = sessionsStateJson(addOwnerSession, showOwnersSession);

		given(this.scriptService.execute(any())).willReturn(new ScriptService.ExecutionResult(
				List.of(ScriptService.PreviewOperation.plain("Add owner: John Doe, 1 Main St., Madison, 6085558763"))));

		this.mockMvc
			.perform(post("/scripting/execute").session(httpSession)
				.param("sessionsState", sessionsState)
				.param("activeSessionId", "session-a")
				.param("runtimeId", runtimeId)
				.param("targetEntryId", "entry-1"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("data-repeated-execution=\"true\"")));

		this.mockMvc
			.perform(post("/scripting/select-session").session(httpSession).param("selectedSessionId", "session-b"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(">Show Owners</span>")));

		this.mockMvc
			.perform(post("/scripting/select-session").session(httpSession).param("selectedSessionId", "session-a"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("data-repeated-execution=\"true\"")));
	}

	@Test
	void testRepeatedExecutionWarningUsesEntryExecutedState() throws Exception {
		String runtimeId = currentRuntimeId();
		String sessionsState = sessionsStateJson(session("session-a", "Add Owner", "session-1", null,
				List.of(entry("entry-1", "add owner", "Add Owner",
						"api.modificationApi.addOwner('John', 'Doe', '1 Main St.', 'Madison', '6085558763');",
						List.of(ScriptService.PreviewOperation
							.plain("Add owner: John Doe, 1 Main St., Madison, 6085558763")),
						false, false, null, true, "Script executed successfully.", null, null))));

		this.mockMvc
			.perform(post("/scripting/select-session").param("sessionsState", sessionsState)
				.param("activeSessionId", "session-a")
				.param("runtimeId", runtimeId)
				.param("selectedSessionId", "session-a"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("data-repeated-execution=\"true\"")));
	}

	@Test
	void testRefreshPreviewUpdatesQueryResultOnSameEntry() throws Exception {
		String runtimeId = currentRuntimeId();
		String scriptName = "Show Owners";
		String scriptText = "api.ownersApi.findByCityStartingWith('Madison');";
		String sessionsState = sessionsStateJson(session("session-a", scriptName, "session-1", null,
				List.of(entry("entry-1", "show owners", scriptName, scriptText, List.of(), false, true,
						List.of(Map.of("id", 1, "firstName", "George", "lastName", "Franklin")), false, null, null,
						null))));
		given(this.scriptService.preview(any())).willReturn(new ScriptService.PreviewResult(List.of(), false,
				ScriptService.ScriptResultData
					.present(List.of(Map.of("id", 2, "firstName", "James", "lastName", "Rutherford"))),
				ScriptService.ScriptKind.OWNER_QUERY));

		this.mockMvc
			.perform(post("/scripting/refresh-preview").param("sessionsState", sessionsState)
				.param("activeSessionId", "session-a")
				.param("runtimeId", runtimeId)
				.param("targetEntryId", "entry-1"))
			.andExpect(status().isOk())
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString("href=\"/owners/2\">James Rutherford</a>")))
			.andExpect(content().string(not(containsString("href=\"/owners/1\">George Franklin</a>"))))
			.andExpect(model().attribute("scriptForm", hasProperty("sessionsState", containsString("Rutherford"))))
			.andExpect(model().attribute("scriptForm", hasProperty("sessionsState", not(containsString("Franklin")))))
			.andExpect(model().attribute("activeChatEntries", hasSize(1)));
	}

	@Test
	void testRefreshPreviewRerunsModificationPreviewWithoutExecuting() throws Exception {
		String runtimeId = currentRuntimeId();
		String scriptName = "Add Owner";
		String scriptText = "api.modificationApi.addOwner('John', 'Doe', '1 Main St.', 'Madison', '6085558763');";
		String sessionsState = sessionsStateJson(session("session-a", "Add Owner", "session-1", null,
				List.of(entry("entry-1", "add owner", scriptName, scriptText,
						List.of(ScriptService.PreviewOperation
							.plain("Add owner: John Doe, 1 Main St., Madison, 6085558763")),
						false, false, null, true, "Script executed successfully.", null, null))));
		given(this.scriptService.preview(any())).willReturn(new ScriptService.PreviewResult(
				List.of(ScriptService.PreviewOperation.plain("Add owner: Jane Doe, 2 Main St., Madison, 6085559999")),
				false, ScriptService.ScriptResultData.absent(), ScriptService.ScriptKind.MODIFICATION));

		this.mockMvc
			.perform(post("/scripting/refresh-preview").param("sessionsState", sessionsState)
				.param("activeSessionId", "session-a")
				.param("runtimeId", runtimeId)
				.param("targetEntryId", "entry-1"))
			.andExpect(status().isOk())
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString("Add owner: Jane Doe, 2 Main St., Madison, 6085559999")))
			.andExpect(content().string(not(containsString("Script executed successfully."))))
			.andExpect(content().string(containsString("data-repeated-execution=\"true\"")));
		verify(this.scriptService, never()).execute(any());
	}

	@Test
	void testRefreshPreviewErrorClearsStalePreviewData() throws Exception {
		String runtimeId = currentRuntimeId();
		String scriptName = "Show Owners";
		String scriptText = "api.ownersApi.findByCityStartingWith('Madison');";
		String sessionsState = sessionsStateJson(session("session-a", scriptName, "session-1", null,
				List.of(entry("entry-1", "show owners", scriptName, scriptText, List.of(), false, true,
						List.of(Map.of("id", 1, "firstName", "George", "lastName", "Franklin")), false, null, null,
						null))));
		given(this.scriptService.preview(any()))
			.willThrow(new IllegalArgumentException("Script preview failed: owner API unavailable"));

		this.mockMvc
			.perform(post("/scripting/refresh-preview").param("sessionsState", sessionsState)
				.param("activeSessionId", "session-a")
				.param("runtimeId", runtimeId)
				.param("targetEntryId", "entry-1"))
			.andExpect(status().isOk())
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString("Script preview failed: owner API unavailable")))
			.andExpect(content().string(not(containsString("href=\"/owners/1\">George Franklin</a>"))))
			.andExpect(content().string(not(containsString("Script Result"))))
			.andExpect(content().string(not(containsString("Add to Find Owners page"))))
			.andExpect(model().attribute("scriptForm", hasProperty("sessionsState", not(containsString("Franklin")))));
	}

	@Test
	void testExecuteScriptingShowsErrorWhenScriptReturnsErrorResult() throws Exception {
		String runtimeId = currentRuntimeId();
		String scriptName = "Add Owner";
		String scriptText = "api.modificationApi.addPet('Missing', 'Owner', 'Unda', 'dog', '2020-05-01');";
		String sessionsState = sessionsStateJson(session("session-a", "Add Owner", "session-1", null,
				List.of(entry("entry-1", "add pet", scriptName, scriptText,
						List.of(ScriptService.PreviewOperation.plain("Add pet: Unda, type dog, birth date 2020-05-01")),
						false, false, null, false, null, null, null))));

		given(this.scriptService.execute(any()))
			.willThrow(new IllegalArgumentException("Script returned an error result: Owner not found"));

		this.mockMvc
			.perform(post("/scripting/execute").param("sessionsState", sessionsState)
				.param("activeSessionId", "session-a")
				.param("runtimeId", runtimeId)
				.param("targetEntryId", "entry-1"))
			.andExpect(status().isOk())
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString("Script returned an error result: Owner not found")))
			.andExpect(content().string(not(containsString("Script executed successfully."))));
	}

	@Test
	void testSaveOwnerQueryPersistsEligibleQueryScript() throws Exception {
		String runtimeId = currentRuntimeId();
		String scriptName = "Show Owners";
		String scriptText = "api.ownersApi.findByCityStartingWith('Madison');";
		String sessionsState = sessionsStateJson(session("session-a", scriptName, "session-1", null,
				List.of(entry("entry-1", "show owners", scriptName, scriptText, List.of(), false, true,
						List.of(Map.of("id", 1, "firstName", "George", "lastName", "Franklin")), false, null, null,
						null))));

		this.mockMvc
			.perform(post("/scripting/save-owner-query").param("sessionsState", sessionsState)
				.param("activeSessionId", "session-a")
				.param("runtimeId", runtimeId)
				.param("targetEntryId", "entry-1"))
			.andExpect(status().isOk())
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString("Added to Find Owners page.")));
	}

	@Test
	void testGenerateAndPreviewMarksRepeatedOwnerQuerySave() throws Exception {
		MockHttpSession httpSession = new MockHttpSession();
		Script<ScriptingExtension> generatedScript = mockGeneratedScript("Show Owners",
				"api.ownersApi.findByCityStartingWith('Madison');");
		given(this.scriptGenerationService.generateScript(eq("show owners in Madison"), any()))
			.willReturn(new ScriptGenerationService.GenerationResult("session-1", generatedScript));
		given(this.scriptService.preview(generatedScript))
			.willReturn(new ScriptService.PreviewResult(List.of(), false, ScriptService.ScriptResultData
				.present(List.of(Map.of("id", 1, "firstName", "George", "lastName", "Franklin")))));
		given(this.savedOwnerQueryScripts.isAlreadySaved("Show Owners", generatedScript.toJSON())).willReturn(true);

		MvcResult pendingResult = this.mockMvc
			.perform(post("/scripting/generate-and-preview").session(httpSession)
				.param("llmPrompt", "show owners in Madison"))
			.andExpect(status().isOk())
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString("Thinking")))
			.andReturn();

		refreshUntilContains(httpSession, activeSessionId(pendingResult), "Show Owners")
			.andExpect(content().string(containsString("data-repeated-owner-query-save=\"true\"")));
	}

	@Test
	void testSaveOwnerQueryRejectsNonOwnerListResult() throws Exception {
		String runtimeId = currentRuntimeId();
		String scriptName = "Show Count";
		String scriptText = "api.ownersApi.findByCityStartingWith('Madison').length;";
		String sessionsState = sessionsStateJson(
				session("session-a", scriptName, "session-1", null, List.of(entry("entry-1", "show count", scriptName,
						scriptText, List.of(), false, true, 4, false, null, null, null))));

		this.mockMvc
			.perform(post("/scripting/save-owner-query").param("sessionsState", sessionsState)
				.param("activeSessionId", "session-a")
				.param("runtimeId", runtimeId)
				.param("targetEntryId", "entry-1"))
			.andExpect(status().isOk())
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString(
					"Only query-only scripts that return an owner list can be added to the Find Owners page.")));
	}

	@Test
	void testStaleRuntimeDoesNotReuseSerializedSessions() throws Exception {
		String sessionsState = sessionsStateJson(session("session-a", "Show Owners", null, null,
				List.of(entry("entry-1", "show owners", "Show Owners",
						"api.ownersApi.findByCityStartingWith('Madison');", List.of(), false, false, null, false, null,
						null, null))));

		this.mockMvc
			.perform(post("/scripting/new-session").param("sessionsState", sessionsState)
				.param("activeSessionId", "session-a")
				.param("runtimeId", "stale-runtime-id"))
			.andExpect(status().isOk())
			.andExpect(model().attribute("sessions", hasSize(0)))
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString(">New Session</button>")))
			.andExpect(content().string(not(containsString("show owners"))));
	}

	@Test
	void testLegacySerializedEntryWithUsedMemberIdsStillLoads() throws Exception {
		String runtimeId = currentRuntimeId();
		Map<String, Object> legacyEntry = new LinkedHashMap<>();
		legacyEntry.put("id", "entry-1");
		legacyEntry.put("prompt", "show owners");
		legacyEntry.put("scriptName", "Show Owners");
		legacyEntry.put("scriptText", "api.ownersApi.findByCityStartingWith('Madison');");
		legacyEntry.put("scriptSourceName", "show-owners.js");
		legacyEntry.put("previewOperations", List.of());
		legacyEntry.put("previewHasWarnings", false);
		legacyEntry.put("scriptResultPresent", false);
		legacyEntry.put("scriptResultValue", null);
		legacyEntry.put("executable", false);
		legacyEntry.put("executed", false);
		legacyEntry.put("executionSuccessMessage", null);
		legacyEntry.put("executionError", null);
		legacyEntry.put("previewError", null);
		legacyEntry.put("usedMemberIds", List.of("OwnersApi#findByCityStartingWith"));

		String sessionsState = sessionsStateJson(session("session-a", "Show Owners", null, null, List.of(legacyEntry)));

		this.mockMvc
			.perform(post("/scripting/select-session").param("sessionsState", sessionsState)
				.param("activeSessionId", "session-a")
				.param("runtimeId", runtimeId)
				.param("selectedSessionId", "session-a"))
			.andExpect(status().isOk())
			.andExpect(view().name("scripts/scriptForm"))
			.andExpect(content().string(containsString("Show Owners")))
			.andExpect(content().string(containsString("show owners")));
	}

	private String currentRuntimeId() throws Exception {
		MvcResult mvcResult = this.mockMvc.perform(get("/scripting")).andReturn();
		ScriptCreationForm scriptForm = (ScriptCreationForm) mvcResult.getModelAndView().getModel().get("scriptForm");
		return scriptForm.getRuntimeId();
	}

	private String activeSessionId(MvcResult mvcResult) {
		ScriptCreationForm scriptForm = (ScriptCreationForm) mvcResult.getModelAndView().getModel().get("scriptForm");
		return scriptForm.getActiveSessionId();
	}

	private ResultActions refreshUntilContains(MockHttpSession httpSession, String activeSessionId, String expected)
			throws Exception {
		AssertionError lastError = null;
		for (int attempt = 0; attempt < 40; attempt++) {
			try {
				ResultActions resultActions = this.mockMvc
					.perform(get("/scripting/state").session(httpSession).param("activeSessionId", activeSessionId))
					.andExpect(status().isOk())
					.andExpect(content().string(containsString(expected)));
				return resultActions;
			}
			catch (AssertionError ex) {
				lastError = ex;
				Thread.sleep(50);
			}
		}
		throw lastError != null ? lastError
				: new AssertionError("Expected refreshed scripting page to contain " + expected);
	}

	private Script<ScriptingExtension> mockGeneratedScript(String caption, String sourceText) {
		return PetClinicScriptTestSupport.createScript(sourceText, caption);
	}

	private String sessionsStateJson(Map<String, Object>... sessions) throws JsonProcessingException {
		return this.objectMapper.writeValueAsString(List.of(sessions));
	}

	private Map<String, Object> session(String id, String name, String llmSessionId, String lastExecutedScriptJson,
			List<Map<String, Object>> entries) {
		Map<String, Object> session = new LinkedHashMap<>();
		session.put("id", id);
		session.put("name", name);
		session.put("llmSessionId", llmSessionId);
		session.put("lastExecutedScriptJson", lastExecutedScriptJson);
		session.put("entries", entries);
		return session;
	}

	private Map<String, Object> entry(String id, String prompt, String scriptName, String scriptText,
			List<ScriptService.PreviewOperation> previewOperations, boolean previewHasWarnings,
			boolean scriptResultPresent, Object scriptResultValue, boolean executed, String executionSuccessMessage,
			String executionError, String previewError) {
		Map<String, Object> entry = new LinkedHashMap<>();
		Script<ScriptingExtension> script = PetClinicScriptTestSupport.createScript(scriptText, scriptName);
		entry.put("id", id);
		entry.put("prompt", prompt);
		entry.put("scriptJson", script.toJSON());
		entry.put("previewOperations", previewOperations);
		entry.put("previewHasWarnings", previewHasWarnings);
		entry.put("scriptResultPresent", scriptResultPresent);
		entry.put("scriptResultValue", scriptResultValue);
		entry.put("executable", previewOperations != null && !previewOperations.isEmpty() && previewError == null);
		entry.put("executed", executed);
		entry.put("executionSuccessMessage", executionSuccessMessage);
		entry.put("executionError", executionError);
		entry.put("previewError", previewError);
		return entry;
	}

}
