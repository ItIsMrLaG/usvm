package org.usvm.machine.state

import kotlinx.atomicfu.atomic
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import org.usvm.machine.state.Color.*
import org.usvm.machine.state.EntityType.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

const val DELIMETR = "|"
const val EXTENSION = "slog"

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

enum class LogType {
    COMMON, LOCAL;

    override fun toString(): String = when (this) {
        COMMON -> "COMMON"
        LOCAL -> "LOCAL"
    }
}

open class LogEntity(
    val name: String,
    var color: Color = Color.Red,
    val mark: EntityType,
    var logType: LogType = LogType.LOCAL,
) {
    override fun toString(): String = "$logType" + DELIMETR + "$mark" + DELIMETR + "$color" + DELIMETR + name
}

class MethodReturn(methodName: String, color: Color = Red) : LogEntity(methodName, color, MInv)
class ExceptionThrow(name: String, color: Color = Blue) : LogEntity(name, color, ExStart)
class ExceptionProcessed(name: String, color: Color = Blue) : LogEntity(name, color, ExEnd)
class Info(msg: String, color: Color = White) : LogEntity(msg, color, Inf)
class MethodInvoke(name: String, val type: InvokeType) : LogEntity(name, type.getColor(), MInv) {
    override fun toString(): String {
        return super.toString() + DELIMETR + "$type"
    }
}

class FrameLogger(val pathToLogDir: String) {

    val CONCAT = "_"

