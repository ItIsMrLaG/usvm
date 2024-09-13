package org.usvm.machine.state

import kotlinx.atomicfu.*
import kotlinx.html.*
import kotlinx.html.stream.createHTML

import java.io.File


typealias LogParentEntityId = Int
typealias LogEntityId = Int

const val DELIMETR = "|"
const val NEW_LINE_DELIMETR = "{n}"
const val INIT_STATE_LOG_ENTITY_ID: LogEntityId = 0
const val INIT_INTERNAL_LOG_ENTITY_ID: LogEntityId = -1

enum class Color {
    Red, Green, Yellow, White, Blue;

    override fun toString(): String {
        return when (this) {
            Red -> "#DA6C75"
            Green -> "#98C379"
            Yellow -> "#CB9A66"
            White -> "#A7B2BF"
            Blue -> "#57A8F2"
        }
    }
}

enum class InvokeType {
    Concrete, Symbolic, SymbButCanBeConc;

    override fun toString(): String = when (this) {
        Concrete -> "C"
        Symbolic -> "S"
        SymbButCanBeConc -> "SbC"
    }

    fun getColor(): Color = when (this) {
        Concrete -> Color.Red
        Symbolic -> Color.Green
        SymbButCanBeConc -> Color.Yellow
    }
}

enum class InternalMark {
    Mocked, StateFork, Report;

    override fun toString(): String = when (this) {
        Mocked -> "MOCKED"
        StateFork -> "FORK"
        Report -> "REPORT"
    }
}

enum class EntityType {
    MInv, MRet, ExStart, ExEnd, Inf;

    override fun toString(): String = when (this) {
        MInv -> "MInv"
        MRet -> "MRet"
        ExStart -> "ExStart"
        ExEnd -> "ExEnd"
        Inf -> "Inf"
    }
}

enum class LogClass {
    Intern, User;

    override fun toString() = when (this) {
        Intern -> "I"
        User -> "U"
    }
}

class FLogger(
    private val fl: File = File(
        "/home/gora/AdiskD/PROG_SPBGU_HW/PROG_SPBU_3/usvm/SpringLogNew.log"
    )
) {

    enum class ParentMode {
        State, Common;
    }

    private val id = atomic(INIT_STATE_LOG_ENTITY_ID)
    private val internal_id = atomic(INIT_INTERNAL_LOG_ENTITY_ID)

    private fun getId(mode: ParentMode) = when (mode) {
        ParentMode.State -> id.incrementAndGet()
        ParentMode.Common -> internal_id.decrementAndGet()
    }

    //region Entities (formatters)

    private open class LogEntity(
        val name: String,
        val color: Color = Color.Red,
        val link: Pair<LogParentEntityId, LogEntityId>,
        val mark: EntityType
    ) {
        override fun toString(): String = "$mark" + DELIMETR + "$link" + DELIMETR + "$color" + DELIMETR + name
    }

    private class MethodInvoke(
        name: String,
        link: Pair<LogParentEntityId, LogEntityId>,
        val type: InvokeType,
    ) : LogEntity(name, type.getColor(), link, EntityType.MInv) {
        override fun toString(): String {
            return super.toString() + DELIMETR + "$type"
        }
    }

    private class MethodReturn(
        methodName: String, color: Color = Color.Red, link: Pair<LogParentEntityId, LogEntityId>
    ) : LogEntity(methodName, color, link, EntityType.MInv)

    private class ExceptionThrow(
        name: String, color: Color = Color.Blue, link: Pair<LogParentEntityId, LogEntityId>
    ) : LogEntity(name, color, link, EntityType.MInv)

    private class ExceptionProcessed(
        name: String, color: Color = Color.Blue, link: Pair<LogParentEntityId, LogEntityId>
    ) : LogEntity(name, color, link, EntityType.ExEnd)

    private class Info(
        msg: String, color: Color = Color.White, link: Pair<LogParentEntityId, LogEntityId>
    ) : LogEntity(msg, color, link, EntityType.Inf)

    private data class LogMarker(
        val msg: String,
        val mark: InternalMark,
        val link: Pair<LogParentEntityId, LogEntityId>,
    ) {
        override fun toString(): String = "$link" + DELIMETR + "$mark" + DELIMETR + msg
    }

    //endregion

    //region Print

    private fun printLog(log: Any) {
        if (log is LogEntity)
            fl.appendText(LogClass.User.toString() + DELIMETR + log.toString().replace("\n", NEW_LINE_DELIMETR) + "\n")
        else
            fl.appendText(
                LogClass.Intern.toString() + DELIMETR + log.toString().replace("\n", NEW_LINE_DELIMETR) + "\n"
            )
    }

    private fun common_entity_log(
        type: EntityType,
        parentId: LogParentEntityId,
        mode: ParentMode,
        name: String = "<GENERIC>",
        invType: InvokeType? = null
    ): Int {
        val newId = getId(mode);
        when (type) {
            EntityType.MInv -> printLog(
                MethodInvoke(
                    name,
                    Pair(parentId, newId),
                    invType ?: throw IllegalStateException("invType shouldn't be null with $type")
                )
            )

            EntityType.MRet -> printLog(MethodReturn(name, link = Pair(parentId, newId)))
            EntityType.ExStart -> printLog(ExceptionThrow(name, link = Pair(parentId, newId)))
            EntityType.ExEnd -> printLog(ExceptionProcessed(name, link = Pair(parentId, newId)))
            EntityType.Inf -> printLog(Info(name, link = Pair(parentId, newId)))
        }
        return newId
    }

    fun log(
        type: InternalMark,
        parentId: LogParentEntityId,
        msg: String = "<GENERIC>",
        mode: ParentMode = ParentMode.Common
    ): Int {
        val newId = getId(mode);
        printLog(LogMarker(msg, type, link = Pair(parentId, newId)))
        return newId
    }


    private fun common_intenal_log(
        type: InternalMark,
        parentId: LogParentEntityId,
        msg: String = "<GENERIC>",
        mode: ParentMode = ParentMode.Common
    ): Int {
        val newId = getId(mode);
        printLog(LogMarker(msg, type, link = Pair(parentId, newId)))
        return newId
    }

    fun log(
        type: EntityType,
        name: String = "<GENERIC>",
        invType: InvokeType? = null,
    ) = common_entity_log(type, internal_id.value, ParentMode.Common, name, invType)

    fun log(
        type: EntityType,
        parentId: LogParentEntityId,
        name: String = "<GENERIC>",
        invType: InvokeType? = null,
    ) = common_entity_log(type, parentId, ParentMode.State, name, invType)

    fun log(
        type: InternalMark,
        msg: String = "<GENERIC>",
    ) = common_intenal_log(type, internal_id.value, msg, ParentMode.Common)

    fun log(
        type: InternalMark,
        parentId: LogParentEntityId,
        msg: String = "<GENERIC>",
    ) = common_intenal_log(type, parentId, msg, ParentMode.State)
    //endregion
}


