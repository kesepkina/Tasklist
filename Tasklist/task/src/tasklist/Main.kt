package tasklist

import kotlinx.datetime.*
import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.lang.IllegalArgumentException
import kotlin.math.min

const val MAX_TASK_LINE_LENGTH = 44
const val MAX_DIGIT = 9
const val SPACING_FOR_BIG_NUMBERS = 4
const val SPACING_FOR_SMALL_NUMBERS = 3

enum class Actions {
    END,
    ADD,
    PRINT,
    DELETE,
    EDIT
}

enum class TaskPriorities {
    C, H, N, L
}

enum class DueTag(meaning: String) {
    I("In time"), T("Today"), O("Overdue")
}

enum class TaskFields {
    PRIORITY, DATE, TIME, TASK
}

data class Task(var content: String, var date: List<Int>, var time: List<Int>, var priority: TaskPriorities) {
    fun getDueTag(): DueTag {
        val taskDate = LocalDate(date[0], date[1], date[2])
        val currentDate = Clock.System.now().toLocalDateTime(TimeZone.of("UTC+0")).date
        val numberOfDays = currentDate.daysUntil(taskDate)
        return when {
            numberOfDays == 0 -> DueTag.T
            numberOfDays > 0 -> DueTag.I
            else -> DueTag.O
        }
    }
}

fun main() {
    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    val type = Types.newParameterizedType(List::class.java, Task::class.java)
    val tasksAdapter = moshi.adapter<List<Task?>>(type)
    val jsonFile = File("tasklist.json")
    val tasks = mutableListOf<Task>()
    if (jsonFile.exists()) {
        val oldTasks = tasksAdapter.fromJson(jsonFile.readText())
        if (oldTasks != null) {
            for (task in oldTasks) {
                if (task != null)
                    tasks.add(task)
            }
        }
    }
    while (true) {
        println("Input an action (add, print, edit, delete, end):")
        when (readln().uppercase()) {
            Actions.END.toString() -> {
                print("Tasklist exiting!")
                break
            }

            Actions.ADD.toString() -> addTask(tasks)
            Actions.PRINT.toString() -> printTasks(tasks)
            Actions.DELETE.toString() -> deleteTask(tasks)
            Actions.EDIT.toString() -> editTask(tasks)
            else -> println("The input action is invalid")
        }
    }
    jsonFile.writeText(tasksAdapter.toJson(tasks))
}

fun editTask(tasks: MutableList<Task>) {
    if (printTasks(tasks)) {
        var taskNumber: Int?
        while (true) {
            println("Input the task number (1-${tasks.size}):")
            taskNumber = readln().toIntOrNull()
            if (taskNumber == null || taskNumber !in 1..tasks.size) {
                println("Invalid task number")
            } else {
                break
            }
        }
        var fieldToEdit: TaskFields
        while (true) {
            println("Input a field to edit (priority, date, time, task):")
            try {
                fieldToEdit = TaskFields.valueOf((readlnOrNull() ?: "").trim().uppercase())
                break
            } catch (e: IllegalArgumentException) {
                println("Invalid field")
            }
        }
        when (fieldToEdit) {
            TaskFields.TASK -> {
                val editedTask = getTaskContent()
                if (editedTask.isBlank()) {
                    println("The task is blank")
                } else {
                    tasks[taskNumber!! - 1].content = editedTask.trim()
                }
            }

            TaskFields.DATE -> tasks[taskNumber!! - 1].date = getTaskDate()
            TaskFields.TIME -> tasks[taskNumber!! - 1].time = getTaskTime()
            TaskFields.PRIORITY -> tasks[taskNumber!! - 1].priority = getTaskPriority()
        }
        println("The task is changed")
    }
}

fun deleteTask(tasks: MutableList<Task>) {
    if (printTasks(tasks)) {
        while (true) {
            println("Input the task number (1-${tasks.size}):")
            val taskNumber = readln().toIntOrNull()
            if (taskNumber == null || taskNumber !in 1..tasks.size) {
                println("Invalid task number")
            } else {
                tasks.removeAt(taskNumber - 1)
                println("The task is deleted")
                break
            }
        }
    }
}

fun addTask(tasks: MutableList<Task>) {
    val priority = getTaskPriority()
    val date = getTaskDate()
    val time = getTaskTime()
    val nextTask = getTaskContent()
    if (nextTask.isBlank()) {
        println("The task is blank")
    } else {
        tasks.add(Task(nextTask.trim(), date, time, priority))
    }
}

