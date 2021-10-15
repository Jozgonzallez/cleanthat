package eu.solven.cleanthat.language.scala.scalafmt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.scalafmt.dynamic.ScalafmtDynamicError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import com.google.common.io.ByteStreams;

import eu.solven.cleanthat.formatter.LineEnding;
import eu.solven.cleanthat.language.ISourceCodeProperties;
import eu.solven.cleanthat.language.SourceCodeProperties;

public class TestScalafmtFormatter {
	private static final Logger LOGGER = LoggerFactory.getLogger(TestScalafmtFormatter.class);

	@Test
	public void testScala() throws IOException {
		ISourceCodeProperties sourceCodeProperties = new SourceCodeProperties();
		ScalafmtProperties scalaFmtProperties = new ScalafmtProperties();
		final ScalafmtStyleEnforcer formatter = new ScalafmtStyleEnforcer(sourceCodeProperties, scalaFmtProperties);
		String scalaCode =
				new String(ByteStreams.toByteArray(new ClassPathResource("/scala/Hello.scala").getInputStream()),
						StandardCharsets.UTF_8);
		String formatted;
		try {
			formatted = formatter.doFormat(scalaCode, LineEnding.KEEP);
		} catch (ScalafmtDynamicError.CannotDownload e) {
			LOGGER.info("Issue downloading Scalafmt. Possibly a proxy issue", e);
			return;
		}

		Assertions.assertThat(formatted).isNotEqualTo(scalaCode).hasLineCount(5);
		Assertions.assertThat(formatted.split("[\r\n]"))
				.hasSize(5)
				.containsExactly("object Hello {",
						"  def main(args: Array[String]) = {",
						"    println(\"Hello, world\")",
						"  }",
						"}");

	}
}
