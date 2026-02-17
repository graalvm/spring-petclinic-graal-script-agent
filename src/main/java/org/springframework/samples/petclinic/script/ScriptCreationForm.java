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

/**
 * Form object for creating scripts.
 *
 */
public class ScriptCreationForm {

	private String llmPrompt;

	private String sessionsState;

	private String activeSessionId;

	private String runtimeId;

	public String getLlmPrompt() {
		return this.llmPrompt;
	}

	public void setLlmPrompt(String llmPrompt) {
		this.llmPrompt = llmPrompt;
	}

	public String getSessionsState() {
		return this.sessionsState;
	}

	public void setSessionsState(String sessionsState) {
		this.sessionsState = sessionsState;
	}

	public String getActiveSessionId() {
		return this.activeSessionId;
	}

	public void setActiveSessionId(String activeSessionId) {
		this.activeSessionId = activeSessionId;
	}

	public String getRuntimeId() {
		return this.runtimeId;
	}

	public void setRuntimeId(String runtimeId) {
		this.runtimeId = runtimeId;
	}

}
