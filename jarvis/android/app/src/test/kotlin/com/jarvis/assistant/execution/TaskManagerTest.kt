package com.jarvis.assistant.execution

import org.junit.Assert.*
import org.junit.Test

class TaskManagerTest {

    @Test
    fun testTaskSubmissionAndCancellation() {
        val manager = TaskManager()
        val task = Task(
            id = "t1",
            name = "Background Sync Task",
            type = TaskType.BACKGROUND
        )

        manager.submitTask(task)
        assertEquals(TaskState.RUNNING, manager.getTaskState("t1"))

        val cancelled = manager.cancelTask("t1")
        assertTrue(cancelled)
        assertEquals(TaskState.CANCELLED, manager.getTaskState("t1"))
        assertTrue(task.cancellationToken.isCancelled)
    }
}
