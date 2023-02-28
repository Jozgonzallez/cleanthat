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
package eu.solven.cleanthat.engine.java.refactorer.mutators;

import java.util.Objects;
import java.util.Optional;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.BinaryExpr.Operator;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;

import eu.solven.cleanthat.engine.java.IJdkVersionConstants;
import eu.solven.cleanthat.engine.java.refactorer.AJavaParserMutator;

/**
 * Turns `.stream(s -> s.size())` into `.stream(String::size)`
 *
 * @author Benoit Lacelle
 */
@SuppressWarnings("PMD.GodClass")
public class LambdaIsMethodReference extends AJavaParserMutator {
	@Override
	public boolean isDraft() {
		// Beware of Objects import management
		return true;
	}

	@Override
	public String minimalJavaVersion() {
		return IJdkVersionConstants.JDK_8;
	}

	@Override
	public Optional<String> getCleanthatId() {
		return Optional.of("LambdaIsMethodReference");
	}

	@Override
	public Optional<String> getSonarId() {
		return Optional.of("S1612");
	}

	@Override
	public String jsparrowUrl() {
		return "https://jsparrow.github.io/rules/lambda-to-method-reference.html#code-changes";
	}

	@SuppressWarnings({ "PMD.CognitiveComplexity", "PMD.NPathComplexity" })
	@Override
	protected boolean processNotRecursively(Node node) {
		if (!(node instanceof LambdaExpr)) {
			return false;
		}

		var lambdaExpr = (LambdaExpr) node;

		// if (lambdaExpr.getParameters().isEmpty()) {
		// return hasZeroVariable(lambdaExpr);
		// } else
		if (lambdaExpr.getParameters().size() == 1) {
			var singleParameter = lambdaExpr.getParameters().get(0);

			return hasOneVariable(lambdaExpr, singleParameter);
		} else {
			return false;
		}

	}

	// private boolean hasZeroVariable(LambdaExpr lambdaExpr) {
	// Statement body = lambdaExpr.getBody();
	//
	// if (body.isExpressionStmt() && body.asExpressionStmt().getExpression().isMethodCallExpr()) {
	// MethodCallExpr methodCallExpr = body.asExpressionStmt().getExpression().asMethodCallExpr();
	//
	// Optional<Expression> optScope = methodCallExpr.getScope();
	// if (optScope.isEmpty()) {
	// return false;
	// }
	//
	// Expression scope = optScope.get();
	//
	// Optional<ResolvedType> scopeType = optResolvedType(scope);
	//
	// if (scopeType.isEmpty()) {
	// return false;
	// } else if (!scopeType.get().isReferenceType()) {
	// return false;
	// }
	//
	// // ClassOrInterfaceType type =
	// // new ClassOrInterfaceType(scopeType.get().asReferenceType().getQualifiedName());
	//
	// // `b -> b.<String>getObject()` -> `B::<String>getObject`
	// // MethodReferenceExpr methodReference = new MethodReferenceExpr(new ClassExpr(type),
	// // new NodeList<>(),
	// // methodCallExpr.getNameAsString());
	// // return lambdaExpr.replace(methodReference);
	//
	// // `b -> System.out.println(b)` into `System.out::println`
	// MethodReferenceExpr methodReference =
	// new MethodReferenceExpr(scope, new NodeList<>(), methodCallExpr.getNameAsString());
	// return lambdaExpr.replace(methodReference);
	//
	// }
	// return false;
	// }

	@SuppressWarnings("PMD.CognitiveComplexity")
	private boolean hasOneVariable(LambdaExpr lambdaExpr, Parameter singleParameter) {
		var body = lambdaExpr.getBody();

		if (body.isExpressionStmt() && body.asExpressionStmt().getExpression().isInstanceOfExpr()) {

			var instanceOfExpr = body.asExpressionStmt().getExpression().asInstanceOfExpr();

			if (!instanceOfExpr.getExpression().isNameExpr()
					|| !instanceOfExpr.getExpression().asNameExpr().getName().equals(singleParameter.getName())) {
				return false;
			}

			// `a -> a instanceof B` -> `B.class::isInstance`
			var newScope = new ClassExpr(instanceOfExpr.getType());
			var methodReference = new MethodReferenceExpr(newScope, new NodeList<>(), "isInstance");
			return lambdaExpr.replace(methodReference);
		} else if (body.isExpressionStmt() && body.asExpressionStmt().getExpression().isCastExpr()) {
			var castExpr = body.asExpressionStmt().getExpression().asCastExpr();

			if (!castExpr.getType().isClassOrInterfaceType()) {
				return false;
			} else if (castExpr.getType().asClassOrInterfaceType().getTypeArguments().isPresent()) {
				// We can not have expression like `Class<? extends XXX>.class`
				return false;
			}

			if (!castExpr.getExpression().isNameExpr()
					|| !castExpr.getExpression().asNameExpr().getName().equals(singleParameter.getName())) {
				return false;
			}

			// `a -> (B) a` -> `B.class::cast`
			var newScope = new ClassExpr(castExpr.getType());
			var methodReference = new MethodReferenceExpr(newScope, new NodeList<>(), "cast");
			return lambdaExpr.replace(methodReference);

		} else if (body.isExpressionStmt() && body.asExpressionStmt().getExpression().isMethodCallExpr()) {
			var methodCallExpr = body.asExpressionStmt().getExpression().asMethodCallExpr();

			return onMethodCall(lambdaExpr, singleParameter, methodCallExpr);

		} else if (body.isExpressionStmt() && body.asExpressionStmt().getExpression().isBinaryExpr()) {
			var binaryExpr = body.asExpressionStmt().getExpression().asBinaryExpr();
			var operator = binaryExpr.getOperator();
			var left = binaryExpr.getLeft();
			var right = binaryExpr.getRight();
			if ((operator == Operator.EQUALS || operator == Operator.NOT_EQUALS)
					&& (left.isNullLiteralExpr() || right.isNullLiteralExpr())) {

				Expression notNunull;
				if (left.isNullLiteralExpr()) {
					if (right.isNullLiteralExpr()) {
						// comparing null with null
						return binaryExpr.replace(new BooleanLiteralExpr(true));
					} else {
						notNunull = right;
					}
				} else {
					notNunull = left;
				}

				if (!notNunull.isNameExpr() || !notNunull.asNameExpr().getName().equals(singleParameter.getName())) {
					return false;
				}

				// `a -> a == null` -> `Objects::isNull`
				String methodIdentifier;
				if (operator == Operator.EQUALS) {
					methodIdentifier = "isNull";
				} else {
					assert operator == Operator.NOT_EQUALS;
					methodIdentifier = "nonNull";
				}

				// TODO This lack the additional Import?
				// What if there is already an import to guava Objects?
				var methodReference = new MethodReferenceExpr(
						new TypeExpr(new ClassOrInterfaceType(null, Objects.class.getSimpleName())),
						new NodeList<>(),
						methodIdentifier);
				return lambdaExpr.replace(methodReference);
			}
		}
		return false;
	}

