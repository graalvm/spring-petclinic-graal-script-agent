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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

/**
 * Shared formatter for script results rendered in the UI.
 *
 */
public final class ScriptResultFormatter {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private static final Set<String> COMPACT_UNLABELED_KEYS = Set.of("type", "petType", "telephone", "address", "city",
			"birthDate", "date", "description");

	private ScriptResultFormatter() {
	}

	public static String formatHtml(Object value) {
		StringBuilder html = new StringBuilder();
		if (isStructuredOwnerDisplayResult(value)) {
			appendResultValue(html, ((Map<?, ?>) value).get("owners"), true);
			return html.toString();
		}
		appendResultValue(html, value, true);
		return html.toString();
	}

	public static String writePrettyJson(Object value) {
		try {
			return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("Could not serialize script result.", ex);
		}
	}

	private static void appendResultValue(StringBuilder html, Object value, boolean topLevel) {
		String compactHtml = tryFormatCompactHtml(value);
		if (compactHtml != null) {
			html.append(compactHtml);
			return;
		}
		if (value == null) {
			html.append("<span style=\"color: #7b8794;\">null</span>");
			return;
		}
		if (value instanceof String || value instanceof Number || value instanceof Boolean) {
			html.append("<span>").append(HtmlUtils.htmlEscape(String.valueOf(value))).append("</span>");
			return;
		}
		if (value instanceof List<?> list) {
			appendResultList(html, list, topLevel);
			return;
		}
		if (value instanceof Map<?, ?> map) {
			appendResultMap(html, map, topLevel);
			return;
		}
		html.append("<span>").append(HtmlUtils.htmlEscape(String.valueOf(value))).append("</span>");
	}

	private static void appendResultList(StringBuilder html, List<?> list, boolean topLevel) {
		if (list.isEmpty()) {
			html.append(
					"<div style=\"padding: 12px 14px; border: 1px dashed #cbd2d9; border-radius: 10px; color: #7b8794;\">Empty list</div>");
			return;
		}
		html.append("<div style=\"display: grid; gap: 8px;\">");
		for (Object item : list) {
			html.append("<div>");
			String compactHtml = tryFormatCompactHtml(item);
			if (compactHtml != null) {
				html.append(compactHtml);
			}
			else if (item instanceof Map<?, ?> || item instanceof List<?>) {
				html.append(
						"<div style=\"padding: 10px 12px; border: 1px solid #d9e2ec; border-radius: 10px; background: #fff;\">");
				appendResultValue(html, item, false);
				html.append("</div>");
			}
			else {
				appendResultValue(html, item, false);
			}
			html.append("</div>");
		}
		html.append("</div>");
	}