fun getTaskPriority(): TaskPriorities {
    var priority: TaskPriorities
    while (true) {
        try {
            println("Input the task priority (C, H, N, L):")
            priority = TaskPriorities.valueOf(readln().uppercase())
            break
        } catch (_: IllegalArgumentException) {
        }
    }
    return priority
}

fun getTaskDate(): List<Int> {
    var date: List<Int>
    while (true) {
        try {
            println("Input the date (yyyy-mm-dd):")
            date = readln().trim().split("-").map { it.toInt() }
            LocalDate(date[0], date[1], date[2])
            break
        } catch (e: Exception) {
            println("The input date is invalid")
        }
    }
    return date
}

fun getTaskTime(): List<Int> {
    var time: List<Int>
    while (true) {
        try {
            println("Input the time (hh:mm):")
            time = readln().trim().split(":").map { it.toInt() }
            val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
            LocalDateTime(today.year, today.monthNumber, today.dayOfMonth, time[0], time[1])
            break
        } catch (e: Exception) {
            println("The input time is invalid")
        }
    }
    return time
}

fun getTaskContent(): String {
    println("Input a new task (enter a blank line to end):")
    var nextTask = ""
    var nextLine = readln().trim()
    while (nextLine.isNotBlank()) {
        nextTask += "$nextLine\n"
        nextLine = readln().trim()
    }
    return nextTask
}

fun printTasks(tasks: MutableList<Task>): Boolean {
    return if (tasks.isEmpty()) {
        println("No tasks have been input")
        false
    } else {
        val numSpacing = if (tasks.lastIndex > MAX_DIGIT) SPACING_FOR_BIG_NUMBERS else SPACING_FOR_SMALL_NUMBERS
        val rowDelimiter = "+-" + "-".repeat(numSpacing) + "+------------+-------+---+---+" + "-".repeat(
            MAX_TASK_LINE_LENGTH
        ) + "+"
        println(rowDelimiter)
        println(
            "| N" + " ".repeat(numSpacing - 1) + "|    Date    | Time  | P | D |" +
                    "                   Task                     |"
        )
        println(rowDelimiter)
        for (i in tasks.indices) {
            val (content, date, time, priority) = tasks[i]
            var minContentLength = if ('\n' in content) min(content.length, content.indexOf('\n'))
            else content.length
            var endIndex = min(minContentLength, MAX_TASK_LINE_LENGTH)
            println(
                "| %-${numSpacing}d| %04d-%02d-%02d | %02d:%02d | ${mapPriorityToColor(priority)} | ${
                    mapDueTagToColor(
                        tasks[i].getDueTag()
                    )
                } |%-${MAX_TASK_LINE_LENGTH}s|".format(
                    i + 1,
                    date[0],
                    date[1],
                    date[2],
                    time[0],
                    time[1],
                    content.substring(0, endIndex)
                )
            )
            while (endIndex < content.length) {
                val startIndex = if (content[endIndex] == '\n') endIndex + 1 else endIndex
                minContentLength = if ('\n' in content.substring(startIndex)) min(
                    content.length,
                    content.indexOf('\n', startIndex)
                ) else content.length
                endIndex = min(minContentLength, startIndex + MAX_TASK_LINE_LENGTH)
                println(
                    "| " + " ".repeat(numSpacing) + "|            |       |   |   |${
                        content.substring(startIndex, endIndex).padEnd(
                            MAX_TASK_LINE_LENGTH, ' '
                        )
                    }|"
                )
            }
            println(rowDelimiter)
        }
        true
    }
}

fun mapPriorityToColor(priority: TaskPriorities): String = when (priority) {
    TaskPriorities.C -> "\u001B[101m \u001B[0m"
    TaskPriorities.H -> "\u001B[103m \u001B[0m"
    TaskPriorities.N -> "\u001B[102m \u001B[0m"
    TaskPriorities.L -> "\u001B[104m \u001B[0m"
}

fun mapDueTagToColor(dueTag: DueTag): String = when (dueTag) {
    DueTag.I -> "\u001B[102m \u001B[0m"
    DueTag.T -> "\u001B[103m \u001B[0m"
    DueTag.O -> "\u001B[101m \u001B[0m"
}