	private boolean onMethodCall(LambdaExpr lambdaExpr, Parameter singleParameter, MethodCallExpr methodCallExpr) {
		Optional<Expression> optScope = methodCallExpr.getScope();
		if (optScope.isEmpty()) {
			return false;
		}
		var scope = optScope.get();

		if (methodCallExpr.getArguments().size() == 1 && methodCallExpr.getArguments().get(0).isNameExpr()
				&& methodCallExpr.getArguments().get(0).asNameExpr().getName().equals(singleParameter.getName())) {

			Optional<ResolvedType> scopeType = optResolvedType(scope);

			if (scopeType.isEmpty()) {
				return false;
			} else if (!scopeType.get().isReferenceType()) {
				return false;
			}

			// ClassOrInterfaceType type =
			// new ClassOrInterfaceType(scopeType.get().asReferenceType().getQualifiedName());

			// `b -> b.<String>getObject()` -> `B::<String>getObject`
			// MethodReferenceExpr methodReference = new MethodReferenceExpr(new ClassExpr(type),
			// new NodeList<>(),
			// methodCallExpr.getNameAsString());
			// return lambdaExpr.replace(methodReference);

			// `b -> System.out.println(b)` into `System.out::println`
			var methodReference = new MethodReferenceExpr(scope, new NodeList<>(), methodCallExpr.getNameAsString());
			return lambdaExpr.replace(methodReference);
		} else if (methodCallExpr.getArguments().isEmpty() && optScope.get().isNameExpr()
				&& optScope.get().asNameExpr().getName().equals(singleParameter.getName())) {

			Optional<ResolvedType> scopeType = optResolvedType(scope);

			if (scopeType.isEmpty()) {
				return false;
			} else if (!scopeType.get().isConstraint()) {
				return false;
			}

			var constraint = scopeType.get().asConstraintType();

			var resolvedBound = constraint.getBound();

			if (!resolvedBound.isReferenceType()) {
				return false;
			}

			var refType = resolvedBound.asReferenceType();

			// https://github.com/javaparser/javaparser/issues/3929
			// return false;

			Optional<ResolvedReferenceTypeDeclaration> optTypeDeclaration = refType.getTypeDeclaration();
			if (optTypeDeclaration.isEmpty()) {
				return false;
			}

			var typeDeclaration = optTypeDeclaration.get();
			var packageName = typeDeclaration.getPackageName();
			var qualifiedName = refType.getQualifiedName();

			Optional<CompilationUnit> compilationUnit = lambdaExpr.findAncestor(CompilationUnit.class);

			String methodRefClassName;
			if (compilationUnit.isPresent() && isImported(compilationUnit.get(), packageName, qualifiedName)) {
				methodRefClassName = typeDeclaration.getName();
			} else {
				methodRefClassName = qualifiedName;
			}

			// `r -> r.run()` into `Runnable::run`
			var methodReference = new MethodReferenceExpr(new NameExpr(methodRefClassName),
					methodCallExpr.getTypeArguments().orElseGet(() -> new NodeList<>()),
					methodCallExpr.getNameAsString());
			return lambdaExpr.replace(methodReference);
		} else {
			return false;
		}
	}

	@Deprecated
	private boolean isImported(CompilationUnit compilationUnit, String methodRefPackage, String qualifiedName) {
		Optional<PackageDeclaration> optPackageDeclaration = compilationUnit.getPackageDeclaration();
		if (optPackageDeclaration.isPresent()) {
			var packageDecl = optPackageDeclaration.get().getNameAsString();

			// see
			// eu.solven.cleanthat.engine.java.refactorer.mutators.UnnecessaryImport.removeSamePackageImports(Collection<ImportDeclaration>,
			// Optional<PackageDeclaration>)
			if (methodRefPackage.equals(packageDecl)) {
				return true;
			}
		}

		NodeList<ImportDeclaration> imports = compilationUnit.getImports();

		if (imports.isEmpty() && methodRefPackage.indexOf('.') >= 0) {
			return false;
		}

		if ("java.lang".equals(methodRefPackage)) {
			return true;
		}
		// TODO manage wildcards/asterisks
		return imports.stream().anyMatch(id -> id.getNameAsString().equals(qualifiedName));
	}
}
