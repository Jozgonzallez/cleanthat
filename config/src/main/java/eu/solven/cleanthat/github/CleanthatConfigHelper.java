package eu.solven.cleanthat.github;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.solven.cleanthat.config.pojo.CleanthatRepositoryProperties;

/**
 * Helps manipulating CleanThat.json files
 *
 * @author Benoit Lacelle
 */
public class CleanthatConfigHelper {

	protected CleanthatConfigHelper() {
		// hidden
	}

	public static CleanthatRepositoryProperties parseConfig(ObjectMapper objectMapper, Map<?, ?> configAsMap) {
		return objectMapper.convertValue(configAsMap, CleanthatRepositoryProperties.class);
	}
}
