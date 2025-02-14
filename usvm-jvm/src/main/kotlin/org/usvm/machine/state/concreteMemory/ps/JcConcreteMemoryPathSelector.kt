package org.usvm.machine.state.concreteMemory.ps

import org.jacodb.api.jvm.JcClassType
import org.usvm.UConcreteHeapRef
import org.usvm.UPathSelector
import org.usvm.api.JcSpringTest
import org.usvm.api.util.JcTestStateResolver
import org.usvm.logger
import org.usvm.machine.state.JcState
import org.usvm.machine.state.concreteMemory.JcConcreteMemory

class JcConcreteMemoryPathSelector(
    private val selector: UPathSelector<JcState>
) : UPathSelector<JcState> {

    private var fixedState: JcState? = null

    override fun isEmpty(): Boolean {
        return selector.isEmpty()
    }

    override fun peek(): JcState {
        if (fixedState != null)
            return fixedState as JcState
        val state = selector.peek()
        fixedState = state
        val memory = state.memory as JcConcreteMemory
        println("picked state: ${state.id}")
        memory.reset()
        return state
    }

    override fun update(state: JcState) {
        selector.update(state)
    }

    override fun add(states: Collection<JcState>) {
        selector.add(states)
    }

    private fun getConcreteValue(state: JcState, expr: UConcreteHeapRef) : Any? {
        if (expr.address == 0) return "null"
        val type = state.ctx.stringType as JcClassType
        return (state.memory as JcConcreteMemory).concretize(state, expr, type, JcTestStateResolver.ResolveMode.MODEL)
    }

    private fun printSpringTestSummary(state: JcState) {
        if (state.callStack.isEmpty()) return
        val userDefinedValues = state.userDefinedValues
        val a = JcSpringTest.generateFromState(state).generateTestDSL(state.ctx.cp)
        userDefinedValues.forEach {
            logger.info("END:\uD83E\uDD7A" + it.key + ": " + getConcreteValue(state, state.models[0].eval(it.value) as UConcreteHeapRef).toString())
        }
    }


    override fun remove(state: JcState) {
        // TODO: care about Engine.assume -- it's fork, but else state of assume is useless #CM
        check(fixedState == state)
        fixedState = null
        printSpringTestSummary(state)

        val a = JcSpringTest.generateFromState(state)
        a.generateTestDSL(state.ctx.cp)

        selector.remove(state)
        (state.memory as JcConcreteMemory).kill()
        println("removed state: ${state.id}")
        // TODO: generate test? #CM
    }
}
