/*******************************************************************************
 * Copyright (c) 2009, 2024 Mountainminds GmbH & Co. KG and Contributors
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Evgeny Mandrikov - initial API and implementation
 *
 *******************************************************************************/
package org.jacoco.core.internal.analysis.filter;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Filters bytecode generated by Compose Kotlin compiler plugin.
 */
final class KotlinComposeFilter implements IFilter {

	public void filter(final MethodNode methodNode,
			final IFilterContext context, final IFilterOutput output) {
		if (!KotlinGeneratedFilter.isKotlinClass(context)) {
			return;
		}
		if (!isComposable(methodNode)) {
			return;
		}
		for (final AbstractInsnNode i : methodNode.instructions) {
			if (i.getType() != AbstractInsnNode.METHOD_INSN) {
				continue;
			}
			final MethodInsnNode mi = (MethodInsnNode) i;
			if ("androidx/compose/runtime/Composer".equals(mi.owner)
					&& "getSkipping".equals(mi.name) && "()Z".equals(mi.desc)
					&& mi.getNext().getOpcode() == Opcodes.IFNE) {
				// https://github.com/JetBrains/kotlin/blob/v2.0.0-RC2/plugins/compose/compiler-hosted/src/main/java/androidx/compose/compiler/plugins/kotlin/lower/ComposableFunctionBodyTransformer.kt#L361-L384
				final JumpInsnNode ji = (JumpInsnNode) mi.getNext();
				output.ignore(methodNode.instructions.getFirst(), ji);
				output.ignore(ji.label, methodNode.instructions.getLast());
			} else if ("androidx/compose/runtime/Composer".equals(mi.owner)
					&& "endRestartGroup".equals(mi.name)
					&& "()Landroidx/compose/runtime/ScopeUpdateScope;"
							.equals(mi.desc)
					&& mi.getNext().getOpcode() == Opcodes.DUP
					&& mi.getNext().getNext().getOpcode() == Opcodes.IFNULL) {
				// https://github.com/JetBrains/kotlin/blob/v2.0.0-RC2/plugins/compose/compiler-hosted/src/main/java/androidx/compose/compiler/plugins/kotlin/lower/ComposableFunctionBodyTransformer.kt#L430-L450
				final JumpInsnNode ji = (JumpInsnNode) mi.getNext().getNext();
				final AbstractInsnNode jumpTarget = AbstractMatcher
						.skipNonOpcodes(ji.label);
				if (jumpTarget.getOpcode() == Opcodes.POP) {
					output.ignore(ji, jumpTarget);
				}
			} else if ("androidx/compose/runtime/ComposerKt".equals(mi.owner)
					&& "isTraceInProgress".equals(mi.name)
					&& "()Z".equals(mi.desc)
					&& mi.getNext().getOpcode() == Opcodes.IFEQ) {
				// https://github.com/JetBrains/kotlin/blob/v2.0.0-RC2/plugins/compose/compiler-hosted/src/main/java/androidx/compose/compiler/plugins/kotlin/lower/ComposableFunctionBodyTransformer.kt#L2123-L2163
				final JumpInsnNode ji = (JumpInsnNode) mi.getNext();
				output.ignore(ji, ji.label);
			}
		}
	}

	private static boolean isComposable(final MethodNode methodNode) {
		if (methodNode.invisibleAnnotations == null) {
			return false;
		}
		for (final AnnotationNode a : methodNode.invisibleAnnotations) {
			if ("Landroidx/compose/runtime/Composable;".equals(a.desc)) {
				return true;
			}
		}
		return false;
	}

}