	private static void appendResultMap(StringBuilder html, Map<?, ?> map, boolean topLevel) {
		if (isStructuredOwnerDisplayResult(map)) {
			appendResultValue(html, map.get("owners"), topLevel);
			return;
		}
		String compactHtml = tryFormatCompactHtml(map);
		if (compactHtml != null) {
			if (topLevel) {
				html.append("<div>").append(compactHtml).append("</div>");
			}
			else {
				html.append(compactHtml);
			}
			return;
		}
		OwnerLink ownerLink = extractOwnerLink(map);
		html.append(
				"<div style=\"border: 1px solid #d9e2ec; border-radius: 12px; background: #fff; overflow: hidden;\">");
		if (ownerLink != null) {
			html.append(
					"<div style=\"padding: 10px 12px; background: #f0f4f8; border-bottom: 1px solid #d9e2ec; font-weight: 600;\">Owner: <a href=\"/owners/")
				.append(ownerLink.ownerId())
				.append("\">")
				.append(HtmlUtils.htmlEscape(ownerLink.displayName()))
				.append("</a></div>");
		}
		html.append("<table class=\"table table-condensed\" style=\"margin-bottom: 0;\">");
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (isEmptyList(entry.getValue())) {
				continue;
			}
			html.append("<tr><th style=\"width: 24%; white-space: nowrap;\">")
				.append(HtmlUtils.htmlEscape(String.valueOf(entry.getKey())))
				.append("</th><td>");
			appendResultValue(html, entry.getValue(), false);
			html.append("</td></tr>");
		}
		html.append("</table></div>");
	}

	private static String tryFormatCompactHtml(Object value) {
		if (value == null) {
			return "<span style=\"color: #7b8794;\">null</span>";
		}
		if (value instanceof String || value instanceof Number || value instanceof Boolean) {
			return "<span>" + HtmlUtils.htmlEscape(String.valueOf(value)) + "</span>";
		}
		if (value instanceof Map<?, ?> map) {
			return tryFormatCompactMapHtml(map);
		}
		return null;
	}

	private static String tryFormatCompactMapHtml(Map<?, ?> map) {
		List<String> tokens = new ArrayList<>();
		List<String> consumedKeys = new ArrayList<>();
		String primaryDisplayHtml = extractPrimaryDisplayHtml(map, consumedKeys);
		if (primaryDisplayHtml != null) {
			tokens.add(primaryDisplayHtml);
		}

		List<ResultEntry> entries = orderedResultEntries(map);
		for (ResultEntry entry : entries) {
			if (isEmptyList(entry.value())) {
				continue;
			}
			if (consumedKeys.contains(entry.key())) {
				continue;
			}
			if (isInlineOwnerField(entry.key())) {
				String relatedOwnerHtml = extractRelatedOwnerHtml(map, consumedKeys);
				if (relatedOwnerHtml != null) {
					tokens.add(relatedOwnerHtml);
					continue;
				}
			}
			if ("id".equals(entry.key()) && entries.size() > 1) {
				continue;
			}
			String tokenHtml = tryFormatCompactToken(entry.key(), entry.value());
			if (tokenHtml == null) {
				return null;
			}
			tokens.add(tokenHtml);
		}

		if (tokens.isEmpty()) {
			return null;
		}
		return "<span>" + String.join(", ", tokens) + "</span>";
	}

	private static String extractPrimaryDisplayHtml(Map<?, ?> map, List<String> consumedKeys) {
		String namedDisplayHtml = extractNamedPrimaryDisplayHtml(map, consumedKeys);
		if (namedDisplayHtml != null) {
			return namedDisplayHtml;
		}

		OwnerLink ownerLink = extractOwnerLink(map);
		if (ownerLink != null) {
			consumedKeys.add("id");
			consumedKeys.add("firstName");
			consumedKeys.add("lastName");
			return ownerLinkHtml(ownerLink);
		}

		Object firstName = map.get("firstName");
		Object lastName = map.get("lastName");
		if (firstName instanceof String first && lastName instanceof String last && StringUtils.hasText(first)
				&& StringUtils.hasText(last)) {
			consumedKeys.add("firstName");
			consumedKeys.add("lastName");
			return HtmlUtils.htmlEscape(first + " " + last);
		}

		return null;
	}

	private static String extractNamedPrimaryDisplayHtml(Map<?, ?> map, List<String> consumedKeys) {
		for (String key : List.of("name", "petName")) {
			Object value = map.get(key);
			if (value != null && isCompactScalar(value)
					&& (!(value instanceof String stringValue) || StringUtils.hasText(stringValue))) {
				consumedKeys.add(key);
				return HtmlUtils.htmlEscape(String.valueOf(value));
			}
		}
		return null;
	}

	private static String extractRelatedOwnerHtml(Map<?, ?> map, List<String> consumedKeys) {
		if (consumedKeys.contains("id") || consumedKeys.contains("firstName") || consumedKeys.contains("lastName")) {
			return null;
		}

		OwnerLink ownerLink = extractOwnerLink(map);
		if (ownerLink != null) {
			consumedKeys.add("id");
			consumedKeys.add("firstName");
			consumedKeys.add("lastName");
			return "owner: " + ownerLinkHtml(ownerLink);
		}

		Object firstName = map.get("firstName");
		Object lastName = map.get("lastName");
		if (firstName instanceof String first && lastName instanceof String last && StringUtils.hasText(first)
				&& StringUtils.hasText(last)) {
			consumedKeys.add("firstName");
			consumedKeys.add("lastName");
			return "owner: " + HtmlUtils.htmlEscape(first + " " + last);
		}
		return null;
	}

	private static boolean isInlineOwnerField(String key) {
		return "id".equals(key) || "firstName".equals(key) || "lastName".equals(key);
	}

	private static String tryFormatCompactToken(String key, Object value) {
		if (value instanceof List<?>) {
			return null;
		}

		String valueHtml;
		if (isCompactScalar(value)) {
			valueHtml = value == null ? "<span style=\"color: #7b8794;\">null</span>"
					: HtmlUtils.htmlEscape(String.valueOf(value));
		}
		else if (value instanceof Map<?, ?> map) {
			valueHtml = extractSimpleRelationHtml(map);
			if (valueHtml == null) {
				return null;
			}
		}
		else {
			return null;
		}

		if (COMPACT_UNLABELED_KEYS.contains(key)) {
			return valueHtml;
		}
		return HtmlUtils.htmlEscape(formatResultKeyLabel(key)) + ": " + valueHtml;
	}

	private static String extractSimpleRelationHtml(Map<?, ?> map) {
		OwnerLink ownerLink = extractOwnerLink(map);
		if (ownerLink != null && containsOnlyKeys(map, Set.of("id", "firstName", "lastName"))) {
			return ownerLinkHtml(ownerLink);
		}

		Object firstName = map.get("firstName");
		Object lastName = map.get("lastName");
		if (firstName instanceof String first && lastName instanceof String last && StringUtils.hasText(first)
				&& StringUtils.hasText(last) && containsOnlyKeys(map, Set.of("firstName", "lastName"))) {
			return HtmlUtils.htmlEscape(first + " " + last);
		}

		Object name = map.get("name");
		if (name != null && isCompactScalar(name)
				&& (!(name instanceof String stringName) || StringUtils.hasText(stringName))
				&& containsOnlyKeys(map, Set.of("id", "name"))) {
			return HtmlUtils.htmlEscape(String.valueOf(name));
		}
		return null;
	}

	private static boolean containsOnlyKeys(Map<?, ?> map, Set<String> allowedKeys) {
		for (Object rawKey : map.keySet()) {
			if (!allowedKeys.contains(String.valueOf(rawKey))) {
				return false;
			}
		}
		return true;
	}

	private static List<ResultEntry> orderedResultEntries(Map<?, ?> map) {
		List<ResultEntry> entries = new ArrayList<>();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			entries.add(new ResultEntry(String.valueOf(entry.getKey()), entry.getValue()));
		}
		entries.sort((left, right) -> {
			int rankCompare = Integer.compare(resultKeyRank(left.key()), resultKeyRank(right.key()));
			if (rankCompare != 0) {
				return rankCompare;
			}
			return left.key().compareTo(right.key());
		});
		return entries;
	}

	private static int resultKeyRank(String key) {
		return switch (key) {
			case "name" -> 0;
			case "petName" -> 1;
			case "type", "petType" -> 2;
			case "telephone" -> 3;
			case "address" -> 4;
			case "city" -> 5;
			case "birthDate", "date" -> 6;
			case "description" -> 7;
			case "owner" -> 8;
			case "id", "firstName", "lastName" -> 8;
			case "pet" -> 9;
			default -> 20;
		};
	}

	private static boolean isCompactScalar(Object value) {
		return value == null || value instanceof String || value instanceof Number || value instanceof Boolean;
	}

	private static boolean isEmptyList(Object value) {
		return value instanceof List<?> list && list.isEmpty();
	}

	private static boolean isStructuredOwnerDisplayResult(Object value) {
		return value instanceof Map<?, ?> map && map.containsKey("owners") && map.size() == 1;
	}

	private static String ownerLinkHtml(OwnerLink ownerLink) {
		return "<a href=\"/owners/" + ownerLink.ownerId() + "\">" + HtmlUtils.htmlEscape(ownerLink.displayName())
				+ "</a>";
	}

	private static String formatResultKeyLabel(String key) {
		StringBuilder label = new StringBuilder();
		for (int i = 0; i < key.length(); i++) {
			char character = key.charAt(i);
			if (i > 0 && Character.isUpperCase(character)) {
				label.append(' ');
				label.append(Character.toLowerCase(character));
			}
			else {
				label.append(character);
			}
		}
		return label.toString();
	}

	private static OwnerLink extractOwnerLink(Map<?, ?> map) {
		Object ownerId = map.get("id");
		Object firstName = map.get("firstName");
		Object lastName = map.get("lastName");
		if (ownerId instanceof Number id && firstName instanceof String first && lastName instanceof String last
				&& StringUtils.hasText(first) && StringUtils.hasText(last)) {
			return new OwnerLink(id.intValue(), first + " " + last);
		}
		return null;
	}

	private record OwnerLink(int ownerId, String displayName) {
	}

	private record ResultEntry(String key, Object value) {
	}

}