    fun initLogDir() {
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

    val stateFiles = mutableMapOf<String, File>()

    var id = atomic(0)

    fun getNewId(): Int {
        return id.incrementAndGet();
    }

    private fun getNewLogFileName(prefix: String?) =
        if (prefix == null) "${getNewId()}"
        else prefix + CONCAT + "${getNewId()}"

    fun addNewState(prefix: String? = null): String {
        val newName = getNewLogFileName(prefix)
        val newFile = File(pathToLogDir, "$newName.$EXTENSION")

        if (prefix == null) {
            File(pathToLogDir, "$newName.$EXTENSION").createNewFile()
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
            for (fl in stateFiles.values) {
                fl.appendText("$log\n")
            }
        } else {
            val fl = stateFiles[stateName] ?: throw IllegalStateException("Existence state error")
            fl.appendText("$log\n")
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

    private fun getFilesList(): List<File> {
        val folder = File(pathToLogDir)

        if (folder.exists() && folder.isDirectory)
            return folder.listFiles()?.filter { it.isFile && it.name.split('.').last() == EXTENSION }?.toList()
                ?: emptyList()
        else
            throw Exception("Incorrect path to dir: $pathToLogDir")
    }

    private fun getLogEntity(logLine: String): LogEntity {
        val name: String
        val color: Color
        val logType: LogType
        val ret: LogEntity
        val structure = logLine.split(DELIMETR)

        logType = when (structure[0]) {
            LogType.LOCAL.toString() -> LogType.LOCAL
            LogType.COMMON.toString() -> LogType.COMMON
            else -> throw IllegalStateException("Impossible LogType: ${structure[0]}")
        }

//        structure[1] -- mark

        color = when (structure[2]) {
            Red.toString() -> Red
            Green.toString() -> Green
            Yellow.toString() -> Yellow
            White.toString() -> White
            Blue.toString() -> Blue
            else -> throw IllegalStateException("Unsupported COLOR: ${structure[2]}")
        }

        name = structure[3]

        ret = when (structure[1]) {
            MInv.toString() -> {
                val type = when (structure[4]) {
                    InvokeType.Concrete.toString() -> InvokeType.Concrete
                    InvokeType.Symbolic.toString() -> InvokeType.Symbolic
                    InvokeType.SymbButCanBeConc.toString() -> InvokeType.SymbButCanBeConc
                    else -> throw IllegalStateException("Unsupported InvokeType: ${structure[2]}")
                }
                MethodInvoke(name, type)
            }

            MRet.toString() -> MethodReturn(name)
            ExStart.toString() -> ExceptionThrow(name)
            ExEnd.toString() -> ExceptionProcessed(name)
            Inf.toString() -> Info(name)
            else -> throw IllegalStateException("Impossible EntityType: ${structure[1]}")
        }
        ret.color = color
        ret.logType = logType
        return ret
    }

    private fun generateStateHtmlRepresentation(logFile: File): String {
        logFile.readLines().forEach { line ->
            getLogEntity(line).toString()
            TODO()
        }
        return "TODO"
    }

    fun generateHtmlConclusion(pathToHtmlResDir: String) {
        println(pathToHtmlResDir)
        val logFiles = getFilesList()
        logFiles.forEach { fl ->
            generateStateHtmlRepresentation(fl)
        }
    }
}

class HtmlRender {
    fun renderLogEntity(log: LogEntity): String = TODO()

    fun renderStateHtmlFile(name: String, logs: List<LogEntity>): String = createHTML().body {
        div("state-container") {
            div("state-container-info") {
                p { +"State: $name" }
            }
            details {
                summary { +"steps:" }
                div("state-steps") {
                    ol {
                        for (log in logs)
                            li { +renderLogEntity(log) }
                    }
                }
            }
        }
    }

    fun renderCommonHtmlFile(fileContents: List<String>): String {
        val mainHtmlContent = createHTML().html {
            head {
                title("Spring-Logs")
                meta(charset = "UTF-8")
                meta {
                    name = "viewport"
                    content = "width=device-width, initial-scale=1.0"
                }
                style {
                    unsafe {
                        +"""
                    p {
                        margin: 0 0 10px 0;
                        font-weight: bold; 
                    }
                    body, html {
                        margin: 0;
                        padding: 0;
                        height: 100%;
                        overflow: hidden;
                        font-family: Arial, sans-serif;
                        background-color: #f0f8ff; /* Светло-голубой фон для всей страницы */
                    }
                    .container {
                        display: flex;
                        flex-direction: column;
                        height: 100%;
                        box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1); /* Легкая тень вокруг контейнера */
                        border-radius: 8px; /* Закругленные углы */
                        overflow: hidden; /* Скрываем содержимое, выходящее за границы контейнера */
                    }
                    .top {
                        display: flex;
                        height: 85%;
                    }
                    .top-left {
                        flex: 1;
                        min-width: 100px;
                        background-color: #e0f7fa; /* Светло-голубой фон для левой панели */
                        overflow: auto;
                        white-space: nowrap;
                        padding: 10px;
                        border-right: 2px solid #b2ebf2; /* Разделительная линия */
                    }
                    .top-right {
                        flex: 1;
                        min-width: 100px;
                        background-color: #f5f5f5; /* Светло-серый фон для правой панели */
                        overflow: auto;
                        white-space: nowrap;
                        padding: 10px;
                    }
                    .resizer {
                        cursor: col-resize;
                        background-color: #b2ebf2; /* Цвет перегородки */
                        width: 5px;
                        height: 100%;
                        position: relative;
                        z-index: 1;
                    }
                    .bottom {
                        height: 15%;
                        background-color: #e0f2f1; /* Светло-зеленый фон для нижней панели */
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        border-top: 2px solid #b2dfdb; /* Разделительная линия сверху */
                    }
                    button {
                        background-color: #80deea; /* Цвет кнопки */
                        border: none;
                        padding: 10px 20px;
                        color: white;
                        font-size: 16px;
                        border-radius: 4px;
                        cursor: pointer;
                        transition: background-color 0.3s ease;
                    }
                    button:hover {
                        background-color: #4dd0e1; /* Цвет кнопки при наведении */
                    }
                    .state-container {
                        background-color: #f0f0f0;
                        padding: 10px;
                        border-radius: 5px;
                        margin-bottom: 10px;
                    }
                    .state-container-info {
                        background-color: #d0eaff; 
                        padding: 10px; 
                        border-radius: 5px; 
                        margin-bottom: 10px;
                    }
                    .state-steps {
                        max-height: 700px;
                        overflow: auto;
                    }
                    """.trimIndent()
                    }
                }
            }
            body {
                div("container") {
                    div("top") {
                        div("top-left") {
                            id = "left-panel"
                            p { +"TODO: граф состояний" }
                        }
                        div("resizer") {
                            id = "resizer"
                        }
                        div("top-right") {
                            id = "right-panel"
                            fileContents.forEach { content ->
                                unsafe {
                                    +content
                                }
                            }
                        }
                    }
                    div("bottom") {
                        button {
                            id = "toggle-button"
                            +"Toggle Left Panel"
                        }
                    }
                }
                script {
                    unsafe {
                        +"""
                    const resizer = document.getElementById('resizer');
                    const leftPanel = document.getElementById('left-panel');
                    const rightPanel = document.getElementById('right-panel');
                    let isDragging = false;

                    resizer.addEventListener('mousedown', function(e) {
                        isDragging = true;
                        document.body.style.cursor = 'col-resize';
                    });

                    document.addEventListener('mousemove', function(e) {
                        if (!isDragging) return;

                        const containerWidth = leftPanel.parentElement.getBoundingClientRect().width;
                        const leftPanelWidth = e.clientX;

                        if (leftPanelWidth >= 0 && leftPanelWidth <= containerWidth) {
                            const leftFlex = leftPanelWidth / containerWidth;
                            const rightFlex = 1 - leftFlex;
                            leftPanel.style.flex = leftFlex;
                            rightPanel.style.flex = rightFlex;
                        }
                    });

                    document.addEventListener('mouseup', function() {
                        isDragging = false;
                        document.body.style.cursor = 'default';
                    });

                    document.getElementById('toggle-button').addEventListener('click', function() {
                        if (leftPanel.style.display === 'none') {
                            leftPanel.style.display = 'block';
                            resizer.style.display = 'block';  // Показать перегородку
                            rightPanel.style.flex = '1';
                        } else {
                            leftPanel.style.display = 'none';
                            resizer.style.display = 'none';  // Скрыть перегородку
                            rightPanel.style.flex = '1';
                            rightPanel.style.width = '100%';
                        }
                    });
                    """.trimIndent()
                    }
                }
            }
        }
        return mainHtmlContent
    }
}


fun main() {
    val lg = FrameLogger("/home/gora/AdiskD/PROG_SPBGU_HW/PROG_SPBU_3/usvm/HtmlLogs")
    lg.generateHtmlConclusion("/home/gora/AdiskD/PROG_SPBGU_HW/PROG_SPBU_3/usvm/HtmlConclusion")
}
