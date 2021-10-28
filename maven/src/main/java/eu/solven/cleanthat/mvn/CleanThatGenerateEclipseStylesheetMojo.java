package eu.solven.cleanthat.mvn;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.ByteStreams;

import eu.solven.cleanthat.language.java.eclipse.checkstyle.XmlProfileWriter;
import eu.solven.cleanthat.language.java.eclipse.generator.EclipseStylesheetGenerator;
import eu.solven.cleanthat.language.java.eclipse.generator.IEclipseStylesheetGenerator;

/**
 * The mojo generates an Eclipse formatter stylesheet minimyzing modifications over existing codebase.
 * 
 * @author Benoit Lacelle
 *
 */
// https://maven.apache.org/guides/plugin/guide-java-plugin-development.html
@Mojo(name = CleanThatGenerateEclipseStylesheetMojo.GOAL_ECLIPSE,
		defaultPhase = LifecyclePhase.NONE,
		threadSafe = true,
		aggregator = true,
		requiresProject = false)
public class CleanThatGenerateEclipseStylesheetMojo extends ACleanThatSpringMojo {
	private static final Logger LOGGER = LoggerFactory.getLogger(CleanThatCleanThatMojo.class);

	// ".*/src/[main|test]/java/.*/.*\\.java"
	public static final String DEFAULT_JAVA_REGEX = ".*\\.java";

	public static final String GOAL_ECLIPSE = "eclipse_formatter-stylesheet";

	// https://stackoverflow.com/questions/3084629/finding-the-root-directory-of-a-multi-module-maven-reactor-project
	@Parameter(property = "eclipse_formatter.url",
			defaultValue = "${maven.multiModuleProjectDirectory}/.cleanthat/eclipse_formatter-stylesheet.xml")
	private String configPath;

	@Parameter(property = "java.regex", defaultValue = DEFAULT_JAVA_REGEX)
	private String javaRegex;

	@VisibleForTesting
	protected void setConfigPath(String configPath) {
		this.configPath = configPath;
	}

	@VisibleForTesting
	protected void setJavaRegex(String javaRegex) {
		this.javaRegex = javaRegex;
	}

	@Override
	protected List<? extends Class<?>> springClasses() {
		return Arrays.asList(EclipseStylesheetGenerator.class);
	}

	@Override
	public void doClean(ApplicationContext appContext) throws IOException {
		// https://github.com/maven-download-plugin/maven-download-plugin/blob/master/src/main/java/com/googlecode/download/maven/plugin/internal/WGet.java#L324
		MavenProject mavenProject = getProject();
		if (isRunOnlyAtRoot() && !mavenProject.isExecutionRoot()) {
			// This will check it is called only if the command is run from the project root.
			// However, it will not prevent the plugin to be called on each module
			getLog().info("maven-cleanthat-plugin:cleanthat skipped (not project root)");
			return;
		}
		IEclipseStylesheetGenerator generator = appContext.getBean(IEclipseStylesheetGenerator.class);

		Map<Path, String> pathToContent = loadAnyJavaFile(mavenProject, generator);

		Map<String, String> settings = generator.generateSettings(pathToContent);
		writeSettings(settings);
	}

	protected Map<Path, String> loadAnyJavaFile(MavenProject mavenProject, IEclipseStylesheetGenerator generator) {
		Set<Path> roots = mavenProject.getCollectedProjects().stream().flatMap(p -> {
			Path projectBaseDir = p.getBasedir().toPath();
			List<String> sourceRoots = p.getCompileSourceRoots();
			List<String> testRoots = p.getTestCompileSourceRoots();

			LOGGER.debug("Consider for baseDir '{}': {} and {}", projectBaseDir, sourceRoots, testRoots);

			// We make path relative to the baseDir, even through it seems mvn provides absolute paths is default case
			return Stream.concat(sourceRoots.stream(), testRoots.stream())
					.map(sourceFolder -> projectBaseDir.resolve(sourceFolder));
		}).collect(Collectors.toSet());

		return loadAnyJavaFile(generator, roots);
	}

	protected Map<Path, String> loadAnyJavaFile(IEclipseStylesheetGenerator generator, Set<Path> roots) {
		Map<Path, String> pathToContent = new LinkedHashMap<>();
		roots.forEach(rootAsPath -> {
			try {
				if (!rootAsPath.toFile().exists()) {
					LOGGER.debug("The root folder '{}' does not exist", rootAsPath);
					return;
				}

				Pattern compiledRegex = Pattern.compile(javaRegex);
				Map<Path, String> fileToContent = generator.loadFilesContent(rootAsPath, compiledRegex);
				LOGGER.info("Loaded {} files from {}", fileToContent.size(), rootAsPath);
				pathToContent.putAll(fileToContent);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
		return pathToContent;
	}

	protected void writeSettings(Map<String, String> settings) throws IOException {
		Path whereToWrite = Paths.get(configPath);
		File whereToWriteAsFile = whereToWrite.toFile().getAbsoluteFile();
		if (whereToWriteAsFile.exists()) {
			if (whereToWriteAsFile.isFile()) {
				LOGGER.warn("We are going to write over '{}'", whereToWrite);
			} else {
				throw new IllegalStateException("There is something but not a file at: " + whereToWriteAsFile);
			}
		} else {
			whereToWriteAsFile.getParentFile().mkdirs();
		}

		try (InputStream is = XmlProfileWriter.writeFormatterProfileToStream("cleanthat", settings);
				OutputStream outputStream = Files.newOutputStream(whereToWrite, StandardOpenOption.CREATE)) {
			ByteStreams.copy(is, outputStream);
		} catch (TransformerException | ParserConfigurationException e) {
			throw new IllegalArgumentException(e);
		}
	}
}