package org.usvm.machine.state

import java.io.File

import kotlinx.html.*
import kotlinx.html.stream.createHTML
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

typealias LNoteId = Long
typealias ParentLNoteId = Long


const val SEPARATOR = "|"

enum class Color {
    Red, Green, Yellow, White;

    override fun toString(): String {
        return when (this) {
            Red -> "#DA6C75"
            Green -> "#98C379"
            Yellow -> "#CB9A66"
            White -> "#A7B2BF"
        }
    }
}

enum class LogType {
    Return, Invoke, Info;

    override fun toString(): String {
        return when (this) {
            Return -> "Ret"
            Invoke -> "Inv"
            Info -> "Inf"
        }
    }
}

data class LNoteName(
    val parentId: ParentLNoteId,
    val id: LNoteId,
) {

    override fun toString(): String {
        return "$parentId:$id"
    }
}

class CLog(
    val color: Color,
    val methodName: String,
    val logType: LogType,
) {

    override fun toString(): String {
        return "$methodName${SEPARATOR}$color${SEPARATOR}$logType"
    }
}

class LNote(
    val name: LNoteName,
    val info: CLog,
) {

    override fun toString(): String {
        return "$name${SEPARATOR}$info"
    }
}

class LNStorage(
    private val logCtx: HTMLFrameLogger,
    val notes: MutableList<LNote>,
) {

    fun clone() = LNStorage(logCtx, notes.toMutableList())

    private fun addNote(log: CLog) {
        val newNote = if (notes.isEmpty()) {
            if (logCtx.lookAtNewId() != 0L)
                println("Something went wrong, HTMLFrameLogger doesn't work correctly!")

            LNote(LNoteName(logCtx.getNewId(), logCtx.getNewId()), log)
        } else {
            val parentId = this.notes.last().name.id
            LNote(LNoteName(parentId, logCtx.getNewId()), log)
        }

        logCtx.saveInLogFile(newNote)
        notes.add(newNote)
    }

    fun addReturnNote() {
        addNote(CLog(Color.White, "", LogType.Return))
    }

    fun addCallingNote(methodName: String) {
        addNote(CLog(Color.Red, methodName, LogType.Invoke))
    }

    fun addConcreteInvNote(methodName: String) {
        addNote(CLog(Color.Green, methodName, LogType.Info))
    }

    fun addCanBeConcreteInv(methodName: String) {
        addNote(CLog(Color.Yellow, methodName, LogType.Info))
    }

    fun addApproximatedInvNote(methodName: String) {
        addNote(CLog(Color.Red, methodName, LogType.Info))
    }
}


class HTMLFrameLogger(flPath: String = "/home/gora/PROG_SPBU/PROG_SPBU_3/usvm/springTestMy.log") {

    private val logFile: File = File(flPath)
    private var newId: LNoteId = 0L
    private val archive = mutableListOf<LNStorage>()

    init {
        this.logFile.writeText("")
    }

    fun lookAtNewId(): LNoteId = newId
    fun getNewId(): LNoteId {
        val id = newId
        newId++
        return id
    }

    fun saveInLogFile(note: LNote) = logFile.appendText("${note.toString()}\n")

    private inline fun saveInArchive(f: () -> LNStorage): LNStorage {
        val res = f()
        archive.add(res)
        return res
    }

    fun getNewLNoteStorage(): LNStorage = saveInArchive { LNStorage(this, mutableListOf()) }
    fun cloneLNStorage(storage: LNStorage): LNStorage = saveInArchive { return storage.clone() }

    fun generateConclusion(savePath: String) {
        var spaceCounter = 0
        val space = "...|"

        fun LI.generateNote(note: LNote) {
            var ret = ""
            var flag = false
            when (note.info.logType) {
                LogType.Return -> if (spaceCounter == 0) ret = " [END]" else {
                    flag = true
                    ret = "[RETURN]"
                }

                LogType.Invoke -> spaceCounter++
                LogType.Info -> {}
            }
            val deep = space.repeat(spaceCounter)
            p {
                span("p_format") {
                    style = "color: ${note.info.color};"
                    +"$deep ${note.info.methodName}$ret"
                }
            }
            if (flag)
                spaceCounter--
        }

        fun LI.generateState(storage: LNStorage) {
            spaceCounter = 0

            ul {
                for (note in storage.notes) li { generateNote(note) }
            }

        }

        val htmlContent = createHTML().html {
            head {
                meta(charset = "UTF-8")

                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                val current = LocalDateTime.now().format(formatter)
                title("${savePath.split("/").last()}_$current")

                style {
                    unsafe {
                        +"""
                        body {
                            background-color: #202020;
                        }
                        .info-box {
                            background-color: #5F8787; /* Белый фон для прямоугольника */
                            border: 1px solid #cccccc; /* Серый цвет границы */
                            padding: 15px; /* Отступы внутри прямоугольника */
                            margin-bottom: 20px; /* Отступ снизу */
                        }
                        .red { color: ${Color.Red}; }
                        .yellow { color: ${Color.Yellow}; }
                        .green { color: ${Color.Green}; }
                        .blue { color:#61AFEF }
                        .dark_blue { color:#0E71A5 }
                        
                        .p_format { white-space: nowrap; }
                        
                        ul {
                            list-style-type: none; /* Убираем маркеры списка */
                            padding: 0; /* Убираем отступы у списка */
                        }
                        li {
                            margin-left: 20px; /* Устанавливаем начальный отступ для каждого элемента */
                        }
                        """
                    }
                }
            }
            body {
                div("info-box") {
                    h1 { span("dark_blue") { +"Colors meaning" } }
                    p { span("red") { +"Symbolic call or approximated" } }
                    p { span("yellow") { +"Potential concrete call" } }
                    p { span("green") { +"Concrete call" } }
                }
                ul {
                    for (idx in 0..<archive.size) li {
                        h2 { span("blue") { +"State $idx" } }
                        generateState(archive[idx])
                    }
                }
            }
        }
        File(savePath).writeText(htmlContent)
    }

    fun generateConclusionFromFile() {
        TODO()
    }
}
