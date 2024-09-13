package org.usvm.machine.state

import kotlinx.atomicfu.atomic
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

const val DELIMETR = "|"

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

open class LogEntity(
    val name: String,
    val color: Color = Color.Red,
    val mark: EntityType
) {
    override fun toString(): String = "$mark" + DELIMETR + "$color" + DELIMETR + name
}

class MethodReturn(methodName: String, color: Color = Color.Red) : LogEntity(methodName, color, EntityType.MInv)
class ExceptionThrow(name: String, color: Color = Color.Blue) : LogEntity(name, color, EntityType.MInv)
class ExceptionProcessed(name: String, color: Color = Color.Blue) : LogEntity(name, color, EntityType.ExEnd)
class Info(msg: String, color: Color = Color.White) : LogEntity(msg, color, EntityType.Inf)
class MethodInvoke(name: String, val type: InvokeType) : LogEntity(name, type.getColor(), EntityType.MInv) {
    override fun toString(): String {
        return super.toString() + DELIMETR + "$type"
    }
}

class TODOFrameLogger(val pathToLogDir: String) {

    val CONACAT = "_"

    init {
        initLogDir()
    }

    private fun initLogDir() {
        val directory = File(pathToLogDir)
        if (directory.exists() && directory.isDirectory) {
            val files = directory.listFiles()

            files?.forEach { file ->
                try {
                    if (file.isFile)
                        file.delete()
                } catch (e: IOException) {
                    println("Can't delete file: ${file.name}: ${e.message}")
                }
            }
        }
    }

    enum class LogType {
        COMMON, LOCAL;

        override fun toString(): String = when (this) {
            COMMON -> "COMMON"
            LOCAL -> "LOCAL"
        }
    }

    val stateFiles = mutableMapOf<String, File>()

    var id = atomic(0)

    fun getNewId(): Int {
        return id.incrementAndGet();
    }

    private fun getNewLogFileName(prefix: String?) =
        if (prefix == null) "${getNewId()}"
        else prefix + CONACAT + "${getNewId()}"

    fun addNewState(prefix: String? = null): String {
        val newName = getNewLogFileName(prefix)
        val newFile = File(pathToLogDir, "$newName.log")

        if (prefix == null) {
            File(pathToLogDir, "$newName.log").createNewFile()
        } else {
            assert(stateFiles[prefix] != null)
            Files.copy(
                stateFiles[prefix]!!.toPath(),
                newFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }

        stateFiles[newName] = newFile
        return newName
    }

    fun addNewLog(stateName: String?, log: LogEntity) {
        if (stateName == null) {
            for (fl in stateFiles.values)
                fl.appendText("${LogType.COMMON}" + DELIMETR + "$log+\n")
        } else {
            val fl = stateFiles[stateName] ?: throw IllegalStateException("Existence state error")
            fl.appendText("${LogType.LOCAL}" + DELIMETR + "$log+\n")
        }
    }

    private fun prettyAddNewLog(
        type: EntityType,
        stateName: String?,
        name: String = "<GENERIC>",
        invType: InvokeType? = null,
        color: Color = Color.White
    ) {
        when (type) {
            EntityType.MInv -> addNewLog(
                stateName,
                MethodInvoke(name, invType ?: throw IllegalStateException("invType shouldn't be null with $type"))
            )

            EntityType.MRet -> addNewLog(stateName, MethodReturn(name))
            EntityType.ExStart -> addNewLog(stateName, ExceptionThrow(name))
            EntityType.ExEnd -> addNewLog(stateName, ExceptionProcessed(name))
            EntityType.Inf -> addNewLog(stateName, Info(name, color = color))
        }
    }

    fun logMethodInvoke(
        stateName: String?,
        methodName: String,
        invType: InvokeType,
    ) = prettyAddNewLog(EntityType.MInv, stateName, methodName, invType)

    fun logMethodReturn(
        stateName: String?,
        methodName: String = "<GENERIC>",
    ) = prettyAddNewLog(EntityType.MRet, stateName, methodName)

    fun logExnThrow(
        stateName: String?,
        exnName: String,
    ) = prettyAddNewLog(EntityType.ExStart, stateName, exnName)

    fun logExnProcessed(
        stateName: String?,
        exnName: String = "<GENERIC>",
    ) = prettyAddNewLog(EntityType.ExEnd, stateName, exnName)

    fun logInfo(
        stateName: String?,
        msg: String,
        color: Color = Color.White
    ) = prettyAddNewLog(EntityType.ExEnd, stateName, msg, color = color)
}


