package eu.solven.cleanthat.language.java.refactorer.mutators;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import com.google.common.collect.ImmutableList;

import eu.solven.cleanthat.language.java.IJdkVersionConstants;
import eu.solven.cleanthat.language.java.refactorer.meta.IClassTransformer;
import eu.solven.cleanthat.language.java.refactorer.meta.IRuleExternalUrls;
import eu.solven.cleanthat.language.java.rules.AJavaParserRule;

/**
 * Order modifiers according the the Java specification.
 *
 * @author Benoit Lacelle
 */
public class ModifierOrder extends AJavaParserRule implements IClassTransformer, IRuleExternalUrls {
	private static final Logger LOGGER = LoggerFactory.getLogger(ModifierOrder.class);

	private static final List<String> ORDERED_MODIFIERS = ImmutableList.of("public",
			"protected",
			"private",
			"abstract",
			"default",
			"static",
			"final",
			"transient",
			"volatile",
			"synchronized",
			"native",
			"strictfp");

	@Override
	public String getId() {
		// Same name as checkstyle
		return "ModifierOrder";
	}

	@Override
	public String minimalJavaVersion() {
		return IJdkVersionConstants.JDK_1;
	}

	@Override
	public String checkstyleUrl() {
		return "https://checkstyle.sourceforge.io/apidocs/com/puppycrawl/tools/checkstyle/checks/modifier/ModifierOrderCheck.html";
	}

	@Override
	public String jsparrowUrl() {
		return "https://jsparrow.github.io/rules/reorder-modifiers.html";
	}

	@Override
	protected boolean processNotRecursively(Node node) {
		if (node instanceof NodeWithModifiers<?>) {
			NodeWithModifiers<?> nodeWithModifiers = (NodeWithModifiers<?>) node;
			NodeList<Modifier> modifiers = nodeWithModifiers.getModifiers();

			NodeList<Modifier> mutableModifiers = new NodeList<>(modifiers);

			Collections.sort(mutableModifiers, new Comparator<Modifier>() {

				@Override
				public int compare(Modifier o1, Modifier o2) {
					return compare2(o1.getKeyword().asString(), o1.getKeyword().asString());
				}

				private int compare2(String left, String right) {
					return Integer.compare(ORDERED_MODIFIERS.indexOf(left), ORDERED_MODIFIERS.indexOf(right));
				}
			});

			boolean changed = areSameReferences(modifiers, mutableModifiers);

			if (changed) {
				LOGGER.debug("We fixed the ordering of modifiers");
				nodeWithModifiers.setModifiers(mutableModifiers);
				return true;
			}
		}

		return false;
	}

	@SuppressWarnings("PMD.CompareObjectsWithEquals")
	private boolean areSameReferences(NodeList<Modifier> modifiers, NodeList<Modifier> mutableModifiers) {
		boolean changed = false;
		for (int i = 0; i < modifiers.size(); i++) {
			// Check by reference
			if (modifiers.get(i) != mutableModifiers.get(i)) {
				changed = true;
				break;
			}
		}
		return changed;
	}
}
