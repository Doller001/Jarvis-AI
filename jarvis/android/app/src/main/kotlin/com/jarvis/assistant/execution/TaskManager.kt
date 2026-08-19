package com.jarvis.assistant.execution

import java.util.concurrent.atomic.AtomicBoolean

enum class TaskState { QUEUED, RUNNING, COMPLETED, CANCELLED, FAILED }
enum class TaskPriority { LOW, NORMAL, HIGH, CRITICAL }
enum class TaskType { FOREGROUND, BACKGROUND }

class CancellationToken {
    private val isCancelledFlag = AtomicBoolean(false)
    val isCancelled: Boolean get() = isCancelledFlag.get()

    fun cancel() {
        isCancelledFlag.set(true)
    }
}

data class Task(
    val id: String,
    val name: String,
    val type: TaskType = TaskType.BACKGROUND,
    val priority: TaskPriority = TaskPriority.NORMAL,
    var state: TaskState = TaskState.QUEUED,
    val cancellationToken: CancellationToken = CancellationToken()
)

class TaskManager {
    private val activeTasks = mutableMapOf<String, Task>()

    fun submitTask(task: Task): Task {
        activeTasks[task.id] = task
        task.state = TaskState.RUNNING
        return task
    }

    fun cancelTask(taskId: String): Boolean {
        val task = activeTasks[taskId] ?: return false
        task.cancellationToken.cancel()
        task.state = TaskState.CANCELLED
        return true
    }

    fun getTaskState(taskId: String): TaskState? = activeTasks[taskId]?.state
}
