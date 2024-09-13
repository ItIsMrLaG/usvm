package org.usvm.machine.state

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.PathNode
import org.usvm.UCallStack
import org.usvm.UState
import org.usvm.api.targets.JcTarget
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.JcContext
import org.usvm.machine.state.concreteMemory.JcConcreteMemory
import org.usvm.memory.UMemory
import org.usvm.merging.MutableMergeGuard
import org.usvm.model.UModelBase
import org.usvm.targets.UTargetsSet

class JcState(
    ctx: JcContext,
    override val entrypoint: JcMethod,
    callStack: UCallStack<JcMethod, JcInst> = UCallStack(),
    pathConstraints: UPathConstraints<JcType> = UPathConstraints(ctx),
    memory: UMemory<JcType, JcMethod> = JcConcreteMemory(ctx, pathConstraints.typeConstraints),
    models: List<UModelBase<JcType>> = listOf(),
    pathNode: PathNode<JcInst> = PathNode.root(),
    forkPoints: PathNode<PathNode<JcInst>> = PathNode.root(),
    var methodResult: JcMethodResult = JcMethodResult.NoCall,
    targets: UTargetsSet<JcTarget, JcInst> = UTargetsSet.empty(),
    logEntityParentPrefix: String? = null,
) : UState<JcType, JcMethod, JcInst, JcContext, JcTarget, JcState>(
    ctx,
    callStack,
    pathConstraints,
    memory,
    models,
    pathNode,
    forkPoints,
    targets
) {
    val logStateUniqName: String = ctx.fLogger.addNewState(logEntityParentPrefix)

    override fun clone(newConstraints: UPathConstraints<JcType>?): JcState {
        val clonedConstraints = newConstraints ?: pathConstraints.clone()
        return JcState(
            ctx,
            entrypoint,
            callStack.clone(),
            clonedConstraints,
            memory.clone(clonedConstraints.typeConstraints),
            models,
            pathNode,
            forkPoints,
            methodResult,
            targets.clone(),
            logStateUniqName
        )
    }

    /**
     * Check if this [JcState] can be merged with [other] state.
     *
     * @return the merged state. TODO: Now it may reuse some of the internal components of the former states.
     */
    override fun mergeWith(other: JcState, by: Unit): JcState? {
        require(entrypoint == other.entrypoint) { "Cannot merge states with different entrypoints" }
        // TODO: copy-paste

        val mergedPathNode = pathNode.mergeWith(other.pathNode, Unit) ?: return null
        val mergedForkPoints = forkPoints.mergeWith(other.forkPoints, Unit) ?: return null

        val mergeGuard = MutableMergeGuard(ctx)
        val mergedCallStack = callStack.mergeWith(other.callStack, Unit) ?: return null
        val mergedPathConstraints = pathConstraints.mergeWith(other.pathConstraints, mergeGuard)
            ?: return null
        val mergedMemory = memory.clone(mergedPathConstraints.typeConstraints).mergeWith(other.memory, mergeGuard)
            ?: return null
        val mergedModels = models + other.models
        val methodResult = if (other.methodResult == JcMethodResult.NoCall && methodResult == JcMethodResult.NoCall) {
            JcMethodResult.NoCall
        } else {
            return null
        }
        val mergedTargets = targets.takeIf { it == other.targets } ?: return null
        mergedPathConstraints += ctx.mkOr(mergeGuard.thisConstraint, mergeGuard.otherConstraint)

        return JcState(
            ctx,
            entrypoint,
            mergedCallStack,
            mergedPathConstraints,
            mergedMemory,
            mergedModels,
            mergedPathNode,
            mergedForkPoints,
            methodResult,
            mergedTargets
        )
    }

    override val isExceptional: Boolean
        get() = methodResult is JcMethodResult.JcException

    override fun toString(): String = buildString {
        appendLine("Instruction: $lastStmt")
        if (isExceptional) appendLine("Exception: $methodResult")
        appendLine(callStack)
    }
}
