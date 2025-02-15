/*
 * Copyright 2023 Benoit Lacelle - SOLVEN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.solven.cleanthat.lambda;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import eu.solven.cleanthat.language.openrewrite.OpenrewriteFormattersFactory;
import eu.solven.cleanthat.language.spotless.SpotlessFormattersFactory;

/**
 * Spring configuration wrapping all available engines
 * 
 * @author Benoit Lacelle
 *
 */
@Configuration
@Import({

		// JavaFormattersFactory.class,
		SpotlessFormattersFactory.class,
		OpenrewriteFormattersFactory.class,

})
public class AllEnginesSpringConfig {
}
